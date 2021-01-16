package com.azuredragon.puddingplayer.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.Utils;
import com.azuredragon.puddingplayer.service.PlaybackService;
import com.azuredragon.puddingplayer.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    NowPlayingFragment player;
    MediaBrowserCompat browser;
    FragmentTransaction fragmentTransaction;
    FragmentManager fragmentManager;
    MediaControllerCompat controllerCompat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        browser = new MediaBrowserCompat(this,
                new ComponentName(this, PlaybackService.class),
                connectionCallback,
                null);
        browser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        browser.disconnect();
        if(controllerCompat != null) controllerCompat.unregisterCallback(controllerCallback);
    }

    @Override
    public void onBackPressed() {
        boolean isHandled = player.onBackPressed();
        if(!isHandled) super.onBackPressed();
    }

    void refreshPlayerFragment() {
        fragmentTransaction = fragmentManager.beginTransaction();
        if(controllerCompat.getPlaybackState().getState() == PlaybackStateCompat.STATE_STOPPED ||
                controllerCompat.getPlaybackState().getState() == PlaybackStateCompat.STATE_NONE) {
            fragmentTransaction.hide(player).commit();
            findViewById(R.id.playlistView).setPadding(0, 0, 0, 0);
            Log.i("", "Stopped!");
        }
        else {
            fragmentTransaction.show(player).commit();
            findViewById(R.id.playlistView).setPadding(0, 0, 0, Utils.dp2px(MainActivity.this, 70));
        }
    }

    void initUserInterface() {
        Button submit = findViewById(R.id.btn_submit);
        final EditText linkInput = findViewById(R.id.input_link);

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    JSONObject id = Utils.decodeYTLink(linkInput.getText().toString());
                    if(!id.has("videoId") && !id.has("listId")) {
                        Toast.makeText(MainActivity.this, "Invalid link.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if(id.has("listId")) {
                        addPlaylist(id.getString("listId"));
                    }
                    else {
                        addItem(id.getString("videoId"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        refreshPlaylist(controllerCompat.getQueue());
    }

    MediaBrowserCompat.ConnectionCallback connectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            super.onConnected();
            try {
                controllerCompat = new MediaControllerCompat(MainActivity.this, browser.getSessionToken());
                controllerCompat.registerCallback(controllerCallback);
                MediaControllerCompat.setMediaController(MainActivity.this, controllerCompat);
                fragmentManager = getSupportFragmentManager();
                player = new NowPlayingFragment(controllerCompat);
                fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.add(R.id.fragment_container, player).commit();
                refreshPlayerFragment();
                initUserInterface();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            refreshPlayerFragment();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
            refreshPlaylist(MediaControllerCompat.getMediaController(MainActivity.this).getQueue());
        }
    };

    void addPlaylist(final String listId) {
        final ProgressDialog dialog = ProgressDialog.show(this,
                "Add playlist", "Adding playlist items...", true);
        dialog.show();
        final FileLoader file = new FileLoader(MainActivity.this);
        final String playlistLink =
                "https://www.youtube.com/list_ajax?style=json&action_get_list=1&list=" + listId;
        file.setOnLoadFailedListener(new FileLoader.OnLoadFailedListener() {
            @Override
            public void onLoadFailed(final String reason) {
                Log.e("PlaylistError", reason);
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        Toast.makeText(MainActivity.this, reason, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        file.setOnDownloadCompletedListener(new FileLoader.OnDownloadCompletedListener() {
            @Override
            public void onCompleted(String fileContent) {
                try {
                    JSONArray playlistVideos = new JSONObject(fileContent).getJSONArray("video");
                    for(int i = 0; i < playlistVideos.length(); i++) {
                        addItem(playlistVideos.getJSONObject(i).getString("encrypted_id"));
                    }
                    dialog.dismiss();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    file.downloadFile(playlistLink, listId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    void addItem(String videoId) {
        Bundle bundle = new Bundle();
        bundle.putString("videoId", videoId);
        bundle.putBoolean("isDeciphered", false);
        controllerCompat.addQueueItem(
                new MediaDescriptionCompat.Builder()
                        .setExtras(bundle)
                        .build());
    }

    void refreshPlaylist(List<MediaSessionCompat.QueueItem> queue) {
        ListView playlistView = findViewById(R.id.playlistView);
        int pos = playlistView.getScrollY();
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
        playlistView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().skipToQueueItem(id);
            }
        });
        playlistView.setScrollY(pos);
    }
}
