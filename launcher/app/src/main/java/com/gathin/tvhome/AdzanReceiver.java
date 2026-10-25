package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/**
 * BroadcastReceiver triggered by AlarmManager or device boot.
 * When prayer time arrives, it wakes up the TV and launches AdzanActivity
 * with high priority over the current foreground app (Netflix, Disney+, Screensaver, etc.).
 */
public class AdzanReceiver extends BroadcastReceiver {
    private static final String TAG = "AdzanReceiver";
    public static final String ACTION_ADZAN_ALARM = "com.gathin.tvhome.ACTION_ADZAN_ALARM";
    public static final String EXTRA_PRAYER_NAME = "prayer_name";
    private static final String CHANNEL_ID = "adzan_prayer_channel";
    private static final int NOTIFICATION_ID = 8802;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String action = intent.getAction();
        Log.i(TAG, "AdzanReceiver onReceive: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            AdzanScheduler.scheduleNextPrayer(context);
            return;
        }

        if (ACTION_ADZAN_ALARM.equals(action)) {
            String prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME);
            if (prayerName == null || prayerName.trim().isEmpty()) {
                prayerName = "Ashar";
            }

            Log.i(TAG, "Adzan alarm fired for: " + prayerName);

            // Acquire short WakeLock to wake the CPU and TV screen
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = null;
            if (pm != null) {
                try {
                    wakeLock = pm.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "GlassTV:AdzanPrayerWake"
                    );
                    wakeLock.acquire(15000L);
                } catch (Exception e) {
                    Log.w(TAG, "Could not acquire WakeLock", e);
                }
            }

            // Prepare intent to launch AdzanActivity
            Intent activityIntent = new Intent(context, AdzanActivity.class);
            activityIntent.putExtra(AdzanActivity.EXTRA_PRAYER, prayerName);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Post High Priority Alarm Notification with fullScreenIntent
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            "Waktu Sholat & Adzan",
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    channel.setDescription("Pemberitahuan Waktu Sholat Glass TV");
                    channel.setSound(null, null); // AdzanActivity handles media sound
                    nm.createNotificationChannel(channel);
                }

                Notification.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder = new Notification.Builder(context, CHANNEL_ID);
                } else {
                    builder = new Notification.Builder(context);
                }

                String displayTitle = "🕌 Waktu Sholat " + prayerName;
                builder.setSmallIcon(R.drawable.banner)
                        .setContentTitle(displayTitle)
                        .setContentText("Panggilan Sholat Adzan - Glass TV")
                        .setFullScreenIntent(fullScreenPendingIntent, true)
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setPriority(Notification.PRIORITY_MAX)
                        .setAutoCancel(true);

                try {
                    nm.notify(NOTIFICATION_ID, builder.build());
                } catch (Exception e) {
                    Log.w(TAG, "Notification post failed", e);
                }
            }

            // Direct start activity fallback
            try {
                context.startActivity(activityIntent);
            } catch (Exception e) {
                Log.e(TAG, "Direct startActivity failed", e);
            }

            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception ignored) {
                }
            }

            // Schedule next upcoming prayer
            AdzanScheduler.scheduleNextPrayer(context);
        }
    }
}
