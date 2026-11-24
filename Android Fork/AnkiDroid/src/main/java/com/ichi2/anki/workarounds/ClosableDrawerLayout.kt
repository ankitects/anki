// SPDX-License-Identifier: GPL-3.0-or-later

// ಠ_ಠ This is required to override a package-private method to block animations
@file:Suppress("PackageDirectoryMismatch")

package androidx.drawerlayout.widget

import android.content.Context
import android.util.AttributeSet
import com.ichi2.anki.common.annotations.CoverageOptIn

@CoverageOptIn
class ClosableDrawerLayout : DrawerLayout {
    private var animationEnabled = true

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    fun setAnimationEnabled(useAnimation: Boolean) {
        animationEnabled = useAnimation
    }

    // This is called internally (onTouchEvent outside the control will close it), so we need it here
    public override fun closeDrawers(peekingOnly: Boolean) {
        // TODO: This fails due to #7344 - tapping on the left side partially opens the menu and blocks the UI
        // permanently. I didn't work too hard on resolving this, but it didn't seem like a simple fix.

        // if (!mAnimationEnabled) {
        //     closeDrawer(GravityCompat.START, false);
        // }
        super.closeDrawers(peekingOnly)
    }
}
