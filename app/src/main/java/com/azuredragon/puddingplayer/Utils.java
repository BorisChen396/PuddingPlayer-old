package com.azuredragon.puddingplayer;

import android.content.Context;
import android.media.MediaRouter;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class Utils {
    static public JSONObject paramToJson(String param) throws JSONException {
        JSONObject obj = new JSONObject();
        String[] paramArray = param.split("&");
        for (String s : paramArray) {
            String[] item = s.split("=");
            obj.put(item[0], item[1]);
        }
        return obj;
    }

    static public String JsonToParam(JSONObject obj) throws JSONException {
        StringBuilder param = new StringBuilder();
        JSONArray names = obj.names();
        for(int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            param.append(key).append("=").append(obj.getString(key)).append("&");
        }
        param.deleteCharAt(param.length() - 1);
        return param.toString();
    }

    static public JSONObject decodeYTLink(String link) throws JSONException {
        JSONObject obj = new JSONObject();
        if(link.contains("youtu.be"))
            link = link.replace("youtu.be/", "www.youtube.comm/watch?v=");
        obj.put("videoId", Uri.parse(link).getQueryParameter("v"));
        obj.put("listId", Uri.parse(link).getQueryParameter("list"));
        return obj;
    }

    static public String secondToString(long second) {
        long hour = second / 3600;
        long min = (second / 60) % 60;
        long sec = second % 60;
        if(hour > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, min, sec);
        else return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    public static int dp2px(Context context, float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dpVal, context.getResources().getDisplayMetrics());
    }
}
