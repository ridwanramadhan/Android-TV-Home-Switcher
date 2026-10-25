package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Fullscreen host for the call to prayer, used when the alarm fires while another app (Netflix, Disney+,
 * YouTube) or the screensaver is in front. Dismissing or finishing the video returns the TV to whatever was
 * showing: this activity sits on top of that app's task and leaves it as it found it.
 *
 * <p>It is not exported: only this app's own alarm receiver and Home start it, so an unrelated app cannot
 * pull a full-screen video over the TV.
 */
public class AdzanActivity extends Activity {
    public static final String EXTRA_PRAYER = "prayer_name";

    private AdzanPlayer adzanPlayer;

    public static void start(Context context, String prayerName) {
        Intent intent = new Intent(context, AdzanActivity.class);
        if (prayerName != null) intent.putExtra(EXTRA_PRAYER, prayerName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The TV may be asleep when the alarm fires: turn the panel on and keep it on for the call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            // Deprecated replacements above only exist from 27; on 26 these flags are the only way in.
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setContentView(R.layout.activity_adzan);

        // No name (an older alarm, or a schedule without one) plays the standard call under the general title.
        String prayerName = getIntent() != null ? getIntent().getStringExtra(EXTRA_PRAYER) : null;

        adzanPlayer = new AdzanPlayer(this);
        adzanPlayer.setOnStateChangeListener(() -> {
            if (!adzanPlayer.isShowing() && !isFinishing()) finish();
        });
        adzanPlayer.play(prayerName);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (adzanPlayer != null && adzanPlayer.onKeyEvent(event)) return true;
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
        // Losing the foreground is not the same as being dismissed: a system dialog or the keyguard must not
        // end the call. Pause here, resume in onResume, and let the overlay's own dismissal finish us.
        if (adzanPlayer != null) adzanPlayer.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adzanPlayer != null) adzanPlayer.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adzanPlayer != null) {
            adzanPlayer.dismiss();
            adzanPlayer = null;
        }
    }
}
