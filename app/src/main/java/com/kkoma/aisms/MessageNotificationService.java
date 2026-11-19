package com.kkoma.aisms;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;

public final class MessageNotificationService extends NotificationListenerService {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean connected;
    private static final Set<String> PACKAGES=new HashSet<>(Arrays.asList("com.google.android.apps.messaging","com.samsung.android.messaging","com.android.mms"));
    private static final long FRESH=180000L;
    private static final class Incoming {
        String key,pkg,number,text; long time; Notification.Action action; RemoteInput input; JSONArray history;
    }
    @Override public void onListenerConnected(){connected=true;if(Settings.enabled(this)&&Settings.notificationMode(this))Settings.error(this,"메시지 알림 대기");}
    @Override public void onListenerDisconnected(){connected=false;}
    @Override public void onDestroy(){connected=false;executor.shutdownNow();super.onDestroy();}
    private boolean active(){return connected&&Build.VERSION.SDK_INT>=30&&Settings.notificationMode(this)&&Settings.enabled(this)&&Settings.prefs(this).getBoolean("consent_v12",false);}
    private boolean trusted(StatusBarNotification s){
        if(s==null||!PACKAGES.contains(s.getPackageName()))return false;
        return s.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }
    @Override public void onNotificationPosted(StatusBarNotification s){
        if(!active()||!trusted(s))return;
        int revision=Settings.revision(this);
        try{
            Incoming msg=extract(s,true);
            if(msg==null||!eligible(msg,revision))return;
            String hash=SmsReceiver.fingerprint("notification\n"+msg.pkg+"\n"+msg.number+"\n"+msg.time+"\n"+msg.text);
            if(!SmsReceiver.reserve(this,hash,msg.number,System.currentTimeMillis()))return;
            Settings.error(this,"메시지 알림 수신 · 응답 준비 중");
            executor.execute(()->reply(msg,revision));
        }catch(Exception e){Settings.error(this,"메시지 알림 처리 실패");}
    }
    private boolean eligible(Incoming m,int revision){
        return active()&&Settings.revision(this)==revision&&Settings.contacts(this).contains(m.number)&&m.time>=Settings.prefs(this).getLong("enabled_since",0)&&System.currentTimeMillis()-m.time<=FRESH&&m.time<=System.currentTimeMillis()+10000L;
    }
    private void reply(Incoming msg,int revision){
        try{
            if(!eligible(msg,revision))return;
            String key=Settings.key(this);if(key.isEmpty()){Settings.error(this,"API 키를 확인하세요");return;}
            Settings.error(this,"Gemini 응답 생성 중");
            String answer=Gemini.ask(key,msg.history,msg.text,Settings.minimum(this),Settings.tone(this));
            synchronized(Settings.class){
                if(!eligible(msg,revision))return;
                StatusBarNotification[] live=getActiveNotifications(new String[]{msg.key});
                Incoming current=null;if(live!=null)for(StatusBarNotification item:live)if(trusted(item)&&msg.key.equals(item.getKey()))current=extract(item,false);
                if(current==null||!msg.number.equals(current.number)||!msg.pkg.equals(current.pkg)||msg.time!=current.time||!msg.text.equals(current.text)){
                    Settings.error(this,"알림 변경 · 이전 답장 취소");return;
                }
                Bundle result=new Bundle();result.putCharSequence(current.input.getResultKey(),answer);
                Intent intent=new Intent();RemoteInput.addResultsToIntent(new RemoteInput[]{current.input},intent,result);
                current.action.actionIntent.send(this,0,intent);
                Settings.error(this,"메시지 앱에 답장 전달됨");
            }
        }catch(PendingIntent.CanceledException e){Settings.error(this,"알림 답장 만료");}
        catch(Exception e){Settings.error(this,e instanceof Gemini.Failure?e.getMessage():"알림 답장 실패 ("+e.getClass().getSimpleName()+")");}
    }
    private String phone(String value){
        if(value==null)return "";
        if(value.startsWith("tel:"))value=Uri.decode(value.substring(4));
        if(!value.matches("[+0-9() .-]{8,32}"))return "";
        String normalized=Settings.canonical(this,value);
        return normalized.replace("+","").length()>=8?normalized:"";
    }
    private String number(Person p){
        if(p==null)return "";
        String uri=p.getUri(),n=phone(uri);
        if(!n.isEmpty())return n;
        if(uri!=null&&uri.startsWith("content://com.android.contacts/contacts/lookup/")){
            try{
                Uri item=Uri.parse(uri);
                if(!"com.android.contacts".equals(item.getAuthority()))return "";
                Set<String> numbers=new HashSet<>();
                Uri contact=ContactsContract.Contacts.lookupContact(getContentResolver(),item);
                if(contact!=null){
                    String id=contact.getLastPathSegment();
                    try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},ContactsContract.CommonDataKinds.Phone.CONTACT_ID+"=?",new String[]{id},null)){
                        if(c!=null)while(c.moveToNext()){String candidate=phone(c.getString(0));if(!candidate.isEmpty())numbers.add(candidate);}
                    }
                }
                if(numbers.size()==1)return numbers.iterator().next();
            }catch(Exception ignored){}
            return "";
        }
        // A Person key is an opaque identifier, not proof of a phone number.
        return "";
    }
    private static boolean self(Person p,Person user){
        if(p==null)return true;
        if(user==null)return false;
        if(p.getKey()!=null&&p.getKey().equals(user.getKey()))return true;
        return p.getUri()!=null&&p.getUri().equals(user.getUri());
    }
    private static String body(Notification.MessagingStyle.Message m){
        if(m.getDataUri()!=null||m.getDataMimeType()!=null)return "";
        return m.getText()==null?"":m.getText().toString().trim();
    }
    private Incoming extract(StatusBarNotification s,boolean withHistory)throws Exception{
        if(!trusted(s))return null;
        Notification n=s.getNotification();if(n==null||n.extras==null||(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return null;
        Bundle e=n.extras;
        if(!e.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)||e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION))return null;
        Parcelable[] raw=e.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(raw==null||raw.length==0)return null;
        List<Notification.MessagingStyle.Message> list=Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw);
        if(list.isEmpty())return null;
        Person user=e.getParcelable(Notification.EXTRA_MESSAGING_PERSON);
        Notification.MessagingStyle.Message last=list.get(list.size()-1);
        Person sender=last.getSenderPerson();if(self(sender,user))return null;
        String number=number(sender),text=body(last);
        if(number.isEmpty()){if(withHistory)Settings.error(this,"알림 발신 번호 확인 불가");return null;}
        if(!Settings.contacts(this).contains(number)||text.isEmpty()||text.startsWith("[AI 자동응답]"))return null;
        // Refuse conversations that contain another or unidentifiable incoming participant.
        for(Notification.MessagingStyle.Message m:list)if(!self(m.getSenderPerson(),user)&&!number.equals(number(m.getSenderPerson())))return null;
        Notification.Action chosen=null; RemoteInput input=null;
        if(n.actions!=null)for(Notification.Action a:n.actions){
            if(a==null||a.actionIntent==null||!s.getPackageName().equals(a.actionIntent.getCreatorPackage())||a.getRemoteInputs()==null)continue;
            if(Build.VERSION.SDK_INT>=31&&(a.isAuthenticationRequired()||a.actionIntent.isImmutable()))continue;
            int semantic=a.getSemanticAction();
            if(semantic!=Notification.Action.SEMANTIC_ACTION_REPLY&&semantic!=Notification.Action.SEMANTIC_ACTION_NONE)continue;
            for(RemoteInput ri:a.getRemoteInputs())if(ri.getAllowFreeFormInput()&&!ri.isDataOnly()){chosen=a;input=ri;break;}
            if(chosen!=null)break;
        }
        if(chosen==null){if(withHistory)Settings.error(this,"메시지 앱의 알림 답장 기능 없음");return null;}
        Incoming m=new Incoming();m.key=s.getKey();m.pkg=s.getPackageName();m.number=number;m.text=text;m.time=last.getTimestamp();m.action=chosen;m.input=input;m.history=new JSONArray();
        if(withHistory&&Settings.count(this)>0){
            List<Notification.MessagingStyle.Message> prior=new ArrayList<>();
            Parcelable[] historic=e.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES);
            if(historic!=null)prior.addAll(Notification.MessagingStyle.Message.getMessagesFromBundleArray(historic));
            prior.addAll(list.subList(0,list.size()-1));prior.sort(Comparator.comparingLong(Notification.MessagingStyle.Message::getTimestamp));
            ArrayList<JSONObject> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(Notification.MessagingStyle.Message item:prior){
                if(item.getTimestamp()>m.time)continue;
                boolean own=self(item.getSenderPerson(),user);
                if(!own&&!number.equals(number(item.getSenderPerson())))continue;
                String value=body(item);if(value.isEmpty())continue;
                String marker=item.getTimestamp()+"\n"+own+"\n"+value;if(!seen.add(marker))continue;
                rows.add(new JSONObject().put("direction",own?"sent":"received").put("text",value));
            }
            for(int idx=Math.max(0,rows.size()-Settings.count(this));idx<rows.size();idx++)m.history.put(rows.get(idx));
        }
        return m;
    }
}
