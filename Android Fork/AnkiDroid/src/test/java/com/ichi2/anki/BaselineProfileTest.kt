// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.BaselineProfileEntry.Flag
import com.ichi2.anki.BaselineProfileEntry.MethodRule
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ensures a manual run of the baseline profile won't drop rules.
 */
class BaselineProfileTest {
    /** A populated deck list needs both row inflation and binding on a cold launch. */
    @Test
    fun `baseline profile covers creating and binding deck rows`() {
        val rules = readProfile("baseline-prof.txt")
        val recordedMethods =
            rules
                .filterIsInstance<MethodRule>()
                .filter { it.className == "com.ichi2.anki.widgets.DeckAdapter" }
                .map { it.methodSignature }
                .toSet()
        val requiredMethods =
            setOf(
                "onCreateViewHolder(Landroid/view/ViewGroup;I)Lcom/ichi2/anki/widgets/DeckAdapter\$ViewHolder;",
                "onBindViewHolder(Lcom/ichi2/anki/widgets/DeckAdapter\$ViewHolder;I)V",
            )
        val missing = requiredMethods - recordedMethods

        assertTrue(
            missing.isEmpty(),
            "baseline-prof.txt does not cover rendering a populated deck list. Missing DeckAdapter methods: " +
                "${missing.joinToString()}. Seed a collection before capture, wait for a deck row, " +
                "then regenerate the profiles (baselineprofile/README.md).",
        )
    }

    /** R8 needs the row classes as well as the activity to optimize a populated deck list's startup. */
    @Test
    fun `startup profile includes deck row rendering classes`() {
        // A method rule also identifies its declaring class for startup layout optimization.
        val recordedClasses = readProfile("startup-prof.txt").map { it.className }.toSet()
        val requiredClasses =
            setOf(
                "com.ichi2.anki.widgets.DeckAdapter\$ViewHolder",
                "com.ichi2.anki.databinding.ItemDeckBinding",
            )
        val missing = requiredClasses - recordedClasses

        assertTrue(
            missing.isEmpty(),
            "startup-prof.txt does not cover rendering a populated deck list. Missing classes: " +
                "${missing.joinToString()}. Record a cold launch with a populated collection " +
                "and wait for a deck row before ending capture, then regenerate the profiles.",
        )
    }

    /**
     * [DeckPicker.onResume] runs on every launch before the deck list is
     * usable, so it must stay ahead-of-time compiled.
     *
     * See https://github.com/ankidroid/Anki-Android/issues/20734
     */
    @Test
    fun `profile contains a startup rule for DeckPicker onResume`() {
        val profile = File("src/main/generated/baselineProfiles/baseline-prof.txt")
        assertTrue(profile.exists(), "baseline profile not found at ${profile.absolutePath}")

        val rules = BaselineProfileEntry.parseProfile(profile.readLines())
        val rule =
            rules.filterIsInstance<MethodRule>().firstOrNull {
                it.className == "com.ichi2.anki.DeckPicker" && it.methodSignature == "onResume()V"
            }

        assertNotNull(
            rule,
            "No rule for DeckPicker.onResume() in ${profile.name}: the method is " +
                "no longer ahead-of-time compiled. Ensure the BaselineProfileGenerator " +
                "journey reaches DeckPicker, then regenerate (baselineprofile/README.md).",
        )
        assertTrue(
            Flag.STARTUP in rule.flags,
            "'$rule' lost the S (startup) flag: DeckPicker.onResume() no longer runs " +
                "inside the profiled startup window. Check the BaselineProfileGenerator " +
                "journey, then regenerate (baselineprofile/README.md).",
        )
    }

    private fun readProfile(name: String): List<BaselineProfileEntry> {
        val profile = File("src/main/generated/baselineProfiles/$name")
        assertTrue(profile.isFile, "profile not found at ${profile.absolutePath}")
        return BaselineProfileEntry.parseProfile(profile.readLines())
    }
}
