// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

/**
 * Test activity producing an ANR, launched in a new process.
 * Runs from the test APK (with no Kotlin runtime). This is Java to avoid adding dependencies.
 */
@RequiresApi(Build.VERSION_CODES.R)
public class AnrTestActivity extends Activity {
    private boolean blocking;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        TextView text = new TextView(this);
        text.setText("Waiting for the ANR trace regression test");
        setContentView(text);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !blocking) {
            blocking = true;
            getWindow().getDecorView().post(this::deliberatelyBlockMainThread);
        }
    }

    @SuppressWarnings("deprecation")
    private void deliberatelyBlockMainThread() {
        Bundle ready = new Bundle();
        ready.putInt("pid", Process.myPid());
        ready.putParcelable("stop", new ResultReceiver(null) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                // Runs on a Binder thread, so cleanup works while the main thread is blocked.
                Process.killProcess(Process.myPid());
            }
        });
        ResultReceiver receiver = getIntent().getParcelableExtra("ready");
        receiver.send(0, ready);
        // Bound the block and clean up even if the test runner disappears.
        SystemClock.sleep(120_000);
        Process.killProcess(Process.myPid());
    }
}
