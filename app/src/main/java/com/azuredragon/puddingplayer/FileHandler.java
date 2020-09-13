package com.azuredragon.puddingplayer;

import android.content.Context;
import android.net.ConnectivityManager;

public class FileHandler {
    Context mContext;
    ConnectivityManager mConnectionManager;

    public FileHandler(Context context) {
        mContext = context;
    }

    public boolean isNetworkConnected() {
        mConnectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        return mConnectionManager.getActiveNetworkInfo() != null && mConnectionManager.getActiveNetworkInfo().isConnected();
    }
}
