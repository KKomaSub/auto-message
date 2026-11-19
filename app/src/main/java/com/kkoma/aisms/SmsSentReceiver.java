package com.kkoma.aisms;

import android.app.Activity;
import android.content.*;
import android.telephony.SmsManager;

public final class SmsSentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!"com.kkoma.aisms.SMS_SENT".equals(i.getAction()))return;
        if(!Settings.enabled(c)||i.getIntExtra("revision",-1)!=Settings.revision(c))return;
        int job=i.getIntExtra("jobId",-1),total=i.getIntExtra("total",0),index=i.getIntExtra("index",-1);
        if(job<0||total<1||total>20||index<0||index>=total)return;
        SharedPreferences p=c.getSharedPreferences("sms_results",Context.MODE_PRIVATE);
        String tag="job:"+job+":";
        synchronized(Settings.class){
            int mask=p.getInt(tag+"mask",0),bit=1<<index;
            if((mask&bit)!=0)return;
            int error=p.getInt(tag+"error",Activity.RESULT_OK);
            int result=getResultCode();
            if(result!=Activity.RESULT_OK&&error==Activity.RESULT_OK)error=result;
            mask|=bit;
            if(mask==((1<<total)-1)){
                p.edit().remove(tag+"mask").remove(tag+"error").apply();
                if(error==Activity.RESULT_OK)Settings.error(c,"SMS 발송 성공");
                else if(error==SmsManager.RESULT_ERROR_NO_SERVICE)Settings.error(c,"SMS 발송 실패 · 통신 서비스 없음");
                else if(error==SmsManager.RESULT_ERROR_RADIO_OFF)Settings.error(c,"SMS 발송 실패 · 비행기 모드 확인");
                else Settings.error(c,"SMS 발송 실패 (코드 "+error+")");
            }else p.edit().putInt(tag+"mask",mask).putInt(tag+"error",error).apply();
        }
    }
}
