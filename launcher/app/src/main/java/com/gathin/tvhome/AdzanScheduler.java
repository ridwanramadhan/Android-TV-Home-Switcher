package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Schedules exact Android OS alarms for upcoming prayer times (Subuh, Dzuhur, Ashar, Maghrib, Isya)
 * so Adzan can trigger reliably even when the TV is on screensaver or playing Netflix / Disney+.
 */
public final class AdzanScheduler {
    private static final String TAG = "AdzanScheduler";
    private static final int REQUEST_CODE = 8801;

    public static void scheduleNextPrayer(Context context) {
        if (context == null) return;

        Context appCtx = context.getApplicationContext();
        boolean enabled = false;
        try {
            enabled = appCtx.getResources().getBoolean(R.bool.dashboard_panels);
        } catch (Exception ignored) {
        }
        if (!enabled) {
            cancelAlarm(appCtx);
            return;
        }

        List<DashboardSnapshot.Prayer> prayers = DashboardRepository.getCachedPrayers(appCtx);
        if (prayers == null || prayers.isEmpty()) {
            // If cache not populated yet, load from repository
            try {
                new DashboardRepository(appCtx).load(snap -> {
                    if (snap != null && snap.prayers != null && !snap.prayers.isEmpty()) {
                        scheduleNextPrayer(appCtx);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to load dashboard repository for scheduler", e);
            }
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        DashboardSnapshot.Prayer nextPrayer = null;
        ZonedDateTime nextPrayerTime = null;

        for (DashboardSnapshot.Prayer p : prayers) {
            if (p.name.equalsIgnoreCase("Imsak") || p.name.equalsIgnoreCase("Syuruq")
                    || p.name.equalsIgnoreCase("Sunrise")) {
                continue;
            }
            ZonedDateTime pTime = now.with(p.time).withSecond(0).withNano(0);
            if (pTime.isAfter(now)) {
                if (nextPrayerTime == null || pTime.isBefore(nextPrayerTime)) {
                    nextPrayer = p;
                    nextPrayerTime = pTime;
                }
            }
        }

        // If all prayers for today passed, schedule for first prayer tomorrow
        if (nextPrayer == null) {
            for (DashboardSnapshot.Prayer p : prayers) {
                if (p.name.equalsIgnoreCase("Imsak") || p.name.equalsIgnoreCase("Syuruq")
                        || p.name.equalsIgnoreCase("Sunrise")) {
                    continue;
                }
                nextPrayer = p;
                nextPrayerTime = now.plusDays(1).with(p.time).withSecond(0).withNano(0);
                break;
            }
        }

        if (nextPrayer == null || nextPrayerTime == null) {
            Log.w(TAG, "No valid prayer times available to schedule");
            return;
        }

        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(appCtx, AdzanReceiver.class);
        intent.setAction(AdzanReceiver.ACTION_ADZAN_ALARM);
        intent.putExtra(AdzanReceiver.EXTRA_PRAYER_NAME, nextPrayer.name);

        PendingIntent pi = PendingIntent.getBroadcast(
                appCtx,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerMs = nextPrayerTime.toInstant().toEpochMilli();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            }
            Log.i(TAG, "Successfully scheduled next Adzan alarm: " + nextPrayer.name + " at " + nextPrayerTime);
        } catch (SecurityException se) {
            Log.w(TAG, "Exact alarm permission restricted, falling back to standard alarm", se);
            am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set alarm", e);
        }
    }

    public static void cancelAlarm(Context context) {
        if (context == null) return;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent intent = new Intent(context, AdzanReceiver.class);
                intent.setAction(AdzanReceiver.ACTION_ADZAN_ALARM);
                PendingIntent pi = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE,
                        intent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
