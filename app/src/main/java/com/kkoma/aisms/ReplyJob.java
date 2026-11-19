package com.kkoma.aisms;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.PersistableBundle;
import android.os.Build;
import android.telephony.SmsManager;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReplyJob extends JobService {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<Integer,AtomicBoolean> active=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters p){AtomicBoolean run=new AtomicBoolean(true);active.put(p.getJobId(),run);executor.execute(()->{
        try{process(p.getJobId(),p.getExtras(),run);}catch(Exception e){if(run.get())Settings.error(this,e instanceof Gemini.Failure?e.getMessage():e instanceof SecurityException?"문자 권한 확인 필요":"자동 응답 실패 ("+e.getClass().getSimpleName()+")");}
        finally{if(run.getAndSet(false))jobFinished(p,false);active.remove(p.getJobId(),run);}
    });return true;}
    @Override public boolean onStopJob(JobParameters p){AtomicBoolean b=active.remove(p.getJobId());if(b!=null)b.set(false);return false;}
    @Override public void onDestroy(){for(AtomicBoolean b:active.values())b.set(false);executor.shutdownNow();super.onDestroy();}
    private boolean allowed(PersistableBundle e,AtomicBoolean run){return run.get()&&Settings.enabled(this)&&Settings.revision(this)==e.getInt("revision")&&Settings.contacts(this).contains(e.getString("number",""))&&System.currentTimeMillis()-e.getLong("received")<600000L;}
    private void process(int jobId,PersistableBundle e,AtomicBoolean run)throws Exception{
        if(!allowed(e,run))return;
        String from=e.getString("from",""),message=e.getString("message",""),key=Settings.key(this);
        if(key.isEmpty()){Settings.error(this,"API 키를 확인하세요");return;}
        Settings.error(this,"이전 문자 조회 중");
        org.json.JSONArray history=History.load(this,from,message,e.getLong("received"),Settings.count(this));
        if(!allowed(e,run))return;
        Settings.error(this,"Gemini 응답 생성 중");
        String answer=Gemini.ask(key,history,message);
        if(answer.trim().isEmpty()){Settings.error(this,"AI 응답 없음");return;}
        if(!allowed(e,run))return;
        String text=answer.trim();if(!text.startsWith(Settings.PREFIX))text=Settings.PREFIX+text;
        SmsManager sms=Build.VERSION.SDK_INT>=31?getSystemService(SmsManager.class):SmsManager.getDefault();int sub=e.getInt("sub",-1);
        if(sub>=0){if(Build.VERSION.SDK_INT>=31)sms=sms.createForSubscriptionId(sub);else sms=SmsManager.getSmsManagerForSubscriptionId(sub);}
        ArrayList<String> parts=sms.divideMessage(text);
        if(parts.size()>20){Settings.error(this,"응답 길이 초과");return;}
        synchronized(Settings.class){
            if(!allowed(e,run))return;
            ArrayList<android.app.PendingIntent> callbacks=new ArrayList<>();
            for(int index=0;index<parts.size();index++){
                android.content.Intent intent=new android.content.Intent(this,SmsSentReceiver.class);
                intent.setAction("com.kkoma.aisms.SMS_SENT");
                intent.setData(android.net.Uri.parse("aisms-result://sent/"+jobId+"/"+index));
                intent.putExtra("jobId",jobId);intent.putExtra("revision",e.getInt("revision"));intent.putExtra("total",parts.size());intent.putExtra("index",index);
                callbacks.add(android.app.PendingIntent.getBroadcast(this,jobId,intent,android.app.PendingIntent.FLAG_ONE_SHOT|android.app.PendingIntent.FLAG_IMMUTABLE));
            }
            Settings.error(this,"SMS 발송 요청 중");
            if(parts.size()==1)sms.sendTextMessage(from,null,parts.get(0),callbacks.get(0),null);
            else sms.sendMultipartTextMessage(from,null,parts,callbacks,null);
        }
    }
}
