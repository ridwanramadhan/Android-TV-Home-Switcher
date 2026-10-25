package com.gathin.tvhome;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Handles playing the full-screen Adzan video call to prayer with audio when prayer time arrives.
 * Displays a full-screen overlay that blocks the launcher until the user explicitly dismisses it
 * or chooses to keep watching undisturbed.
 */
public final class AdzanPlayer {
    private static final String TAG = "AdzanPlayer";
    private static final long AUTO_HIDE_DELAY_MS = 6000L;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());

    private View overlayView;
    private FullScreenTextureView textureView;
    private View gradientTop;
    private View gradientBottom;
    private View adzanHeader;
    private TextView adzanTitle;
    private TextView adzanSubtitle;
    private View adzanControls;
    private TextView btnDismiss;
    private TextView btnKeep;
    private TextView adzanRemoteHint;

    private MediaPlayer mediaPlayer;
    private Surface videoSurface;
    private boolean isShowing;
    private boolean controlsHidden;
    private String lastPlayedKey = "";
    private String currentPrayer = "";
    private Runnable onStateChangeListener;

    private final Runnable autoHideRunnable = this::hideControls;

    public AdzanPlayer(Activity activity) {
        this.activity = activity;
    }

    public void setOnStateChangeListener(Runnable listener) {
        this.onStateChangeListener = listener;
    }

    private void ensureViews() {
        if (overlayView != null) return;
        overlayView = activity.findViewById(R.id.adzanOverlay);
        if (overlayView == null) return;

        textureView = overlayView.findViewById(R.id.adzanTextureView);
        gradientTop = overlayView.findViewById(R.id.adzanGradientTop);
        gradientBottom = overlayView.findViewById(R.id.adzanGradientBottom);
        adzanHeader = overlayView.findViewById(R.id.adzanHeader);
        adzanTitle = overlayView.findViewById(R.id.adzanTitle);
        adzanSubtitle = overlayView.findViewById(R.id.adzanSubtitle);
        adzanControls = overlayView.findViewById(R.id.adzanControls);
        btnDismiss = overlayView.findViewById(R.id.btnDismissAdzan);
        btnKeep = overlayView.findViewById(R.id.btnKeepAdzan);
        adzanRemoteHint = overlayView.findViewById(R.id.adzanRemoteHint);

        btnDismiss.setOnClickListener(v -> dismiss());
        btnKeep.setOnClickListener(v -> hideControls());
        overlayView.setOnClickListener(v -> {
            if (controlsHidden) {
                showControls();
                scheduleAutoHideControls();
            }
        });
    }

    /**
     * Checks if the current minute coincides with any prayer time.
     * Fires only once per prayer per day.
     */
    public synchronized void checkPrayerTime(List<DashboardSnapshot.Prayer> prayers, ZonedDateTime now) {
        if (prayers == null || prayers.isEmpty() || now == null) return;
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();
        String today = now.toLocalDate().toString();

        for (DashboardSnapshot.Prayer prayer : prayers) {
            if (prayer.name.equalsIgnoreCase("Imsak") || prayer.name.equalsIgnoreCase("Syuruq")) {
                continue;
            }
            if (prayer.time.getHour() == currentHour && prayer.time.getMinute() == currentMinute) {
                String key = today + "_" + prayer.name;
                if (!key.equals(lastPlayedKey)) {
                    lastPlayedKey = key;
                    play(prayer.name);
                }
                break;
            }
        }
    }

    /**
     * Starts fullscreen adzan video & audio playback with interactive HUD controls.
     */
    public void play(String prayerName) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            playInternal(prayerName);
        } else {
            main.post(() -> playInternal(prayerName));
        }
    }

    private void playInternal(String prayerName) {
        try {
            this.currentPrayer = prayerName != null ? prayerName.trim() : "";
            ensureViews();
            if (overlayView == null || textureView == null) {
                Log.w(TAG, "Adzan overlay views not available");
                return;
            }

            // Synchronously reset previous playback if running
            releasePlayer();

            String titleText = (prayerName != null && !prayerName.trim().isEmpty())
                    ? activity.getString(R.string.adzan_prayer_time, prayerName)
                    : activity.getString(R.string.adzan_title_default);
            if (adzanTitle != null) {
                adzanTitle.setText("🕌 " + titleText);
            }

            isShowing = true;
            overlayView.setVisibility(View.VISIBLE);
            overlayView.bringToFront();
            overlayView.setAlpha(1f);

            showControls();

            if (textureView.isAvailable()) {
                startPlaybackOnSurface(textureView.getSurfaceTexture());
            } else {
                textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        if (isShowing) {
                            startPlaybackOnSurface(surface);
                        }
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        releasePlayer();
                        return true;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    }
                });
            }

            if (btnDismiss != null) {
                btnDismiss.requestFocus();
            }

            if (onStateChangeListener != null) onStateChangeListener.run();
            Log.i(TAG, "Playing fullscreen adzan: " + prayerName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start adzan playback", e);
            dismiss();
        }
    }

    private void startPlaybackOnSurface(SurfaceTexture surfaceTexture) {
        try {
            releasePlayer();

            videoSurface = new Surface(surfaceTexture);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setSurface(videoSurface);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mediaPlayer.setAudioAttributes(attrs);

            int videoRes = R.raw.adzan;
            if (currentPrayer.equalsIgnoreCase("Subuh") || currentPrayer.equalsIgnoreCase("Fajr")) {
                videoRes = R.raw.adzan_subuh;
            }

            AssetFileDescriptor afd = null;
            try {
                afd = activity.getResources().openRawResourceFd(videoRes);
            } catch (Exception ignored) {
            }

            if (afd != null) {
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else {
                Uri videoUri = Uri.parse("android.resource://" + activity.getPackageName() + "/" + videoRes);
                mediaPlayer.setDataSource(activity, videoUri);
            }

            mediaPlayer.setOnPreparedListener(mp -> {
                Log.i(TAG, "Adzan MediaPlayer prepared, starting playback");
                if (isShowing) {
                    mp.start();
                    scheduleAutoHideControls();
                } else {
                    releasePlayer();
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "Adzan video playback completed (3:20 reached)");
                dismiss();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "Adzan MediaPlayer error: what=" + what + ", extra=" + extra);
                dismiss();
                return true;
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare MediaPlayer on surface", e);
            dismiss();
        }
    }

    public synchronized boolean isShowing() {
        return isShowing && overlayView != null && overlayView.getVisibility() == View.VISIBLE;
    }

    public synchronized boolean isPlaying() {
        try {
            return isShowing() && mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Exception ignored) {
            return false;
        }
    }

    public void dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal();
        } else {
            main.post(this::dismissInternal);
        }
    }

    private void dismissInternal() {
        if (!isShowing) return;
        isShowing = false;
        main.removeCallbacks(autoHideRunnable);

        releasePlayer();

        if (overlayView != null) {
            overlayView.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        if (!isShowing && overlayView != null) {
                            overlayView.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }

        View grid = activity.findViewById(R.id.grid);
        if (grid != null) {
            grid.requestFocus();
        }

        if (onStateChangeListener != null) onStateChangeListener.run();
        Log.i(TAG, "Adzan dismissed and returned to launcher");
    }

    public void stop() {
        dismiss();
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        if (videoSurface != null) {
            try {
                videoSurface.release();
            } catch (Exception ignored) {
            }
            videoSurface = null;
        }
    }

    public void showControls() {
        controlsHidden = false;
        main.removeCallbacks(autoHideRunnable);
        fadeView(adzanControls, 1f);
        fadeView(adzanHeader, 1f);
        fadeView(gradientTop, 1f);
        fadeView(gradientBottom, 1f);
        fadeView(adzanRemoteHint, 1f);

        if (btnDismiss != null && !btnDismiss.isFocused() && btnKeep != null && !btnKeep.isFocused()) {
            btnDismiss.requestFocus();
        }
    }

    public void hideControls() {
        controlsHidden = true;
        main.removeCallbacks(autoHideRunnable);
        fadeView(adzanControls, 0f);
        fadeView(adzanHeader, 0f);
        fadeView(gradientTop, 0f);
        fadeView(gradientBottom, 0f);
        fadeView(adzanRemoteHint, 0f);
    }

    private void scheduleAutoHideControls() {
        main.removeCallbacks(autoHideRunnable);
        main.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS);
    }

    private void fadeView(View view, float alpha) {
        if (view != null) {
            view.animate().alpha(alpha).setDuration(250).start();
        }
    }

    /**
     * Intercepts and handles remote key events while Adzan is visible.
     * Prevents unintentional dismissal by arrow keys or volume changes,
     * requiring explicit user action (Back button or Dismiss button) to exit.
     */
    public boolean onKeyEvent(KeyEvent event) {
        if (!isShowing()) return false;

        int action = event.getAction();
        int keyCode = event.getKeyCode();

        // Let system volume keys pass through freely to change TV volume
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            // BACK key explicitly dismisses adzan
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                dismiss();
                return true;
            }

            // If HUD is hidden, any D-pad key reveals it and resets the timer
            if (controlsHidden) {
                showControls();
                scheduleAutoHideControls();
                return true;
            }

            // D-pad navigation between Dismiss and Keep Screen buttons
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (btnDismiss != null) btnDismiss.requestFocus();
                scheduleAutoHideControls();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (btnKeep != null) btnKeep.requestFocus();
                scheduleAutoHideControls();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                scheduleAutoHideControls();
                return true;
            }

            // Center/OK/Enter activates focused action
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (btnKeep != null && btnKeep.isFocused()) {
                    hideControls();
                } else {
                    dismiss();
                }
                return true;
            }

            // Consume any other key so launcher underneath is not disturbed
            return true;
        } else if (action == KeyEvent.ACTION_UP) {
            return true;
        }

        return true;
    }
}
