package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline photo playback. Construct and call start/stop on the main thread. */
final class MosqueGallery {
    private static final String TAG = "TvHome.MosqueGallery";
    private static final long INTERVAL_MS = 60_000L;
    private static final long CROSSFADE_MS = 1_600L;
    private static final long CAPTION_FADE_MS = 250L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Resources resources;
    private final List<MosquePhoto> photos;
    private final Random random;
    private final View ambient;
    private final View scrim;
    private final View caption;
    private final TextView name;
    private final TextView location;
    private final TextView credit;
    private ImageView current;
    private ImageView next;
    private ExecutorService decoder;
    private List<MosquePhoto> deck = Collections.emptyList();
    private int deckIndex;
    private MosquePhoto shownPhoto;
    private int ambientVisibility;

    // BitmapFactory may ignore interruption. The gate serializes even workers from a stopped session.
    // Only workers acquire it; stop() never waits for a blocking decode on the main thread.
    private final Object decodeGate = new Object();
    private final Object resultLock = new Object();
    private Decoded pendingResult;
    private volatile boolean active;
    private volatile int generation;

    MosqueGallery(View root, List<MosquePhoto> photos, Random random) {
        resources = root.getResources();
        this.photos = new ArrayList<>(photos);
        this.random = random;
        current = root.findViewById(R.id.dreamMosqueCurrent);
        next = root.findViewById(R.id.dreamMosqueNext);
        ambient = root.findViewById(R.id.dreamAmbient);
        scrim = root.findViewById(R.id.dreamMosqueScrim);
        caption = root.findViewById(R.id.dreamMosqueCaption);
        name = root.findViewById(R.id.dreamMosqueName);
        location = root.findViewById(R.id.dreamMosqueLocation);
        credit = root.findViewById(R.id.dreamMosqueCredit);
    }

    void start() {
        if (active) return;
        active = true;
        generation++;
        ambientVisibility = ambient.getVisibility();
        deck = Collections.emptyList();
        deckIndex = 0;
        shownPhoto = null;
        if (decoder == null || decoder.isShutdown()) {
            decoder = Executors.newSingleThreadExecutor();
        }
        showNext(generation, photos.size());
    }

    void stop() {
        if (!active) return;
        active = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
        current.animate().withEndAction(null).cancel();
        next.animate().withEndAction(null).cancel();
        caption.animate().withEndAction(null).cancel();
        if (decoder != null) decoder.shutdownNow();
        synchronized (resultLock) {
            discardPendingResult();
        }
        clearImages();
        ambient.setVisibility(ambientVisibility);
        deck = Collections.emptyList();
        shownPhoto = null;
    }

    private boolean isCurrent(int requestGeneration) {
        return active && generation == requestGeneration;
    }

    private MosquePhoto nextPhoto() {
        if (deckIndex >= deck.size()) {
            deck = MosqueRotation.shuffled(photos, shownPhoto, random);
            deckIndex = 0;
        }
        return deck.get(deckIndex++);
    }

    private void showNext(int requestGeneration, int attemptsLeft) {
        if (!isCurrent(requestGeneration) || photos.isEmpty()) return;
        MosquePhoto photo = nextPhoto();
        decoder.execute(() -> {
            synchronized (decodeGate) {
                if (!isCurrent(requestGeneration)) return;
                Bitmap bitmap = decode(photo);
                synchronized (resultLock) {
                    if (!isCurrent(requestGeneration)) {
                        if (bitmap != null) bitmap.recycle(); // Never attached to a view.
                        return;
                    }
                    pendingResult = new Decoded(photo, bitmap);
                    // The callback holds no bitmap: stop() can reclaim a result even if it removes this post.
                    handler.post(() -> acceptDecoded(requestGeneration, attemptsLeft));
                }
            }
        });
    }

    private Bitmap decode(MosquePhoto photo) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(resources, photo.drawableRes, options);
            if (bitmap == null) Log.w(TAG, "Could not decode " + photo.mosque);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError failure) {
            Log.w(TAG, "Could not decode " + photo.mosque, failure);
            return null;
        }
    }

    private void acceptDecoded(int requestGeneration, int attemptsLeft) {
        Decoded decoded;
        synchronized (resultLock) {
            if (!isCurrent(requestGeneration)) return;
            decoded = pendingResult;
            pendingResult = null;
        }
        if (decoded == null) return;
        if (decoded.bitmap == null) {
            if (attemptsLeft > 1) {
                showNext(requestGeneration, attemptsLeft - 1);
            } else {
                // A whole failed selection leaves the glow (or last good photo) intact and retries later.
                scheduleNext(requestGeneration);
            }
            return;
        }
        if (shownPhoto == null) {
            current.setImageBitmap(decoded.bitmap);
            current.setAlpha(1f);
            current.setVisibility(View.VISIBLE);
            scrim.setVisibility(View.VISIBLE);
            ambient.setVisibility(View.GONE);
            setCaption(decoded.photo);
            caption.setAlpha(1f);
            caption.setVisibility(View.VISIBLE);
            shownPhoto = decoded.photo;
            scheduleNext(requestGeneration);
            return;
        }

        // Until the caption is invisible it still describes the outgoing image.
        ViewGroup imageParent = (ViewGroup) current.getParent();
        if (imageParent.indexOfChild(next) < imageParent.indexOfChild(current)) {
            // Reorder only the two bottom image layers; keep scrim, clock, cards and caption above them.
            imageParent.removeView(next);
            imageParent.addView(next, imageParent.indexOfChild(current) + 1);
        }
        MosquePhoto incomingPhoto = decoded.photo;
        next.setImageBitmap(decoded.bitmap);
        next.setAlpha(0f);
        next.setVisibility(View.VISIBLE);
        caption.animate().alpha(0f).setDuration(CAPTION_FADE_MS).withEndAction(() -> {
            if (!isCurrent(requestGeneration)) return;
            setCaption(incomingPhoto);
            caption.animate().alpha(1f).setDuration(CROSSFADE_MS).start();
            next.animate().alpha(1f).setDuration(CROSSFADE_MS).withEndAction(() -> {
                if (!isCurrent(requestGeneration)) return;
                ImageView outgoing = current;
                current = next;
                next = outgoing;
                next.setImageDrawable(null);
                next.setVisibility(View.GONE);
                next.setAlpha(0f);
                shownPhoto = incomingPhoto;
                scheduleNext(requestGeneration);
            }).start();
        }).start();
    }

    private void scheduleNext(int requestGeneration) {
        if (!isCurrent(requestGeneration)) return;
        handler.postDelayed(() -> showNext(requestGeneration, photos.size()), INTERVAL_MS);
    }

    private void setCaption(MosquePhoto photo) {
        name.setText(photo.mosque);
        location.setText(photo.location);
        credit.setText("Photo: " + photo.creator + " · " + photo.license + " · Wikimedia Commons");
    }

    private void clearImages() {
        // Do not recycle displayed bitmaps: the renderer may still be finishing its last frame.
        current.setImageDrawable(null);
        next.setImageDrawable(null);
        current.setVisibility(View.GONE);
        next.setVisibility(View.GONE);
        current.setAlpha(1f);
        next.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        caption.setVisibility(View.GONE);
        caption.setAlpha(1f);
        name.setText("");
        location.setText("");
        credit.setText("");
    }

    private void discardPendingResult() {
        if (pendingResult != null && pendingResult.bitmap != null) pendingResult.bitmap.recycle();
        pendingResult = null;
    }

    private static final class Decoded {
        final MosquePhoto photo;
        final Bitmap bitmap;

        Decoded(MosquePhoto photo, Bitmap bitmap) {
            this.photo = photo;
            this.bitmap = bitmap;
        }
    }
}
