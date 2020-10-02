package com.azuredragon.puddingplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class VideoInfo {
    private Context mContext;
    private String mVideoId;
    private MediaSessionCompat.QueueItem mQueueItem;
    private FileHandler mFileHandler;
    private OnErrorListener mOnErrorListener;
    private OnAudioSourcePreparedListener mOnInfoPreparedListener;
    private OnMediaMetadataLoaded mOnMediaMetadataLoaded;
    private static OnDecipheredListener mOnDecipheredListener;

    private JSONObject playerResponse;
    private String TAG = "VideoInfo";

    public int AUDIO_QUALITY_LOW = 0;
    public int AUDIO_QUALITY_HIGH = 1;

    public VideoInfo(Context context, MediaSessionCompat.QueueItem queueItem) {
        mContext = context;
        mQueueItem = queueItem;
        mVideoId = queueItem.getDescription().getExtras().getString("videoId");
        mFileHandler = new FileHandler(context);
    }

    public void setOnAudioSourcePreparedListener(OnAudioSourcePreparedListener onInfoPreparedListener) {
        mOnInfoPreparedListener = onInfoPreparedListener;
    }

    public void setOnMediaMetadataLoaded(OnMediaMetadataLoaded onMediaMetadataLoaded) {
        mOnMediaMetadataLoaded = onMediaMetadataLoaded;
    }

    public void setOnErrorListener(OnErrorListener onErrorListener) {
        mOnErrorListener = onErrorListener;
    }

    public void getInfo() throws JSONException, IOException {
        if(mVideoId == null) {
            Log.e(TAG, "Video ID cannot be null.");
            if(mOnErrorListener != null) mOnErrorListener.onError("Video ID cannot be null.");
            return;
        }

        Log.i(TAG, "Getting video info...");
        String eurl = Uri.encode("http://kej.tw/");
        int sts = 18529;
        String link = "https://www.youtube.com/get_video_info?" +
                jsonObjectToParam(new JSONObject(
                        "{eurl: \"" + eurl + "\"," +
                                "sts: \"" + sts + "\"," +
                                "video_id: \"" + mVideoId + "\"}"
                ));
        mFileHandler.setOnLoadFailedListener(new FileHandler.OnLoadFailedListener() {
            @Override
            public void onLoadFailed(String reason) {
                mOnErrorListener.onError(reason);
            }
        });
        mFileHandler.setOnDownloadCompletedListener(new FileHandler.OnDownloadCompletedListener() {
            @Override
            public void onCompleted(String fileContent) {
                        try {
                            playerResponse = new JSONObject(
                                    Uri.decode(paramToJsonObject(fileContent).getString("player_response")));
                            final JSONObject videoDetails = playerResponse.getJSONObject("videoDetails");
                            mOnDecipheredListener = new OnDecipheredListener() {
                                @Override
                                public void onDeciphered(final String audioLink) {
                                    try {
                                        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                                                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                                        Integer.parseInt(videoDetails.getString("lengthSeconds")) * 1000)
                                                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, audioLink)
                                                .build();
                                        if(mOnInfoPreparedListener != null)
                                            mOnInfoPreparedListener.onPrepared(metadata);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            getAudioLink(playerResponse.getJSONObject("streamingData")
                                    .getJSONArray("adaptiveFormats"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
            }
        });
        mFileHandler.downloadFile(link, mVideoId);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void getAudioLink(JSONArray adaptiveFormats) throws JSONException {
        JSONArray array = new JSONArray();
        for(int i = 0; i < adaptiveFormats.length(); i++) {
            if(adaptiveFormats.getJSONObject(i).has("audioQuality"))
                array.put(array.length(), adaptiveFormats.getJSONObject(i));
        }

        if(array.getJSONObject(0).has("signatureCipher")) {
            final JSONObject signatureCipher = paramToJsonObject(array.getJSONObject(0)
                    .getString("signatureCipher"));
            for(int i = 0; i < adaptiveFormats.length(); i++) {
                if(adaptiveFormats.getJSONObject(i).has("audioQuality")) {
                    array.put(array.length(), adaptiveFormats.getJSONObject(i));
                }
            }
            final WebView wv = new WebView(mContext);
            final WebAppInterface webInterface = new WebAppInterface();
            wv.addJavascriptInterface(webInterface, "Android");
            WebSettings webSettings = wv.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setAppCacheEnabled(true);
            wv.loadUrl("file:///android_asset/decipher.html");

            final Handler timeOutHandler = new Handler();
            final Runnable onTimeOut = new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Webview timeout!  Retrying...");
                    wv.loadUrl("file:///android_asset/decipher.html");
                }
            };
            timeOutHandler.postDelayed(onTimeOut, 7500);
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    timeOutHandler.removeCallbacks(onTimeOut);
                    try {
                        webInterface.decipher(view, Uri.decode(signatureCipher.getString("url")),
                                Uri.decode(signatureCipher.getString("s")));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        else {
            if(mOnDecipheredListener != null)
                mOnDecipheredListener.onDeciphered(array.getJSONObject(0).getString("url"));
        }
    }

    void getMediaMetadata() {
        if(mQueueItem == null) return;
        final String videoId = mQueueItem.getDescription().getExtras().getString("videoId");
        String link = "https://www.youtube.com/oembed?url=youtu.be/" + videoId;
        mFileHandler.setOnDownloadCompletedListener(new FileHandler.OnDownloadCompletedListener() {
            @Override
            public void onCompleted(String fileContent) {
                try {
                    JSONObject oembedInfo = new JSONObject(fileContent);
                    Bundle bundle = mQueueItem.getDescription().getExtras();
                    bundle.putBoolean("hasMetadata", true);
                    MediaDescriptionCompat.Builder des = new MediaDescriptionCompat.Builder()
                            .setTitle(oembedInfo.getString("title"))
                            .setSubtitle(oembedInfo.getString("author_name"))
                            .setIconUri(Uri.parse(oembedInfo.getString("thumbnail_url")))
                            .setExtras(bundle);
                    if(mOnMediaMetadataLoaded != null) mOnMediaMetadataLoaded.onLoaded(des);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mFileHandler.downloadFile(link, "des_" + mQueueItem.getDescription().getExtras().getString("videoId"));
    }

    static String jsonObjectToParam(JSONObject object) throws JSONException {
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

    public interface OnAudioSourcePreparedListener {
        void onPrepared(MediaMetadataCompat metadata);
    }

    public interface OnMediaMetadataLoaded {
        void onLoaded(MediaDescriptionCompat.Builder builder);
    }

    public interface OnErrorListener {
        void onError(String reason);
    }

    public interface OnDecipheredListener {
        void onDeciphered(String audioLink);
    }

    public static class WebAppInterface {
        String mLink = "";
        WebView mWebView;

        void decipher(WebView wv, String link, String s) {
            mLink = link;
            mWebView = wv;
            wv.evaluateJavascript("decipher('" + s + "')", null);
        }

        /** Show a toast from the web page */
        @JavascriptInterface
        public void decipherCompleted(String sig) {
            if(mOnDecipheredListener != null)
                mOnDecipheredListener.onDeciphered(mLink + "&sig=" + sig);
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.destroy();
                }
            });
        }

        @JavascriptInterface
        public void test(String str) {
            Log.i("Test", str);
        }
    }
}
