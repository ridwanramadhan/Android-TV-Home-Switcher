package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which prayer times the launcher announces, and when.
 *
 * <p>Three places used to decide this on their own: Home's minute tick, the screensaver's tick, and the
 * alarm scheduler. Each carried its own copy of the skip list and of the "fire once a day" key, and they
 * had already drifted (the scheduler skipped sunrise, the other two did not). The rules live here so a
 * change to them is one edit, and so they can be tested off-device.
 *
 * <p>Timings come from AlAdhan (see DashboardRepository), which names the five calls Subuh, Dzuhur, Ashar,
 * Maghrib and Isya, and the two markers Imsak and Syuruq. A device set to an English locale may see the
 * Arabic names instead, so Subuh is recognised as either.
 */
final class PrayerTimes {

    /** Shown in the schedule but not a call to prayer: the pre-dawn fast marker and sunrise. */
    private static final Set<String> NOT_A_CALL = Set.of("imsak", "syuruq", "sunrise");

    static final String SUBUH = "Subuh";
    static final String FAJR = "Fajr";

    private PrayerTimes() {
    }

    /**
     * True when {@code name} is one of the five calls, rather than Imsak or sunrise. An unknown name is
     * treated as a call: a schedule we do not recognise should still be announced rather than silently dropped.
     */
    static boolean isCallToPrayer(String name) {
        return name != null && !NOT_A_CALL.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Subuh is the longer dawn call and has its own video. */
    static boolean isDawn(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return SUBUH.equalsIgnoreCase(trimmed) || FAJR.equalsIgnoreCase(trimmed);
    }

    /** The video that goes with {@code name}: the dawn call for Subuh, the standard one otherwise. */
    static int videoResource(String name) {
        return isDawn(name) ? R.raw.adzan_subuh : R.raw.adzan;
    }

    /**
     * The call whose time falls in {@code now}'s hour and minute, or null.
     *
     * <p>Matching to the minute (rather than to the second) is deliberate: the feeds give whole minutes and
     * the ticks that call this are not aligned to the wall clock, so an exact instant would rarely be seen.
     */
    static DashboardSnapshot.Prayer dueAt(List<DashboardSnapshot.Prayer> prayers, LocalTime now) {
        if (prayers == null || now == null) return null;
        for (DashboardSnapshot.Prayer prayer : prayers) {
            if (prayer == null || prayer.time == null || !isCallToPrayer(prayer.name)) continue;
            if (prayer.time.getHour() == now.getHour() && prayer.time.getMinute() == now.getMinute()) {
                return prayer;
            }
        }
        return null;
    }

    /**
     * The key that keeps a call to once a day: the date and the prayer's name. Callers remember the last key
     * they acted on, so the minute-long window above cannot fire the same prayer twice, and a time zone or
     * clock change cannot fire yesterday's.
     */
    static String dailyKey(LocalDate date, String name) {
        return (date == null ? "" : date.toString()) + "_" + (name == null ? "" : name.trim());
    }

    /**
     * The next call to prayer strictly after {@code now}: today's next one, or tomorrow's first when the day's
     * calls have all passed. Null when the schedule holds no call at all.
     */
    static Occurrence next(List<DashboardSnapshot.Prayer> prayers, ZonedDateTime now) {
        if (prayers == null || now == null) return null;

        Occurrence soonest = null;
        for (DashboardSnapshot.Prayer prayer : prayers) {
            if (prayer == null || prayer.time == null || !isCallToPrayer(prayer.name)) continue;
            ZonedDateTime at = now.with(prayer.time).withSecond(0).withNano(0);
            if (!at.isAfter(now)) continue;
            if (soonest == null || at.isBefore(soonest.at)) soonest = new Occurrence(prayer, at);
        }
        if (soonest != null) return soonest;

        // Every remaining call today is behind us: the first one of tomorrow is next.
        for (DashboardSnapshot.Prayer prayer : prayers) {
            if (prayer == null || prayer.time == null || !isCallToPrayer(prayer.name)) continue;
            return new Occurrence(prayer, now.plusDays(1).with(prayer.time).withSecond(0).withNano(0));
        }
        return null;
    }

    /** A scheduled call: which prayer it is, and the instant it should play. */
    static final class Occurrence {
        final DashboardSnapshot.Prayer prayer;
        final ZonedDateTime at;

        Occurrence(DashboardSnapshot.Prayer prayer, ZonedDateTime at) {
            this.prayer = prayer;
            this.at = at;
        }

        String name() {
            return prayer.name;
        }
    }
}
