// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A real IME: metadata and input travel through Android's active connection.
 * Uses only framework classes because the test APK's separate process cannot load
 * dependencies (including Kotlin) packaged only in the application under test.
 * This checks the input protocol; keyboard UI compatibility also needs a device
 * check (for example, long-press e for an accent, then submit with Done).
 */
public class TestInputMethod extends InputMethodService {
    private static TestInputMethod instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private Bundle command(String method, String text) {
        Bundle result = new Bundle();
        EditorInfo info = getCurrentInputEditorInfo();
        if (!getCurrentInputStarted() || info == null) return result;
        result.putParcelable("editor", info);
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return result;
        // EditorInfo can outlive its connection while Chromium restarts input.
        // Probe immediately before each action, as well as when awaiting metadata.
        // Returning before dispatch lets the caller safely retry without typing twice.
        if (connection.getTextBeforeCursor(0, 0) == null) return result;
        result.putBoolean("ready", true);
        boolean accepted = true;
        switch (method) {
            case "editor":
                break;
            case "commit":
                accepted = connection.commitText(text, 1);
                break;
            case "compose":
                accepted = connection.setComposingText(text, 1);
                break;
            case "finish":
                accepted = connection.finishComposingText();
                break;
            case "done":
                accepted = connection.performEditorAction(EditorInfo.IME_ACTION_DONE);
                break;
            case "chars":
                for (char c : text.toCharArray()) sendKeyChar(c);
                break;
            default:
                throw new IllegalArgumentException(method);
        }
        result.putBoolean("accepted", accepted);
        return result;
    }

    public static class Control extends ContentProvider {
        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            // Check on the Binder thread, before posting loses the caller's identity.
            if (getContext().getPackageManager().checkSignatures(Binder.getCallingUid(), Process.myUid())
                    != PackageManager.SIGNATURE_MATCH) {
                throw new SecurityException("Test IME control requires a matching signature");
            }
            return runOn(Looper.getMainLooper(), 5, TimeUnit.SECONDS, () ->
                    instance == null ? new Bundle() : instance.command(method, arg));
        }

        static Bundle runOn(Looper looper, long timeout, TimeUnit unit, Callable<Bundle> command) {
            FutureTask<Bundle> task = new FutureTask<>(command);
            new Handler(looper).post(task);
            try {
                return task.get(timeout, unit);
            } catch (TimeoutException e) {
                Thread thread = looper.getThread();
                StringBuilder message = new StringBuilder("Test IME did not respond. Its ")
                        .append(thread.getName()).append(" thread is at:");
                for (StackTraceElement frame : thread.getStackTrace()) {
                    message.append("\n\tat ").append(frame);
                }
                throw new IllegalStateException(message.toString(), e);
            } catch (Exception e) {
                throw new IllegalStateException("Test IME did not respond", e);
            }
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getType(Uri uri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int delete(Uri uri, String selection, String[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] args) {
            throw new UnsupportedOperationException();
        }
    }
}
