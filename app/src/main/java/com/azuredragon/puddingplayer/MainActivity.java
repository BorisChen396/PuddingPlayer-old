package com.azuredragon.puddingplayer;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.net.ConnectivityManagerCompat;

import android.content.ComponentName;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    MediaBrowserCompat browser;

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
        if(!browser.isConnected()) browser.connect();
        if(MediaControllerCompat.getMediaController(this) != null) {
            buildTransportTools();
        }
        else {
           buildTransportTools();
        }
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
                MediaControllerCompat controller = new MediaControllerCompat(MainActivity.this, token);
                MediaControllerCompat.setMediaController(MainActivity.this, controller);
                MediaControllerCompat.getMediaController(MainActivity.this).registerCallback(controllerCallback);
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
    };

    void buildTransportTools() {
        Button addButton = findViewById(R.id.button);
        final TextView videoLink = findViewById(R.id.editText);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!browser.isConnected()) browser.connect();
                String[] queueItems = videoLink.getText().toString().split(";");
                for(int i = 0; i < queueItems.length; i++) {
                    try {
                        String link = queueItems[i];
                        JSONObject param;
                        if(link.contains("youtu.be")) {
                            link = link.replace("youtu.be/", "www.youtube.com/watch?v=");
                        }

                        if(link.contains("?")) {
                            param = VideoInfo.paramToJsonObject(link.split("\\?")[1]);
                        }
                        else {
                            param = new JSONObject().put("v", link);
                        }

                        Bundle bundle = new Bundle();
                        bundle.putString("videoId", param.getString("v"));
                        bundle.putBoolean("isDeciphered", false);
                        MediaControllerCompat.getMediaController(MainActivity.this).addQueueItem(
                                new MediaDescriptionCompat.Builder()
                                        .setExtras(bundle)
                                        .build());
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
                if(!browser.isConnected()) browser.connect();
                if(MediaControllerCompat.getMediaController(MainActivity.this)
                        .getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
                    MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().pause();
                    pausePlayButton.setText("Play");
                }
                if(MediaControllerCompat.getMediaController(MainActivity.this)
                        .getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED) {
                    MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().play();
                    pausePlayButton.setText("Pause");
                }
            }
        });
        View.OnClickListener pauseClicked = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().pause();
                pausePlayButton.setText("Play");
            }
        };
        View.OnClickListener playClicked = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().play();
                pausePlayButton.setText("Pause");
            }
        };
        Button nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().skipToNext();
            }
        });
        Button prevButton = findViewById(R.id.prevButton);
        prevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().skipToPrevious();
            }
        });
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
