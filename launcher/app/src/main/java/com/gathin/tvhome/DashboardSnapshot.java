package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/** Values displayed on Home. Each feed may be absent independently. */
final class DashboardSnapshot {
    static final class Prayer {
        final String name;
        final LocalTime time;
        Prayer(String name, LocalTime time) { this.name = name; this.time = time; }
    }
    static final class NextPrayer {
        final String name;
        final long minutes;
        NextPrayer(String name, long minutes) { this.name = name; this.minutes = minutes; }
    }
    static final class Weather {
        final int temperature, high, low, code;
        final String[] hours;
        final int[] hourlyTemperatures, hourlyCodes;
        final long updatedAt;
        Weather(int temperature, int high, int low, int code, String[] hours,
                int[] hourlyTemperatures, int[] hourlyCodes, long updatedAt) {
            this.temperature = temperature; this.high = high; this.low = low; this.code = code;
            this.hours = hours; this.hourlyTemperatures = hourlyTemperatures;
            this.hourlyCodes = hourlyCodes; this.updatedAt = updatedAt;
        }
    }
    static final class Air {
        final int aqi;
        final long updatedAt;
        Air(int aqi, long updatedAt) { this.aqi = aqi; this.updatedAt = updatedAt; }
    }

    final String place;
    final boolean savedPlace;
    final LocalDate prayerDate;
    final List<Prayer> prayers;
    final Weather weather;
    final Air air;

    DashboardSnapshot(String place, boolean savedPlace, LocalDate prayerDate, List<Prayer> prayers,
                      Weather weather, Air air) {
        this.place = place; this.savedPlace = savedPlace; this.prayerDate = prayerDate;
        this.prayers = prayers == null ? Collections.emptyList() : Collections.unmodifiableList(prayers);
        this.weather = weather; this.air = air;
    }

    static String aqiCategory(int value) {
        if (value < 0) return "Unavailable";
        if (value <= 50) return "Good";
        if (value <= 100) return "Moderate";
        if (value <= 150) return "Sensitive groups";
        if (value <= 200) return "Unhealthy";
        if (value <= 300) return "Very unhealthy";
        return "Hazardous";
    }

    static NextPrayer nextPrayer(List<Prayer> prayers, ZonedDateTime now) {
        if (prayers == null || prayers.isEmpty()) return null;
        // Imsak and sunrise are shown in the schedule but are not prayer starts.
        for (Prayer prayer : prayers) {
            if (prayer.name.equals("Imsak") || prayer.name.equals("Syuruq")) continue;
            ZonedDateTime at = now.toLocalDate().atTime(prayer.time).atZone(now.getZone());
            if (at.isAfter(now)) return new NextPrayer(prayer.name,
                    Math.max(1, (java.time.Duration.between(now, at).toMillis() + 59_999) / 60_000));
        }
        for (Prayer prayer : prayers) {
            if (prayer.name.equals("Subuh")) {
                ZonedDateTime at = now.toLocalDate().plusDays(1).atTime(prayer.time).atZone(now.getZone());
                return new NextPrayer(prayer.name,
                        Math.max(1, (java.time.Duration.between(now, at).toMillis() + 59_999) / 60_000));
            }
        }
        return null;
    }
}
