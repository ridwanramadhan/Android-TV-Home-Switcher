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
 * Schedules one exact OS alarm for the next call to prayer, so the Adzan still fires when the TV is on the
 * screensaver or another app is in front and this process may be asleep.
 *
 * <p>The alarm is deliberately one-shot: AdzanReceiver re-arms the following one after each call, and a
 * reboot or clock change re-arms it too. A repeating alarm would drift against a schedule that changes daily.
 */
public final class AdzanScheduler {
    private static final String TAG = "AdzanScheduler";
    private static final int REQUEST_CODE = 8801;

    private AdzanScheduler() {
    }

    /**
     * Arms the next call. Does nothing on a build without the dashboard panels: {@code dashboard_panels}
     * is the build-wide switch for the widgets, the Adzan panel among them, so a fresh-mode APK sets no alarm.
     */
    public static void scheduleNextPrayer(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();

        if (!panelsEnabled(appCtx)) {
            cancelAlarm(appCtx);
            return;
        }

        List<DashboardSnapshot.Prayer> prayers = DashboardRepository.getCachedPrayers(appCtx);
        if (prayers == null || prayers.isEmpty()) {
            // Nothing cached yet (a cold boot): fetch once, which calls back into this method when it lands.
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

        PrayerTimes.Occurrence next = PrayerTimes.next(prayers, ZonedDateTime.now());
        if (next == null) {
            Log.w(TAG, "No valid prayer times available to schedule");
            return;
        }

        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(appCtx, AdzanReceiver.class);
        intent.setAction(AdzanReceiver.ACTION_ADZAN_ALARM);
        intent.putExtra(AdzanReceiver.EXTRA_PRAYER_NAME, next.name());
        PendingIntent pi = PendingIntent.getBroadcast(
                appCtx,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerMs = next.at.toInstant().toEpochMilli();

        // Android 12+ can withhold exact alarms (the user or the OEM can revoke the grant). Say so once in the
        // log rather than silently arming an inexact one: the call would then fire minutes late.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarms are not permitted; the adzan may fire late");
        }

        try {
            // minSdk is 26, so the exact variant exists on every supported device; no inexact fallback branch.
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            Log.i(TAG, "Scheduled next Adzan alarm: " + next.name() + " at " + next.at);
        } catch (SecurityException se) {
            Log.w(TAG, "Exact alarm permission restricted, falling back to a standard alarm", se);
            am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set alarm", e);
        }
    }

    private static boolean panelsEnabled(Context appCtx) {
        try {
            return appCtx.getResources().getBoolean(R.bool.dashboard_panels);
        } catch (Exception e) {
            Log.w(TAG, "dashboard_panels is missing from this build", e);
            return false;
        }
    }

    /** Drops the pending call; used by a fresh-mode build and before re-arming. */
    public static void cancelAlarm(Context context) {
        if (context == null) return;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(context, AdzanReceiver.class);
            intent.setAction(AdzanReceiver.ACTION_ADZAN_ALARM);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not cancel the adzan alarm", e);
        }
    }
}
