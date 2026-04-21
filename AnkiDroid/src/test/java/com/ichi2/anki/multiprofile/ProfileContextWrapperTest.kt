/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class ProfileContextWrapperTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseContext: Context
    private lateinit var profileBaseDir: File
    private val profileId = ProfileId("p_test123")

    @Before
    fun setUp() {
        // spy to verify calls passed to super.getSharedPreferences
        baseContext = spy(ApplicationProvider.getApplicationContext<Context>())

        val appDataRoot = baseContext.filesDir.parentFile!!
        profileBaseDir = File(appDataRoot, "p_test123")

        if (profileBaseDir.exists()) {
            profileBaseDir.deleteRecursively()
        }
    }

    // --- Standard path tests ---

    @Test
    fun `getFilesDir returns correct path and creates directory`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val result = wrapper.filesDir
        val expected = File(profileBaseDir, "files")
        assertEquals(expected.absolutePath, result.absolutePath)
        assertTrue("Directory should be created", result.exists())
    }

    @Test
    fun `getCacheDir returns correct path and creates directory`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val result = wrapper.cacheDir
        val expected = File(profileBaseDir, "cache")
        assertEquals(expected.absolutePath, result.absolutePath)
        assertTrue(result.exists())
    }

    @Test
    fun `getCodeCacheDir returns correct path and creates directory`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val result = wrapper.codeCacheDir
        val expected = File(profileBaseDir, "code_cache")
        assertEquals(expected.absolutePath, result.absolutePath)
        assertTrue(result.exists())
    }

    @Test
    fun `getNoBackupFilesDir returns correct path and creates directory`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val result = wrapper.noBackupFilesDir
        val expected = File(profileBaseDir, "no_backup")
        assertEquals(expected.absolutePath, result.absolutePath)
        assertTrue(result.exists())
    }

    @Test
    fun `getDatabasePath returns correct path inside databases folder`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val dbName = "my_collection.db"
        val result = wrapper.getDatabasePath(dbName)
        val expectedDir = File(profileBaseDir, "databases")
        val expectedFile = File(expectedDir, dbName)
        assertEquals(expectedFile.absolutePath, result.absolutePath)
        assertTrue("Parent databases folder should be created", expectedDir.exists())
    }

    // --- Security tests ---

    @Test
    fun `getDir returns correct custom path for valid names`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val dirName = "app_textures"
        val result = wrapper.getDir(dirName, Context.MODE_PRIVATE)
        val expected = File(profileBaseDir, dirName)
        assertEquals(expected.absolutePath, result.absolutePath)
        assertTrue(result.exists())
    }

    @Test(expected = SecurityException::class)
    fun `getDir throws exception for directory traversal with dot dot`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        wrapper.getDir("..", Context.MODE_PRIVATE)
    }

    @Test(expected = SecurityException::class)
    fun `getDatabasePath throws exception for directory traversal`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        wrapper.getDatabasePath("../dangerous.db")
    }

    // --- Shared pref tests ---

    @Test
    @SuppressLint("WrongConstant") // mockito eq support
    fun `getSharedPreferences prefixes name for custom profile`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val originalName = "deck_options"

        doReturn(mock<SharedPreferences>())
            .`when`(baseContext)
            .getSharedPreferences(any(), anyInt())

        wrapper.getSharedPreferences(originalName, Context.MODE_PRIVATE)

        val expectedName = "profile_${profileId.value}_$originalName"
        verify(baseContext).getSharedPreferences(eq(expectedName), eq(Context.MODE_PRIVATE))
    }

    @Test
    @SuppressLint("WrongConstant") // mockito eq support
    fun `getSharedPreferences does not prefix name for default profile`() {
        val wrapper = ProfileContextWrapper.create(baseContext, ProfileId.DEFAULT, profileBaseDir)
        val originalName = "deck_options"

        doReturn(mock<SharedPreferences>())
            .`when`(baseContext)
            .getSharedPreferences(any(), anyInt())

        wrapper.getSharedPreferences(originalName, Context.MODE_PRIVATE)

        verify(baseContext).getSharedPreferences(eq(originalName), eq(Context.MODE_PRIVATE))
    }

    @Test
    @SuppressLint("WrongConstant") // mockito eq support
    fun `getSharedPreferences does not double-prefix if name is already prefixed`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val alreadyPrefixedName = "profile_${profileId.value}_deck_options"

        doReturn(mock<SharedPreferences>())
            .`when`(baseContext)
            .getSharedPreferences(any(), anyInt())

        wrapper.getSharedPreferences(alreadyPrefixedName, Context.MODE_PRIVATE)

        verify(baseContext).getSharedPreferences(eq(alreadyPrefixedName), eq(Context.MODE_PRIVATE))
    }

    // --- Factory tests ---

    @Test
    fun `create factory initializes directories immediately`() {
        profileBaseDir.deleteRecursively()

        ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)

        val filesDir = File(profileBaseDir, "files")
        val databasesDir = File(profileBaseDir, "databases")

        assertTrue("Files dir should be created by factory", filesDir.exists())
        assertTrue("Databases dir should be created by factory", databasesDir.exists())
    }

    @Test(expected = IOException::class)
    fun `create factory throws IOException if directory creation fails`() {
        if (!profileBaseDir.exists()) {
            profileBaseDir.createNewFile()
        } else {
            profileBaseDir.deleteRecursively()
            profileBaseDir.createNewFile()
        }

        ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
    }

    @Test
    fun `default profile delegates to super and ignores profileBaseDir`() {
        val wrapper = ProfileContextWrapper.create(baseContext, ProfileId.DEFAULT, profileBaseDir)

        val result = wrapper.filesDir

        assertEquals(baseContext.filesDir.absolutePath, result.absolutePath)
        assertNotEquals(File(profileBaseDir, "files").absolutePath, result.absolutePath)
    }

    @Test
    fun `all profile internal directories sit under profileBaseDir`() {
        val wrapper = ProfileContextWrapper.create(baseContext, profileId, profileBaseDir)
        val rootPrefix = profileBaseDir.absolutePath + File.separator

        assertTrue("filesDir", wrapper.filesDir.absolutePath.startsWith(rootPrefix))
        assertTrue("cacheDir", wrapper.cacheDir.absolutePath.startsWith(rootPrefix))
        assertTrue("codeCacheDir", wrapper.codeCacheDir.absolutePath.startsWith(rootPrefix))
        assertTrue("noBackupFilesDir", wrapper.noBackupFilesDir.absolutePath.startsWith(rootPrefix))
        assertTrue(
            "databasePath",
            wrapper.getDatabasePath("collection.anki2").absolutePath.startsWith(rootPrefix),
        )
        assertTrue(
            "getDir",
            wrapper.getDir("acra", Context.MODE_PRIVATE).absolutePath.startsWith(rootPrefix),
        )
    }

    @Test
    fun `two non-default profiles have disjoint internal directory trees`() {
        val appDataRoot = baseContext.filesDir.parentFile!!
        val profileA = ProfileId("p_alpha")
        val profileB = ProfileId("p_bravo")
        val baseA = File(appDataRoot, profileA.value).apply { deleteRecursively() }
        val baseB = File(appDataRoot, profileB.value).apply { deleteRecursively() }

        val wrapperA = ProfileContextWrapper.create(baseContext, profileA, baseA)
        val wrapperB = ProfileContextWrapper.create(baseContext, profileB, baseB)

        assertNotEquals(wrapperA.filesDir.absolutePath, wrapperB.filesDir.absolutePath)
        assertNotEquals(wrapperA.cacheDir.absolutePath, wrapperB.cacheDir.absolutePath)
        assertNotEquals(wrapperA.codeCacheDir.absolutePath, wrapperB.codeCacheDir.absolutePath)
        assertNotEquals(wrapperA.noBackupFilesDir.absolutePath, wrapperB.noBackupFilesDir.absolutePath)
        assertNotEquals(
            wrapperA.getDatabasePath("collection.anki2").absolutePath,
            wrapperB.getDatabasePath("collection.anki2").absolutePath,
        )
    }
}
