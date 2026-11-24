// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.uiAutomator
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Generates a baseline profile that captures the critical user journey for AnkiDroid's
 * cold start: launching the app and reaching the main [com.ichi2.anki.DeckPicker] screen.
 *
 * The captured launch path is:
 * 1. [com.ichi2.anki.IntentHandler] — the MAIN/LAUNCHER activity declared in
 *    the manifest. `startActivityAndWait()` launches this.
 * 2. [com.ichi2.anki.DeckPicker] — IntentHandler.onCreate() immediately
 *    creates an intent for DeckPicker and calls `launchDeckPickerIfNoOtherTasks()`.
 * 3. The `device.wait` + `device.waitForIdle()` calls wait for DeckPicker to
 *    fully render before the trace window closes.
 *
 * On a fresh install, [com.ichi2.anki.IntroductionActivity] appears before
 * DeckPicker. The journey handles this by tapping the "Get Started" button
 * (resource id `get_started`) if it is present, so the profile captures the
 * full path to DeckPicker regardless of device state.
 *
 * That first launch only goes into the baseline profile. [launchWithDecks] runs
 * after it, adds two decks and records launches that wait for a deck row, which
 * feed both profiles.
 *
 * Run on a connected API 28+ physical device with:
 * ```
 * ./gradlew :AnkiDroid:generateBaselineProfile
 * ```
 * The generated profile is committed to
 * `AnkiDroid/src/main/baselineProfiles/baseline-prof.txt` by the
 * [androidx.baselineprofile] plugin and is automatically embedded in subsequent
 * app builds.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val targetPackage =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: throw IllegalStateException("targetAppId not passed as instrumentation runner arg")

    @Test
    fun firstLaunch() {
        rule.collect(packageName = targetPackage) {
            pressHome()
            // Launches IntentHandler (MAIN/LAUNCHER), which routes to DeckPicker.
            startActivityAndWait()

            // On a fresh install, IntroductionActivity appears before DeckPicker.
            // Dismiss it by tapping "Get Started" so the profile always captures
            // the full path through to DeckPicker, regardless of device state.
            device.findObject(By.res(targetPackage, "get_started"))?.click()

            // Wait for DeckPicker to fully render so all startup classes are captured.
            device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), 5_000)
            device.waitForIdle()
        }
    }

    @Test
    fun launchWithDecks() {
        createDecksIfEmpty()

        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            assertTrue(
                "Deck list did not render within 5 seconds; refusing to capture an incomplete startup profile",
                device.wait(Until.hasObject(By.res(targetPackage, "deck_name")), 5_000),
            )
            device.waitForIdle()
        }
    }

    private fun createDecksIfEmpty() =
        uiAutomator {
            fun id(name: String) = "$targetPackage:id/$name"

            startApp(targetPackage)
            val firstScreen = onElement { viewIdResourceName in setOf(id("get_started"), id("fab_main")) }
            if (firstScreen.resourceName == id("get_started")) firstScreen.click()

            val deckList = onElement { viewIdResourceName in setOf(id("deck_name"), id("no_decks_placeholder")) }
            if (deckList.resourceName == id("deck_name")) return@uiAutomator

            for (name in listOf("Japanese::Vocabulary", "Spanish")) {
                onElement { viewIdResourceName == id("fab_main") }.click()
                onElement { viewIdResourceName == id("add_deck_button") }.click()
                onElement { viewIdResourceName == id("dialog_text_input") }.text = name
                onElement { viewIdResourceName == "android:id/button1" && isEnabled }.click()
            }
            onElement { viewIdResourceName == id("deck_name") }
        }
}
