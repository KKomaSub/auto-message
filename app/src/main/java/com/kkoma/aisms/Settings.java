package com.kkoma.aisms;

import android.Manifest;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Base64;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Settings {
    static final String PREFIX = "[AI 자동응답] ";
    static final String[] SMS = {Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS};
    private static final String ALIAS = "ai_sms_key_v1";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static boolean permissions(Context c) { for (String p : SMS) if (c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) return false; return true; }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false) && (notificationMode(c)?notificationAccess(c):permissions(c)); }
    static int revision(Context c) { return prefs(c).getInt("revision",0); }
    static Set<String> contacts(Context c) { return new HashSet<>(prefs(c).getStringSet("contacts", Collections.emptySet())); }
    static int count(Context c) { return Math.max(0,Math.min(100,prefs(c).getInt("count",10))); }
    static int minimum(Context c){return Math.max(0,Math.min(700,prefs(c).getInt("min_chars",0)));}
    static boolean notificationMode(Context c){return prefs(c).getBoolean("notification_mode",false);}
    static boolean notificationAccess(Context c){return android.os.Build.VERSION.SDK_INT>=30&&c.getSystemService(android.app.NotificationManager.class).isNotificationListenerAccessGranted(new android.content.ComponentName(c,MessageNotificationService.class));}
    static synchronized void changeMode(Context c,boolean mode){
        toggle(c,false);
        prefs(c).edit().putBoolean("notification_mode",mode).commit();
    }
    static synchronized void toggle(Context c, boolean b) {
        prefs(c).edit().putBoolean("enabled",b).putInt("revision",revision(c)+1).putLong("enabled_since",System.currentTimeMillis()).remove("error").commit();
        if(!b) c.getSystemService(JobScheduler.class).cancelAll();
    }
    static String canonical(Context c,String n) {
        if(n==null) return "";
        String region="KR";
        try { TelephonyManager tm=c.getSystemService(TelephonyManager.class); String r=tm.getSimCountryIso(); if(r!=null && r.length()==2) region=r.toUpperCase(Locale.ROOT); }catch(Exception ignored){}
        String normalized=PhoneNumberUtils.normalizeNumber(n);
        if(!normalized.matches("\\+?[0-9]{5,15}")) return "";
        String e164=PhoneNumberUtils.formatNumberToE164(normalized,region);
        return e164!=null?e164:normalized;
    }
    static Set<String> aliases(Context c,String from) {
        Set<String> s=new HashSet<>(); String a=PhoneNumberUtils.normalizeNumber(from), b=canonical(c,from);
        if(!a.isEmpty()) s.add(a); if(!b.isEmpty()) s.add(b);
        if(b.startsWith("+82")) { s.add("0"+b.substring(3)); s.add("82"+b.substring(3)); s.add("0082"+b.substring(3)); }
        if(b.startsWith("+1")) {s.add(b.substring(2));s.add(b.substring(1));}
        return s;
    }
    static void error(Context c,String e) { prefs(c).edit().putString("error",e).apply(); }
    private static SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS)) return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return kg.generateKey();
    }
    static synchronized void saveKey(Context c,String key) throws Exception {
        if(key.isEmpty()){prefs(c).edit().remove("key").commit();return;}
        Cipher ci=Cipher.getInstance("AES/GCM/NoPadding");ci.init(Cipher.ENCRYPT_MODE,secret());
        byte[] encrypted=ci.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        prefs(c).edit().putString("key",Base64.encodeToString(ci.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit();
    }
    static synchronized String key(Context c) {
        String val=prefs(c).getString("key","");if(val.isEmpty())return "";
        try {String[] a=val.split(":",2);Cipher ci=Cipher.getInstance("AES/GCM/NoPadding");ci.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.DEFAULT)));return new String(ci.doFinal(Base64.decode(a[1],Base64.DEFAULT)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}
    }
}
