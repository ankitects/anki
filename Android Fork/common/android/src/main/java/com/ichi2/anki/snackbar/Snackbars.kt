// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.snackbar

import android.app.Activity
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.onAttachedToWindow2
import com.ichi2.anki.common.android.BuildConfig
import com.ichi2.anki.common.android.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.common.utils.android.showThemedToast
import timber.log.Timber

typealias SnackbarBuilder = Snackbar.() -> Unit

/**
 * An activity/fragment can implement this interface to specify a
 * base configuration for snackbars shown in the activity/fragment.
 */
interface BaseSnackbarBuilderProvider {
    /**
     * The SnackbarBuilder that will be run to provide a base configuration for snackbars shown.
     */
    val baseSnackbarBuilder: SnackbarBuilder
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver An [Activity] that has a [CoordinatorLayout] with id `root_layout`.
 * @param textResource String resource to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun Activity.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val text = getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver An [Activity] that has a [CoordinatorLayout] with id `root_layout`.
 * @param text Text to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun Activity.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
): Snackbar? {
    val view: View? = findViewById(R.id.root_layout) as? CoordinatorLayout

    if (view != null) {
        val baseSnackbarBuilder = (this as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder
        return view.showSnackbar(text, duration) {
            baseSnackbarBuilder?.invoke(this)
            snackbarBuilder?.invoke(this)
            Timber.d("displayed snackbar: '%s'", text)
        }
    } else {
        val errorMessage =
            "While trying to show a snackbar, " +
                "could not find a view with id root_layout in $this"

        if (BuildConfig.DEBUG) {
            throw IllegalArgumentException(errorMessage)
        } else {
            Timber.e(errorMessage)
            showThemedToast(this, text, false)
            return null
        }
    }
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [View] that is either a [CoordinatorLayout],
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 * @param textResource String resource to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun View.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val text = resources.getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [View] that is either a [CoordinatorLayout],
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 * @param text Text to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun View.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
): Snackbar {
    val snackbar = Snackbar.make(this, text, duration)
    snackbar.setMaxLines(4)
    snackbar.behavior = SwipeDismissBehaviorFix()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        snackbar.fixMarginsWhenInsetsChange()
    }

    if (snackbarBuilder != null) {
        snackbar.snackbarBuilder()
    }

    if (snackbar.anchorView?.isVisible == false) {
        val errorMessage = "While trying to show a snackbar, anchorView was not visible"
        if (BuildConfig.DEBUG) {
            throw IllegalArgumentException(errorMessage)
        }
        Timber.w(errorMessage)
        CrashReportService.sendExceptionReport(
            ManuallyReportedException(errorMessage),
            "View.showSnackbar",
            onlyIfSilent = true,
        )

        snackbar.anchorView = null
    }

    snackbar.retainUntilShown(rootView)
    snackbar.show()
    return snackbar
}

/**
 * Keeps the latest pending snackbar alive on [host] until shown or dismissed.
 *
 * Workaround to fix losing a pending snackbar after a GC (#22076).
 *
 * See [SnackbarManager.show and SnackbarRecord](https://github.com/material-components/material-components-android/blob/1.14.0/lib/java/com/google/android/material/snackbar/SnackbarManager.java).
 */
private fun Snackbar.retainUntilShown(host: View) {
    host.retainPendingSnackbar(this)
    addCallback(
        object : Snackbar.Callback() {
            override fun onShown(snackbar: Snackbar) = release(snackbar)

            override fun onDismissed(
                snackbar: Snackbar,
                event: Int,
            ) = release(snackbar)

            private fun release(snackbar: Snackbar) {
                host.releasePendingSnackbar(snackbar)
                snackbar.removeCallback(this)
            }
        },
    )
}

/** Stores a strong reference to [snackbar] on this view, replacing any previously retained snackbar. */
private fun View.retainPendingSnackbar(snackbar: Snackbar) {
    setTag(R.id.pending_snackbar, snackbar)
}

/** Releases the strong reference to [snackbar] only if it is still the retained snackbar. */
private fun View.releasePendingSnackbar(snackbar: Snackbar) {
    // A callback from an older snackbar must not release its replacement.
    if (getTag(R.id.pending_snackbar) !== snackbar) return
    setTag(R.id.pending_snackbar, null)
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [View] that is either a [CoordinatorLayout],
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 * @param text Text to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun Fragment.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
): Snackbar? {
    val baseSnackbarBuilder = (this as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder
    return requireActivity().showSnackbar(text, duration) {
        baseSnackbarBuilder?.invoke(this)
        snackbarBuilder?.invoke(this)
        Timber.d("displayed snackbar: '%s'", text)
    }
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [DialogFragment], ideally where the [root view][DialogFragment.getView] has been
 *  initialized
 * @param text Text to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun DialogFragment.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val baseSnackbarBuilder = (this as? BaseSnackbarBuilderProvider)?.baseSnackbarBuilder
    view?.showSnackbar(text, duration) {
        baseSnackbarBuilder?.invoke(this)
        snackbarBuilder?.invoke(this)
        Timber.d("displayed snackbar: '%s'", text)
    } ?: run {
        requireActivity().showSnackbar(text, duration, snackbarBuilder)
    }
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [View] that is either a [CoordinatorLayout],
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 * @param textResource String resource to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun Fragment.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null,
) {
    val text = resources.getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}

// **********************************************************************************************

fun Activity.canProperlyShowSnackbars() = findViewById<View>(R.id.root_layout) is CoordinatorLayout

fun Snackbar.setMaxLines(maxLines: Int) {
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = maxLines
}

/** Calls [action] when this snackbar is dismissed, for any reason. */
fun Snackbar.doOnDismissed(action: () -> Unit): Snackbar =
    addCallback(
        object : Snackbar.Callback() {
            override fun onDismissed(
                transientBottomBar: Snackbar?,
                event: Int,
            ) = action()
        },
    )

/**
 * When bottom inset change, for instance, when keyboard is open or closed,
 * snackbar fails to adjust its margins and can appear too high or too low.
 * While snackbar does employ an `OnApplyWindowInsetsListener`, its methods don't get called.
 * This here is an atrocious workaround that solves the issue. It is awful and despicable.
 *
 * First of all, we use our own `OnApplyWindowInsetsListener`. Note that we *need to post it*,
 * as apparently something else resets it after this call. (Not sure if it's feasible
 * to find out what as this requires method breakpoints, which are prohibitively slow.)
 * Also, if we set an inset listener for a view, [View.dispatchApplyWindowInsets] will call
 * `onApplyWindowInsets` on our listener rather than the view, so we better call the original.
 *
 * Then, we want to call [Snackbar.updateMargins], which is private,
 * and the one method is not private that calls it is [Snackbar.onAttachedToWindow],
 * so we hack into its package namespace using a helper method.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun Snackbar.fixMarginsWhenInsetsChange() {
    view.post {
        view.rootView.setOnApplyWindowInsetsListener { rootView, insets ->
            onAttachedToWindow2()
            rootView.onApplyWindowInsets(insets)
        }
    }

    addCallback(
        object : Snackbar.Callback() {
            override fun onDismissed(
                snackbar: Snackbar,
                event: Int,
            ) {
                view.rootView.setOnApplyWindowInsetsListener(null)
            }
        },
    )
}
