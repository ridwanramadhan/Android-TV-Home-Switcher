package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow, along with optional live Liquid Glass informational cards (Prayer times, Weather, AQI) and
 * automatic Adzan call to prayer triggers.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final View ambient;
    private final DashboardView dashboard;
    private final boolean dashboardEnabled;
    private DashboardRepository dashboardRepository;
    private DashboardSnapshot currentSnapshot;
    /** Date + prayer last announced from the screensaver, so a minute of ticks fires one call. */
    private String lastAnnouncedKey = "";
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** Bumped by stop(): a decode started before it finds a different number and drops its result. Main thread only. */
    private int wallpaperGeneration;
    /** The mosque screensaver gallery, built only for the widgets build when clock_wallpaper is off. */
    private MosqueGallery mosqueGallery;
    /** True once useHomeWallpaper selected the gallery; shifts the scene away from the ambient glow. */
    private boolean useMosqueGallery;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            if (dashboard != null && dashboardEnabled) {
                dashboard.updateCountdown(ZonedDateTime.now());
                if (currentSnapshot != null) {
                    checkPrayerTime(currentSnapshot);
                }
            }
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        dashboard = root.findViewById(R.id.dreamDashboard);

        Context ctx = root.getContext();
        boolean panels = false;
        try {
            panels = ctx.getResources().getBoolean(R.bool.dashboard_panels);
        } catch (Exception ignored) {
        }
        dashboardEnabled = panels;

        if (dashboard != null) {
            dashboard.setScreensaverStyle(true);
            dashboard.setVisibility(dashboardEnabled ? View.VISIBLE : View.GONE);
        }

        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    void start() {
        if (useMosqueGallery && mosqueGallery != null) {
            mosqueGallery.start();
        }
        content.setAlpha(0f);
        if (dashboard != null && dashboardEnabled) {
            dashboard.setAlpha(0f);
        }
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
            if (dashboard != null && dashboardEnabled) {
                dashboard.animate().alpha(1f).setDuration(1200).start();
            }
        });

        if (dashboard != null && dashboardEnabled) {
            if (dashboardRepository == null) {
                dashboardRepository = new DashboardRepository(content.getContext());
            }
            dashboardRepository.load(snap -> {
                currentSnapshot = snap;
                dashboard.setSnapshot(snap);
                checkPrayerTime(snap);
            });
        }

        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        if (useMosqueGallery && mosqueGallery != null) {
            mosqueGallery.stop();
        }
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
        if (dashboard != null) dashboard.animate().cancel();
        if (dashboardRepository != null) {
            dashboardRepository.stop();
        }
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put Home's wallpaper (the theme's single picture, or the first of its collection) behind
     * the clock, darkened by clock_wallpaper_dim, in place of the glow. Decoded off the main thread at screen size
     * in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read, the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!cfg.clockWallpaper) {
            // The widgets build plays the mosque gallery here; the plain-black clock_glow=false branch
            // only applies when the gallery is unavailable (the non-widgets build).
            if (dashboardEnabled) {
                useMosqueGallery = true;
                if (mosqueGallery == null) {
                    mosqueGallery = new MosqueGallery(content.getRootView(), MosquePhotos.all(), random);
                }
                return;
            }
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            }
            return;
        }
        wallpaperFile = cfg.wallpaper != null ? cfg.wallpaper : cfg.wallpapers.isEmpty() ? null : cfg.wallpapers.get(0);
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                ambient.setBackground(new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(res, bmp), new ColorDrawable(shade)}));
            });
        }, "clock-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A spot that keeps the clock on screen with OLED burn-in protection, glow centred. */
    private void placeClock() {
        View parent = (View) content.getParent();
        if (dashboardEnabled && dashboard != null) {
            // Anchor clock above the 3 widgets, with gentle OLED pixel-shift (+-14px)
            float density = content.getResources().getDisplayMetrics().density;
            float baseX = 42f * density;
            float baseY = 16f * density;
            float driftX = (random.nextFloat() * 28f - 14f) * density;
            float driftY = (random.nextFloat() * 16f - 8f) * density;
            content.setX(baseX + driftX);
            content.setY(baseY + driftY);
            dashboard.setTranslationX(driftX * 0.4f);
            // DashboardView draws its cards starting at 162/720 of its content box; ask it rather than
            // repeating the baseline, so the letterboxing on non-16:9 panels stays aligned.
            float cardTop = DashboardView.cardTopPx(dashboard.getWidth(), dashboard.getHeight());
            float clockBottom = content.getY() + content.getHeight();
            dashboard.setTranslationY(Math.max(0f, clockBottom + 12f * density - cardTop));
            if (parent.getWidth() > 0 && parent.getHeight() > 0) {
                glow.setGradientCenter(0.5f, 0.45f);
            }
        } else {
            int maxX = Math.max(0, parent.getWidth() - content.getWidth());
            int maxY = Math.max(0, parent.getHeight() - content.getHeight());
            float x = maxX * (0.1f + 0.8f * random.nextFloat());
            float y = maxY * (0.1f + 0.8f * random.nextFloat());
            content.setX(x);
            content.setY(y);
            if (parent.getWidth() > 0 && parent.getHeight() > 0) {
                glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                        (y + content.getHeight() / 2f) / parent.getHeight());
            }
        }
    }

    private void checkPrayerTime(DashboardSnapshot snap) {
        if (snap == null || snap.prayers == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        DashboardSnapshot.Prayer due = PrayerTimes.dueAt(snap.prayers, now.toLocalTime());
        if (due == null) return;
        String key = PrayerTimes.dailyKey(now.toLocalDate(), due.name);
        if (key.equals(lastAnnouncedKey)) return;
        lastAnnouncedKey = key;
        AdzanActivity.start(content.getContext(), due.name);
    }

    private void shiftGlow() {
        if (wallpaperShown || useMosqueGallery) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
