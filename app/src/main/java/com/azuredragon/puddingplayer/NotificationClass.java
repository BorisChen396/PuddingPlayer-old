package com.azuredragon.puddingplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static android.content.Context.NOTIFICATION_SERVICE;

public class NotificationClass {
    static int NOTIFICATION_ID = "PUDDING_IS_GOOD".length();

    private static MediaBrowserServiceCompat browserService;
    private static MediaSessionCompat session;
    private static NotificationManager manager;
    private static NotificationCompat.Action[] actions;

    static NotificationManager getNotificationManager() {
        return manager;
    }

    static NotificationCompat.Builder getNotificationBuilder(MediaBrowserServiceCompat mBrowserService,
                                       MediaSessionCompat mSession,
                                       final MediaDescriptionCompat des,
                                       NotificationCompat.Action[] mActions) throws IOException {
        browserService = mBrowserService;
        session = mSession;
        actions = mActions;

        String channelId = "playerNotification";
        final NotificationCompat.Builder notifyBuilder = new NotificationCompat.Builder(browserService, channelId);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager = (NotificationManager) browserService.getSystemService(NOTIFICATION_SERVICE);
            if(manager == null) return null;
            NotificationChannel channel = new NotificationChannel(channelId,
                    browserService.getResources().getString(R.string.notifiyChannel), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            channel.setDescription(browserService.getString(R.string.notifyDes));
            manager.createNotificationChannel(channel);
        }
        else {
            notifyBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        }

        int[] a = new int[actions.length];
        for(int i = 0; i < actions.length; i++) a[i] = i;
        notifyBuilder.setContentTitle(des.getTitle())
                .setContentText(des.getSubtitle())
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(a))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        for (NotificationCompat.Action action : actions) {
            notifyBuilder.addAction(action);
        }

        if(des.getIconUri() != null) {
            URL url = new URL(des.getIconUri().toString());
            InputStream in = url.openStream();
            notifyBuilder.setLargeIcon(BitmapFactory.decodeStream(in));
        }
        return notifyBuilder;
    }

    static NotificationCompat.Action[] getActions() {
        return actions;
    }
}
