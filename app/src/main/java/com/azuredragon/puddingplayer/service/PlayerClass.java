package com.azuredragon.puddingplayer.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.azuredragon.puddingplayer.NetworkHandler;

import java.io.IOException;
import java.util.Objects;

import static java.net.HttpURLConnection.HTTP_OK;

class PlayerClass {
    private static MediaPlayer player;
    private Context context;
    private MediaSessionCompat session;
    long bufferedPosition;

    private OnErrorListener mOnErrorListener = new OnErrorListener() {
        @Override
        public void onError(Bundle error) {}
    };

    void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    boolean isNull() {
        return player == null;
    }

    boolean isCompleted() {
        return player.getCurrentPosition() >= player.getDuration();
    }

    PlayerClass(Context mContext, MediaSessionCompat mSession) {
        if(!isNull()) return;
        context = mContext;
        session = mSession;
        mOnErrorListener = new OnErrorListener() {
            @Override
            public void onError(Bundle error) {}
        };
    }

    private void initPlayer() {
        player = new MediaPlayer();
        player.reset();
        player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        WifiManager.WifiLock wifiLock = ((WifiManager) Objects.requireNonNull(context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE)))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "wifilock");
        wifiLock.acquire();
        player.setOnPreparedListener(playerPrepared);
        player.setOnCompletionListener(playerCompleted);
        player.setOnErrorListener(playerError);
    }

    void setPlayerSource(final String src) throws IOException {
        if(isNull()) initPlayer();
        Bundle sourceState = new NetworkHandler(src, context).getUrlHttpStatus();
        if(sourceState.getInt("responseCode") != HTTP_OK) {
            Bundle error = new Bundle();
            error.putInt("errorCode", -1);
            error.putString("reason", sourceState.getString("responseMessage"));
            mOnErrorListener.onError(error);
            return;
        }
        player.reset();
        player.setDataSource(src);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i("Player", "Preparing...");
                player.prepareAsync();
            }
        }).start();
    }

    int getCurrentPosition() {
        return player.getCurrentPosition();
    }

    void resumePlayer() {
        player.start();
    }

    void pausePlayer() {
        player.pause();
    }

    boolean waitForBuffering = false;
    void seekTo(long pos) {
        if(isNull()) return;
        player.seekTo((int) pos);
        if(bufferedPosition < pos) {
            waitForBuffering = true;
            session.getController().getTransportControls().pause();
        }
    }

    void stopPlayer() {
        if(isNull()) return;
        player.reset();
        player.release();
        player = null;
    }

    private MediaPlayer.OnPreparedListener playerPrepared = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            player.setOnBufferingUpdateListener(playerBuffering);
            session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                    .setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 1.0f)
                    .build());
            session.getController().getTransportControls().play();
        }
    };

    private MediaPlayer.OnBufferingUpdateListener playerBuffering = new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                    .setBufferedPosition(percent * player.getDuration() / 100)
                    .build());
            bufferedPosition = mp.getDuration() * percent / 100;
            if(waitForBuffering) {
                waitForBuffering = false;
                session.getController().getTransportControls().play();
            }
        }
    };

    private MediaPlayer.OnCompletionListener playerCompleted = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            session.getController().getTransportControls().skipToNext();
        }
    };

    private MediaPlayer.OnErrorListener playerError = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Bundle error = new Bundle();
            error.putInt("errorCode", -2);
            error.putString("reason", what + "" + extra);
            mOnErrorListener.onError(error);
            return false;
        }
    };

    interface OnErrorListener {
        void onError(Bundle error);
    }
}
