// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.receiver

import android.app.Application
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.ext.requireSystemService
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.testutils.EmptyApplication
import com.ichi2.utils.TruncatedString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContextImpl

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class CopyToClipboardReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clipboardManager
        get() = context.requireSystemService<ClipboardManager>()
    private val notificationManager
        get() = context.requireSystemService<NotificationManager>()

    @Test
    fun `copies the error log to the clipboard and dismisses the notification`() {
        NotificationManagerCompat.from(context).notify(
            NotificationId.SYNC_MEDIA,
            NotificationCompat
                .Builder(context, "channel")
                .setSmallIcon(R.drawable.ic_star_notify)
                .build(),
        )
        val intent = CopyToClipboardReceiver.getIntent(context, TruncatedString.from("sync error log"))

        CopyToClipboardReceiver().onReceive(context, intent)

        val copiedText =
            clipboardManager.primaryClip
                ?.getItemAt(0)
                ?.text
        assertThat(copiedText, equalTo("sync error log"))
        assertThat("notification is dismissed", shadowOf(notificationManager).size(), equalTo(0))
    }

    @Test
    fun `does not copy when the error log extra is missing`() {
        val intent = Intent(context, CopyToClipboardReceiver::class.java)

        CopyToClipboardReceiver().onReceive(context, intent)

        assertThat(clipboardManager.hasPrimaryClip(), equalTo(false))
    }

    @Test
    fun `keeps the notification if the copy fails`() {
        NotificationManagerCompat.from(context).notify(
            NotificationId.SYNC_MEDIA,
            NotificationCompat
                .Builder(context, "channel")
                .setSmallIcon(R.drawable.ic_star_notify)
                .build(),
        )
        val shadowNotificationManager = shadowOf(notificationManager)
        // simulate a copy failure: the clipboard service is unavailable
        Shadow
            .extract<ShadowContextImpl>((context as Application).baseContext)
            .removeSystemService(Context.CLIPBOARD_SERVICE)
        val intent = CopyToClipboardReceiver.getIntent(context, TruncatedString.from("sync error log"))

        CopyToClipboardReceiver().onReceive(context, intent)

        assertThat("the error log must remain accessible", shadowNotificationManager.size(), equalTo(1))
    }
}
