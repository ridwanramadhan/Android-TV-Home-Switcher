package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Plays the full-screen call to prayer: the 1080p video and its audio, started by the alarm receiver, by the
 * screensaver, or by Home's minute tick.
 *
 * <p>This class owns playback and the once-a-day decision; the overlay's views and key handling belong to
 * {@link AdzanOverlay}, and which prayer is due belongs to {@link PrayerTimes}.
 */
public final class AdzanPlayer {
    private static final String TAG = "AdzanPlayer";

    private final Activity activity;
    private final AdzanOverlay overlay;
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaPlayer mediaPlayer;
    private Surface videoSurface;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusHeld;
    private boolean isShowing;
    /** The last prayer this player announced, as a date + name key, so a minute-long tick fires once. */
    private String lastPlayedKey = "";
    private String currentPrayer = "";
    private Runnable onStateChangeListener;

    public AdzanPlayer(Activity activity) {
        this.activity = activity;
        this.overlay = new AdzanOverlay(activity);
        this.overlay.setOnDismiss(this::dismiss);
    }

    /** Told whenever the overlay appears or goes away, so a host activity can finish itself. */
    public void setOnStateChangeListener(Runnable listener) {
        this.onStateChangeListener = listener;
    }

    /**
     * Plays the call when the current minute is a prayer's, at most once per prayer per day.
     * Safe to call every minute; the once-a-day key makes repeats no-ops.
     */
    public synchronized void checkPrayerTime(List<DashboardSnapshot.Prayer> prayers, ZonedDateTime now) {
        if (now == null) return;
        DashboardSnapshot.Prayer due = PrayerTimes.dueAt(prayers, now.toLocalTime());
        if (due == null) return;
        String key = PrayerTimes.dailyKey(now.toLocalDate(), due.name);
        if (key.equals(lastPlayedKey)) return;
        lastPlayedKey = key;
        play(due.name);
    }

    /** Starts the full-screen video and audio for {@code prayerName}; empty means "prayer time" in general. */
    public void play(String prayerName) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            playInternal(prayerName);
        } else {
            main.post(() -> playInternal(prayerName));
        }
    }

    private void playInternal(String prayerName) {
        try {
            currentPrayer = prayerName == null ? "" : prayerName.trim();
            if (!overlay.bind()) {
                Log.w(TAG, "Adzan overlay is not in this activity's layout");
                return;
            }

            releasePlayer();

            overlay.setTitle(titleFor(currentPrayer));
            isShowing = true;
            overlay.show();

            FullScreenTextureView view = overlay.textureView();
            if (view.isAvailable()) {
                startPlaybackOnSurface(view.getSurfaceTexture());
            } else {
                view.setSurfaceTextureListener(surfaceListener);
            }

            notifyStateChanged();
            Log.i(TAG, "Playing fullscreen adzan: " + currentPrayer);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start adzan playback", e);
            dismiss();
        }
    }

    /** The prayer's name in the title when there is one, the general line otherwise. */
    private CharSequence titleFor(String prayerName) {
        return prayerName.isEmpty()
                ? activity.getString(R.string.adzan_title_default)
                : activity.getString(R.string.adzan_prayer_time, prayerName);
    }

    /** The surface arrives after play() when the overlay was hidden; start then, or give up if dismissed. */
    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (isShowing) startPlaybackOnSurface(surface);
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
    };

    private void startPlaybackOnSurface(SurfaceTexture surfaceTexture) {
        try {
            releasePlayer();

            videoSurface = new Surface(surfaceTexture);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setSurface(videoSurface);

            AudioAttributes attrs = adhanAudioAttributes();
            mediaPlayer.setAudioAttributes(attrs);
            requestAudioFocus(attrs);

            int videoRes = PrayerTimes.videoResource(currentPrayer);
            AssetFileDescriptor afd = null;
            try {
                afd = activity.getResources().openRawResourceFd(videoRes);
            } catch (Exception ignored) {
                // Raw resources are not always accessible as a file descriptor (compressed assets); the
                // Uri path below opens them through AssetManager instead.
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
                    overlay.scheduleAutoHide();
                } else {
                    releasePlayer();
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> dismiss());

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
        return isShowing && overlay.isVisible();
    }

    public synchronized boolean isPlaying() {
        try {
            return isShowing() && mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Pauses the video without giving up the call: a system dialog or the keyguard taking the foreground
     * must not end the Adzan, so the host activity pauses here and resumes on the way back.
     */
    public void pause() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause();
        } catch (Exception e) {
            Log.w(TAG, "Could not pause adzan playback", e);
        }
    }

    /** Continues a paused call, if the overlay is still up. */
    public void resume() {
        try {
            if (isShowing && mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start();
        } catch (Exception e) {
            Log.w(TAG, "Could not resume adzan playback", e);
        }
    }

    /** Ends the call and takes the overlay away; also the way out when playback fails. */
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
        overlay.cancelAutoHide();
        releasePlayer();
        overlay.hide();

        notifyStateChanged();
        Log.i(TAG, "Adzan dismissed and returned to launcher");
    }

    private void notifyStateChanged() {
        if (onStateChangeListener != null) onStateChangeListener.run();
    }

    private static AudioAttributes adhanAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    /** Takes the transient focus the call needs, so the app underneath pauses instead of playing over it. */
    private void requestAudioFocus(AudioAttributes attributes) {
        if (audioFocusHeld) return;
        AudioManager manager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        AudioFocusRequest request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        dismiss();
                    } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pause();
                    }
                })
                .build();
        audioManager = manager;
        audioFocusRequest = request;
        audioFocusHeld = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!audioFocusHeld) Log.w(TAG, "Audio focus not granted; playing the adhan anyway");
    }

    private void abandonAudioFocus() {
        if (audioManager != null && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }
        audioFocusHeld = false;
        audioManager = null;
        audioFocusRequest = null;
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
                // Already released, or a codec gave up mid-teardown: nothing left to do either way.
            }
            mediaPlayer = null;
        }
        abandonAudioFocus();
        if (videoSurface != null) {
            try {
                videoSurface.release();
            } catch (Exception ignored) {
            }
            videoSurface = null;
        }
    }

    /** Remote keys while the call is up; see AdzanOverlay for the mapping. */
    public boolean onKeyEvent(KeyEvent event) {
        return overlay.handleKey(event);
    }
}
