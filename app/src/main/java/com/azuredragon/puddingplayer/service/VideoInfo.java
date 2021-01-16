package com.azuredragon.puddingplayer.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;

public class VideoInfo {
    private Context mContext;
    private String mVideoId;
    private MediaSessionCompat.QueueItem mQueueItem;
    private FileLoader mFileLoader;
    private OnErrorListener mOnErrorListener;
    private OnAudioSourcePreparedListener mOnInfoPreparedListener;
    private static OnDecipheredListener mOnDecipheredListener;

    private JSONObject playerResponse;
    private static String TAG = "VideoInfo";

    private WebView wv;
    private WebAppInterface webInterface;
    private static OnGetSts onGetSts;

    public int AUDIO_QUALITY_LOW = 0;
    public int AUDIO_QUALITY_HIGH = 1;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    public VideoInfo(Context context, MediaSessionCompat.QueueItem queueItem) {
        mContext = context;
        mQueueItem = queueItem;
        mVideoId = queueItem.getDescription().getExtras().getString("videoId");
        mFileLoader = new FileLoader(context);
        mOnErrorListener = new OnErrorListener() {
            @Override
            public void onError(String reason) {

            }
        };
        wv = new WebView(mContext);
        webInterface = new WebAppInterface();
        wv.addJavascriptInterface(webInterface, "Android");
        WebSettings webSettings = wv.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
    }

    public void setOnAudioSourcePreparedListener(OnAudioSourcePreparedListener onInfoPreparedListener) {
        mOnInfoPreparedListener = onInfoPreparedListener;
    }

    public void setOnErrorListener(OnErrorListener onErrorListener) {
        mOnErrorListener = onErrorListener;
    }

    public void getInfo() {
        if(mVideoId == null) {
            Log.e(TAG, "Video ID cannot be null.");
            mOnErrorListener.onError("Video ID cannot be null.");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                mFileLoader.setOnDownloadCompletedListener(new FileLoader.OnDownloadCompletedListener() {
                    @Override
                    public void onCompleted(String fileContent) {
                        wv.loadUrl("file:" + mFileLoader.APPLICATION_DATA_DIR + "decipher.html");
                        wv.setWebViewClient(new WebViewClient() {
                            @Override
                            public void onPageFinished(WebView view, String url) {
                                super.onPageFinished(view, url);
                                onGetSts = new OnGetSts() {
                                    @Override
                                    public void onGet(String sts) {
                                        final String link = new Uri.Builder()
                                                .scheme("https")
                                                .authority("www.youtube.com")
                                                .appendPath("get_video_info")
                                                .appendQueryParameter("eurl", "http://kej.tw")
                                                .appendQueryParameter("sts", sts)
                                                .appendQueryParameter("video_id", mVideoId)
                                                .build().toString();
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    getVideoInfo(link);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }).start();
                                    }
                                };
                                webInterface.getSts(wv);
                            }
                        });
                    }
                });
                try {
                    mFileLoader.downloadFile(
                            "https://raw.githubusercontent.com/BorisChen396/PuddingPlayer/master/decipher/decipher.html",
                            "decipher.html");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void getVideoInfo(String link) throws Exception {
        mFileLoader.setOnLoadFailedListener(new FileLoader.OnLoadFailedListener() {
            @Override
            public void onLoadFailed(String reason) {
                mOnErrorListener.onError(reason);
            }
        });
        mFileLoader.setOnDownloadCompletedListener(new FileLoader.OnDownloadCompletedListener() {
            @Override
            public void onCompleted(String fileContent) {
                try {
                    playerResponse = new JSONObject(
                            Uri.decode(Utils.paramToJson(fileContent).getString("player_response")));
                    if(!playerResponse.getJSONObject("playabilityStatus").getString("status").equals("OK")) {
                        Log.e("Error", playerResponse.getJSONObject("playabilityStatus").getString("reason"));
                        mOnErrorListener.onError(playerResponse.getJSONObject("playabilityStatus").getString("reason"));
                        return;
                    }
                    mOnDecipheredListener = new OnDecipheredListener() {
                        @Override
                        public void onDeciphered(String audioLink) {
                            try {
                                Bundle bundle = mQueueItem.getDescription().getExtras();
                                bundle.putLong("lengthSeconds",
                                        playerResponse.getJSONObject("videoDetails").getLong("lengthSeconds"));
                                MediaDescriptionCompat des = new MediaDescriptionCompat.Builder()
                                        .setTitle(mQueueItem.getDescription().getTitle())
                                        .setSubtitle(mQueueItem.getDescription().getSubtitle())
                                        .setIconUri(mQueueItem.getDescription().getIconUri())
                                        .setExtras(bundle)
                                        .setMediaUri(Uri.parse(audioLink))
                                        .build();
                                if(mOnInfoPreparedListener != null)
                                    mOnInfoPreparedListener.onPrepared(des);
                            } catch (JSONException e) {
                                mOnErrorListener.onError(e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    };
                    getAudioLink(playerResponse.getJSONObject("streamingData")
                            .getJSONArray("adaptiveFormats"));
                } catch (JSONException e) {
                    mOnErrorListener.onError(e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        mFileLoader.downloadFile(link, "videoInfo");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void getAudioLink(JSONArray adaptiveFormats) throws JSONException {
        JSONArray array = new JSONArray();
        for(int i = 0; i < adaptiveFormats.length(); i++) {
            if(adaptiveFormats.getJSONObject(i).has("audioQuality"))
                array.put(array.length(), adaptiveFormats.getJSONObject(i));
        }

        if(array.getJSONObject(0).has("signatureCipher")) {
            final JSONObject signatureCipher = Utils.paramToJson(array.getJSONObject(0)
                    .getString("signatureCipher"));
            for(int i = 0; i < adaptiveFormats.length(); i++) {
                if(adaptiveFormats.getJSONObject(i).has("audioQuality")) {
                    array.put(array.length(), adaptiveFormats.getJSONObject(i));
                }
            }
            webInterface.decipher(wv, Uri.decode(signatureCipher.getString("url")),
                    Uri.decode(signatureCipher.getString("s")));
        }
        else {
            if(mOnDecipheredListener != null)
                mOnDecipheredListener.onDeciphered(array.getJSONObject(0).getString("url"));
        }
    }

    public static class WebAppInterface {
        void getSts(WebView wv) {
            wv.evaluateJavascript("getSts()", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    onGetSts.onGet(value);
                }
            });
        }

        void decipher(WebView wv, final String link, String s) {
            wv.evaluateJavascript("getDecipher('" + s + "')", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    value = value.replace("\"", "");
                    if(mOnDecipheredListener != null)
                        mOnDecipheredListener.onDeciphered(link + "&sig=" + value);
                }
            });
        }
    }

    private interface OnGetSts {
        void onGet(String sts);
    }

    public interface OnAudioSourcePreparedListener {
        void onPrepared(MediaDescriptionCompat des);
    }

    public interface OnErrorListener {
        void onError(String reason);
    }

    public interface OnDecipheredListener {
        void onDeciphered(String audioLink);
    }
}
