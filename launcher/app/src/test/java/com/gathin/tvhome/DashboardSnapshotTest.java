package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

public class DashboardSnapshotTest {
    @Test public void nextPrayerRollsOverAfterIsya() {
        var prayers = Arrays.asList(
                new DashboardSnapshot.Prayer("Subuh", LocalTime.of(4, 11)),
                new DashboardSnapshot.Prayer("Maghrib", LocalTime.of(17, 49)));
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 25, 17, 50, 0, 0, ZoneId.of("Asia/Jakarta"));
        DashboardSnapshot.NextPrayer next = DashboardSnapshot.nextPrayer(prayers, now);
        assertEquals("Subuh", next.name);
        assertEquals(621, next.minutes);
    }

    @Test public void classifiesUsAqi() {
        assertEquals("Good", DashboardSnapshot.aqiCategory(50));
        assertEquals("Moderate", DashboardSnapshot.aqiCategory(68));
        assertEquals("Sensitive groups", DashboardSnapshot.aqiCategory(101));
    }
}
