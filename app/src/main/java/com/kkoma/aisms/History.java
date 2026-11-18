package com.kkoma.aisms;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

final class History {
    static JSONArray load(Context c,String from,String current,long received,int max)throws Exception{
        JSONArray result=new JSONArray();if(max<=0)return result;
        Set<String> aliases=Settings.aliases(c,from);if(aliases.isEmpty())return result;
        // Only query the selected sender's SMS rows; include both received and sent messages.
        String marks=String.join(",",Collections.nCopies(aliases.size(),"?"));
        String cleaned="replace(replace(replace(replace(replace(address,'-',''),' ',''),'(',''),')',''),'.','')";
        String where="type IN (1,2) AND date <= ? AND "+cleaned+" IN ("+marks+")";
        ArrayList<String> args=new ArrayList<>();args.add(Long.toString(received+1000));args.addAll(aliases);
        List<JSONObject> rows=new ArrayList<>();boolean skippedCurrent=false;
        try(Cursor cur=query(c,from,where,args,aliases,received)){
            if(cur==null)throw new IllegalStateException("SMS provider unavailable");
            while(cur.moveToNext()&&rows.size()<max){
                String address=cur.getString(0),body=cur.getString(1);int type=cur.getInt(2);long date=cur.getLong(3);
                if(!Settings.canonical(c,from).equals(Settings.canonical(c,address))&&!aliases.contains(android.telephony.PhoneNumberUtils.normalizeNumber(address)))continue;
                if(body==null)continue;
                if(!skippedCurrent&&type==1&&body.equals(current)&&Math.abs(date-received)<8000L){skippedCurrent=true;continue;}
                if(date>received)continue;
                JSONObject row=new JSONObject();row.put("direction",type==1?"received":"sent");row.put("text",body);rows.add(row);
            }
        }
        Collections.reverse(rows);for(JSONObject row:rows)result.put(row);return result;
    }
    private static Cursor query(Context c,String from,String where,ArrayList<String> args,Set<String> aliases,long received){
        String[] projection={"address","body","type","date"};
        try {return c.getContentResolver().query(Telephony.Sms.CONTENT_URI,projection,where,args.toArray(new String[0]),"date DESC, _id DESC");}
        catch(IllegalArgumentException | android.database.sqlite.SQLiteException ex){
            // Some vendor providers prohibit SQL scalar functions in the selection.
            Set<String> exact=new HashSet<>(aliases);exact.add(from);
            for(String number:aliases){String formatted=android.telephony.PhoneNumberUtils.formatNumber(number,"KR");if(formatted!=null)exact.add(formatted);}
            ArrayList<String> a=new ArrayList<>();a.add(Long.toString(received+1000));a.addAll(exact);
            return c.getContentResolver().query(Telephony.Sms.CONTENT_URI,projection,"type IN (1,2) AND date <= ? AND address IN ("+String.join(",",Collections.nCopies(exact.size(),"?"))+")",a.toArray(new String[0]),"date DESC, _id DESC");
        }
    }
}
