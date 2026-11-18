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
            if(number.isEmpty()||!Settings.contacts(c).contains(number))return;
            StringBuilder body=new StringBuilder();
            for(SmsMessage p:parts) {if(p==null||!number.equals(Settings.canonical(c,p.getOriginatingAddress())))return; if(p.getMessageBody()!=null)body.append(p.getMessageBody());}
            String message=body.toString();if(message.trim().isEmpty()||message.startsWith(Settings.PREFIX)||message.startsWith("[AI 자동응답]"))return;
            long now=System.currentTimeMillis();int sub=i.getIntExtra("subscription",i.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",-1));
            String hash=fingerprint(number+"\n"+parts[0].getTimestampMillis()+"\n"+message);
            if(!reserve(c,hash,number,now))return;
            PersistableBundle extras=new PersistableBundle();extras.putString("from",from);extras.putString("number",number);extras.putString("message",message);extras.putLong("received",now);extras.putInt("sub",sub);extras.putInt("revision",Settings.revision(c));
            int id=nextId(c);
            JobInfo.Builder builder=new JobInfo.Builder(id,new ComponentName(c,ReplyJob.class)).setExtras(extras).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1000);
            if(c.getSystemService(JobScheduler.class).schedule(builder.build())!=JobScheduler.RESULT_SUCCESS){c.getSharedPreferences("guard",0).edit().remove("d:"+hash).apply();Settings.error(c,"자동 응답 예약 실패");}
        }catch(Exception e){Settings.error(c,"문자 처리 실패");}
    }
    private static String fingerprint(String s)throws Exception {return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);}
    private static synchronized int nextId(Context c){android.content.SharedPreferences p=c.getSharedPreferences("guard",0);int n=p.getInt("next",1000);p.edit().putInt("next",n==Integer.MAX_VALUE?1000:n+1).commit();return n;}
    private static synchronized boolean reserve(Context c,String hash,String sender,long now){
        android.content.SharedPreferences p=c.getSharedPreferences("guard",0);String d="d:"+hash;long old=p.getLong(d,0);if(old>0&&now-old<86400000L)return false;
        String rate="r:"+sender;long start=p.getLong(rate+":start",0);int count=p.getInt(rate+":count",0);if(now-start>=60000L||now<start){start=now;count=0;}if(count>=6)return false;
        android.content.SharedPreferences.Editor e=p.edit();for(java.util.Map.Entry<String,?> item:p.getAll().entrySet())if(item.getKey().startsWith("d:")&&item.getValue() instanceof Long&&now-(Long)item.getValue()>86400000L)e.remove(item.getKey());
        return e.putLong(d,now).putLong(rate+":start",start).putInt(rate+":count",count+1).commit();
    }
}
