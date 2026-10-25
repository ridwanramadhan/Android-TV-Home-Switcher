package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Dedicated fullscreen Adzan activity that displays the 1080p call to prayer video and audio.
 * Can be triggered from the screensaver or in the background while watching movies
 * on Netflix, Disney+, YouTube, etc.
 * Dismissing or completion returns the TV directly back to the previous screen.
 */
public class AdzanActivity extends Activity {
    private static final String TAG = "AdzanActivity";
    public static final String EXTRA_PRAYER = "prayer_name";

    private AdzanPlayer adzanPlayer;

    public static void start(Context context, String prayerName) {
        Intent intent = new Intent(context, AdzanActivity.class);
        intent.putExtra(EXTRA_PRAYER, prayerName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and keep awake for the prayer call
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setContentView(R.layout.activity_adzan);

        String prayerName = getIntent() != null ? getIntent().getStringExtra(EXTRA_PRAYER) : null;
        if (prayerName == null || prayerName.trim().isEmpty()) {
            prayerName = "Ashar";
        }

        adzanPlayer = new AdzanPlayer(this);
        adzanPlayer.setOnStateChangeListener(() -> {
            if (!adzanPlayer.isShowing()) {
                finish();
            }
        });

        adzanPlayer.play(prayerName);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (adzanPlayer != null && adzanPlayer.onKeyEvent(event)) {
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            if (adzanPlayer != null) adzanPlayer.dismiss();
            else finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adzanPlayer != null) {
            adzanPlayer.stop();
        }
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adzanPlayer != null) {
            adzanPlayer.stop();
            adzanPlayer = null;
        }
    }
}
