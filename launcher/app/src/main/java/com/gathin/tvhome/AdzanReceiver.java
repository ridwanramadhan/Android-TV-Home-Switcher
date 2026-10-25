package com.gathin.tvhome;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/**
 * The alarm's landing point: wakes the panel, opens the call to prayer over whatever is in front, and re-arms
 * tomorrow's.
 *
 * <p>The activity is started directly as well as through a full-screen notification. A broadcast receiver
 * cannot normally start an activity on Android 10+, but an app holding {@code SYSTEM_ALERT_WINDOW} is exempt
 * (that is the only reason the switchers grant it — this app draws no overlay), and the notification's
 * full-screen intent covers the case where the direct start is refused.
 */
public class AdzanReceiver extends BroadcastReceiver {
    private static final String TAG = "AdzanReceiver";
    public static final String ACTION_ADZAN_ALARM = "com.gathin.tvhome.ACTION_ADZAN_ALARM";
    public static final String EXTRA_PRAYER_NAME = "prayer_name";
    private static final String CHANNEL_ID = "adzan_prayer_channel";
    private static final int NOTIFICATION_ID = 8802;
    /** Long enough for the activity to come up and start its own playback; a wakelock held longer drains the TV. */
    private static final long WAKE_MS = 15_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        Log.i(TAG, "AdzanReceiver onReceive: " + action);

        // Recompute after a reboot or a clock change: the alarm was set against the old wall clock.
        // ACTION_TIME_CHANGED is the constant whose value is "android.intent.action.TIME_SET", the action the
        // manifest filter listens for.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            AdzanScheduler.scheduleNextPrayer(context);
            return;
        }

        if (!ACTION_ADZAN_ALARM.equals(action)) return;

        // An alarm posted before this version carries no name: play the standard call under the general title.
        String prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME);
        if (prayerName == null) prayerName = "";
        Log.i(TAG, "Adzan alarm fired for: " + prayerName);

        PowerManager.WakeLock wakeLock = wakePanel(context);

        Intent activityIntent = new Intent(context, AdzanActivity.class)
                .putExtra(AdzanActivity.EXTRA_PRAYER, prayerName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        postAlarmNotification(context, prayerName, fullScreenIntent);

        // Direct start, the normal path when the SYSTEM_ALERT_WINDOW exemption above applies.
        try {
            context.startActivity(activityIntent);
        } catch (Exception e) {
            Log.e(TAG, "Direct startActivity failed; the notification is the remaining path", e);
        }

        release(wakeLock);

        // Arm tomorrow's call; the alarm is one-shot, so a missed re-arm means no adzan until the next reboot.
        AdzanScheduler.scheduleNextPrayer(context);
    }

    private static PowerManager.WakeLock wakePanel(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return null;
        try {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "GlassTV:AdzanPrayerWake");
            wakeLock.acquire(WAKE_MS);
            return wakeLock;
        } catch (Exception e) {
            Log.w(TAG, "Could not acquire WakeLock", e);
            return null;
        }
    }

    private static void release(PowerManager.WakeLock wakeLock) {
        if (wakeLock == null || !wakeLock.isHeld()) return;
        try {
            wakeLock.release();
        } catch (Exception ignored) {
            // Released by the timeout already.
        }
    }

    /**
     * The high-priority notification whose full-screen intent opens the call. Silence itself: AdzanActivity
     * owns the audio, and a notification sound would talk over it.
     */
    private static void postAlarmNotification(Context context, String prayerName,
                                              PendingIntent fullScreenIntent) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.adzan_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.adzan_channel_description));
            channel.setSound(null, null);
            nm.createNotificationChannel(channel);
        }

        if (!canPostNotifications(context)) {
            // Android 13+: without the runtime grant the notification (and its full-screen intent) would be
            // dropped, and the direct start above is what opens the call.
            Log.i(TAG, "POST_NOTIFICATIONS not granted; relying on the direct activity start");
            return;
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        CharSequence title = prayerName.isEmpty()
                ? context.getString(R.string.adzan_title_default)
                : context.getString(R.string.adzan_prayer_time, prayerName);

        builder.setSmallIcon(R.drawable.ic_adzan_notification)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.adzan_notification_body))
                .setContentIntent(fullScreenIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true);

        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "Notification post failed", e);
        }
    }

    private static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
