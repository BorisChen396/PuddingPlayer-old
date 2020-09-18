package com.azuredragon.puddingplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import static android.content.Context.NOTIFICATION_SERVICE;

public class MediaNotificationManager {
    static String channelId = "playerNotification";
    static int NOTIFICATION_ID = "PUDDING_IS_GOOD".length();

    private static NotificationManager mManager;
    private static PlaybackService mService;

    static NotificationCompat.Action mPlayIntent;
    static NotificationCompat.Action mPauseIntent;
    static NotificationCompat.Action mNextIntent;
    static NotificationCompat.Action mPrevIntent;

    public MediaNotificationManager(PlaybackService service) {
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

    public Notification getNotification(MediaSessionCompat.Token token,
                                        MediaDescriptionCompat des,
                                        NotificationCompat.Action[] actions) {
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
        int[] a = new int[actions.length];
        for(int i = 0; i < actions.length; i++) a[i] = i;
        notifyBuilder.setContentTitle(des.getTitle())
                .setContentText(des.getSubtitle())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(a))
                .setSmallIcon(R.drawable.ic_notify_player)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(mService, 0,
                        notificationIntent, 0))
                .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(mService,
                        PlaybackStateCompat.ACTION_STOP));
        if(des.getIconUri() != null) {
            Bitmap origin = BitmapFactory.decodeFile(
                    FileHandler.APPLICATION_DATA_DIR + "album_" + des.getExtras().getString("videoId"));
            notifyBuilder.setLargeIcon(Bitmap.createBitmap(origin, origin.getWidth() / 10, 0, origin.getHeight(), origin.getHeight()));
        }
        for (NotificationCompat.Action action : actions) {
            notifyBuilder.addAction(action);
        }

        return notifyBuilder.build();
    }

    public NotificationManager getManager() {
        return mManager;
    }
}
