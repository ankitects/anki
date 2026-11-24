// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.ContextWrapper
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.targetContext
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class IdsFileTest : AndroidTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `parcelled snapshot can be restored repeatedly`() {
        val ids = listOf(9L, 1L, Long.MAX_VALUE)
        val file = IdsFile(temporaryFolder.root, ids, IdsFile.Purpose.PREVIEW)
        val parcel = Parcel.obtain()
        try {
            file.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = IdsFile.CREATOR.createFromParcel(parcel)
            assertEquals(ids, restored.getIdsOrNull())
            assertEquals(ids, restored.getIdsOrNull())
            assertTrue(file.exists())
        } finally {
            parcel.recycle()
        }
    }

    private val now = 1_800_000_000_000L
    private lateinit var storageContext: ProfileContextWrapper

    @Before
    fun setUpStorage() {
        val time = mockk<Time>()
        every { time.intTimeMS() } returns now
        TimeManager.resetWith(time)
        val root = temporaryFolder.newFolder("storage")
        val base =
            object : ContextWrapper(targetContext) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }

                override fun getFilesDir() = File(root, "files").apply { mkdirs() }

                override fun getCacheDir() = File(root, "cache").apply { mkdirs() }

                override fun getExternalCacheDir() = File(root, "external-cache").apply { mkdirs() }
            }
        storageContext = ProfileContextWrapper.create(base, ProfileId.DEFAULT, root)
    }

    @After
    fun resetTime() = TimeManager.reset()

    @Test
    fun `snapshots and cleanup are isolated by profile`() {
        val otherProfile =
            ProfileContextWrapper.create(
                storageContext.baseContext,
                ProfileId("p_other"),
                File(storageContext.filesDir.parentFile, "p_other"),
            )
        val file = IdsFile(IdsFile.getDirectory(storageContext), listOf(9L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        val otherFile = IdsFile(IdsFile.getDirectory(otherProfile), listOf(10L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
        assertEquals(otherProfile.cacheDir, otherFile.parentFile!!.parentFile)
        IdsFile.deleteStaleFiles(storageContext)
        assertFalse(file.exists())
        assertTrue(otherFile.exists())
        IdsFile.deleteStaleFiles(otherProfile)
        assertFalse(otherFile.exists())
    }

    @Test
    fun `cleanup leaves files outside the snapshot directory untouched`() {
        val files =
            listOf(storageContext.cacheDir, storageContext.externalCacheDir!!).map { directory ->
                IdsFile(directory, listOf(1L), IdsFile.Purpose.PREVIEW).aged(8.days.inWholeMilliseconds)
            }

        IdsFile.deleteStaleFiles(storageContext)

        files.forEach { assertTrue(it.exists(), it.path) }
    }

    @Test
    fun `parcelled snapshot remains readable until its file expires`() {
        val file = IdsFile(IdsFile.getDirectory(storageContext), listOf(7L, 11L), IdsFile.Purpose.PREVIEW).aged(7.days.inWholeMilliseconds)
        val parcel = Parcel.obtain()
        try {
            file.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = IdsFile.CREATOR.createFromParcel(parcel)
            IdsFile.deleteStaleFiles(storageContext)
            assertEquals(listOf(7L, 11L), restored.getIdsOrNull())
            file.aged(8.days.inWholeMilliseconds)
            IdsFile.deleteStaleFiles(storageContext)
            assertNull(restored.getIdsOrNull())
        } finally {
            parcel.recycle()
        }
    }

    private fun <T : File> T.aged(ageMillis: Long): T =
        apply {
            assertTrue(setLastModified(now - ageMillis))
        }
}
