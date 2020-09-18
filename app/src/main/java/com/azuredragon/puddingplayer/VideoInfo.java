package com.azuredragon.puddingplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class VideoInfo {
    private Context mContext;
    private String mVideoId;
    private MediaSessionCompat.QueueItem mQueueItem;
    private FileHandler mFileHandler;
    private OnErrorListener mOnErrorListener;
    private OnInfoPreparedListener mOnInfoPreparedListener;
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

    public JSONObject getPlayerResponse() {
        return playerResponse;
    }

    public void setOnInfoPreparedListener(OnInfoPreparedListener onInfoPreparedListener) {
        mOnInfoPreparedListener = onInfoPreparedListener;
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
        int sts = 18519;
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
                                public void onDeciphered(String audioLink) {
                                    Log.i(TAG, audioLink);
                                    try {
                                        Bundle bundle = mQueueItem.getDescription().getExtras();
                                        bundle.putBoolean("isDeciphered", true);
                                        bundle.putInt("lengthSeconds", Integer.parseInt(
                                                videoDetails.getString("lengthSeconds")));
                                        MediaDescriptionCompat.Builder desBuilder =
                                                getMediaDescription(Uri.parse(audioLink), videoDetails);
                                        desBuilder.setExtras(bundle);
                                        if(mOnInfoPreparedListener != null)
                                            mOnInfoPreparedListener.onPrepared(desBuilder.build());
                                    } catch (JSONException | UnsupportedEncodingException e) {
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
            wv.loadUrl("file:///android_asset/decipher.html");
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
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

    private MediaDescriptionCompat.Builder getMediaDescription(Uri audioUri, JSONObject videoDetails) throws JSONException,
            UnsupportedEncodingException {
        Log.i(TAG, videoDetails.getString("title"));
        MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                .setTitle(URLDecoder.decode(videoDetails.getString("title"), "UTF-8"))
                .setSubtitle(URLDecoder.decode(videoDetails.getString("author"), "UTF-8"))
                .setIconUri(Uri.parse(videoDetails.getJSONObject("thumbnail").getJSONArray("thumbnails")
                        .getJSONObject(videoDetails.getJSONObject("thumbnail")
                                .getJSONArray("thumbnails").length() - 2).getString("url")))
                .setMediaUri(audioUri)
                .setExtras(new Bundle());
        return builder;
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

    public interface OnInfoPreparedListener {
        void onPrepared(MediaDescriptionCompat description);
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
