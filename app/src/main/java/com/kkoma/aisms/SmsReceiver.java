package com.kkoma.aisms;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(i.getAction()) || !Settings.enabled(c))return;
        try {
            SmsMessage[] parts=Telephony.Sms.Intents.getMessagesFromIntent(i);
            if(parts==null||parts.length==0||parts[0]==null)return;
            String from=parts[0].getOriginatingAddress(), number=Settings.canonical(c,from);
            if(!Settings.accepts(c,number)){Settings.error(c,"선택하지 않은 번호의 SMS 수신");return;}
            StringBuilder body=new StringBuilder();
            for(SmsMessage p:parts) {if(p==null||!number.equals(Settings.canonical(c,p.getOriginatingAddress())))return; if(p.getMessageBody()!=null)body.append(p.getMessageBody());}
            String message=body.toString();if(!Settings.safeMessage(message)||message.startsWith(Settings.PREFIX)||message.startsWith("[AI 자동응답]"))return;
            long now=System.currentTimeMillis();int sub=i.getIntExtra("subscription",i.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",-1));
            String hash=fingerprint(number+"\n"+parts[0].getTimestampMillis()+"\n"+message);
            if(!reserve(c,hash,number,message,now))return;
            if(!schedule(c,from,number,message,sub,now,"sms",hash))Settings.error(c,"자동 응답 예약 실패");
        }catch(Exception e){Settings.error(c,"문자 처리 실패");}
    }
    static String fingerprint(String s)throws Exception {return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);}
    static boolean schedule(Context c,String from,String number,String message,int sub,long received,String source,String hash){
        try{
            PersistableBundle extras=new PersistableBundle();
            extras.putString("from",from);extras.putString("number",number);extras.putString("message",message);
            extras.putLong("received",received);extras.putInt("sub",sub);extras.putInt("revision",Settings.revision(c));extras.putString("source",source);
            JobScheduler scheduler=c.getSystemService(JobScheduler.class);
            if(scheduler==null)throw new IllegalStateException("No JobScheduler");
            int id=nextId(c);
            ComponentName component=new ComponentName(c,ReplyJob.class);
            Settings.error(c,"mms".equals(source)?"MMS 텍스트 수신 · SMS 답장 준비 중":"SMS 수신 · 응답 준비 중");
            int scheduled=JobScheduler.RESULT_FAILURE;
            if(android.os.Build.VERSION.SDK_INT>=31){
                try{scheduled=scheduler.schedule(new JobInfo.Builder(id,component).setExtras(extras).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExpedited(true).build());}
                catch(IllegalArgumentException ex){android.util.Log.w("AI-SMS","Expedited job unavailable",ex);}
            }
            if(scheduled!=JobScheduler.RESULT_SUCCESS)
                scheduled=scheduler.schedule(new JobInfo.Builder(id,component).setExtras(extras).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1000).build());
            if(scheduled==JobScheduler.RESULT_SUCCESS)return true;
        }catch(Exception ex){android.util.Log.w("AI-SMS","SMS reply schedule failure",ex);}
        c.getSharedPreferences("guard",0).edit().remove("d:"+hash).apply();
        return false;
    }
    private static synchronized int nextId(Context c){android.content.SharedPreferences p=c.getSharedPreferences("guard",0);int n=p.getInt("next",1000);p.edit().putInt("next",n==Integer.MAX_VALUE?1000:n+1).commit();return n;}
    static synchronized boolean reserve(Context c,String hash,String sender,String message,long now)throws Exception{
        android.content.SharedPreferences p=c.getSharedPreferences("guard",0);String d="d:"+hash;long old=p.getLong(d,0);if(old>0&&now-old<86400000L)return false;
        String overlap="x:"+fingerprint(sender+"\n"+message.trim());
        long last=p.getLong(overlap,0);
        if(last>0&&now-last>=0&&now-last<30000L)return false;
        String rate="r:"+sender;long start=p.getLong(rate+":start",0);int count=p.getInt(rate+":count",0);if(now-start>=60000L||now<start){start=now;count=0;}if(count>=6)return false;
        long globalStart=p.getLong("globalStart",0);int globalCount=p.getInt("globalCount",0);if(now-globalStart>=600000L||now<globalStart){globalStart=now;globalCount=0;}if(Settings.allPeople(c)&&globalCount>=30){Settings.error(c,"자동 응답 전체 제한");return false;}
        long burst=p.getLong(rate+":burst",0);int burstCount=p.getInt(rate+":burstCount",0);if(now-burst>=600000L||now<burst){burst=now;burstCount=0;}if(burstCount>=12){Settings.error(c,"자동 응답 반복 제한");return false;}
        android.content.SharedPreferences.Editor e=p.edit();for(java.util.Map.Entry<String,?> item:p.getAll().entrySet())if((item.getKey().startsWith("d:")||item.getKey().startsWith("x:"))&&item.getValue() instanceof Long&&now-(Long)item.getValue()>(item.getKey().startsWith("d:")?86400000L:120000L))e.remove(item.getKey());
        return e.putLong(d,now).putLong(overlap,now).putLong(rate+":start",start).putInt(rate+":count",count+1).putLong(rate+":burst",burst).putInt(rate+":burstCount",burstCount+1).putLong("globalStart",globalStart).putInt("globalCount",Math.min(100000,globalCount+1)).commit();
    }
}
