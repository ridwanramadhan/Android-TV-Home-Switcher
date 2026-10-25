package com.gathin.tvhome;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/**
 * The fullscreen Adzan overlay's views: the video surface it hands to AdzanPlayer, the prayer title, the
 * two glass buttons, and the behaviour of the HUD (fade in and out, focus, the remote's keys).
 *
 * <p>Split out of AdzanPlayer so that class changes for one reason (playback) and this one for another
 * (what the overlay looks like and which key does what). The overlay exists both in Home's layout and in
 * AdzanActivity's — see layout/view_adzan_overlay.xml — so it binds by id from whichever activity hosts it.
 */
final class AdzanOverlay {

    /** How long the buttons and hint stay up before fading, so the video is not permanently covered. */
    private static final long AUTO_HIDE_DELAY_MS = 6000L;
    private static final long FADE_MS = 250L;

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());

    private View overlayView;
    private FullScreenTextureView textureView;
    private View gradientTop;
    private View gradientBottom;
    private View header;
    private TextView title;
    private View controls;
    private TextView btnDismiss;
    private TextView btnKeep;
    private TextView remoteHint;

    private boolean controlsHidden;
    private Runnable onDismiss;

    private final Runnable autoHide = this::hideControls;

    AdzanOverlay(Activity activity) {
        this.activity = activity;
    }

    /**
     * Looks the views up in the hosting activity. False when the host layout carries no overlay, which
     * Home's layout may not (an old theme, or a build without the adzan panel).
     */
    boolean bind() {
        if (overlayView != null) return textureView != null;
        overlayView = activity.findViewById(R.id.adzanOverlay);
        if (overlayView == null) return false;

        textureView = overlayView.findViewById(R.id.adzanTextureView);
        gradientTop = overlayView.findViewById(R.id.adzanGradientTop);
        gradientBottom = overlayView.findViewById(R.id.adzanGradientBottom);
        header = overlayView.findViewById(R.id.adzanHeader);
        title = overlayView.findViewById(R.id.adzanTitle);
        controls = overlayView.findViewById(R.id.adzanControls);
        btnDismiss = overlayView.findViewById(R.id.btnDismissAdzan);
        btnKeep = overlayView.findViewById(R.id.btnKeepAdzan);
        remoteHint = overlayView.findViewById(R.id.adzanRemoteHint);

        btnDismiss.setOnClickListener(v -> dismiss());
        // "Keep the screen": the prayer stays, only the buttons go away.
        btnKeep.setOnClickListener(v -> hideControls());
        overlayView.setOnClickListener(v -> {
            if (controlsHidden) {
                showControls();
                scheduleAutoHide();
            }
        });
        return textureView != null;
    }

    /** Fired for the Back key, the Dismiss button, and a finished or failed video. */
    void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    FullScreenTextureView textureView() {
        return textureView;
    }

    boolean isVisible() {
        return overlayView != null && overlayView.getVisibility() == View.VISIBLE;
    }

    void setTitle(CharSequence text) {
        if (title != null) title.setText(text);
    }

    /** Brings the overlay up, cancelling any fade-out still running from the previous call. */
    void show() {
        if (overlayView == null) return;
        overlayView.animate().cancel();
        overlayView.setAlpha(1f);
        overlayView.setVisibility(View.VISIBLE);
        overlayView.bringToFront();
        showControls();
        if (btnDismiss != null) btnDismiss.requestFocus();
    }

    /** Fades out and hides; the caller releases playback. Safe to call when already hidden. */
    void hide() {
        main.removeCallbacks(autoHide);
        final View view = overlayView;
        if (view == null) return;
        view.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .withEndAction(() -> {
                    // A show() cancels this animation, so a faded-to-zero view here is genuinely dismissed.
                    if (view.getAlpha() == 0f) view.setVisibility(View.GONE);
                })
                .start();
    }

    void showControls() {
        controlsHidden = false;
        main.removeCallbacks(autoHide);
        fade(controls, 1f);
        fade(header, 1f);
        fade(gradientTop, 1f);
        fade(gradientBottom, 1f);
        fade(remoteHint, 1f);
        if (btnDismiss != null && !btnDismiss.isFocused() && btnKeep != null && !btnKeep.isFocused()) {
            btnDismiss.requestFocus();
        }
    }

    void hideControls() {
        controlsHidden = true;
        main.removeCallbacks(autoHide);
        fade(controls, 0f);
        fade(header, 0f);
        fade(gradientTop, 0f);
        fade(gradientBottom, 0f);
        fade(remoteHint, 0f);
    }

    void scheduleAutoHide() {
        main.removeCallbacks(autoHide);
        main.postDelayed(autoHide, AUTO_HIDE_DELAY_MS);
    }

    void cancelAutoHide() {
        main.removeCallbacks(autoHide);
    }

    /**
     * Handles the remote while the overlay is up: Back dismisses, a hidden HUD comes back on any key,
     * arrows move between the two buttons, and everything else is swallowed so Home underneath never sees it.
     * Volume keys are left alone so the TV's own volume still works.
     */
    boolean handleKey(KeyEvent event) {
        if (!isVisible()) return false;

        int action = event.getAction();
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }
        if (action == KeyEvent.ACTION_UP) return true;
        if (action != KeyEvent.ACTION_DOWN) return true;

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            dismiss();
            return true;
        }
        if (controlsHidden) {
            showControls();
            scheduleAutoHide();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (btnDismiss != null) btnDismiss.requestFocus();
            scheduleAutoHide();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (btnKeep != null) btnKeep.requestFocus();
            scheduleAutoHide();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            scheduleAutoHide();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (btnKeep != null && btnKeep.isFocused()) hideControls();
            else dismiss();
            return true;
        }
        return true;
    }

    private void dismiss() {
        if (onDismiss != null) onDismiss.run();
    }

    private void fade(View view, float alpha) {
        if (view != null) view.animate().alpha(alpha).setDuration(FADE_MS).start();
    }
}
