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
        try{process(p.getExtras(),run);}catch(Exception e){if(run.get())Settings.error(this,e instanceof Gemini.Failure?e.getMessage():"자동 응답 실패");}
        finally{if(run.getAndSet(false))jobFinished(p,false);active.remove(p.getJobId(),run);}
    });return true;}
    @Override public boolean onStopJob(JobParameters p){AtomicBoolean b=active.remove(p.getJobId());if(b!=null)b.set(false);return false;}
    @Override public void onDestroy(){for(AtomicBoolean b:active.values())b.set(false);executor.shutdownNow();super.onDestroy();}
    private boolean allowed(PersistableBundle e,AtomicBoolean run){return run.get()&&Settings.enabled(this)&&Settings.revision(this)==e.getInt("revision")&&Settings.contacts(this).contains(e.getString("number",""))&&System.currentTimeMillis()-e.getLong("received")<600000L;}
    private void process(PersistableBundle e,AtomicBoolean run)throws Exception{
        if(!allowed(e,run))return;
        String from=e.getString("from",""),message=e.getString("message",""),key=Settings.key(this);
        if(key.isEmpty()){Settings.error(this,"API 키를 확인하세요");return;}
        String answer=Gemini.ask(key,History.load(this,from,message,e.getLong("received"),Settings.count(this)),message);
        if(answer.trim().isEmpty()){Settings.error(this,"AI 응답 없음");return;}
        if(!allowed(e,run))return;
        String text=answer.trim();if(!text.startsWith(Settings.PREFIX))text=Settings.PREFIX+text;
        SmsManager sms=Build.VERSION.SDK_INT>=31?getSystemService(SmsManager.class):SmsManager.getDefault();int sub=e.getInt("sub",-1);
        if(sub>=0){if(Build.VERSION.SDK_INT>=31)sms=sms.createForSubscriptionId(sub);else sms=SmsManager.getSmsManagerForSubscriptionId(sub);}
        ArrayList<String> parts=sms.divideMessage(text);
        if(parts.size()>20){Settings.error(this,"응답 길이 초과");return;}
        synchronized(Settings.class){if(!allowed(e,run))return;if(parts.size()==1)sms.sendTextMessage(from,null,parts.get(0),null,null);else sms.sendMultipartTextMessage(from,null,parts,null,null);}
        Settings.error(this,"");
    }
}
