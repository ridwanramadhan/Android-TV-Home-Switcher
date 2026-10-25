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

/**
 * Three informational cards in Apple Liquid Glass style:
 * 1. Live Islamic Prayer Times with Next Prayer Countdown
 * 2. Real-time Weather & 5-hour micro forecast
 * 3. Air Quality Index (AQI) with color-coded arc meter
 * Includes subtle Glass TV watermark and minimalist aesthetic.
 *
 * Attribution: https://github.com/cocombee/glass-tv
 */
public final class DashboardView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular;
    private final Typeface medium;
    private final Typeface light;
    private final GlassRim panelRim;
    private DashboardSnapshot data;
    private ZonedDateTime now = ZonedDateTime.now();

    private static final int WHITE = Color.WHITE;
    private static final int MUTED = 0xB3CBD5E1; // slate-300 with high readability
    private static final int SUBTLE = 0x8094A3B8; // slate-400
    private static final int TEAL = 0xFF22D2CA;
    private static final int EMERALD = 0xFF34D399;

    public DashboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        Typeface reg, med, lig;
        try {
            reg = UiFont.get(context, R.string.font_regular);
            med = UiFont.get(context, R.string.font_medium);
            lig = UiFont.get(context, R.string.font_light);
        } catch (Exception e) {
            reg = Typeface.create("sans-serif", Typeface.NORMAL);
            med = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            lig = Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        regular = reg;
        medium = med;
        light = lig;
        // Apple Liquid Glass specular rim: specular diagonal shine catching top-left & bottom-right
        panelRim = new GlassRim(1.4f, 0xD0FFFFFF, 0x1AFFFFFF, 0.75f);
    }

    void setSnapshot(DashboardSnapshot snapshot) {
        data = snapshot;
        invalidate();
    }

    void updateCountdown(ZonedDateTime time) {
        now = time;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / 1280f, sy = getHeight() / 720f;
        canvas.save();
        canvas.scale(sx, sy);

        RectF prayer = new RectF(42, 162, 570, 382);
        RectF weather = new RectF(580, 162, 918, 382);
        RectF air = new RectF(928, 162, 1238, 382);

        // Draw Liquid Glass Panels
        liquidPanel(canvas, prayer);
        liquidPanel(canvas, weather);
        liquidPanel(canvas, air);

        drawPrayer(canvas, prayer);
        drawWeather(canvas, weather);
        drawAir(canvas, air);

        // Watermark: Minimalist attribution to Glass TV (cocombee/glass-tv)
        drawWatermark(canvas);

        canvas.restore();
    }

    private void liquidPanel(Canvas c, RectF r) {
        // Translucent frosted glass gradient
        p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, 0x3D182230, 0x240F1622, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(r, 22, 22, p);
        p.setShader(null);

        // Top specular sheen line (Apple Liquid Glass highlight)
        p.setColor(0x2EFFFFFF);
        p.setStrokeWidth(1.2f);
        p.setStyle(Paint.Style.STROKE);
        c.drawLine(r.left + 26, r.top + 1.2f, r.right - 26, r.top + 1.2f, p);

        // Liquid Glass Rim with diagonal specular shine
        panelRim.fit(r);
        c.drawRoundRect(r, 22, 22, panelRim.paint);
        p.setStyle(Paint.Style.FILL);
    }

    /** The three faces the panels are drawn in, as passed to {@link #txt}. */
    private static final int FONT_REGULAR = 0;
    private static final int FONT_MEDIUM = 1;
    private static final int FONT_LIGHT = 2;

    private void txt(Canvas c, String s, float x, float y, float size, int color, int face) {
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(face == FONT_MEDIUM ? medium : face == FONT_LIGHT ? light : regular);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShader(null);
        c.drawText(s, x, y, p);
    }

    private void line(Canvas c, float x1, float y, float x2) {
        p.setColor(0x22FFFFFF);
        p.setStrokeWidth(1);
        c.drawLine(x1, y, x2, y, p);
    }

    private String place() {
        return data == null ? "Pekayon, Pasar Rebo, East Jakarta" : data.place;
    }

    private String compactPlace() {
        String place = place();
        return place.length() > 37 ? place.substring(0, 34) + "…" : place;
    }

    private String shortPlace() {
        return place().contains("Pekayon") ? "Pekayon, East Jakarta" : compactPlace();
    }

    private void drawPrayer(Canvas c, RectF r) {
        txt(c, "☪", 64, 206, 32, TEAL, FONT_MEDIUM);
        txt(c, "Prayer Times", 108, 196, 22, WHITE, FONT_MEDIUM);
        txt(c, (data != null && data.savedPlace ? "⌖ Saved: " : "⌖ ") + compactPlace(),
                109, 218, 13, MUTED, FONT_REGULAR);
        line(c, 64, 233, 548);

        if (data == null || data.prayers.isEmpty() || !LocalDate.now().equals(data.prayerDate)) {
            txt(c, "Today's schedule unavailable", 71, 284, 18, WHITE, FONT_REGULAR);
            txt(c, "Connect to Wi-Fi to update prayer times", 71, 311, 13, SUBTLE, FONT_REGULAR);
            return;
        }

        DashboardSnapshot.NextPrayer next = DashboardSnapshot.nextPrayer(data.prayers, now);
        float cell = 69f;
        for (int i = 0; i < Math.min(7, data.prayers.size()); i++) {
            DashboardSnapshot.Prayer prayer = data.prayers.get(i);
            float x = 64 + i * cell;
            boolean isNext = next != null && prayer.name.equalsIgnoreCase(next.name);

            if (isNext) {
                // Soft liquid glass pill under next prayer
                RectF nextPill = new RectF(x - 4, 245, x + 58, 298);
                p.setColor(0x2822D2CA);
                c.drawRoundRect(nextPill, 10, 10, p);
                p.setColor(0x6622D2CA);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f);
                c.drawRoundRect(nextPill, 10, 10, p);
                p.setStyle(Paint.Style.FILL);
            }

            txt(c, prayer.name, x, 263, 12, isNext ? TEAL : MUTED,
                    isNext ? FONT_MEDIUM : FONT_REGULAR);
            txt(c, prayer.time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)),
                    x, 288, 16, isNext ? WHITE : 0xEEFFFFFF, FONT_MEDIUM);
        }

        // Countdown pill: it spans the same 64..548 column as the schedule above, so the card reads as one
        // stack rather than a floating label.
        RectF countdownPill = new RectF(64, 312, 548, 362);
        p.setColor(0x261E293B);
        c.drawRoundRect(countdownPill, 14, 14, p);
        p.setColor(0x28FFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        c.drawRoundRect(countdownPill, 14, 14, p);
        p.setStyle(Paint.Style.FILL);

        if (next != null) {
            long hours = next.minutes / 60, minutes = next.minutes % 60;
            txt(c, "◷", 80, 344, 22, TEAL, FONT_MEDIUM);
            txt(c, "Next prayer", 112, 342, 15, MUTED, FONT_REGULAR);
            txt(c, next.name + " in " + hours + "h " + minutes + "m", 216, 342, 16, TEAL, FONT_MEDIUM);
        }
    }

    private void drawWeather(Canvas c, RectF r) {
        txt(c, "⌖ " + shortPlace(), 602, 196, 13, 0xFF93C5FD, FONT_REGULAR);
        DashboardSnapshot.Weather w = data == null ? null : data.weather;
        if (w == null) {
            txt(c, "Weather unavailable", 604, 268, 20, WHITE, FONT_MEDIUM);
            txt(c, "Open-Meteo · waiting for data", 604, 302, 13, SUBTLE, FONT_REGULAR);
            return;
        }

        txt(c, w.temperature + "°", 602, 276, 64, WHITE, FONT_LIGHT);
        txt(c, weatherSymbol(w.code), 754, 254, 32, WHITE, FONT_REGULAR);
        txt(c, weatherName(w.code), 798, 248, 16, WHITE, FONT_MEDIUM);
        txt(c, "H:" + w.high + "°  L:" + w.low + "°", 798, 274, 15, 0xFFCBD5E1, FONT_REGULAR);
        line(c, 604, 294, 894);

        for (int i = 0; i < w.hours.length; i++) {
            float x = 604 + i * 58;
            txt(c, w.hours[i], x, 316, 12, MUTED, FONT_REGULAR);
            txt(c, weatherSymbol(w.hourlyCodes[i]), x + 8, 338, 16, WHITE, FONT_REGULAR);
            txt(c, w.hourlyTemperatures[i] + "°", x + 3, 361, 14, WHITE, FONT_MEDIUM);
        }
        txt(c, "Open-Meteo · " + age(w.updatedAt), 604, 376, 10, SUBTLE, FONT_REGULAR);
    }

    private void drawAir(Canvas c, RectF r) {
        txt(c, "≋", 950, 204, 28, 0xFF67E8F9, FONT_MEDIUM);
        txt(c, "Air Quality Index", 988, 198, 19, WHITE, FONT_MEDIUM);
        txt(c, "⌖ " + shortPlace(), 988, 220, 12, MUTED, FONT_REGULAR);

        DashboardSnapshot.Air a = data == null ? null : data.air;
        if (a == null) {
            txt(c, "AQI unavailable", 953, 293, 20, WHITE, FONT_MEDIUM);
            txt(c, "Open-Meteo / CAMS", 953, 321, 13, SUBTLE, FONT_REGULAR);
            return;
        }

        int color = a.aqi <= 50 ? 0xFF22C55E : a.aqi <= 100 ? 0xFFFBBF24 : a.aqi <= 150 ? 0xFFFB923C : 0xFFEF4444;

        // Circular radial gauge
        p.setColor(0x2E475569);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(11);
        c.drawCircle(998, 299, 50, p);

        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(948, 249, 1048, 349), -90, Math.min(330, a.aqi * 1.8f), false, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);

        txt(c, "AQI", 984, 295, 12, MUTED, FONT_REGULAR);
        txt(c, Integer.toString(a.aqi), 972, 320, 27, WHITE, FONT_MEDIUM);

        txt(c, DashboardSnapshot.aqiCategory(a.aqi), 1068, 293, 19, color, FONT_MEDIUM);
        txt(c, "Regional estimate", 1068, 316, 13, SUBTLE, FONT_REGULAR);
        txt(c, "Open-Meteo / CAMS · " + age(a.updatedAt), 950, 372, 10, SUBTLE, FONT_REGULAR);
    }

    private void drawWatermark(Canvas c) {
        // Minimalist Glass TV Watermark (cocombee/glass-tv)
        p.setColor(0x40FFFFFF);
        p.setTextSize(11);
        p.setTypeface(medium);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setLetterSpacing(0.12f);
        c.drawText("GLASS TV", 1238, 398, p);
        p.setLetterSpacing(0f);
    }

    private static String age(long updated) {
        long minutes = Math.max(0, (System.currentTimeMillis() - updated) / 60_000);
        return minutes < 2 ? "updated now" : "updated " + minutes + "m ago";
    }

    private static String weatherSymbol(int code) {
        if (code <= 1) return "☀";
        if (code <= 3) return "⛅";
        if (code >= 45 && code <= 48) return "☁";
        if (code >= 51 && code <= 67) return "🌧";
        if (code >= 71 && code <= 77) return "❄";
        if (code >= 80 && code <= 82) return "🌧";
        if (code >= 95) return "⛈";
        return "⛅";
    }

    private static String weatherName(int code) {
        if (code <= 1) return "Clear";
        if (code <= 3) return "Partly Cloudy";
        if (code >= 45 && code <= 48) return "Foggy";
        if (code >= 51 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 95) return "Thunderstorm";
        return "Hazy";
    }
}
