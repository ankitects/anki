// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.widgets.R
import com.ichi2.anki.widgets.databinding.WidgetSmallBinding
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AnkiDroidWidgetSmallTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `initial widget uses the fixed blue icon on Android 12`() =
        withSmallWidget {
            assertEquals(R.drawable.ic_anki_unthemed, shadowOf(ankidroidWidgetSmallButton.drawable).createdFromResId)
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `initial widget keeps the legacy icon before Android 12`() =
        withSmallWidget {
            assertEquals(R.drawable.widget_bg_small, shadowOf(ankidroidWidgetSmallButton.drawable).createdFromResId)
        }
}

/** Inflates the small widget's initial layout for the current Android version and exposes its views. */
internal fun withSmallWidget(block: WidgetSmallBinding.() -> Unit) {
    val context = getApplicationContext<Context>()
    val initialLayout =
        context.resources.getXml(R.xml.widget_provider_small).use { metadata ->
            while (metadata.eventType != XmlPullParser.START_TAG) {
                metadata.next()
            }
            metadata.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "initialLayout", 0)
        }
    val view = RemoteViews(context.packageName, initialLayout).apply(context, null)
    WidgetSmallBinding.bind(view).block()
}
