package com.azuredragon.puddingplayer.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackService extends MediaBrowserServiceCompat {
    private MediaSessionCompat mSession;
    private PlayerClass player;
    private MediaNotification notifyManager;
    private AudioManager audioManager;
    private BecomingNoisyReceiver noisyAudioReceiver = new BecomingNoisyReceiver();

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot("", new Bundle());
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {}

    @Override
    public void onCreate() {
        super.onCreate();
        mSession = new MediaSessionCompat(this, "PuddingIsGood!");
        setSessionToken(mSession.getSessionToken());

        mSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build());
        mSession.setMetadata(new MediaMetadataCompat.Builder().build());
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
        mSession.setCallback(sessionCallback);
        mSession.setQueue(new ArrayList<MediaSessionCompat.QueueItem>());
        mSession.setMediaButtonReceiver(null);
        mSession.setActive(true);

        player = new PlayerClass(this, mSession);
        player.setOnErrorListener(new PlayerClass.OnErrorListener() {
            @Override
            public void onError(final Bundle error) {
                Log.e("MediaPlayer", "Error " + error.getInt("errorCode") + ": " + error.getString("reason"));
                Handler handler = new Handler(getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String reason = "Player error " + error.getInt("errorCode") + ": " + error.getString("reason");
                        PlaybackService.this.onError(reason);
                    }
                });
                mSession.getController().getTransportControls().stop();
            }
        });
        notifyManager = new MediaNotification(this);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notifyManager.cancelAll();
        if(mSession.getController().getPlaybackState().getState() != PlaybackStateCompat.STATE_NONE)
            mSession.getController().getTransportControls().stop();
        mSession.setActive(false);
        Log.i("", "Service stopped.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    void onError(final String reason) {
        Handler errorHandler = new Handler(getMainLooper());
        errorHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PlaybackService.this, reason, Toast.LENGTH_LONG).show();
            }
        });
        mSession.getController().getTransportControls().stop();
    }

    MediaSessionCompat.Callback sessionCallback = new MediaSessionCompat.Callback() {
        ArrayList<MediaSessionCompat.QueueItem> queueList;
        long current = -1;

        ExecutorService mLoadVideoOembed = Executors.newFixedThreadPool(1);
        @Override
        public void onAddQueueItem(final MediaDescriptionCompat description) {
            super.onAddQueueItem(description);
            if(description == null || description.getExtras() == null) return;
            final String videoId = description.getExtras().getString("videoId");
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    FileLoader mFileLoader;
                    mFileLoader = new FileLoader(PlaybackService.this);
                    mFileLoader.setOnDownloadCompletedListener(new FileLoader.OnDownloadCompletedListener() {
                        @Override
                        public void onCompleted(String fileContent) {
                            try {
                                if(fileContent.equals("Bad Request")) {
                                    onError(getString(R.string.toast_oembed_error));
                                    return;
                                }
                                JSONObject oembedInfo = new JSONObject(fileContent);
                                Bundle bundle = description.getExtras();
                                bundle.putBoolean("hasMetadata", true);
                                MediaDescriptionCompat des = new MediaDescriptionCompat.Builder()
                                        .setTitle(oembedInfo.getString("title"))
                                        .setSubtitle(oembedInfo.getString("author_name"))
                                        .setIconUri(Uri.parse(oembedInfo.getString("thumbnail_url")))
                                        .setExtras(bundle).build();

                                queueList = (ArrayList<MediaSessionCompat.QueueItem>) mSession.getController().getQueue();
                                queueList.add(new MediaSessionCompat.QueueItem(des, queueList.size()));
                                mSession.setQueue(queueList);

                                if(queueList.size() == 1) mSession.getController().getTransportControls().skipToQueueItem(0);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    mFileLoader.setOnLoadFailedListener(new FileLoader.OnLoadFailedListener() {
                        @Override
                        public void onLoadFailed(final String reason) {
                            Handler handler = new Handler(getMainLooper());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    onError(getString(R.string.toast_oembed_error) + reason);
                                }
                            });
                        }
                    });
                    try {
                        mFileLoader.downloadFile("https://www.youtube.com/oembed?url=youtu.be/" +
                                videoId, "oembed_" + videoId, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            mLoadVideoOembed.execute(task);
        }

        @Override
        public void onSkipToQueueItem(long id) {
            super.onSkipToQueueItem(id);
            if(mSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_CONNECTING) return;
            startService(new Intent(PlaybackService.this, PlaybackService.class));
            current = id;
            if(mSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) player.pausePlayer();
            mSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f)
                    .build());
            mSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putText(MediaMetadataCompat.METADATA_KEY_TITLE, queueList.get((int) id).getDescription().getTitle())
                    .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, queueList.get((int) id).getDescription().getSubtitle())
                    .build());
            startForeground(notifyManager.NOTIFICATION_ID,
                    notifyManager.getNotification(mSession.getSessionToken(),
                            mSession.getController().getMetadata(),
                            mSession.getController().getPlaybackState()));
            VideoInfo videoInfo = new VideoInfo(PlaybackService.this, queueList.get((int) id));
            videoInfo.setOnAudioSourcePreparedListener(new VideoInfo.OnAudioSourcePreparedListener() {
                @Override
                public void onPrepared(final MediaDescriptionCompat des) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if(des.getMediaUri() == null) throw new Exception("Media URI should not be null.");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            final FileLoader mFileLoader = new FileLoader(PlaybackService.this);
                            mFileLoader.setOnDownloadCompletedListener(new FileLoader.OnDownloadCompletedListener() {
                                @Override
                                public void onCompleted(String fileContent) {
                                    try {
                                        mFileLoader.copyFile(mFileLoader.APPLICATION_CACHE_DIR + "thumbnail_" +
                                                des.getExtras().getString("videoId") + ".jpg", "thumbnail.jpg");
                                        MediaMetadataCompat metadata =
                                                new MediaMetadataCompat.Builder(mSession.getController().getMetadata())
                                                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                                            des.getExtras().getLong("lengthSeconds") * 1000)
                                                    .build();
                                        mSession.setMetadata(metadata);
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    player.setPlayerSource(des.getMediaUri().toString());
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }).start();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            try {
                                mFileLoader.downloadFile(des.getIconUri().toString(), "thumbnail_" +
                                        des.getExtras().getString("videoId") + ".jpg", true);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            });
            videoInfo.setOnErrorListener(new VideoInfo.OnErrorListener() {
                @Override
                public void onError(String reason) {
                    onError(reason);
                }
            });
            videoInfo.getInfo();
        }

        @Override
        public void onPlay() {
            super.onPlay();
            if(mSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_CONNECTING ||
                    player.isNull() || player.waitForBuffering) return;
            player.resumePlayer();
            mSession.setPlaybackState(new PlaybackStateCompat.Builder(mSession.getController().getPlaybackState())
                    .setState(PlaybackStateCompat.STATE_PLAYING, player.getCurrentPosition(), 1.0f)
                    .build());
            startForeground(notifyManager.NOTIFICATION_ID,
                    notifyManager.getNotification(mSession.getSessionToken(),
                            mSession.getController().getMetadata(),
                            mSession.getController().getPlaybackState()));
            registerReceiver(noisyAudioReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            requestFocus();
        }

        AudioFocusRequest focusRequest;
        void requestFocus() {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build())
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .setAcceptsDelayedFocusGain(true)
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            }
            else {
                audioManager.requestAudioFocus(focusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if(mSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_CONNECTING || player.isNull()) return;
            mSession.setPlaybackState(new PlaybackStateCompat.Builder(mSession.getController().getPlaybackState())
                    .setState(PlaybackStateCompat.STATE_PAUSED, player.getCurrentPosition(), 1.0f)
                    .build());
            player.pausePlayer();
            stopForeground(false);
            notifyManager.getManager().notify(notifyManager.NOTIFICATION_ID,
                    notifyManager.getNotification(mSession.getSessionToken(),
                            mSession.getController().getMetadata(),
                            mSession.getController().getPlaybackState()));
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            PlaybackStateCompat state = mSession.getController().getPlaybackState();
            if(state.getState() != PlaybackStateCompat.STATE_PLAYING &&
                    state.getState() != PlaybackStateCompat.STATE_PAUSED) return;
            player.seekTo(pos);
            mSession.setPlaybackState(new PlaybackStateCompat.Builder(state)
                    .setState(state.getState(), player.getCurrentPosition(), 1.0f)
                    .build());
        }

        @Override
        public void onStop() {
            super.onStop();
            player.stopPlayer();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
            else {
                audioManager.abandonAudioFocus(focusChangeListener);
            }
            unregisterReceiver(noisyAudioReceiver);
            mSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .build());
            stopForeground(true);
            stopSelf();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            if(mSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_CONNECTING || player.isNull()) return;
            if(current + 1 >= mSession.getController().getQueue().size()) {
                if(player.isCompleted()) mSession.getController().getTransportControls().pause();
                return;
            }
            mSession.getController().getTransportControls().skipToQueueItem(current + 1);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            if(mSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_CONNECTING || player.isNull()) return;
            if(current == 0) return;
            mSession.getController().getTransportControls().skipToQueueItem(current - 1);
        }

        long mediaButtonLastCalled;
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            if(mediaButtonEvent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
                KeyEvent keyEvent = (KeyEvent) mediaButtonEvent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                MediaControllerCompat controller = mSession.getController();
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

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                // Pause the playback
                mSession.getController().getTransportControls().pause();
            }
        }
    }


    AudioManager.OnAudioFocusChangeListener focusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if(!player.isNull() && mSession != null) {
                if(focusChange != AudioManager.AUDIOFOCUS_GAIN) {
                    mSession.getController().getTransportControls().pause();
                }
                else {
                    mSession.getController().getTransportControls().play();
                }
            }
        }
    };
}
