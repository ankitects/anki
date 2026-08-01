/*
 Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.testutils.createTransientFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NoteServiceTest : RobolectricTest() {
    // TODO: Storage.kt needs a different openCollection override, and accepting media files
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    // temporary directory to test importMediaToDirectory function
    @get:Rule
    var directory = TemporaryFolder()

    @get:Rule
    var directory2 = TemporaryFolder()

    // tests if the text fields of the notes are the same after calling updateJsonNoteFromMultimediaNote
    @Test
    fun updateJsonNoteTest() {
        val testNoteType = col.notetypes.byName("Basic")
        val multiMediaNote: IMultimediaEditableNote? = NoteService.createEmptyNote(testNoteType!!)
        multiMediaNote!!.getField(0)!!.text = "foo"
        multiMediaNote.getField(1)!!.text = "bar"

        val basicNote =
            Note.fromNotetypeId(col, testNoteType.id).apply {
                setField(0, "this should be changed to foo")
                setField(1, "this should be changed to bar")
            }

        NoteService.updateJsonNoteFromMultimediaNote(multiMediaNote, basicNote)
        assertEquals(basicNote.fields[0], multiMediaNote.getField(0)!!.text)
        assertEquals(basicNote.fields[1], multiMediaNote.getField(1)!!.text)
    }

    // tests if updateJsonNoteFromMultimediaNote throws a RuntimeException if the ID's of the notes don't match
    @Test
    fun updateJsonNoteRuntimeErrorTest() {
        // note type with ID 42
        var testNotetype = NotetypeJson("""{"flds": [{"name": "foo bar", "ord": "1"}], "id": "42"}""")
        val multiMediaNoteWithID42: IMultimediaEditableNote? = NoteService.createEmptyNote(testNotetype)

        // note type with ID 45
        testNotetype = col.notetypes.newBasicNotetype()
        testNotetype.id = 45
        col.notetypes.add(testNotetype)
        val noteWithID45 = Note.fromNotetypeId(col, testNotetype.id)
        val expectedException: Throwable =
            assertThrows(
                RuntimeException::class.java,
            ) { NoteService.updateJsonNoteFromMultimediaNote(multiMediaNoteWithID42, noteWithID45) }
        assertEquals(expectedException.message, "Source and Destination Note ID do not match.")
    }

    @Test
    @Throws(IOException::class)
    fun importAudioClipToDirectoryTest() {
        val fileAudio = directory.newFile("testaudio.wav")

        // writes a line in the file so the file's length isn't 0
        FileWriter(fileAudio).use { fileWriter -> fileWriter.write("line1") }

        val audioField = MediaClipField()
        audioField.mediaFile = fileAudio

        NoteService.importMediaToDirectory(col, audioField)

        val outFile = File(col.media.dir, fileAudio.name)

        assertThat(
            "path should be equal to new file made in NoteService.importMediaToDirectory",
            outFile,
            equalTo(audioField.mediaFile),
        )
    }

    // Similar test like above, but with an ImageField instead of a MediaClipField
    @Test
    @Throws(IOException::class)
    fun importImageToDirectoryTest() {
        val fileImage = directory.newFile("test_image.png")

        // writes a line in the file so the file's length isn't 0
        FileWriter(fileImage).use { fileWriter -> fileWriter.write("line1") }

        val imgField = ImageField()
        imgField.extraImageFileRef = fileImage

        NoteService.importMediaToDirectory(col, imgField)

        val outFile = File(col.media.dir, fileImage.name)

        assertThat(
            "path should be equal to new file made in NoteService.importMediaToDirectory",
            outFile,
            equalTo(imgField.extraImageFileRef),
        )
    }

    /**
     * Tests if after importing:
     *
     * * New file keeps its name
     * * File with same name, but different content, has its name changed
     * * File with same name and content don't have its name changed
     *
     * @throws IOException if new created files already exist on temp directory
     */
    @Test
    @Throws(IOException::class)
    fun importAudioWithSameNameTest() {
        val f1 = directory.newFile("audio.mp3")
        val f2 = directory2.newFile("audio.mp3")

        // writes a line in the file so the file's length isn't 0
        FileWriter(f1).use { fileWriter -> fileWriter.write("1") }
        // do the same to the second file, but with different data
        FileWriter(f2).use { fileWriter -> fileWriter.write("2") }

        val fld1 = MediaClipField()
        fld1.mediaFile = f1

        val fld2 = MediaClipField()
        fld2.mediaFile = f2

        // third field to test if name is kept after reimporting the same file
        val fld3 = MediaClipField()
        fld3.mediaFile = f1

        Timber.e("media folder is %s %b", col.media.dir, col.media.dir.exists())
        NoteService.importMediaToDirectory(col, fld1)
        val o1 = File(col.media.dir, f1.name)

        NoteService.importMediaToDirectory(col, fld2)
        val o2 = File(col.media.dir, f2.name)

        NoteService.importMediaToDirectory(col, fld3)
        // creating a third outfile isn't necessary because it should be equal to the first one

        assertThat(
            "path should be equal to new file made in NoteService.importMediaToDirectory",
            o1,
            equalTo(fld1.mediaFile),
        )
        assertThat(
            "path should be different to new file made in NoteService.importMediaToDirectory",
            o2,
            not(fld2.mediaFile),
        )
        assertThat(
            "path should be equal to new file made in NoteService.importMediaToDirectory",
            o1,
            equalTo(fld3.mediaFile),
        )
    }

    // Similar test like above, but with an ImageField instead of a MediaClipField
    @Test
    @Throws(IOException::class)
    fun importImageWithSameNameTest() {
        val f1 = directory.newFile("img.png")
        val f2 = directory2.newFile("img.png")

        // write a line in the file so the file's length isn't 0
        FileWriter(f1).use { fileWriter -> fileWriter.write("1") }
        // do the same to the second file, but with different data
        FileWriter(f2).use { fileWriter -> fileWriter.write("2") }

        val fld1 = ImageField()
        fld1.extraImageFileRef = f1

        val fld2 = ImageField()
        fld2.extraImageFileRef = f2

        // third field to test if name is kept after reimporting the same file
        val fld3 = ImageField()
        fld3.extraImageFileRef = f1

        NoteService.importMediaToDirectory(col, fld1)
        val o1 = File(col.media.dir, f1.name)

        NoteService.importMediaToDirectory(col, fld2)
        val o2 = File(col.media.dir, f2.name)

        NoteService.importMediaToDirectory(col, fld3)
        // creating a third outfile isn't necessary because it should be equal to the first one

        assertThat(
            "path should be equal to new file made in NoteService.importMediaToDirectory",
            o1,
            equalTo(fld1.extraImageFileRef),
        )
        assertThat(
            "path should be different to new file made in NoteService.importMediaToDirectory",
            o2,
            not(fld2.extraImageFileRef),
        )
        assertThat(
            "path should be equal to new file made in NoteService.importMediaToDirectory",
            o1,
            equalTo(fld3.extraImageFileRef),
        )
    }

    /**
     * Sometimes media files cannot be imported directly to the media directory,
     * so they are copied to cache then imported and deleted.
     * This tests if cached media are properly deleted after import.
     */
    @Test
    fun tempAudioIsDeletedAfterImport() {
        val file = createTransientFile("foo")

        val field =
            MediaClipField().apply {
                mediaFile = file
                hasTemporaryMedia = true
            }

        NoteService.importMediaToDirectory(col, field)

        assertThat("Audio temporary file should have been deleted after importing", file, not(anExistingFile()))
    }

    // Similar test like above, but with an ImageField instead of a MediaClipField
    @Test
    fun tempImageIsDeletedAfterImport() {
        val file = createTransientFile("foo")

        val field =
            ImageField().apply {
                extraImageFileRef = file
                hasTemporaryMedia = true
            }

        NoteService.importMediaToDirectory(col, field)

        assertThat("Image temporary file should have been deleted after importing", file, not(anExistingFile()))
    }
}
