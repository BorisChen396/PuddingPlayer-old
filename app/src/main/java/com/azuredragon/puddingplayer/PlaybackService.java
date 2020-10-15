package com.azuredragon.puddingplayer;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PlaybackService extends MediaBrowserServiceCompat {
    String TAG = "PlaybackService";
    boolean isRunning = false;
    boolean autoplay = true;

    BecomingNoisyReceiver mReceiver;
    static boolean isReceiverRegistered = false;

    MediaSessionCompat session;
    AudioManager audioManager;
    static int currentItem;

    private static MediaNotificationManager mManager;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        session = new MediaSessionCompat(this, TAG);
        session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1)
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                ).build());
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        session.setCallback(sessionCallback);
        session.setQueue(new ArrayList<MediaSessionCompat.QueueItem>());
        session.setMediaButtonReceiver(null);
        session.setActive(true);
        setSessionToken(session.getSessionToken());

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);

        mManager = new MediaNotificationManager(this);

        mReceiver = new BecomingNoisyReceiver();


        FileHandler fileHandler = new FileHandler(PlaybackService.this);
        if(fileHandler.checkFileExistance(FileHandler.APPLICATION_DATA_DIR + "playlist_nowPlaying")) {
            fileHandler.setOnFileLoadedListener(new FileHandler.OnFileLoadedListener() {
                @Override
                public void onFileLoaded(String fileContent) {
                    try {
                        JSONArray saveList = new JSONArray(fileContent);
                        for(int i = 0; i < saveList.length(); i++) {
                            autoplay = false;
                            String videoId = saveList.getString(i);
                            Bundle bundle = new Bundle();
                            bundle.putString("videoId", videoId);
                            bundle.putBoolean("isDeciphered", false);
                            session.getController().addQueueItem(
                                    new MediaDescriptionCompat.Builder()
                                            .setExtras(bundle)
                                            .build());
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
            try {
                fileHandler.loadFile("playlist_nowPlaying");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

        try {
            JSONArray saveList = new JSONArray();
            for(int i = 0; i < session.getController().getQueue().size(); i++) {
                saveList.put(i,
                        session.getController().getQueue().get(i).getDescription()
                                .getExtras().getString("videoId"));
            }
            FileHandler fileHandler = new FileHandler(PlaybackService.this);
            fileHandler.saveFile(saveList.toString(), "playlist_nowPlaying", true);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        session.setActive(false);
        session = null;
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
        long mediaButtonLastCalled;
        List<Runnable> loadMetadataTasks = new ArrayList<>();
        Thread loadMetadata;
        boolean isLoadingMetadata = false;

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            super.onRemoveQueueItem(description);
            List<MediaSessionCompat.QueueItem> list = session.getController().getQueue();
            for(int i = 0; i < list.size(); i++) {
                if(list.get(i).getDescription().getExtras().getString("videoId").equals(
                        description.getExtras().getString("videoId"))) {
                    if(i == currentItem) session.getController().getTransportControls().skipToNext();
                    list.remove(i);
                    i--;
                }
            }
            session.setQueue(list);
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            super.onAddQueueItem(description);
            final List<MediaSessionCompat.QueueItem> queueItemList;
            if(session.getController().getQueue() != null) {
                queueItemList = session.getController().getQueue();
            }
            else {
                queueItemList = new ArrayList<>();
            }
            queueItemList.add(new MediaSessionCompat.QueueItem(description, queueItemList.size()));
            session.setQueue(queueItemList);


            loadMetadataTasks.add(new Runnable() {
                @Override
                public void run() {
                    final MediaSessionCompat.QueueItem mQueue = queueItemList.get(queueItemList.size() - 1);
                    VideoInfo videoInfo = new VideoInfo(PlaybackService.this, mQueue);
                    videoInfo.setOnMediaMetadataLoaded(new VideoInfo.OnMediaMetadataLoaded() {
                        @Override
                        public void onLoaded(MediaDescriptionCompat.Builder builder) {
                            if(session == null) {
                                isLoadingMetadata = false;
                                return;
                            }
                            List<MediaSessionCompat.QueueItem> list = session.getController().getQueue();
                            if(list.size() == 0) {
                                isLoadingMetadata = false;
                                return;
                            }
                            MediaDescriptionCompat des = builder.setMediaUri(mQueue.getDescription().getMediaUri()).build();
                            list.set((int) mQueue.getQueueId(), new MediaSessionCompat.QueueItem(des, mQueue.getQueueId()));
                            session.setQueue(list);
                            isLoadingMetadata = false;
                        }
                    });
                    videoInfo.getMediaMetadata();
                }
            });

            if(loadMetadata == null) {
                loadMetadata = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (loadMetadataTasks.size() > 0) {
                            if(session.getController().getQueue().size() == 0) {
                                loadMetadataTasks.clear();
                                autoplay = true;
                                break;
                            }
                            if (!isLoadingMetadata) {
                                isLoadingMetadata = true;
                                new Thread(loadMetadataTasks.get(0)).start();
                                loadMetadataTasks.remove(0);
                            }
                        }
                        loadMetadata = null;
                    }
                });
                loadMetadata.start();
            }

            if(queueItemList.size() == 1 && autoplay) onSkipToQueueItem(0);
        }

        @Override
        public void onSkipToQueueItem(final long id) {
            super.onSkipToQueueItem(id);
            startService(new Intent(PlaybackService.this, PlaybackService.class));
            startForeground(MediaNotificationManager.NOTIFICATION_ID,
                    mManager.getNotification(
                            session.getSessionToken(),
                            new MediaDescriptionCompat.Builder()
                                    .setTitle("Preparing...")
                                    .setSubtitle("Getting video information...").build(),
                            new NotificationCompat.Action[0]
                    ));
            if(!PlayerClass.isNull()) PlayerClass.stopPlayer();
            currentItem = (int)id;
            session.setPlaybackState(
                    new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                            .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f)
                            .build());
            MediaSessionCompat.QueueItem queueItem = session.getController().getQueue().get((int)id);
            if(!queueItem.getDescription().getExtras().getBoolean("isDeciphered")) {
                VideoInfo videoInfo = new VideoInfo(PlaybackService.this, queueItem);
                videoInfo.setOnAudioSourcePreparedListener(new VideoInfo.OnAudioSourcePreparedListener() {
                    @Override
                    public void onPrepared(MediaMetadataCompat metadata) {
                        List<MediaSessionCompat.QueueItem> queueItemList = session.getController().getQueue();
                        MediaDescriptionCompat des = new MediaDescriptionCompat.Builder()
                                .setTitle(queueItemList.get((int) id).getDescription().getTitle())
                                .setSubtitle(queueItemList.get((int) id).getDescription().getSubtitle())
                                .setIconUri(queueItemList.get((int) id).getDescription().getIconUri())
                                .setExtras(queueItemList.get((int) id).getDescription().getExtras())
                                .setMediaUri(Uri.parse(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)))
                                .build();
                        queueItemList.set((int)id, new MediaSessionCompat.QueueItem(des, id));
                        session.setQueue(queueItemList);
                        currentItem = (int)id;
                        session.getController().getTransportControls().playFromUri(
                                queueItemList.get((int) id).getDescription().getMediaUri(),
                                queueItemList.get((int) id).getDescription().getExtras());
                        session.setMetadata(new MediaMetadataCompat.Builder(metadata)
                                .putText(MediaMetadataCompat.METADATA_KEY_TITLE, des.getTitle())
                                .putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, des.getSubtitle())
                                .build());
                    }
                });
                try {
                    videoInfo.getInfo();
                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                session.getController().getTransportControls().playFromUri(queueItem.getDescription().getMediaUri(),
                        queueItem.getDescription().getExtras());
            }
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            super.onPlayFromUri(uri, extras);
            FileHandler fileHandler = new FileHandler(PlaybackService.this);
            String videoId = extras.getString("videoId");
            final MediaDescriptionCompat des = session.getController().getQueue().get(currentItem).getDescription();
            boolean albumExistance = fileHandler.checkFileExistance(
                    FileHandler.APPLICATION_DATA_DIR + "album_" + videoId);
            if(!albumExistance) {
                startForeground(MediaNotificationManager.NOTIFICATION_ID,
                        mManager.getNotification(
                                session.getSessionToken(),
                                new MediaDescriptionCompat.Builder()
                                        .setTitle("Preparing...")
                                        .setSubtitle("Loading the video thumbnail...").build(),
                                new NotificationCompat.Action[0]
                        ));
                fileHandler.setOnDownloadCompletedListener(new FileHandler.OnDownloadCompletedListener() {
                    @Override
                    public void onCompleted(String fileContent) {
                        try {
                            PlayerClass.initPlayer(PlaybackService.this, session);
                            PlayerClass.setPlayerSource(des.getMediaUri().toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                fileHandler.downloadFile(des.getIconUri().toString(), "album_" + videoId);
            }
            else {
                try {
                    PlayerClass.initPlayer(PlaybackService.this, session);
                    PlayerClass.setPlayerSource(des.getMediaUri().toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
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
            if(session.getController().getRepeatMode() == PlaybackStateCompat.REPEAT_MODE_ONE && PlayerClass.isCompleted) {
                currentItem--;
                PlayerClass.seekTo(0);
                PlayerClass.resumePlayer();
                return;
            }
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
            if(session == null || session.getController().getQueue().size() == 0) return;
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

            if(!isReceiverRegistered) {
                registerReceiver(mReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                isReceiverRegistered = true;
            }
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
        public void onSetRepeatMode(int repeatMode) {
            super.onSetRepeatMode(repeatMode);
            switch (repeatMode) {
                case PlaybackStateCompat.REPEAT_MODE_ALL:
                    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_GROUP:
                    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_INVALID:
                    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_INVALID);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_NONE:
                    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ONE:
                    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE);
                    break;
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            stopForeground(true);
            if(isReceiverRegistered) {
                unregisterReceiver(mReceiver);
                isReceiverRegistered = false;
            }
            session.setMetadata(new MediaMetadataCompat.Builder().build());
            session.setPlaybackState(new PlaybackStateCompat.Builder(session.getController().getPlaybackState())
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .build());
            PlayerClass.stopPlayer();

            stopService(new Intent(PlaybackService.this, PlaybackService.class));
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            if(mediaButtonEvent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
                KeyEvent keyEvent = (KeyEvent) mediaButtonEvent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                MediaControllerCompat controller = session.getController();
                if(keyEvent.getAction() != KeyEvent.ACTION_DOWN ||
                        System.currentTimeMillis() - mediaButtonLastCalled < 10) return true;
                mediaButtonLastCalled = System.currentTimeMillis();
                switch (keyEvent.getKeyCode()) {
                    case KeyEvent.KEYCODE_HEADSETHOOK:
                        break;

                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        controller.getTransportControls().play();
                        break;

                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        controller.getTransportControls().pause();
                        break;

                    case KeyEvent.KEYCODE_MEDIA_STOP:
                        controller.getTransportControls().stop();
                        break;

                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        controller.getTransportControls().skipToNext();
                        break;

                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        controller.getTransportControls().skipToPrevious();
                        break;
                }
            }
            return super.onMediaButtonEvent(mediaButtonEvent);
        }
    };

    AudioManager.OnAudioFocusChangeListener audioFocusChanged = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if(!PlayerClass.isNull() && session != null) {
                if(focusChange != AudioManager.AUDIOFOCUS_GAIN) {
                    session.getController().getTransportControls().pause();
                }
                else {
                    session.getController().getTransportControls().play();
                }
            }
        }
    };

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
                session.getController().getTransportControls().pause();
            }
        }
    }
}
