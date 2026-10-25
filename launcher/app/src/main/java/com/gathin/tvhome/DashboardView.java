package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Three static information cards. No focus targets, animations, bitmaps or blur layers. */
public final class DashboardView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private DashboardSnapshot data;
    private ZonedDateTime now = ZonedDateTime.now();
    private static final int WHITE = Color.WHITE, MUTED = 0xFFB8C1CF, TEAL = 0xFF22D2CA;

    public DashboardView(Context context, AttributeSet attrs) { super(context, attrs); setFocusable(false); }
    void setSnapshot(DashboardSnapshot snapshot) { data = snapshot; invalidate(); }
    void updateCountdown(ZonedDateTime time) { now = time; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / 1280f, sy = getHeight() / 720f;
        canvas.save(); canvas.scale(sx, sy);
        RectF prayer = new RectF(42, 162, 570, 382);
        RectF weather = new RectF(580, 162, 918, 382);
        RectF air = new RectF(928, 162, 1238, 382);
        panel(canvas, prayer, 0xED111419, 0xEF202733);
        panel(canvas, weather, 0xEF364761, 0xF0192E48);
        panel(canvas, air, 0xED302B25, 0xF0242428);
        drawPrayer(canvas, prayer);
        drawWeather(canvas, weather);
        drawAir(canvas, air);
        canvas.restore();
    }

    private void panel(Canvas c, RectF r, int top, int bottom) {
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, top, bottom, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL); c.drawRoundRect(r, 24, 24, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f); p.setColor(0x55FFFFFF);
        c.drawRoundRect(r, 24, 24, p); p.setStyle(Paint.Style.FILL);
    }

    private void txt(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        p.setColor(color); p.setTextSize(size); p.setTypeface(bold ? medium : regular);
        p.setTextAlign(Paint.Align.LEFT); p.setShader(null); c.drawText(s, x, y, p);
    }

    private void line(Canvas c, float x1, float y, float x2) {
        p.setColor(0x33FFFFFF); p.setStrokeWidth(1); c.drawLine(x1, y, x2, y, p);
    }

    private String place() { return data == null ? "Pekayon, Pasar Rebo, East Jakarta" : data.place; }
    private String compactPlace() {
        String place = place();
        return place.length() > 37 ? place.substring(0, 34) + "…" : place;
    }
    private String shortPlace() {
        return place().contains("Pekayon") ? "Pekayon, East Jakarta" : compactPlace();
    }

    private void drawPrayer(Canvas c, RectF r) {
        txt(c, "☪", 64, 212, 38, TEAL, true);
        txt(c, "Prayer Times", 112, 198, 25, WHITE, true);
        txt(c, (data != null && data.savedPlace ? "⌖ Saved: " : "⌖ ") + compactPlace(),
                113, 224, 14, MUTED, false);
        line(c, 67, 241, 545);
        if (data == null || data.prayers.isEmpty() || !LocalDate.now().equals(data.prayerDate)) {
            txt(c, "Today's schedule unavailable", 71, 284, 19, WHITE, false);
            txt(c, "Connect to update prayer times", 71, 311, 14, MUTED, false);
            return;
        }
        float cell = 68;
        for (int i = 0; i < Math.min(7, data.prayers.size()); i++) {
            DashboardSnapshot.Prayer prayer = data.prayers.get(i);
            float x = 65 + i * cell;
            txt(c, prayer.name, x, 267, 13, MUTED, false);
            txt(c, prayer.time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)), x, 290, 17, WHITE, true);
        }
        p.setColor(0xBB202A36); c.drawRoundRect(new RectF(64, 307, 548, 362), 17, 17, p);
        DashboardSnapshot.NextPrayer next = DashboardSnapshot.nextPrayer(data.prayers, now);
        if (next != null) {
            long hours = next.minutes / 60, minutes = next.minutes % 60;
            txt(c, "◷", 79, 343, 25, TEAL, true);
            txt(c, "Next prayer", 113, 341, 17, MUTED, false);
            txt(c, next.name + " in " + hours + "h " + minutes + "m", 237, 341, 17, TEAL, true);
        }
    }

    private void drawWeather(Canvas c, RectF r) {
        txt(c, "⌖ " + shortPlace(), 602, 196, 13, 0xFFD2E2FF, false);
        DashboardSnapshot.Weather w = data == null ? null : data.weather;
        if (w == null) {
            txt(c, "Weather unavailable", 604, 268, 22, WHITE, true);
            txt(c, "Open-Meteo · waiting for data", 604, 302, 13, MUTED, false);
            return;
        }
        txt(c, w.temperature + "°", 602, 280, 70, WHITE, false);
        txt(c, weatherSymbol(w.code), 758, 258, 35, WHITE, false);
        txt(c, weatherName(w.code), 804, 252, 17, WHITE, false);
        txt(c, "H:" + w.high + "°  L:" + w.low + "°", 802, 279, 16, 0xFFCCD7E9, false);
        line(c, 604, 298, 895);
        for (int i = 0; i < w.hours.length; i++) {
            float x = 604 + i * 58;
            txt(c, w.hours[i], x, 319, 12, MUTED, false);
            txt(c, weatherSymbol(w.hourlyCodes[i]), x + 9, 342, 16, WHITE, false);
            txt(c, w.hourlyTemperatures[i] + "°", x + 4, 365, 15, WHITE, true);
        }
        txt(c, "Open-Meteo · " + age(w.updatedAt), 604, 379, 10, MUTED, false);
    }

    private void drawAir(Canvas c, RectF r) {
        txt(c, "≋", 951, 205, 31, WHITE, true);
        txt(c, "Air Quality Index", 996, 199, 20, WHITE, true);
        txt(c, "⌖ " + shortPlace(), 995, 221, 12, MUTED, false);
        DashboardSnapshot.Air a = data == null ? null : data.air;
        if (a == null) {
            txt(c, "AQI unavailable", 953, 293, 21, WHITE, true);
            txt(c, "Open-Meteo / CAMS", 953, 321, 13, MUTED, false);
            return;
        }
        int color = a.aqi <= 50 ? 0xFF65D59F : a.aqi <= 100 ? 0xFFFFD34D : 0xFFFF806D;
        p.setColor(0x664F5B67); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(13);
        c.drawCircle(1002, 303, 54, p);
        p.setColor(color); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(948, 249, 1056, 357), -90, Math.min(330, a.aqi * 1.8f), false, p);
        p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.BUTT);
        txt(c, "AQI", 983, 300, 13, MUTED, false);
        txt(c, Integer.toString(a.aqi), 973, 326, 30, WHITE, true);
        txt(c, DashboardSnapshot.aqiCategory(a.aqi), 1074, 298, 20, color, true);
        txt(c, "Regional estimate", 1074, 321, 13, MUTED, false);
        txt(c, "Open-Meteo / CAMS · " + age(a.updatedAt), 952, 372, 10, MUTED, false);
    }

    private static String age(long updated) {
        long minutes = Math.max(0, (System.currentTimeMillis() - updated) / 60_000);
        return minutes < 2 ? "updated now" : "updated " + minutes + "m ago";
    }

    private static String weatherSymbol(int code) {
        if (code <= 1) return "☀";
        if (code <= 3) return "☁";
        if (code >= 51 && code <= 99) return "☂";
        return "☁";
    }
    private static String weatherName(int code) {
        if (code <= 1) return "Clear";
        if (code <= 3) return "Cloudy";
        if (code >= 51 && code <= 99) return "Rain";
        return "Hazy";
    }
}
