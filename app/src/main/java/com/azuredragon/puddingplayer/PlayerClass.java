package com.azuredragon.puddingplayer;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import java.io.IOException;
import java.util.Objects;

public class PlayerClass {
    private static WifiManager.WifiLock wifiLock;
    private static MediaPlayer player;
    private static MediaSessionCompat session;
    private static MediaControllerCompat controller;
    private static int bufferedPosition;

    static boolean isCompleted;

    static boolean isNull() {
        return player == null;
    }

    static void initPlayer(Context mContext, MediaSessionCompat mSession) {
        if(!isNull()) return;
        session = mSession;
        controller = mSession.getController();
        player = new MediaPlayer();
        player.reset();
        player.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
        wifiLock = ((WifiManager) Objects.requireNonNull(mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE)))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "wifilock");
        wifiLock.acquire();
        player.setOnPreparedListener(playerPrepared);
        player.setOnCompletionListener(playerCompleted);
        player.setOnErrorListener(playerError);
    }

    static void setPlayerSource(String src) throws IOException {
        if(isNull()) throw new IllegalStateException("MediaPlayer is null.");
        player.reset();
        player.setDataSource(src);
        player.prepareAsync();
    }

    static void resumePlayer() {
        isCompleted = false;
        player.start();
        session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                .setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 1.0f)
                .build());
    }

    static void pausePlayer() {
        player.pause();
        session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                .setState(PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), 1.0f)
                .build());
    }

    static void seekTo(long pos) {
        if(isNull()) return;
        player.seekTo((int) pos);
        session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                .setState(session.getController().getPlaybackState().getState(),
                        player.getCurrentPosition(), 1.0f)
                .build()
        );
    }

    static void stopPlayer() {
        if(isNull()) return;
        player.reset();
        player.release();
        player = null;
    }

    private static MediaPlayer.OnPreparedListener playerPrepared = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            isCompleted = false;
            player.setOnBufferingUpdateListener(playerBuffering);
            controller.getTransportControls().play();
        }
    };

    private static MediaPlayer.OnBufferingUpdateListener playerBuffering = new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            bufferedPosition = percent * player.getDuration() / 100;
            session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                    .setBufferedPosition(bufferedPosition)
                    .build());
        }
    };

    private static MediaPlayer.OnCompletionListener playerCompleted = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            isCompleted = true;
            controller.getTransportControls().skipToNext();
        }
    };

    private static MediaPlayer.OnErrorListener playerError = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            stopPlayer();
            return false;
        }
    };
}
