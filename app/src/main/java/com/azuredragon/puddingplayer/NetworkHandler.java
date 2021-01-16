package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkHandler {
    Context mContext;
    String mUrl;
    private OnErrorListener mOnErrorListener;

    public NetworkHandler(String url, Context context) {
        mUrl = url;
        mContext = context;
        mOnErrorListener = new OnErrorListener() {
            @Override
            public void onError(Bundle info) {
            }
        };
    }

    public void setOnErrorListener(OnErrorListener onErrorListener) {
        mOnErrorListener = onErrorListener;
    }

    public boolean isNetworkConnected() throws Exception {
        ConnectivityManager mConnectionManager;
        mConnectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(mConnectionManager == null) throw new Exception("ConnectivityManager returns null value.");
        return mConnectionManager.getActiveNetworkInfo() != null && mConnectionManager.getActiveNetworkInfo().isConnected();
    }

    public Bundle getUrlHttpStatus() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(mUrl).openConnection();
        Bundle status = new Bundle();
        status.putInt("responseCode", connection.getResponseCode());
        if(connection.getResponseCode() == 404) {
            status.putString("responseMessage", mContext.getString(R.string.error_http_error_404));
        }
        else {
            status.putString("responseMessage", connection.getResponseMessage());
        }
        return status;
    }

    public InputStream getUrlDownloadStream() throws Exception {
        if(!isNetworkConnected()) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt("errorCode", -2);
            errorBundle.putString("reason", mContext.getString(R.string.error_no_internet));
            mOnErrorListener.onError(errorBundle);
            return null;
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(mUrl).openConnection();
        connection.setRequestMethod("GET");
        try {
            connection.connect();
        } catch (IOException e) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt("errorCode", -2);
            errorBundle.putString("reason", e.getMessage());
            mOnErrorListener.onError(errorBundle);
            return null;
        }
        Bundle httpStatus = getUrlHttpStatus();
        if(httpStatus.getInt("responseCode") != HttpURLConnection.HTTP_OK) {
            Bundle errorBundle = new Bundle();
            errorBundle.putInt("errorCode", -1);
            errorBundle.putInt("httpErrorCode", httpStatus.getInt("responseCode"));
            errorBundle.putString("reason", httpStatus.getString("responseMessage"));
            mOnErrorListener.onError(errorBundle);
            return null;
        }
        return connection.getInputStream();
    }

    public interface OnErrorListener {
        void onError(Bundle info);
    }
}
