package com.tda.mediafloatbutton;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

/**
 * The pinned notification-shade row with direct On/Off/Tắt actions.
 * It is only posted while Settings → "show pinned notification" is on.
 * Swiping it away re-posts it (delete intent); it disappears only when
 * the user turns the Settings switch off or taps "Tắt".
 */
public final class PinnedNotification {

    static final String CHANNEL_ID = "pinned_toggle";
    static final int NOTIFICATION_ID = 42;

    public static final String ACTION_TOGGLE =
            "com.tda.mediafloatbutton.ACTION_NOTIFICATION_TOGGLE";
    public static final String ACTION_DISMISSED =
            "com.tda.mediafloatbutton.ACTION_NOTIFICATION_DISMISSED";

    private PinnedNotification() {}

    public static void refresh(Context context) {
        if (!Prefs.isPinnedNotificationShown(context)) {
            cancel(context);
            return;
        }
        Context ctx = AppLocale.wrap(context.getApplicationContext());
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, ctx.getString(R.string.channel_pinned_name),
                NotificationManager.IMPORTANCE_LOW));

        boolean on = Prefs.isOverlayEnabled(ctx);
        PendingIntent toggle = PendingIntent.getBroadcast(ctx, 1,
                new Intent(ctx, ToggleReceiver.class).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent dismissed = PendingIntent.getBroadcast(ctx, 2,
                new Intent(ctx, ToggleReceiver.class).setAction(ACTION_DISMISSED),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(on ? R.drawable.ic_pause : R.drawable.ic_play)
                .setContentTitle(ctx.getString(R.string.pinned_title))
                .setContentText(ctx.getString(
                        on ? R.string.pinned_status_on : R.string.pinned_status_off))
                .setContentIntent(open)
                .setDeleteIntent(dismissed)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .addAction(on ? R.drawable.ic_pause : R.drawable.ic_play,
                        ctx.getString(on ? R.string.action_turn_off : R.string.action_turn_on),
                        toggle);
        nm.notify(NOTIFICATION_ID, builder.build());
    }

    public static void cancel(Context context) {
        NotificationManager nm =
                context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}
