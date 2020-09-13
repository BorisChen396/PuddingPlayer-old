package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

public class VideoDecipher {
    static String TAG = "VideoDecipher";
    private static Context context;
    private static MediaSessionCompat session;
    private static String appDir;

    static void playMusicFromUri(String videoId, MediaSessionCompat mSession, Context mContext) throws Exception {
        context = mContext;
        session = mSession;
        appDir = context.getApplicationInfo().dataDir;
        getVideoInfo(videoId);
    }

    private static void getVideoInfo(final String videoId) throws Exception {
        String eurl = Uri.encode("http://kej.tw/");
        int sts = 18508;
        final String link = "https://www.youtube.com/get_video_info?" +
                jsonObjectToParam(new JSONObject(
                        "{eurl: \"" + eurl + "\"," +
                                "sts: \"" + sts + "\"," +
                                "video_id: \"" + videoId + "\"}"
                ));

        Runnable downloadVideoInfo = new Runnable() {
            @Override
            public void run() {
                InputStream input;
                String filePath = appDir + "/" + videoId;
                try {
                    input = new URL(link).openStream();
                    FileOutputStream output = new FileOutputStream(filePath);
                    byte[] buffer = new byte[1024];
                    int nextByte;
                    while((nextByte = input.read(buffer, 0, 1024)) != -1) {
                        output.write(buffer, 0, nextByte);
                    }
                    output.close();
                    input.close();

                    BufferedReader reader = new BufferedReader(new FileReader(filePath));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while((line = reader.readLine()) != null) {
                        builder.append(line).append("\n");
                    }
                    builder.deleteCharAt(builder.length() - 1);
                    decipherInfo(builder.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(downloadVideoInfo).start();
    }

    private static void decipherInfo(String videoInfo) throws Exception {
        JSONObject _videoInfo = paramToJsonObject(videoInfo);
        if(_videoInfo.getString("status").equals("fail")) {
            Log.i(TAG, URLDecoder.decode(_videoInfo.getString("reason"), "UTF-8"));
            return;
        }
        JSONObject playerResponse = new JSONObject(Uri.decode(_videoInfo.getString("player_response")));
        JSONArray adaptiveFormats = playerResponse.getJSONObject("streamingData").getJSONArray("adaptiveFormats");
        JSONArray audioSources = new JSONArray();
        boolean hasLowQuality = false;
        for(int spec = 0; spec < adaptiveFormats.length(); spec++) {
            if(adaptiveFormats.getJSONObject(spec).has("audioQuality")) {
                audioSources.put(audioSources.length(), adaptiveFormats.getJSONObject(spec));
                if(audioSources.getJSONObject(audioSources.length() - 1)
                        .getString("audioQuality").equals("AUDIO_QUALITY_LOW")) {
                    hasLowQuality = true;
                }
            }
        }
        final String musicSource;
        if(audioSources.getJSONObject(0).has("url")) {
            musicSource = audioSources.getJSONObject(0).getString("url");
        }
        else {
            JSONObject signatureCipher = paramToJsonObject(audioSources.getJSONObject(0)
                    .getString("signatureCipher"));
            musicSource = Uri.decode(signatureCipher.getString("url")) + "&" +
                    jsonObjectToParam(new JSONObject("{" +
                            "\"" + Uri.decode(signatureCipher.getString("sp")) + "\": \"" +
                            Dv.decipherSignature(Uri.decode(signatureCipher.getString("s"))) +
                            "\"}"));
        }

        final Bundle bundle = new Bundle();
        bundle.putString("duration", playerResponse.getJSONObject("videoDetails").getString("lengthSeconds"));
        bundle.putString("videoId", playerResponse.getJSONObject("videoDetails").getString("videoId"));
        bundle.putLong("expireTime", (System.currentTimeMillis() / 1000) +
                Long.parseLong(playerResponse.getJSONObject("streamingData").getString("expiresInSeconds")));
        bundle.putBoolean("isDeciphered", true);

        final String albumArt = playerResponse.getJSONObject("videoDetails")
                .getJSONObject("thumbnail").getJSONArray("thumbnails")
                .getJSONObject(playerResponse.getJSONObject("videoDetails")
                        .getJSONObject("thumbnail").getJSONArray("thumbnails").length() - 2)
                .getString("url");

        final JSONObject _playerResponse = playerResponse;
        Runnable downloadAlbum = new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = new URL(albumArt).openStream();
                    FileOutputStream out = new FileOutputStream(appDir + "/albumArt_" + bundle.getString("videoId"));
                    byte[] buffer = new byte[1024];
                    int nextByte;
                    while((nextByte = in.read(buffer, 0, 1024)) != -1) {
                        out.write(buffer, 0, nextByte);
                    }
                    out.close();
                    in.close();

                    MediaDescriptionCompat des = new MediaDescriptionCompat.Builder()
                            .setTitle(URLDecoder.decode(
                                    _playerResponse.getJSONObject("videoDetails").getString("title"), "UTF-8"))
                            .setSubtitle(URLDecoder.decode(
                                    _playerResponse.getJSONObject("videoDetails").getString("author"), "UTF-8"))
                            .setMediaUri(Uri.parse(musicSource))
                            .setIconUri(Uri.parse(appDir + "/albumArt_" + bundle.getString("videoId")))
                            .setExtras(bundle)
                            .build();
                    List<MediaSessionCompat.QueueItem> newQueue = session.getController().getQueue();
                    newQueue.set(PlaybackService.currentItem,
                            new MediaSessionCompat.QueueItem(des, PlaybackService.currentItem));
                    session.setQueue(newQueue);
                    session.getController().getTransportControls().skipToQueueItem(PlaybackService.currentItem);
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(downloadAlbum).start();
    }

    private static class Dv {
        private static String decipherSignature(String s) {
            String[] t = s.split("");
            String[] a;
            if(t[0].equals("")) {
                a = new String[t.length - 1];
                System.arraycopy(t, 1, a, 0, t.length);
            }
            else {
                a = t;
            }
            a = Dv.p4(a, 2);
            Dv.tS(a, 28);
            Dv.tS(a, 46);
            a = Dv.Je(a);
            a = Dv.p4(a, 1);
            StringBuilder sig = new StringBuilder();
            for (String value : a) {
                sig.append(value);
            }
            return sig.toString();
        }

        private static String[] Je(String[] a) {
            String[] b = new String[a.length];
            for(int i = 0; i < b.length; i++) {
                b[i] = a[a.length - i - 1];
            }
            return b;
        }

        private static String[] p4(String[] a, int b) {
            String[] c = new String[a.length - b];
            if (a.length - b >= 0) System.arraycopy(a, b, c, 0, a.length - b);
            return c;
        }

        private static void tS(String[] a, int b) {
            String c = a[0];
            a[0] = a[b % a.length];
            a[b % a.length] = c;
        }
    }

    private static String jsonObjectToParam(JSONObject object) throws JSONException {
        JSONArray param = object.names();
        StringBuilder paramString = new StringBuilder();
        if(param == null) return null;
        for(int i = 0; i < param.length(); i++) {
            if(i != 0) paramString.append("&");
            paramString.append(param.getString(i)).append("=").append(object.getString(param.getString(i)));
        }
        return paramString.toString();
    }

    static JSONObject paramToJsonObject(String param) throws JSONException {
        JSONObject paramObject = new JSONObject();
        String[] _param = param.split("&");
        for (String s : _param) {
            paramObject.put(s.split("=")[0], s.split("=")[1]);
        }
        return paramObject;
    }
}
