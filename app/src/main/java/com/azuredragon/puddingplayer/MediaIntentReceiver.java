package com.azuredragon.puddingplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

public class MediaIntentReceiver extends BroadcastReceiver {
    private static MediaSessionCompat session;

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
            KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
            switch (keyEvent.getKeyCode()) {
                case KeyEvent.KEYCODE_HEADSETHOOK:

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    switch (session.getController().getPlaybackState().getState()) {
                        case PlaybackStateCompat.STATE_PLAYING:
                            session.getController().getTransportControls().pause();
                            break;

                        case PlaybackStateCompat.STATE_PAUSED:
                            session.getController().getTransportControls().play();
                            break;
                    }
                    break;

                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    session.getController().getTransportControls().play();
                    break;

                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    Log.i("MediaButton", "Paused.");
                    session.getController().getTransportControls().pause();
                    break;

                case KeyEvent.KEYCODE_MEDIA_STOP:
                    session.getController().getTransportControls().stop();
                    break;

                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    session.getController().getTransportControls().skipToNext();
                    break;

                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    session.getController().getTransportControls().skipToPrevious();
                    break;
            }
        }
    }

    static void initReceiver(MediaSessionCompat mSession) {
        session = mSession;
    }
}
