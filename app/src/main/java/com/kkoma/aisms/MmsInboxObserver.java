package com.kkoma.aisms;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class MmsInboxObserver {
    private final Context context;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ContentObserver observer;
    private volatile boolean started=false;

    MmsInboxObserver(Context context){this.context=context.getApplicationContext();}
    void start(){
        if(started)return;
        started=true;
        observer=new ContentObserver(handler){
            @Override public void onChange(boolean selfChange){scanAsync();}
            @Override public void onChange(boolean selfChange,Uri uri){scanAsync();}
        };
        try{
            context.getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,true,observer);
            scanAsync();
        }catch(SecurityException ex){
            Log.w("AI-SMS","MMS provider observer not permitted",ex);stop();
        }
    }
    void stop(){
        started=false;
        if(observer!=null){try{context.getContentResolver().unregisterContentObserver(observer);}catch(Exception ignored){}observer=null;}
        executor.shutdownNow();
    }
    private void scanAsync(){
        if(!started)return;
        try{executor.execute(this::scan);}catch(RejectedExecutionException ignored){}
    }
    private void scan(){
        if(!started||!Settings.notificationMode(context)||!Settings.enabled(context))return;
        long now=System.currentTimeMillis();
        long enabledAt=Settings.prefs(context).getLong("enabled_since",now);
        long since=Math.max(enabledAt,now-240000L);
        try(Cursor cur=context.getContentResolver().query(Telephony.Mms.Inbox.CONTENT_URI,new String[]{"_id","date"},null,null,"date DESC")){
            if(cur==null)return;
            int checked=0;
            while(started&&cur.moveToNext()&&checked++<30){
                long id=cur.getLong(0);
                long time=cur.getLong(1)*1000L;
                if(time<since-2000L||time>now+10000L)continue;
                String sender=sender(id);
                String number=Settings.canonical(context,sender);
                if(!Settings.accepts(context,number))continue;
                String message=text(id);
                if(!Settings.safeMessage(message))continue;
                String hash=SmsReceiver.fingerprint("mms\n"+id+"\n"+number+"\n"+message);
                long received=System.currentTimeMillis();
                if(!SmsReceiver.reserve(context,hash,number,message,received))continue;
                if(!SmsReceiver.schedule(context,sender,number,message,-1,received,"mms",hash))
                    Settings.error(context,"MMS 응답 예약 실패");
            }
        }catch(SecurityException ex){Settings.error(context,"MMS 저장소 읽기 권한 필요");}
         catch(Exception ex){Log.w("AI-SMS","MMS scan issue",ex);}
    }
    private String sender(long id){
        Uri addr=Uri.parse("content://mms/"+id+"/addr");
        try(Cursor cur=context.getContentResolver().query(addr,new String[]{"address","type"},"type=137",null,null)){
            if(cur==null)return "";
            String found="";
            while(cur.moveToNext()){
                String value=cur.getString(0);
                if(value==null||value.equals("insert-address-token"))continue;
                if(!found.isEmpty()&&!found.equals(value))return "";
                found=value;
            }
            return found;
        }catch(Exception e){return "";}
    }
    private String text(long id){
        Uri part=Uri.parse("content://mms/"+id+"/part");
        StringBuilder out=new StringBuilder();
        try(Cursor cur=context.getContentResolver().query(part,new String[]{"_id","ct","text"},null,null,null)){
            if(cur==null)return "";
            while(cur.moveToNext()){
                String mime=cur.getString(1);
                if(mime==null)continue;
                if(mime.startsWith("image/")||mime.startsWith("video/")||mime.startsWith("audio/"))return "";
                if(!"text/plain".equalsIgnoreCase(mime))continue;
                String value=cur.getString(2);
                if(value==null){
                    try(InputStream in=context.getContentResolver().openInputStream(Uri.parse("content://mms/part/"+cur.getLong(0)))){
                        if(in!=null){
                            ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[] block=new byte[1024];int n;
                            while((n=in.read(block))!=-1&&buf.size()<65536)buf.write(block,0,Math.min(n,65536-buf.size()));
                            value=new String(buf.toByteArray(),StandardCharsets.UTF_8);
                        }
                    }
                }
                if(value!=null&&!value.trim().isEmpty()){
                    if(out.length()>0)out.append('\n');
                    out.append(value.trim());
                    if(out.length()>10000)return "";
                }
            }
        }catch(Exception ex){return "";}
        return out.toString().trim();
    }
}
