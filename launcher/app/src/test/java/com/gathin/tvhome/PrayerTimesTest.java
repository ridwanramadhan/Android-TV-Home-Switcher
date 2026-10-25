package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The prayer rules shared by Home, the screensaver and the alarm scheduler. */
public class PrayerTimesTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");

    private static DashboardSnapshot.Prayer prayer(String name, int hour, int minute) {
        return new DashboardSnapshot.Prayer(name, LocalTime.of(hour, minute));
    }

    /** The feed's usual shape: two markers around the five calls. */
    private static List<DashboardSnapshot.Prayer> schedule() {
        return Arrays.asList(
                prayer("Imsak", 4, 20),
                prayer("Subuh", 4, 40),
                prayer("Syuruq", 5, 48),
                prayer("Dzuhur", 11, 45),
                prayer("Ashar", 15, 5),
                prayer("Maghrib", 17, 50),
                prayer("Isya", 19, 5));
    }

    @Test
    public void imsakAndSunriseAreNotCalls() {
        assertFalse(PrayerTimes.isCallToPrayer("Imsak"));
        assertFalse(PrayerTimes.isCallToPrayer("Syuruq"));
        assertFalse(PrayerTimes.isCallToPrayer("Sunrise"));
        assertFalse(PrayerTimes.isCallToPrayer(" sunrise "));
        assertTrue(PrayerTimes.isCallToPrayer("Subuh"));
        assertTrue(PrayerTimes.isCallToPrayer("Ashar"));
    }

    @Test
    public void unknownNamesAreStillAnnounced() {
        // A feed that renames a call should not silently lose it.
        assertTrue(PrayerTimes.isCallToPrayer("Some New Prayer"));
        assertFalse(PrayerTimes.isCallToPrayer(null));
    }

    @Test
    public void theDueCallIsTheOneInThisMinute() {
        assertEquals("Maghrib", PrayerTimes.dueAt(schedule(), LocalTime.of(17, 50)).name);
        assertNull(PrayerTimes.dueAt(schedule(), LocalTime.of(17, 51)));
        assertNull(PrayerTimes.dueAt(schedule(), LocalTime.of(17, 49)));
    }

    @Test
    public void markersNeverBecomeDue() {
        assertNull(PrayerTimes.dueAt(schedule(), LocalTime.of(5, 48)));
        assertNull(PrayerTimes.dueAt(schedule(), LocalTime.of(4, 20)));
    }

    @Test
    public void missingScheduleIsHarmless() {
        assertNull(PrayerTimes.dueAt(null, LocalTime.NOON));
        assertNull(PrayerTimes.dueAt(Collections.emptyList(), LocalTime.NOON));
        assertNull(PrayerTimes.dueAt(schedule(), null));
        assertNull(PrayerTimes.dueAt(Collections.singletonList(prayer(null, 12, 0)), LocalTime.NOON));
    }

    @Test
    public void subuhIsTheDawnCallUnderEitherName() {
        assertTrue(PrayerTimes.isDawn("Subuh"));
        assertTrue(PrayerTimes.isDawn("subuh"));
        assertTrue(PrayerTimes.isDawn("Fajr"));
        assertFalse(PrayerTimes.isDawn("Dzuhur"));
        assertFalse(PrayerTimes.isDawn(null));
    }

    @Test
    public void dawnPlaysItsOwnVideo() {
        assertEquals(R.raw.adzan_subuh, PrayerTimes.videoResource("Subuh"));
        assertEquals(R.raw.adzan_subuh, PrayerTimes.videoResource("Fajr"));
        assertEquals(R.raw.adzan, PrayerTimes.videoResource("Maghrib"));
        assertEquals(R.raw.adzan, PrayerTimes.videoResource(null));
    }

    @Test
    public void theDailyKeySeparatesPrayersAndDays() {
        assertEquals("2026-10-25_Ashar", PrayerTimes.dailyKey(LocalDate.of(2026, 10, 25), "Ashar"));
        assertFalse(PrayerTimes.dailyKey(LocalDate.of(2026, 10, 25), "Ashar")
                .equals(PrayerTimes.dailyKey(LocalDate.of(2026, 10, 26), "Ashar")));
        assertFalse(PrayerTimes.dailyKey(LocalDate.of(2026, 10, 25), "Ashar")
                .equals(PrayerTimes.dailyKey(LocalDate.of(2026, 10, 25), "Isya")));
    }

    @Test
    public void nextIsTodaysNextCall() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, JAKARTA);
        PrayerTimes.Occurrence next = PrayerTimes.next(schedule(), now);
        assertEquals("Ashar", next.name());
        assertEquals(ZonedDateTime.of(2026, 10, 25, 15, 5, 0, 0, JAKARTA), next.at);
    }

    @Test
    public void aCallLaterThisMinuteIsAlreadyPast() {
        // Firing exactly at the minute belongs to the tick, not the alarm: the alarm must not re-arm for now.
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 25, 15, 5, 0, 0, JAKARTA);
        assertEquals("Maghrib", PrayerTimes.next(schedule(), now).name());
    }

    @Test
    public void afterTheLastCallTomorrowsFirstOneIsNext() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 25, 21, 30, 0, 0, JAKARTA);
        PrayerTimes.Occurrence next = PrayerTimes.next(schedule(), now);
        assertEquals("Subuh", next.name());
        assertEquals(ZonedDateTime.of(2026, 10, 26, 4, 40, 0, 0, JAKARTA), next.at);
    }

    @Test
    public void nextSkipsMarkersAndEmptySchedules() {
        assertEquals("Subuh", PrayerTimes.next(schedule(), ZonedDateTime.of(
                2026, 10, 25, 4, 30, 0, 0, JAKARTA)).name());
        assertNull(PrayerTimes.next(null, ZonedDateTime.now(JAKARTA)));
        assertNull(PrayerTimes.next(Collections.emptyList(), ZonedDateTime.now(JAKARTA)));
        assertNull(PrayerTimes.next(Arrays.asList(prayer("Imsak", 4, 20), prayer("Syuruq", 5, 48)),
                ZonedDateTime.now(JAKARTA)));
    }
}
