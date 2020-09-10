package com.azuredragon.puddingplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

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
            try {;
                Log.i("BrowserClient", "Connected.");
                MediaSessionCompat.Token token = browser.getSessionToken();
                MediaControllerCompat controller = new MediaControllerCompat(MainActivity.this, token);
                MediaControllerCompat.setMediaController(MainActivity.this, controller);
                buildTransportTools();
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
    };

    void buildTransportTools() {
        Button addButton = findViewById(R.id.button);
        final TextView videoLink = findViewById(R.id.editText);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String link = videoLink.getText().toString();
                    JSONObject param;
                    if(link.contains("\\?")) {
                        if(link.contains("youtu.be")) link =
                                link.replace("youtu.be/", "www.youtube.com/watch?v=");
                        param = VideoDecipher.paramToJsonObject(link.split("\\?")[1]);
                    }
                    else {
                        param = new JSONObject().put("v", link);
                    }

                    Bundle bundle = new Bundle();
                    bundle.putCharSequence("videoId", param.getString("v"));
                    bundle.putBoolean("isDeciphered", false);
                    MediaControllerCompat.getMediaController(MainActivity.this).addQueueItem(
                            new MediaDescriptionCompat.Builder()
                                    .setExtras(bundle)
                                    .build());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
