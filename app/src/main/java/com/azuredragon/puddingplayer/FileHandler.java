package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileHandler {
    private Context mContext;
    private ConnectivityManager mConnectionManager;
    private OnFileLoadedListener mOnFileLoaded;
    private OnDownloadCompletedListener mOnDownloaded;
    private OnLoadFailedListener mOnError;

    static String APPLICATION_DATA_DIR;

    public FileHandler(Context context) {
        mContext = context;
        APPLICATION_DATA_DIR = context.getApplicationInfo().dataDir + "/";
    }

    public boolean isNetworkConnected() {
        mConnectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return mConnectionManager.getActiveNetworkInfo() != null && mConnectionManager.getActiveNetworkInfo().isConnected();
    }

    public int checkURLConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        return connection.getResponseCode();
    }

    public boolean checkFileExistance(String path) {
        File file = new File(path);
        return file.exists();
    }

    public void setOnFileLoadedListener(OnFileLoadedListener onFileLoadedListener) {
        mOnFileLoaded = onFileLoadedListener;
    }

    public void setOnDownloadCompletedListener(OnDownloadCompletedListener onDownloadCompletedListener) {
        mOnDownloaded = onDownloadCompletedListener;
    }

    public void setOnLoadFailedListener(OnLoadFailedListener onLoadFailedListener) {
        mOnError = onLoadFailedListener;
    }

    public void downloadFile(final String url, final String fileName) throws IOException {
        final Runnable download = new Runnable() {
            @Override
            public void run() {
                try {
                    if(!isNetworkConnected()) {
                        if(mOnError != null) mOnError.onLoadFailed("Network connection failed.");
                        return;
                    }
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();
                    if(checkURLConnection(connection.getURL().toString()) != HttpURLConnection.HTTP_OK) {
                        if(mOnError != null) mOnError.onLoadFailed("HTTP Error " + connection.getResponseCode());
                        return;
                    }
                    InputStream in = connection.getInputStream();
                    FileOutputStream out = new FileOutputStream(APPLICATION_DATA_DIR + fileName);
                    byte[] buffer = new byte[1024];
                    int nextByte;
                    while((nextByte = in.read(buffer, 0, 1024)) != -1) {
                        out.write(buffer, 0, nextByte);
                    }
                    out.close();
                    in.close();

                    if(mOnDownloaded != null) {
                        Looper looper = Looper.getMainLooper();
                        new Handler(looper).post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mOnFileLoaded = new OnFileLoadedListener() {
                                        @Override
                                        public void onFileLoaded(String fileContent) {
                                            mOnDownloaded.onCompleted(fileContent);
                                        }
                                    };
                                    loadFile(fileName);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(download).start();
    }

    public void loadFile(String fileName) throws IOException {
        File file = new File(APPLICATION_DATA_DIR + fileName);
        if(!file.exists() || !file.canRead()) {
            if(mOnError != null) mOnError.onLoadFailed("Unable to load the specific file.");
            return;
        }
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder stringBuilder = new StringBuilder();
        String line = "";
        while((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        if(mOnFileLoaded != null) mOnFileLoaded.onFileLoaded(stringBuilder.toString());
    }

    public interface OnFileLoadedListener {
        void onFileLoaded(String fileContent);
    }

    public interface OnDownloadCompletedListener {
        void onCompleted(String fileContent);
    }

    public interface OnLoadFailedListener {
        void onLoadFailed(String reason);
    }
}
