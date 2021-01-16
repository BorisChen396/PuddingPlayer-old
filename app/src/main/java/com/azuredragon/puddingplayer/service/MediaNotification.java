package com.azuredragon.puddingplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.azuredragon.puddingplayer.FileLoader;
import com.azuredragon.puddingplayer.R;
import com.azuredragon.puddingplayer.ui.MainActivity;

import static android.content.Context.NOTIFICATION_SERVICE;

class MediaNotification {
    int NOTIFICATION_ID = "PUDDING_IS_GOOD".length();

    private static NotificationManager mManager;
    private static PlaybackService mService;

    private static NotificationCompat.Action mPlayIntent;
    private static NotificationCompat.Action mPauseIntent;
    private static NotificationCompat.Action mNextIntent;
    private static NotificationCompat.Action mPrevIntent;

    MediaNotification(PlaybackService service) {
        mService = service;
        mManager = (NotificationManager) mService.getSystemService(NOTIFICATION_SERVICE);

        mPlayIntent = new NotificationCompat.Action(
                R.drawable.ic_button_play,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_PLAY));
        mPauseIntent = new NotificationCompat.Action(
                R.drawable.ic_button_pause,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_PAUSE));
        mNextIntent = new NotificationCompat.Action(
                R.drawable.ic_button_next,
                "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT));
        mPrevIntent = new NotificationCompat.Action(
                R.drawable.ic_button_prev,
                "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                        mService,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));

        mManager.cancelAll();
    }

    Notification getNotification(MediaSessionCompat.Token token,
                                 MediaMetadataCompat metadata,
                                 final PlaybackStateCompat state) {
        String channelId = "playerNotification";
        NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(mService, channelId);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    mService.getResources().getString(R.string.notifiyChannel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setDescription(mService.getString(R.string.notifyDes));
            mManager.createNotificationChannel(channel);
        }
        else {
            notifyBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        }

        Intent notificationIntent = new Intent(mService, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notifyBuilder.setContentTitle(metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE))
                .setContentText(metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(0, 1, 2))
                .setSmallIcon(R.drawable.ic_notify_player)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(mService, 0,
                        notificationIntent, 0))
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(mService,
                        PlaybackStateCompat.ACTION_STOP))
                .addAction(mPrevIntent)
                .addAction(state.getState() == PlaybackStateCompat.STATE_PLAYING ? mPauseIntent : mPlayIntent)
                .addAction(mNextIntent);
        Bitmap origin = BitmapFactory.decodeFile(new FileLoader(mService).APPLICATION_DATA_DIR + "thumbnail.jpg");
        notifyBuilder.setLargeIcon(state.getState() == PlaybackStateCompat.STATE_PLAYING || state.getState() == PlaybackStateCompat.STATE_PAUSED ?
                Bitmap.createBitmap(origin, origin.getWidth() / 10, 0, origin.getHeight(), origin.getHeight()) : null);
        return notifyBuilder.build();
    }

    void cancelAll() {mManager.cancelAll();}

    NotificationManager getManager() {
        return mManager;
    }
}
