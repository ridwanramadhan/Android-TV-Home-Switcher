package com.gathin.tvhome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Home-only data load; a failed feed leaves the others and their caches intact. */
final class DashboardRepository {
    private static final String TAG = "Dashboard";
    private static final double SAVED_LAT = -6.3456, SAVED_LON = 106.8639;
    private static final long HALF_HOUR = 30 * 60_000L;
    private final Context context;
    private final SharedPreferences cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;

    DashboardRepository(Context context) {
        this.context = context.getApplicationContext();
        cache = this.context.getSharedPreferences("dashboard_feed", Context.MODE_PRIVATE);
    }

    void load(Consumer<DashboardSnapshot> result) {
        final int request = ++generation;
        worker.execute(() -> {
            Place place = location();
            LocalDate today = LocalDate.now();
            String prayer = cached("prayer", place, Long.MAX_VALUE, today);
            String weather = cached("weather", place, Long.MAX_VALUE, today);
            String air = cached("air", place, Long.MAX_VALUE, today);
            deliver(request, result, snapshot(place, today, prayer, weather, air));
            long now = System.currentTimeMillis();
            boolean updatePrayer = prayer == null;
            boolean updateWeather = weather == null || now - cache.getLong("weather_time", 0) > HALF_HOUR;
            boolean updateAir = air == null || now - cache.getLong("air_time", 0) > HALF_HOUR;
            if (updatePrayer) {
                String fetched = fetch(String.format(Locale.US,
                        "https://api.aladhan.com/v1/timings/%s?latitude=%.5f&longitude=%.5f&method=20",
                        today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)), place.lat, place.lon));
                if (fetched != null && parsePrayers(fetched) != null) {
                    prayer = fetched; save("prayer", place, today, fetched);
                    deliver(request, result, snapshot(place, today, prayer, weather, air));
                }
            }
            if (updateWeather) {
                String fetched = fetch(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto",
                        place.lat, place.lon));
                if (fetched != null && parseWeather(fetched, now) != null) {
                    weather = fetched; save("weather", place, today, fetched);
                    deliver(request, result, snapshot(place, today, prayer, weather, air));
                }
            }
            if (updateAir) {
                String fetched = fetch(String.format(Locale.US,
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.5f&longitude=%.5f&current=us_aqi&timezone=auto",
                        place.lat, place.lon));
                if (fetched != null && parseAir(fetched, now) != null) {
                    air = fetched; save("air", place, today, fetched);
                    deliver(request, result, snapshot(place, today, prayer, weather, air));
                }
            }
        });
    }

    void stop() { generation++; }
    void close() { stop(); worker.shutdownNow(); }

    private void deliver(int request, Consumer<DashboardSnapshot> result, DashboardSnapshot value) {
        main.post(() -> { if (request == generation) result.accept(value); });
    }

    private static final class Place {
        final double lat, lon;
        final boolean saved;
        final String label;
        Place(double lat, double lon, boolean saved, String label) {
            this.lat = lat; this.lon = lon; this.saved = saved; this.label = label;
        }
        String key() { return String.format(Locale.US, "%.2f,%.2f", lat, lon); }
    }

    private Place location() {
        Location best = null;
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                for (String provider : manager.getProviders(true)) {
                    Location candidate = manager.getLastKnownLocation(provider);
                    if (candidate != null && candidate.getTime() <= System.currentTimeMillis()
                            && System.currentTimeMillis() - candidate.getTime() < 30L * 24 * 60 * 60_000L
                            && candidate.getLatitude() >= -90 && candidate.getLatitude() <= 90
                            && candidate.getLongitude() >= -180 && candidate.getLongitude() <= 180
                            && (best == null || candidate.getTime() > best.getTime())) best = candidate;
                }
            } catch (RuntimeException ex) { Log.w(TAG, "Location unavailable", ex); }
        }
        if (best == null) return new Place(SAVED_LAT, SAVED_LON, true, "Pekayon, Pasar Rebo, East Jakarta");
        double lat = best.getLatitude(), lon = best.getLongitude();
        // Keep the familiar local label when Android resolves only coordinates near this TV.
        String label = Math.abs(lat - SAVED_LAT) < 0.06 && Math.abs(lon - SAVED_LON) < 0.06
                ? "Pekayon, Pasar Rebo, East Jakarta"
                : String.format(Locale.US, "Current location  %.3f, %.3f", lat, lon);
        return new Place(lat, lon, false, label);
    }

    private String cached(String feed, Place place, long maxAge, LocalDate day) {
        if (!place.key().equals(cache.getString(feed + "_place", ""))) return null;
        if (feed.equals("prayer") && !day.toString().equals(cache.getString(feed + "_day", ""))) return null;
        if (System.currentTimeMillis() - cache.getLong(feed + "_time", 0) > maxAge) return null;
        return cache.getString(feed + "_json", null);
    }

    private void save(String feed, Place place, LocalDate day, String json) {
        cache.edit().putString(feed + "_place", place.key()).putString(feed + "_day", day.toString())
                .putLong(feed + "_time", System.currentTimeMillis()).putString(feed + "_json", json).apply();
    }

    private DashboardSnapshot snapshot(Place place, LocalDate today, String prayer, String weather, String air) {
        List<DashboardSnapshot.Prayer> prayers = prayer == null ? null : parsePrayers(prayer);
        DashboardSnapshot.Weather w = weather == null ? null : parseWeather(weather, cache.getLong("weather_time", 0));
        DashboardSnapshot.Air a = air == null ? null : parseAir(air, cache.getLong("air_time", 0));
        return new DashboardSnapshot(place.label, place.saved, prayers == null ? null : today, prayers, w, a);
    }

    static List<DashboardSnapshot.Prayer> getCachedPrayers(Context context) {
        if (context == null) return null;
        SharedPreferences cache = context.getApplicationContext().getSharedPreferences("dashboard_feed", Context.MODE_PRIVATE);
        String json = cache.getString("prayer_json", null);
        if (json == null) return null;
        return parsePrayers(json);
    }

    private static List<DashboardSnapshot.Prayer> parsePrayers(String json) {
        try {
            JSONObject times = new JSONObject(json).getJSONObject("data").getJSONObject("timings");
            String[] names = {"Imsak", "Subuh", "Syuruq", "Dzuhur", "Ashar", "Maghrib", "Isya"};
            String[] keys = {"Imsak", "Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
            List<DashboardSnapshot.Prayer> result = new ArrayList<>();
            for (int i = 0; i < keys.length; i++) {
                String raw = times.getString(keys[i]);
                result.add(new DashboardSnapshot.Prayer(names[i], LocalTime.parse(raw.substring(0, 5))));
            }
            return result;
        } catch (Exception ex) { Log.w(TAG, "Prayer parse failed", ex); return null; }
    }

    private static DashboardSnapshot.Weather parseWeather(String json, long updated) {
        try {
            JSONObject root = new JSONObject(json), current = root.getJSONObject("current"), daily = root.getJSONObject("daily"), hourly = root.getJSONObject("hourly");
            JSONArray times = hourly.getJSONArray("time"), temps = hourly.getJSONArray("temperature_2m"), codes = hourly.getJSONArray("weather_code");
            String currentTime = current.getString("time");
            int first = 0;
            while (first < times.length() - 1 && times.getString(first).compareTo(currentTime) < 0) first++;
            int n = Math.min(5, times.length() - first);
            String[] hours = new String[n]; int[] hourlyTemps = new int[n], hourlyCodes = new int[n];
            for (int i = 0; i < n; i++) {
                String time = times.getString(first + i);
                hours[i] = time.length() >= 16 ? time.substring(11, 16) : time;
                hourlyTemps[i] = (int) Math.round(temps.getDouble(first + i));
                hourlyCodes[i] = codes.getInt(first + i);
            }
            return new DashboardSnapshot.Weather((int) Math.round(current.getDouble("temperature_2m")),
                    (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                    (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                    current.getInt("weather_code"), hours, hourlyTemps, hourlyCodes, updated);
        } catch (Exception ex) { Log.w(TAG, "Weather parse failed", ex); return null; }
    }

    private static DashboardSnapshot.Air parseAir(String json, long updated) {
        try { return new DashboardSnapshot.Air(new JSONObject(json).getJSONObject("current").getInt("us_aqi"), updated); }
        catch (Exception ex) { Log.w(TAG, "AQI parse failed", ex); return null; }
    }

    private static String fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AQUA-TV-Home/1.0");
            if (connection.getResponseCode() != 200) return null;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int read;
                while ((read = input.read(buffer)) != -1) {
                    if (bytes.size() + read > 65536) return null;
                    bytes.write(buffer, 0, read);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception ex) { Log.w(TAG, "Feed unavailable: " + url.split("/", 4)[2], ex); return null; }
        finally { if (connection != null) connection.disconnect(); }
    }
}
