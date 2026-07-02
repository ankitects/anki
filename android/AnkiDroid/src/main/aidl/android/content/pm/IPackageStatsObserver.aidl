// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2007, The Android Open Source Project

// This file was adapted from
// https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/content/pm/IPackageStatsObserver.aidl

package android.content.pm;

import android.content.pm.PackageStats;
/**
 * API for package data change related callbacks from the Package Manager.
 * Some usage scenarios include deletion of cache directory, generate
 * statistics related to code, data, cache usage(TODO)
 * {@hide}
 *
 * This generates a deprecation warning during builds, and it would be great to remove it.
 * There is only one usage: FileUtils::getUserDataAndCacheSizeUsingGetPackageSizeInfo
 * The code that references this will no longer be needed after Build.VERSION_CODES >= 0
 * or minSdk >= 26 - at that API level we should be able to remove all related code / deprecation
 */
oneway interface IPackageStatsObserver {
    void onGetStatsCompleted(in PackageStats pStats, boolean succeeded);
}