package com.azuredragon.puddingplayer;

import android.content.ComponentName;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlaybackService extends MediaBrowserServiceCompat {
    String TAG = "PlaybackService";
    boolean isRunning = false;

    MediaSessionCompat session;
    AudioManager audioManager;
    static int currentItem;

    private static MediaNotificationManager mManager;

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
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        session.setCallback(sessionCallback);
        session.setQueue(new ArrayList<MediaSessionCompat.QueueItem>());
        session.setActive(true);
        setSessionToken(session.getSessionToken());

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        MediaIntentReceiver.initReceiver(session);
        audioManager.registerMediaButtonEventReceiver(new ComponentName(this, PlaybackService.class));
        mManager = new MediaNotificationManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(session, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(!PlayerClass.isNull()) PlayerClass.stopPlayer();
        session.setActive(false);
        isRunning = false;
        Log.i(TAG, "Service stopped.");
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Log.i(TAG, "Received client request.");
        return new BrowserRoot("", null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {

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
            startService(new Intent(PlaybackService.this, PlaybackService.class));
            if(extras.getBoolean("isDeciphered")) {
                try {
                    startForeground(MediaNotificationManager.NOTIFICATION_ID,
                            mManager.getNotification(
                                    session.getSessionToken(),
                                    new MediaDescriptionCompat.Builder()
                                            .setTitle("Preparing...")
                                            .setSubtitle("Preparing player...")
                                            .build(),
                                    new NotificationCompat.Action[] {}));
                    MediaDescriptionCompat des = session.getController().getQueue().get(currentItem).getDescription();
                    MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, des.getTitle().toString())
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, des.getTitle().toString())
                            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, des.getSubtitle().toString())
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                    Long.parseLong(des.getExtras().getString("duration")) * 1000)
                            .build();
                    session.setMetadata(metadata);
                    if(PlayerClass.isNull()) PlayerClass.initPlayer(PlaybackService.this, session);
                    PlayerClass.setPlayerSource(uri.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    startForeground(MediaNotificationManager.NOTIFICATION_ID,
                            mManager.getNotification(
                                    session.getSessionToken(),
                                    new MediaDescriptionCompat.Builder()
                                            .setTitle("Preparing...")
                                            .setSubtitle("Getting video Info...")
                                            .build(),
                                    new NotificationCompat.Action[] {}));
                    if(!new FileHandler(PlaybackService.this).isNetworkConnected()) {
                        Toast.makeText(PlaybackService.this, "Network disconnected.", Toast.LENGTH_LONG).show();
                        session.getController().getTransportControls().stop();
                        return;
                    }
                    VideoDecipher.playMusicFromUri(String.valueOf(extras.getCharSequence("videoId")),
                            session, PlaybackService.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            List<MediaSessionCompat.QueueItem> queue = session.getController().getQueue();
            currentItem--;
            if(currentItem >= 0) {
                session.getController().getTransportControls().skipToQueueItem(currentItem);
            }
            else {
                currentItem++;
            }
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            List<MediaSessionCompat.QueueItem> queue = session.getController().getQueue();
            currentItem++;
            if(currentItem < queue.size()) {
                session.getController().getTransportControls().skipToQueueItem(currentItem);
            }
            else {
                if(!PlayerClass.isCompleted) {
                    currentItem--;
                    return;
                }
                if(session.getController().getRepeatMode() == PlaybackStateCompat.REPEAT_MODE_ALL) {
                    session.getController().getTransportControls().skipToQueueItem(0);
                    return;
                }
                session.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                        .build());
                session.getController().getTransportControls().stop();
            }
        }

        @Override
        public void onPlay() {
            super.onPlay();
            if(!new FileHandler(PlaybackService.this).isNetworkConnected()) {
                Toast.makeText(PlaybackService.this, "Network disconnected.", Toast.LENGTH_LONG).show();
                session.getController().getTransportControls().stop();
                return;
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(
                        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                .setAudioAttributes(new AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .build())
                                .setOnAudioFocusChangeListener(audioFocusChanged)
                                .build());
            }
            else {
                audioManager.requestAudioFocus(audioFocusChanged,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            startForeground(MediaNotificationManager.NOTIFICATION_ID, mManager.getNotification(
                    session.getSessionToken(),
                    session.getController().getQueue().get(currentItem).getDescription(),
                    new NotificationCompat.Action[] {
                            MediaNotificationManager.mPrevIntent,
                            MediaNotificationManager.mPauseIntent,
                            MediaNotificationManager.mNextIntent
                    }));
            PlayerClass.resumePlayer();
        }

        @Override
        public void onPause() {
            super.onPause();
            stopForeground(false);
            mManager.getManager().notify(MediaNotificationManager.NOTIFICATION_ID,
                    mManager.getNotification(
                            session.getSessionToken(),
                            session.getController().getQueue().get(currentItem).getDescription(),
                            new NotificationCompat.Action[]{
                                    MediaNotificationManager.mPrevIntent,
                                    MediaNotificationManager.mPlayIntent,
                                    MediaNotificationManager.mNextIntent
                            }));
            PlayerClass.pausePlayer();
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            PlayerClass.seekTo(pos);
        }

        @Override
        public void onStop() {
            super.onStop();
            stopForeground(true);
            PlayerClass.stopPlayer();
            stopService(new Intent(PlaybackService.this, PlaybackService.class));
        }
    };

    AudioManager.OnAudioFocusChangeListener audioFocusChanged = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if(focusChange != AudioManager.AUDIOFOCUS_GAIN) {
                session.getController().getTransportControls().pause();
            }
            else {
                session.getController().getTransportControls().play();
            }
        }
    };
}
