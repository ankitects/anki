// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.themes.Themes
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Activity aimed to host a fragment on the entire screen.
 * For that, it uses [R.layout.activity_single_fragment], which has only a [FragmentContainerView]
 *
 * Useful to avoid creating a Activity for every new screen
 * while being able to reuse the fragment on other places.
 *
 * [getIntent] can be used as an easy way to build a [SingleFragmentActivity]
 *
 * See also: [ConfigAwareSingleFragmentActivity]
 */
open class SingleFragmentActivity :
    AnkiActivity(R.layout.activity_single_fragment),
    BaseSnackbarBuilderProvider {
    // delegate to the fragment in all cases
    override val baseSnackbarBuilder: SnackbarBuilder
        get() = (fragment as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder ?: { }

    // the same host class serves every screen it shows, so report what it's showing
    override val analyticsScreenName: String
        get() = intent.getStringExtra(EXTRA_FRAGMENT_NAME)?.substringAfterLast('.') ?: super.analyticsScreenName

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) {
            return
        }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { Themes.isNightTheme })
        val root = findViewById<CoordinatorLayout>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout())
            // apply the insets only for content/fragments defined by SingleFragmentActivity
            // directly, subclasses(ex. ManageSpaceActivity, Preferences) should handle their
            // content independently
            if (this::class.java == SingleFragmentActivity::class.java) {
                findViewById<FragmentContainerView>(R.id.fragment_container)?.updatePadding(
                    left = constraints.left,
                    right = constraints.right,
                    top = constraints.top,
                    bottom = constraints.bottom,
                )
            }
            insets
        }

        // avoid recreating the fragment on configuration changes
        // the fragment should handle state restoration
        if (savedInstanceState != null) {
            return
        }

        val fragment = createFragment()
        if (fragment == null) {
            Timber.w("'%s' extra not provided, finishing", EXTRA_FRAGMENT_NAME)
            showThemedToast(this, CommonString.something_wrong, false)
            finish()
            return
        }
        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, FRAGMENT_TAG)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)!!
        return if (fragment is DispatchKeyEventListener) {
            fragment.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    protected open fun createFragment(): Fragment? {
        val fragmentClassName = intent.getStringExtra(EXTRA_FRAGMENT_NAME) ?: return null
        Timber.d("Creating fragment %s", fragmentClassName)
        return FragmentFactoryUtils.instantiate<Fragment>(this, fragmentClassName).apply {
            arguments = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS)
        }
    }

    /** Reference to the hosted fragment */
    val fragment
        get() = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)

    override val shortcuts: ShortcutGroup?
        get() = (fragment as? ShortcutGroupProvider)?.shortcuts

    companion object {
        const val EXTRA_FRAGMENT_NAME = "extra_fragment_name"
        const val EXTRA_FRAGMENT_ARGS = "extra_fragment_args"
        const val FRAGMENT_TAG = "SingleFragmentActivityTag"

        fun getIntent(
            context: Context,
            fragmentClass: KClass<out Fragment>,
            arguments: Bundle? = null,
            intentAction: String? = null,
        ): Intent =
            Intent(context, SingleFragmentActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_NAME, fragmentClass.jvmName)
                putExtra(EXTRA_FRAGMENT_ARGS, arguments)
                action = intentAction
            }
    }
}

interface DispatchKeyEventListener {
    fun dispatchKeyEvent(event: KeyEvent): Boolean
}
