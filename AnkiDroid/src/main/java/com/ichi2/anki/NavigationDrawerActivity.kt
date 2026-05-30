// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.GravityCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.drawerlayout.widget.ClosableDrawerLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.ichi2.anki.IntentHandler.Companion.grantedStoragePermissions
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.common.android.animationEnabled
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.HandlerUtils
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.pages.StatisticsDestination
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.workarounds.FullDraggableContainerFix
import com.ichi2.utils.IntentUtil
import timber.log.Timber

abstract class NavigationDrawerActivity(
    @LayoutRes contentLayoutId: Int? = null,
) : AnkiActivity(contentLayoutId),
    NavigationView.OnNavigationItemSelectedListener {
    /**
     * Navigation Drawer
     */
    open var fragmented = false
        protected set
    private var navButtonGoesBack = false

    // Navigation drawer list item entries
    lateinit var drawerLayout: DrawerLayout
    private var navigationView: NavigationView? = null
    lateinit var drawerToggle: ActionBarDrawerToggle
        private set

    /**
     * runnable that will be executed after the drawer has been closed.
     */
    private var pendingRunnable: Runnable? = null

    private val drawerBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.d("drawerBackCallback")
                closeDrawer()
            }
        }

    override fun setViewBinding(binding: ViewBinding) {
        val preferences = baseContext.sharedPrefs()

        // Using ClosableDrawerLayout as a parent view.
        val closableDrawerLayout =
            LayoutInflater.from(this).inflate(
                navigationDrawerLayout,
                null,
                false,
            ) as ClosableDrawerLayout

        val coordinatorLayout = binding.root
        if (preferences.getBoolean(FULL_SCREEN_NAVIGATION_DRAWER, false)) {
            // If full screen navigation drawer is needed, then add FullDraggableContainer as a child view of closableDrawerLayout.
            // Then add coordinatorLayout as a child view of fullDraggableContainer.
            val fullDraggableContainer = FullDraggableContainerFix(this)
            fullDraggableContainer.addView(coordinatorLayout)
            closableDrawerLayout.addView(fullDraggableContainer, 0)
        } else {
            // If full screen navigation drawer is not needed, then directly add coordinatorLayout as the child view.
            closableDrawerLayout.addView(coordinatorLayout, 0)
        }
        setContentView(closableDrawerLayout)
    }

    override fun setContentView(
        @LayoutRes layoutResID: Int,
    ) {
        val preferences = baseContext.sharedPrefs()

        // Using ClosableDrawerLayout as a parent view.
        val closableDrawerLayout =
            LayoutInflater.from(this).inflate(
                navigationDrawerLayout,
                null,
                false,
            ) as ClosableDrawerLayout
        // Get CoordinatorLayout using resource ID
        val coordinatorLayout =
            LayoutInflater
                .from(this)
                .inflate(layoutResID, closableDrawerLayout, false) as CoordinatorLayout
        if (preferences.getBoolean(FULL_SCREEN_NAVIGATION_DRAWER, false)) {
            // If full screen navigation drawer is needed, then add FullDraggableContainer as a child view of closableDrawerLayout.
            // Then add coordinatorLayout as a child view of fullDraggableContainer.
            val fullDraggableContainer = FullDraggableContainerFix(this)
            fullDraggableContainer.addView(coordinatorLayout)
            closableDrawerLayout.addView(fullDraggableContainer, 0)
        } else {
            // If full screen navigation drawer is not needed, then directly add coordinatorLayout as the child view.
            closableDrawerLayout.addView(coordinatorLayout, 0)
        }
        setContentView(closableDrawerLayout)
    }

    @get:LayoutRes
    private val navigationDrawerLayout: Int
        get() = if (fitsSystemWindows()) R.layout.activity_navigation_drawer else R.layout.activity_navigation_drawer_fullscreen

    /** Whether android:fitsSystemWindows="true" should be applied to the navigation drawer  */
    protected open fun fitsSystemWindows(): Boolean = true

    fun navDrawerIsReady(): Boolean = navigationView != null

    // Navigation drawer initialisation
    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    protected fun initNavigationDrawer(mainView: View = findViewById(android.R.id.content)) {
        // Create inherited navigation drawer layout here so that it can be used by parent class
        drawerLayout = mainView.findViewById(R.id.drawer_layout)
        // set a custom shadow that overlays the main content when the drawer opens
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        // Force transparent status bar with primary dark color underlaid so that the drawer displays under status bar
        window.statusBarColor = getColor(R.color.transparent)
        drawerLayout.setStatusBarBackgroundColor(
            MaterialColors.getColor(
                this,
                R.attr.appBarColor,
                0,
            ),
        )
        // Setup toolbar and hamburger
        navigationView = drawerLayout.findViewById(R.id.navdrawer_items_container)
        navigationView!!.setNavigationItemSelectedListener(this)
        val toolbar: Toolbar? = mainView.findViewById(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            // enable ActionBar app icon to behave as action to toggle nav drawer
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setHomeButtonEnabled(true)

            // Decide which action to take when the navigation button is tapped.
            toolbar.setNavigationOnClickListener { onNavigationPressed() }
        }
        setupBackPressedCallbacks()
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        drawerToggle =
            object : ActionBarDrawerToggle(
                this,
                drawerLayout,
                R.string.drawer_open,
                R.string.drawer_close,
            ) {
                override fun onDrawerClosed(drawerView: View) {
                    super.onDrawerClosed(drawerView)
                    invalidateOptionsMenu()
                    drawerBackCallback.isEnabled = false
                    // If animations are disabled, this is executed before onNavigationItemSelected is called
                    // PERF: May be able to reduce this delay
                    HandlerUtils.postDelayedOnNewHandler({
                        if (pendingRunnable != null) {
                            HandlerUtils.postOnNewHandler(pendingRunnable!!) // TODO: See if we can use the same handler here
                            pendingRunnable = null
                        }
                    }, 100)
                }

                override fun onDrawerOpened(drawerView: View) {
                    super.onDrawerOpened(drawerView)
                    invalidateOptionsMenu()
                    drawerBackCallback.isEnabled = true
                }
            }
        if (drawerLayout is ClosableDrawerLayout) {
            (drawerLayout as ClosableDrawerLayout).setAnimationEnabled(animationEnabled())
        } else {
            Timber.w("Unexpected Drawer layout - could not modify navigation animation")
        }
        drawerToggle.isDrawerSlideAnimationEnabled = animationEnabled()
        drawerLayout.addDrawerListener(drawerToggle)

        enablePostShortcut(this)
        val intent = Intent("com.ichi2.widget.UPDATE_WIDGET").setClassName("com.ichi2.widget", "WidgetPermissionReceiver")
        this.sendBroadcast(intent)
    }

    /**
     * A method which allows control of the ordering of adding the [OnBackPressedCallback]
     * for the drawer.
     *
     * Typically, call super after initialization code, so the drawer takes priority
     */
    @CallSuper
    protected open fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, drawerBackCallback)
    }

    /**
     * Sets selected navigation drawer item
     */
    protected fun selectNavigationItem(itemId: Int) {
        val menu = navigationView!!.menu
        if (itemId == -1) {
            for (i in 0 until menu.size) {
                menu[i].isChecked = false
            }
        } else {
            val item = menu.findItem(itemId)
            if (item != null) {
                item.isChecked = true
            } else {
                Timber.e("Could not find item %d", itemId)
            }
        }
    }

    override fun setTitle(title: CharSequence) {
        if (supportActionBar != null) {
            supportActionBar!!.title = title
        }
        super.setTitle(title)
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState()
    }

    private val preferences: SharedPreferences
        get() = this@NavigationDrawerActivity.sharedPrefs()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pass any configuration change to the drawer toggles
        drawerToggle.onConfigurationChanged(newConfig)
    }

    /**
     * This function locks the navigation drawer closed in regards to swipes,
     * but continues to allowed it to be opened via it's indicator button. This
     * function in a noop if the drawer hasn't been initialized.
     */
    protected fun disableDrawerSwipe() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    /**
     * This function allows swipes to open the navigation drawer. This
     * function in a noop if the drawer hasn't been initialized.
     */
    protected fun enableDrawerSwipe() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    private val preferencesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val preferences = preferences
            Timber.i(
                "Handling Activity Result: %d. Result: %d",
                REQUEST_PREFERENCES_UPDATE,
                result.resultCode,
            )

            // We trigger a notifications channel set-up since the user may have changed the locale set
            // from within the app, which should cause the notification channel names to be reloaded to
            // match the new locale
            setupNotificationChannels(applicationContext)
            // Restart the activity on preference change
            // collection path hasn't been changed so just restart the current activity
            if (this is Reviewer && preferences.getBoolean("tts", false)) {
                // Workaround to kick user back to StudyOptions after opening settings from Reviewer
                // because onDestroy() of old Activity interferes with TTS in new Activity
                finish()
            } else {
                ActivityCompat.recreate(this)
            }
        }

    /**
     * Called, when navigation button of the action bar is pressed.
     * Design pattern: template method. Subclasses can override this to define their own behaviour.
     */
    open fun onNavigationPressed() {
        if (navButtonGoesBack) {
            finish()
        } else {
            openDrawer()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Don't do anything if user selects already selected position
        if (item.isChecked) {
            closeDrawer()
            return true
        }

        /*
         * This runnable will be executed in onDrawerClosed(...)
         * to make the animation more fluid on older devices.
         */
        pendingRunnable =
            Runnable {
                // Take action if a different item selected
                when (item.itemId) {
                    R.id.nav_decks -> {
                        Timber.i("Navigating to decks")
                        val deckPicker = Intent(this@NavigationDrawerActivity, DeckPicker::class.java)
                        // opening DeckPicker should use the instance on the back stack & clear back history
                        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(deckPicker)
                    }

                    R.id.nav_browser -> {
                        Timber.i("Navigating to card browser")
                        openCardBrowser()
                    }

                    R.id.nav_stats -> {
                        Timber.i("Navigating to stats")
                        openStatistics()
                    }

                    R.id.nav_settings -> {
                        Timber.i("Navigating to settings")
                        openSettings()
                    }

                    R.id.nav_help -> {
                        Timber.i("Navigating to help")
                        showDialogFragment(HelpDialog.newHelpInstance())
                    }

                    R.id.support_ankidroid -> {
                        Timber.i("Navigating to support AnkiDroid")
                        val canRateApp = IntentUtil.canOpenIntent(this, AnkiDroidApp.getMarketIntent(this))
                        showDialogFragment(HelpDialog.newSupportInstance(canRateApp))
                    }
                }
            }
        closeDrawer()
        return true
    }

    protected fun openCardBrowser() {
        val intent = Intent(this@NavigationDrawerActivity, CardBrowser::class.java)
        if (currentCardId != null) {
            intent.putExtra("currentCard", currentCardId)
        }
        startActivity(intent)
    }

    /**
     * Opens the Statistics Screen.
     */
    protected fun openStatistics() {
        Timber.i("launching statistics")
        val intent =
            StatisticsDestination().toIntent(this)
        startActivity(intent)
    }

    /**
     * Opens AnkiDroid's Settings Screen.
     */
    protected fun openSettings() {
        val intent = PreferencesActivity.getIntent(this)
        preferencesLauncher.launch(intent)
    }

    // Override this to specify a specific card id
    protected open val currentCardId: CardId?
        get() = null

    protected fun showBackIcon() {
        drawerToggle.isDrawerIndicatorEnabled = false
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }
        navButtonGoesBack = true
    }

    protected fun restoreDrawerIcon() {
        drawerToggle.isDrawerIndicatorEnabled = true
        navButtonGoesBack = false
    }

    @VisibleForTesting
    open val isDrawerOpen: Boolean
        get() = drawerLayout.isDrawerOpen(GravityCompat.START)

    /** Opens the drawer if closed, closes it if open */
    @Suppress("unused")
    fun toggleDrawer() {
        if (!isDrawerOpen) {
            openDrawer()
        } else {
            closeDrawer()
        }
    }

    private fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START, animationEnabled())
    }

    private fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START, animationEnabled())
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (!isDrawerOpen) {
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            closeDrawer()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        // Intent request codes
        const val REQUEST_PREFERENCES_UPDATE = 100
        const val FULL_SCREEN_NAVIGATION_DRAWER = "gestureFullScreenNavigationDrawer"

        const val EXTRA_STARTED_WITH_SHORTCUT = "com.ichi2.anki.StartedWithShortcut"

        // The main reason to use dynamic shortcut is that it ensures that they are only available when permission is granted to access the collection.
        // Even if we were going to ensure that all shortcut can deal nicely with missing permission, we probably would still need to use dynamic shortcuts because:
        // * this ensure we don't offer shortcut until we have permission to access the collection
        // * to use static shortcut, the package name must be given in res/shortcuts.xml. See https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts#static
        // * AnkiDroid package name is not constant. For example in debug variant, it is com.ichi2.anki.debug
        // * having variables in shortcuts used to be doable with https://plugins.gradle.org/plugin/de.timfreiheit.resourceplaceholders, however
        // * after manually testing it, and looking at open issue https://github.com/timfreiheit/ResourcePlaceholdersPlugin/issues/13 , it seems this was broken with recent version of gradle
        fun enablePostShortcut(context: Context) {
            if (runCatching { grantedStoragePermissions(context, showToast = false) }.getOrNull() != true) {
                Timber.w("No storage access, not enabling shortcuts")
                return
            }
            // Review Cards Shortcut
            val intentReviewCards =
                Reviewer.getIntent(context).apply {
                    action = Intent.ACTION_VIEW
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_STARTED_WITH_SHORTCUT, true)
                }
            val deckPickerIntent =
                Intent(context, IntentHandler::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
            val reviewCardsShortcut =
                ShortcutInfoCompat
                    .Builder(context, "reviewCardsShortcutId")
                    .setShortLabel(context.getString(R.string.studyoptions_start))
                    .setLongLabel(context.getString(R.string.studyoptions_start))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.review_shortcut))
                    .setIntents(arrayOf(deckPickerIntent, intentReviewCards))
                    .build()

            // Add Shortcut
            val intentAddNote = Intent(context, IntentHandler2::class.java)
            intentAddNote.action = Intent.ACTION_VIEW
            intentAddNote.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            intentAddNote.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
            val noteEditorShortcut =
                ShortcutInfoCompat
                    .Builder(context, "noteEditorShortcutId")
                    .setShortLabel(context.getString(R.string.menu_add))
                    .setLongLabel(context.getString(R.string.menu_add))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.add_shortcut))
                    .setIntent(intentAddNote)
                    .build()

            // CardBrowser Shortcut
            val intentCardBrowser = Intent(context, CardBrowser::class.java)
            intentCardBrowser.action = Intent.ACTION_VIEW
            intentCardBrowser.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            val cardBrowserShortcut =
                ShortcutInfoCompat
                    .Builder(context, "cardBrowserShortcutId")
                    .setShortLabel(context.getString(R.string.card_browser))
                    .setLongLabel(context.getString(R.string.card_browser))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.browse_shortcut))
                    .setIntent(intentCardBrowser)
                    .build()
            ShortcutManagerCompat.addDynamicShortcuts(
                context,
                listOf(
                    reviewCardsShortcut,
                    noteEditorShortcut,
                    cardBrowserShortcut,
                ),
            )
        }
    }
}

fun Fragment.requireNavigationDrawerActivity() = (requireActivity() as NavigationDrawerActivity)
