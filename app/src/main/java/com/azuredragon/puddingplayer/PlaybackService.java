package com.azuredragon.puddingplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.BundleCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PlaybackService extends MediaBrowserServiceCompat {
    String TAG = "PlaybackService";
    boolean isRunning = false;

    MediaSessionCompat session;
    static int currentItem;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        session = new MediaSessionCompat(this, TAG);
        session.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .build());
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        session.setCallback(sessionCallback);
        session.setQueue(new ArrayList<MediaSessionCompat.QueueItem>());
        setSessionToken(session.getSessionToken());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Log.i(TAG, "Received client request.");
        startService(new Intent(this, PlaybackService.class));
        return new BrowserRoot("", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {

    }

    void showPlayerNotification(final MediaDescriptionCompat des) {
        String channelId = "playerNotification";
        final NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(PlaybackService.this, channelId);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId,
                    getResources().getString(R.string.notifiyChannel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            assert manager != null;
            manager.createNotificationChannel(channel);
        }
        else {
            notifyBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        }
        notifyBuilder.setContentTitle(des.getTitle())
                .setContentText(des.getSubtitle())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.getSessionToken()))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if(des.getIconUri() != null) {
            Runnable loadAlbumArt = new Runnable() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(des.getIconUri().toString());
                        InputStream in = url.openStream();
                        notifyBuilder.setLargeIcon(BitmapFactory.decodeStream(in));
                        startForeground(1, notifyBuilder.build());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            new Thread(loadAlbumArt).start();
        }
        else {
            startForeground(1, notifyBuilder.build());
        }
    }

    MediaSessionCompat.Callback sessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            super.onAddQueueItem(description);
            List<MediaSessionCompat.QueueItem> queue = session.getController().getQueue();
            if(queue == null) queue = new ArrayList<>();
            queue.add(new MediaSessionCompat.QueueItem(description, queue.size()));
            session.setQueue(queue);
            if(queue.size() == 1) onSkipToQueueItem(0);
        }

        @Override
        public void onSkipToQueueItem(long id) {
            super.onSkipToQueueItem(id);
            currentItem = (int)id;
            onPlayFromUri(session.getController().getQueue().get(currentItem).getDescription().getMediaUri(),
                    session.getController().getQueue().get(currentItem).getDescription().getExtras());
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            super.onPlayFromUri(uri, extras);
            if(extras.getBoolean("isDeciphered")) {
                try {
                    MediaDescriptionCompat des = session.getController().getQueue().get(currentItem).getDescription();
                    showPlayerNotification(des);
                    Log.i(TAG, des.getExtras().getString("duration"));
                    MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, des.getTitle().toString())
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, des.getSubtitle().toString())
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                    Long.parseLong(des.getExtras().getString("duration")) * 1000)
                            .build();
                    session.setMetadata(metadata);
                    if(PlayerClass.isNull()) PlayerClass.initPlayer(PlaybackService.this, session.getController());
                    PlayerClass.setPlayerSource(uri.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    showPlayerNotification(new MediaDescriptionCompat.Builder()
                            .setTitle("Preparing...")
                            .setSubtitle("Getting video Info...")
                            .build());
                    VideoDecipher.playMusicFromUri(String.valueOf(extras.getCharSequence("videoId")),
                            session, PlaybackService.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            List<MediaSessionCompat.QueueItem> queue = session.getController().getQueue();
            currentItem++;
            if(currentItem < queue.size()) {
                session.getController().getTransportControls().playFromUri(
                        queue.get(currentItem).getDescription().getMediaUri(), null);
            }
            else {
                if(session.getController().getRepeatMode() == PlaybackStateCompat.REPEAT_MODE_ALL) {
                    session.getController().getTransportControls().skipToQueueItem(0);
                    return;
                }
                session.getController().getTransportControls().stop();
            }
        }
    };
}
