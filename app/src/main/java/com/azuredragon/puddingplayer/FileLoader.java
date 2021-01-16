package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

public class FileLoader {
    private Context mContext;
    private OnFileLoadedListener mOnFileLoaded;
    private OnDownloadCompletedListener mOnDownloaded;
    private OnLoadFailedListener mOnError;
    private NetworkHandler mNetworkHandler;

    public String APPLICATION_DATA_DIR;
    public String APPLICATION_CACHE_DIR;

    static String TYPE_PLAYLIST;
    static String TYPE_METADATA;
    static String TYPE_SMALL_ARTWORK;
    static String TYPE_LARGE_ARTWORK;

    public FileLoader(Context context) {
        mContext = context;
        APPLICATION_DATA_DIR = context.getApplicationInfo().dataDir + "/";
        APPLICATION_CACHE_DIR = context.getCacheDir().toString() + "/";
        TYPE_PLAYLIST = APPLICATION_DATA_DIR + "playlist";
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

    public void downloadFile(final String url, final String fileName) throws Exception {
        downloadFile(url, fileName, false);
    }

    public void downloadFile(final String url, String fileName, boolean isCache) throws Exception {
        fileName = (isCache ? APPLICATION_CACHE_DIR : APPLICATION_DATA_DIR) + fileName;
        if(isCache) {
            mOnFileLoaded = new OnFileLoadedListener() {
                @Override
                public void onFileLoaded(String fileContent) {
                    mOnDownloaded.onCompleted(fileContent);
                }
            };
            if(loadFile(fileName)) return;
        }

        NetworkHandler networkHandler = new NetworkHandler(url, mContext);
        networkHandler.setOnErrorListener(new NetworkHandler.OnErrorListener() {
            @Override
            public void onError(Bundle info) {
                if(mOnError != null) {
                    if(info.getInt("errorCode") == -1) {
                        mOnError.onLoadFailed(
                                String.format(Locale.getDefault(),
                                        mContext.getString(R.string.error_http_error),
                                        info.getInt("httpErrorCode"), info.getString("reason")));
                        new Exception("HTTP Error " + info.getInt("httpErrorCode") + ". Request URL = " + url).printStackTrace();
                    }
                    else {
                        mOnError.onLoadFailed(info.getString("reason"));
                        new Exception(info.getString("reason") + ". Request URL = " + url).printStackTrace();
                    }
                }
            }
        });
        InputStream in = networkHandler.getUrlDownloadStream();
        if(in == null) return;
        FileOutputStream out = new FileOutputStream(fileName);
        byte[] buffer = new byte[1024];
        int nextByte;
        while((nextByte = in.read(buffer, 0, 1024)) != -1) {
            out.write(buffer, 0, nextByte);
        }
        out.close();
        in.close();

        if(mOnDownloaded != null) {
            Looper looper = Looper.getMainLooper();
            final String finalFileName = fileName;
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
                        loadFile(finalFileName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public boolean loadFile(String fileName) throws IOException {
        File file = new File(fileName);
        if(!file.exists() || !file.canRead()) return false;
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder stringBuilder = new StringBuilder();
        String line = "";
        while((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        if(mOnFileLoaded != null) mOnFileLoaded.onFileLoaded(stringBuilder.toString());
        return true;
    }

    public void saveFile(String content, String fileName, boolean replaceControl) throws IOException {
        File file = new File(APPLICATION_DATA_DIR + fileName);
        if(replaceControl && file.exists()) {
            if(!file.delete()) throw new IOException("Unable to replace the specific file.");
        }
        if(!file.exists()) {
            if(!file.createNewFile()) throw new IOException("Unable to create the specific file.");
        }
        if(!file.canWrite()) throw new IOException("Unable to write the specific file.");
        FileOutputStream output = new FileOutputStream(file);
        output.write(content.getBytes(), 0, content.getBytes().length);
        output.flush();
        output.close();
    }

    public boolean copyFile(String source, String dest) throws IOException {
        if(!new File(source).exists()) {
            return false;
        }
        dest = APPLICATION_DATA_DIR + dest;
        FileInputStream inputStream = new FileInputStream(new File(source));
        byte[] data = new byte[1024];
        FileOutputStream outputStream =new FileOutputStream(new File(dest));
        while (inputStream.read(data) != -1) {
            outputStream.write(data);
        }
        inputStream.close();
        outputStream.close();
        return true;
    }

    public void deleteFile(String fileName) throws IOException {
        File file = new File(APPLICATION_DATA_DIR + fileName);
        if(!file.exists()) throw new IOException("Can't find the specific file.");
        if(!file.canWrite()) throw new IOException("Unable to write the specific file.");
        if(!file.delete()) throw new IOException("Unable to delete the specific file.");
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
