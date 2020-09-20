package com.azuredragon.puddingplayer;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    MediaBrowserCompat browser;
    PlaybackStateCompat mState;
    MediaControllerCompat mController;
    Handler mHandler;
    Runnable seekBarControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        browser = new MediaBrowserCompat(this,
                new ComponentName(this, PlaybackService.class),
                connectionCallback,
                null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        browser.connect();
    }



    @Override
    protected void onStop() {
        super.onStop();
        if (MediaControllerCompat.getMediaController(MainActivity.this) != null) {
            MediaControllerCompat.getMediaController(MainActivity.this).unregisterCallback(controllerCallback);
        }
        browser.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (MediaControllerCompat.getMediaController(MainActivity.this) != null) {
            MediaControllerCompat.getMediaController(MainActivity.this).unregisterCallback(controllerCallback);
        }
        browser.disconnect();
    }

    MediaBrowserCompat.ConnectionCallback connectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            super.onConnected();
            try {
                Log.i("BrowserClient", "Connected.");
                MediaSessionCompat.Token token = browser.getSessionToken();
                MediaControllerCompat.setMediaController(MainActivity.this,
                        new MediaControllerCompat(MainActivity.this, token));
                mController = MediaControllerCompat.getMediaController(MainActivity.this);
                mController.registerCallback(controllerCallback);
                mState = mController.getPlaybackState();
                buildTransportTools();
                refreshPlaylist(MediaControllerCompat.getMediaController(MainActivity.this).getQueue());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConnectionFailed() {
            super.onConnectionFailed();
            Log.i("BrowserClient", "Connection Failed.");
        }
    };


    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        Handler mHandler;
        Runnable r = new Runnable() {
            @Override
            public void run() {

                mHandler.postDelayed(this, 1000);
            }
        };

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
            refreshPlaylist(MediaControllerCompat.getMediaController(MainActivity.this).getQueue());
        }

        @Override
        public void onPlaybackStateChanged(final PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);

            mState = state;
            refreshMediaButton();
            SeekBar seekbar = findViewById(R.id.posSeekBar);
            seekbar.setSecondaryProgress((int) state.getBufferedPosition() / 1000);
            if(state.getState() == PlaybackStateCompat.STATE_STOPPED) {
                browser.disconnect();
                if(mHandler != null) {
                    mHandler.removeCallbacks(r);
                    mHandler = null;
                }
            }
            if(state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                if(mHandler == null) {

                    mHandler = new Handler();
                    mHandler.post(r);
                }
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            super.onRepeatModeChanged(repeatMode);
            refreshMediaButton();
        }
    };

    void addPlaylist(String listId) throws IOException {
        FileHandler file = new FileHandler(MainActivity.this);
        String playlistLink =
                "https://www.youtube.com/list_ajax?style=json&action_get_list=1&list=" + listId;
        file.setOnLoadFailedListener(new FileHandler.OnLoadFailedListener() {
            @Override
            public void onLoadFailed(final String reason) {
                Log.e("PlaylistError", reason);
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, reason, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        file.setOnDownloadCompletedListener(new FileHandler.OnDownloadCompletedListener() {
            @Override
            public void onCompleted(String fileContent) {
                try {
                    JSONArray playlistVideos = new JSONObject(fileContent).getJSONArray("video");
                    for(int i = 0; i < playlistVideos.length(); i++) {
                        addItem(playlistVideos.getJSONObject(i).getString("encrypted_id"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        file.downloadFile(playlistLink, listId);
    }

    void addItem(String videoId) {
        Bundle bundle = new Bundle();
        bundle.putString("videoId", videoId);
        bundle.putBoolean("isDeciphered", false);
        MediaControllerCompat.getMediaController(MainActivity.this).addQueueItem(
                new MediaDescriptionCompat.Builder()
                        .setExtras(bundle)
                        .build());
    }

    void buildTransportTools() {
        final Button addButton = findViewById(R.id.button);
        final TextView videoLink = findViewById(R.id.editText);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!browser.isConnected()) browser.connect();
                String[] queueItems = videoLink.getText().toString().split(";");
                for (String queueItem : queueItems) {
                    try {
                        String link = queueItem;
                        final JSONObject param;
                        if (link.contains("youtu.be")) {
                            link = link.replace("youtu.be/", "www.youtube.com/watch?v=");
                        }

                        if (link.contains("?")) {
                            param = VideoInfo.paramToJsonObject(link.split("\\?")[1]);
                        } else {
                            param = new JSONObject().put("v", link);
                        }

                        if(param.has("list")) {
                            DialogInterface.OnClickListener addAll = new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        addPlaylist(param.getString("list"));
                                    } catch (IOException | JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            final DialogInterface.OnClickListener onlyVideo = new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        addItem(param.getString("v"));
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            if(param.has("v")) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(getResources().getString(R.string.dialog_title_add_playlist))
                                        .setMessage(getResources().getString(R.string.dialog_msg_add_playlist))
                                        .setPositiveButton(
                                                getResources().getString(R.string.dialog_btn_addAll), addAll)
                                        .setNegativeButton(
                                                getResources().getString(R.string.dialog_btn_addVideo), onlyVideo)
                                        .setCancelable(true)
                                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                            @Override
                                            public void onCancel(DialogInterface dialog) {
                                                onlyVideo.onClick(dialog, 0);
                                            }
                                        }).create().show();
                            }
                            else {
                                addAll.onClick(null, 0);
                            }
                            return;
                        }
                        else {
                            addItem(param.getString("v"));
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        final Button pausePlayButton = findViewById(R.id.pausePlayButton);
        pausePlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    mController.getTransportControls().pause();
                    pausePlayButton.setText(getResources().getString(R.string.button_play));
                }
                if(mState.getState() == PlaybackStateCompat.STATE_PAUSED) {
                    mController.getTransportControls().play();
                    pausePlayButton.setText(getResources().getString(R.string.button_pause));
                }
            }
        });
        Button nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().skipToNext();
            }
        });
        Button prevButton = findViewById(R.id.prevButton);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().skipToPrevious();
            }
        });
        Button repeatModeButton = findViewById(R.id.repeatModeButton);
        repeatModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mController.getRepeatMode()) {
                    case PlaybackStateCompat.REPEAT_MODE_NONE:
                        mController.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE);
                        break;
                    case PlaybackStateCompat.REPEAT_MODE_ONE:
                        mController.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);
                        break;
                    case PlaybackStateCompat.REPEAT_MODE_ALL:
                        mController.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
                        break;
                }
            }
        });
        final SeekBar posSeekBar = findViewById(R.id.posSeekBar);
        posSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mController.getTransportControls().seekTo(seekBar.getProgress() * 1000);
            }
        });
        mHandler = new Handler(Looper.getMainLooper());
        seekBarControl = new Runnable() {
            @Override
            public void run() {
                posSeekBar.setProgress((int) (
                        mController.getPlaybackState().getPosition() / 1000));

                TextView currentPosTextView = findViewById(R.id.currentPosition);
                String min = String.format(Locale.getDefault(), "%02d",
                        (mController.getPlaybackState().getPosition() / 1000) / 60);
                String sec = String.format(Locale.getDefault(), "%02d",
                        (mController.getPlaybackState().getPosition() / 1000) % 60);
                currentPosTextView.setText(min + ":" + sec);

                mHandler.postDelayed(this, 1000);
            }
        };

        refreshMediaButton();
    }

    void refreshMediaButton() {
        Button pausePlay = findViewById(R.id.pausePlayButton);
        if(mState.getState() == PlaybackStateCompat.STATE_PLAYING) {
            pausePlay.setText(getResources().getString(R.string.button_pause));
        }
        else {
            pausePlay.setText(getResources().getString(R.string.button_play));
        }

        Button repeatMode = findViewById(R.id.repeatModeButton);
        switch (mController.getRepeatMode()) {
            case PlaybackStateCompat.REPEAT_MODE_NONE:
                repeatMode.setText(getResources().getString(R.string.button_repeat_none));
                break;
            case PlaybackStateCompat.REPEAT_MODE_ONE:
                repeatMode.setText(getResources().getString(R.string.button_repeat_one));
                break;
            case PlaybackStateCompat.REPEAT_MODE_ALL:
                repeatMode.setText(getResources().getString(R.string.button_repeat_all));
                break;
        }

        SeekBar posSeekBar = findViewById(R.id.posSeekBar);
        if(mController.getMetadata() != null) {
            int lengthSec = (int) (mController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000);
            posSeekBar.setMax(lengthSec);

            String min = String.format(Locale.getDefault(), "%02d", lengthSec / 60);
            String sec = String.format(Locale.getDefault(), "%02d", lengthSec % 60);
            TextView durationTextView = findViewById(R.id.duration);
            durationTextView.setText(min + ":" + sec);
        }
        if(mHandler != null) {
            if(mState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                runOnUiThread(seekBarControl);
            }
            else {
                mHandler.removeCallbacks(seekBarControl);
            }
        }
    }

    void refreshPlaylist(List<MediaSessionCompat.QueueItem> queue) {
        ListView playlistView = findViewById(R.id.playlistView);
        String[] str = new String[queue.size()];
        for(int i = 0; i < queue.size(); i++) {
            if(queue.get(i).getDescription().getTitle() == null) {
                str[i] = (i+1) + ". " + queue.get(i).getDescription().getExtras().getString("videoId") + ("\n");
            }
            else {
                str[i] = (i+1) + ". " + queue.get(i).getDescription().getTitle() + "\n";
            }
        }
        playlistView.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                str));
    }
}
