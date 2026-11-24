// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileName.ValidationResult
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.BlockReason
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.SafetyCheck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SwitchProfilesViewModelTest {
    private lateinit var context: Context
    private lateinit var profileManager: ProfileManager

    private val prefs
        get() = context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    private var restarts = 0

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        ioDispatcher = dispatcher
        context = ApplicationProvider.getApplicationContext()
        prefs.edit(commit = true) { clear() }
        profileManager = ProfileManager.create(context)
    }

    @After
    fun tearDown() {
        prefs.edit(commit = true) { clear() }
        Dispatchers.resetMain()
        ioDispatcher = Dispatchers.IO
    }

    private fun viewModel(vararg checks: SafetyCheck) =
        SwitchProfilesViewModel(
            profileManager = profileManager,
            switchGuard = ProfileSwitchGuard(profileManager, checks.toList()),
            restartApp = { restarts++ },
        )

    private fun viewModelWithRestart(
        vararg checks: SafetyCheck,
        restartApp: suspend () -> Unit,
    ) = SwitchProfilesViewModel(profileManager, ProfileSwitchGuard(profileManager, checks.toList()), restartApp)

    private fun viewModelThatCannotSave(): SwitchProfilesViewModel {
        val registry = prefs
        val cannotSave =
            object : SharedPreferences by registry {
                override fun edit(): SharedPreferences.Editor =
                    object : SharedPreferences.Editor by registry.edit() {
                        override fun commit() = false
                    }
            }
        val manager =
            ProfileManager.create(
                object : ContextWrapper(context) {
                    override fun getApplicationContext(): Context? = null

                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences = if (name == PROFILE_REGISTRY_FILENAME) cannotSave else super.getSharedPreferences(name, mode)
                },
            )
        return SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) { restarts++ }
    }

    private val lastActiveProfileId: String?
        get() = prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null)

    private val validName: ProfileName
        get() = (ProfileName.validate("Work") as ValidationResult.Valid).name

    private fun createProfile(name: String) = profileManager.createNewProfile(nameOf(name))

    private fun waitingFor(saving: CompletableDeferred<Unit>) =
        SafetyCheck {
            saving.await()
            null
        }

    private fun nameOf(raw: String) = (ProfileName.validate(raw) as ValidationResult.Valid).name

    @Test
    fun `add-profile dialog is hidden initially`() {
        assertFalse(viewModel().isAddProfileDialogVisible.value)
    }

    @Test
    fun `showAddProfileDialog makes the dialog visible`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        assertTrue(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `dismissAddProfileDialog hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        viewModel.dismissAddProfileDialog()
        assertFalse(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `addProfile hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showAddProfileDialog()
        viewModel.addProfile(validName)
        assertFalse(viewModel.isAddProfileDialogVisible.value)
    }

    @Test
    fun `addProfile lists the new profile`() {
        val viewModel = viewModel()

        viewModel.addProfile(nameOf("Work"))

        assertEquals(listOf("Default", "Work"), viewModel.profiles.value.map { it.name })
    }

    @Test
    fun `showRenameProfileDialog opens the dialog for that profile`() {
        val viewModel = viewModel()
        val default = viewModel.profiles.value.single()

        viewModel.showRenameProfileDialog(default)

        assertEquals(default, viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `dismissRenameProfileDialog hides the dialog`() {
        val viewModel = viewModel()
        viewModel.showRenameProfileDialog(viewModel.profiles.value.single())

        viewModel.dismissRenameProfileDialog()

        assertNull(viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `renameProfile lists the new name and closes the dialog`() {
        createProfile("Work")
        val viewModel = viewModel()
        viewModel.showRenameProfileDialog(viewModel.profiles.value.single { it.name == "Work" })

        viewModel.renameProfile(nameOf("Office"))

        assertEquals(listOf("Default", "Office"), viewModel.profiles.value.map { it.name })
        assertNull(viewModel.profileBeingRenamed.value)
    }

    @Test
    fun `tapping another profile asks before switching to it`() {
        createProfile("Work")
        val viewModel = viewModel()
        val work = viewModel.profiles.value.single { it.name == "Work" }

        viewModel.requestSwitch(work)

        assertEquals(work, viewModel.profileToSwitchTo.value)
    }

    @Test
    fun `tapping the active profile does not ask to switch`() {
        val viewModel = viewModel()

        viewModel.requestSwitch(viewModel.profiles.value.single())

        assertNull(viewModel.profileToSwitchTo.value)
    }

    @Test
    fun `dismissing the switch dialog keeps the current profile`() {
        createProfile("Work")
        val viewModel = viewModel()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })

        viewModel.dismissSwitchProfileDialog()

        assertNull(viewModel.profileToSwitchTo.value)
        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId)
        assertEquals(0, restarts)
    }

    @Test
    fun `confirming a switch saves the profile and restarts the app`() {
        val work = createProfile("Work")
        val viewModel = viewModel()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertNull(viewModel.profileToSwitchTo.value)
        assertEquals(work.value, lastActiveProfileId)
        assertEquals(1, restarts)
    }

    @Test
    fun `a blocked switch keeps the current profile and says why`() {
        val work = createProfile("Work")
        val viewModel = viewModel(SafetyCheck { BlockReason.SYNC_IN_PROGRESS })
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertTrue(viewModel.isSwitchBlocked.value)
        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId)
        assertEquals(0, restarts)
    }

    @Test
    fun `another profile cannot be picked while a switch is saving`() {
        val work = createProfile("Work")
        createProfile("Personal")
        val saving = CompletableDeferred<Unit>()
        val viewModel = viewModel(waitingFor(saving))
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })
        viewModel.switchProfile()

        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Personal" })
        saving.complete(Unit)

        assertNull(viewModel.profileToSwitchTo.value, "the app is about to restart into Work")
        assertEquals(work.value, lastActiveProfileId)
        assertEquals(1, restarts)
    }

    @Test
    fun `confirming twice switches once`() {
        val work = createProfile("Work")
        var checks = 0
        val viewModel =
            viewModel(
                SafetyCheck {
                    checks++
                    null
                },
            )
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()
        viewModel.switchProfile()

        assertEquals(1, checks)
        assertEquals(1, restarts)
    }

    @Test
    fun `another profile can be picked after a blocked switch`() {
        createProfile("Work")
        val personal = createProfile("Personal")
        val viewModel = viewModel(SafetyCheck { BlockReason.SYNC_IN_PROGRESS })
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })
        viewModel.switchProfile()

        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == personal })

        assertEquals(personal, viewModel.profileToSwitchTo.value?.id)
    }

    @Test
    fun `leaving the screen while a switch is saving still restarts into it`() {
        val work = createProfile("Work")
        val saving = CompletableDeferred<Unit>()
        val store = ViewModelStore()
        val viewModel = viewModel(waitingFor(saving)).also { store.put("profiles", it) }
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })
        viewModel.switchProfile()

        store.clear()
        saving.complete(Unit)

        assertEquals(work.value, lastActiveProfileId)
        assertEquals(1, restarts)
    }

    @Test
    fun `a switch that cannot be saved says so and does not restart`() {
        val viewModel = viewModelThatCannotSave()
        val work = createProfile("Work")
        viewModel.refresh()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertEquals(work, viewModel.profileSwitchFailed.value?.id)
        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId)
        assertEquals(0, restarts)
    }

    @Test
    fun `another profile can be picked after a failed switch`() {
        val viewModel = viewModelThatCannotSave()
        createProfile("Work")
        val personal = createProfile("Personal")
        viewModel.refresh()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })
        viewModel.switchProfile()

        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == personal })

        assertEquals(personal, viewModel.profileToSwitchTo.value?.id)
    }

    @Test
    fun `dismissing the failure message hides it`() {
        val viewModel = viewModelThatCannotSave()
        createProfile("Work")
        viewModel.refresh()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })
        viewModel.switchProfile()

        viewModel.dismissSwitchFailedMessage()

        assertNull(viewModel.profileSwitchFailed.value)
    }

    @Test
    fun `a restart that fails switches back and says so`() {
        val work = createProfile("Work")
        val viewModel = viewModelWithRestart { throw IllegalStateException("cannot start activities") }
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertEquals(work, viewModel.profileSwitchFailed.value?.id)
        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId, "the next start must not open Work unasked")
    }

    @Test
    fun `a restart that fails switches back even if a sync has started`() {
        val work = createProfile("Work")
        var restartFailed = false
        val viewModel =
            viewModelWithRestart(SafetyCheck { BlockReason.SYNC_IN_PROGRESS.takeIf { restartFailed } }) {
                restartFailed = true
                throw IllegalStateException("cannot start activities")
            }
        viewModel.requestSwitch(viewModel.profiles.value.single { it.id == work })

        viewModel.switchProfile()

        assertEquals(ProfileId.DEFAULT.value, lastActiveProfileId)
    }

    @Test
    fun `dismissing the blocked message hides it`() {
        val viewModel = viewModel(SafetyCheck { BlockReason.BACKUP_IN_PROGRESS })
        createProfile("Work")
        viewModel.refresh()
        viewModel.requestSwitch(viewModel.profiles.value.single { it.name == "Work" })
        viewModel.switchProfile()

        viewModel.dismissSwitchBlockedMessage()

        assertFalse(viewModel.isSwitchBlocked.value)
    }

    @Test
    fun `the list starts with the Default profile`() {
        assertEquals(listOf(ProfileId.DEFAULT), viewModel().profiles.value.map { it.id })
    }

    @Test
    fun `every registered profile is listed`() {
        val work = createProfile("Work")

        val listed = viewModel().profiles.value.map { it.id }

        assertEquals(setOf(ProfileId.DEFAULT, work), listed.toSet())
    }

    @Test
    fun `profiles are listed by name so the order is stable`() {
        createProfile("Zebra")
        createProfile("Alpha")

        val names = viewModel().profiles.value.map { it.name }

        assertEquals(listOf("Alpha", "Default", "Zebra"), names)
    }

    @Test
    fun `profiles are listed by name ignoring case`() {
        createProfile("bravo")
        createProfile("Alpha")

        val names = viewModel().profiles.value.map { it.name }

        assertEquals(listOf("Alpha", "bravo", "Default"), names)
    }

    @Test
    fun `refresh picks up a profile added after the view model was created`() {
        val viewModel = viewModel()
        createProfile("Later")

        assertEquals(1, viewModel.profiles.value.size, "not visible until refreshed")
        viewModel.refresh()

        assertEquals(listOf("Default", "Later"), viewModel.profiles.value.map { it.name })
    }
}
