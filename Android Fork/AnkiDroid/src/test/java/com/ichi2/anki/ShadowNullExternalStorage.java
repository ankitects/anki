// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki;

import android.os.Environment;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;

/**
 * Makes {@code Context.getExternalFilesDir} return null without replacing {@code ShadowContextImpl}.
 *
 * <p>{@code ShadowContextImpl.getExternalFilesDir} returns null when
 * {@link Environment#getExternalStoragePublicDirectory} does.
 */
@Implements(Environment.class)
public class ShadowNullExternalStorage extends ShadowEnvironment {
    @Implementation
    public static File getExternalStoragePublicDirectory(String type) {
        return null;
    }
}
