// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.anki.observability.ensureOpsExecuted
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import com.ichi2.testutils.withViewModel
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backend regressions link to upstream issues; other ignores describe deferred screen behavior. */
class ManageTagsExpansionStateRegressionTest : JvmTest() {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    @Ignore("Anki Issue 5965: saving expansion discards redo: https://github.com/ankitects/anki/issues/5965")
    fun `saving expansion of an existing parent preserves a pending redo`() =
        runTest {
            val note = addTags("source", "parent", "parent::child")
            col.tags.remove("source")
            col.undo()
            assertNotNull(col.undoStatus().redo)

            col.tags.setCollapsed("parent", false)

            assertNotNull(col.undoStatus().redo, "Changing expansion must not discard a pending redo")
            col.redo()
            assertEquals(setOf("parent", "parent::child"), col.getNote(note.id).tags.toSet())
        }

    @Test
    @Ignore("Anki Issue 5966: redo restores stale expansion: https://github.com/ankitects/anki/issues/5966")
    fun `redo keeps expansion explicitly saved after renaming a parent`() =
        runTest {
            addBasicNote().update {
                addTag("source")
                addTag("source::child")
            }
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed("source").join()
                renameTag("source", "destination").join()
                col.tags.setCollapsed("destination", false)
                refreshTags().join()
                assertNull(content.error)
                assertEquals(listOf("destination", "destination::child"), content.visibleTagNames)
                col.undo()
                refreshTags().join()
                assertEquals(listOf("source", "source::child"), content.visibleTagNames)
                col.redo()
                refreshTags().join()
                assertEquals(listOf("destination", "destination::child"), content.visibleTagNames)
            }
        }

    @Test
    @Ignore("Deferred UI behavior: undo does not restore automatically expanded paths")
    fun `undoing a second rename restores the revealed ancestor`() =
        runTest {
            addBasicNote().update { addTag("source") }
            withViewModel(ManageTagsViewModel()) {
                renameTag("source", "source::child").join()
                assertEquals(listOf("source", "source::child"), content.visibleTagNames)
                renameTag("source", "destination").join()
                assertEquals(listOf("destination", "destination::child"), content.visibleTagNames)
                col.undo()
                refreshTags().join()
                assertEquals(listOf("source", "source::child"), content.visibleTagNames)
            }
        }

    @Test
    @Ignore("Deferred UI behavior: rename and undo do not preserve manual search collapses")
    fun `undoing a rename restores manually collapsed search results`() =
        runTest {
            addBasicNote().update { addTag("source::branch::leaf") }
            withViewModel(ManageTagsViewModel()) {
                filter("leaf")
                toggleCollapsed("source::branch").join()
                assertEquals(listOf("source", "source::branch"), content.visibleTagNames)
                renameTag("source", "destination").join()
                assertEquals(listOf("destination", "destination::branch"), content.visibleTagNames)
                col.undo()
                refreshTags().join()
                assertEquals(listOf("source", "source::branch"), content.visibleTagNames)
            }
        }

    @Test
    @Ignore("Deferred UI behavior: moving a tag does not remap automatically expanded paths")
    fun `moving a renamed branch preserves its automatic expansion`() =
        runTest {
            addBasicNote().update { addTag("source") }
            addBasicNote().update { addTag("target") }
            withViewModel(ManageTagsViewModel()) {
                renameTag("source", "source::child").join()
                toggleCollapsed("target").join()
                moveTag("source", "target").join()
                assertNull(content.error)
                assertEquals(listOf("target", "target::source", "target::source::child"), content.visibleTagNames)
            }
        }

    @Test
    @Ignore("Anki Issue 5967 and deferred ancestor reveal: https://github.com/ankitects/anki/issues/5967")
    fun `renaming an expanded branch beneath new ancestors keeps its children visible`() =
        runTest {
            addTags("parent::source::open::leaf", "parent::source::closed::leaf")
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed("parent").join()
                toggleCollapsed("parent::source").join()
                toggleCollapsed("parent::source::open").join()
                renameTag("parent::source", "parent::source::of::topic").join()
                assertNull(content.error)
                assertEquals(
                    listOf(
                        "parent",
                        "parent::source",
                        "parent::source::of",
                        "parent::source::of::topic",
                        "parent::source::of::topic::closed",
                        "parent::source::of::topic::open",
                        "parent::source::of::topic::open::leaf",
                    ),
                    content.visibleTagNames,
                )
            }
        }

    @Test
    @Ignore("Anki Issue 5967: rename drops saved expansion: https://github.com/ankitects/anki/issues/5967")
    fun `renaming an expanded branch preserves expansion through refresh and reopening`() =
        runTest {
            val note = addTags("parent::of::open::leaf", "parent::of::closed::leaf")
            val renamed = listOf("parent", "parent::ofh", "parent::ofh::closed", "parent::ofh::open", "parent::ofh::open::leaf")
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed("parent").join()
                toggleCollapsed("parent::of").join()
                toggleCollapsed("parent::of::open").join()
                ensureOpsExecuted(1) { renameTag("parent::of", "parent::ofh").join() }
                assertEquals(renamed, content.visibleTagNames)
                refreshTags().join()
                assertEquals(renamed, content.visibleTagNames)
                col.undo()
                assertEquals(setOf("parent::of::open::leaf", "parent::of::closed::leaf"), col.getNote(note.id).tags.toSet())
                col.redo()
                assertEquals(setOf("parent::ofh::open::leaf", "parent::ofh::closed::leaf"), col.getNote(note.id).tags.toSet())
            }
            withViewModel(ManageTagsViewModel()) { assertEquals(renamed, content.visibleTagNames) }
        }

    @Test
    @Ignore("Anki Issue 5967: rename drops saved expansion: https://github.com/ankitects/anki/issues/5967")
    fun `rename expansion follows canonical names without recreating unused descendants`() =
        runTest {
            addTags("Straße::open::leaf")
            addUnusedTags("Straße::unused::leaf")
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed("Straße").join()
                toggleCollapsed("Straße::open").join()
                toggleCollapsed("Straße::unused").join()
                renameTag("Straße", "cafe\u0301").join()
                assertNull(content.error)
                assertEquals(listOf("café", "café::open", "café::open::leaf"), content.visibleTagNames)
                assertFalse(tagExists(TagName.asValid("café::unused")))
            }
        }

    @Test
    @Ignore("Deferred UI behavior: renaming does not remap manually collapsed search paths")
    fun `renaming a branch retains explicit collapses within search results`() =
        runTest {
            addTags("parent::of::open::leaf", "parent::of::closed::leaf")
            withViewModel(ManageTagsViewModel()) {
                filter("leaf")
                toggleCollapsed("parent::of::closed").join()
                renameTag("parent::of", "parent::ofh").join()
                assertEquals(
                    listOf("parent", "parent::ofh", "parent::ofh::closed", "parent::ofh::open", "parent::ofh::open::leaf"),
                    content.visibleTagNames,
                )
            }
        }

    @Test
    @Ignore("Anki Issue 5967: rename drops saved expansion: https://github.com/ankitects/anki/issues/5967")
    fun `rename preserves expansion changed since the screen was loaded`() =
        runTest {
            addTags("source::leaf")
            withViewModel(ManageTagsViewModel()) {
                col.tags.setCollapsed("source", false)
                renameTag("source", "renamed").join()
                assertEquals(listOf("renamed", "renamed::leaf"), content.visibleTagNames)
            }
        }

    @Test
    @Ignore("Deferred UI behavior: new rename ancestors are not automatically revealed")
    fun `appending a child to a leaf reveals the renamed tag through refresh and undo`() =
        runTest {
            val note = addTags("parent::source")
            val renamed = listOf("parent", "parent::source", "parent::source::of")
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed("parent").join()
                ensureOpsExecuted(1) { renameTag("parent::source", "parent::source::of").join() }
                assertNull(content.error)
                assertEquals(renamed, content.visibleTagNames)
                refreshTags().join()
                assertEquals(renamed, content.visibleTagNames)
                col.undo()
                assertEquals(listOf("parent::source"), col.getNote(note.id).tags)
                refreshTags().join()
                assertEquals(listOf("parent", "parent::source"), content.visibleTagNames)
                col.redo()
                assertEquals(listOf("parent::source::of"), col.getNote(note.id).tags)
                refreshTags().join()
                assertEquals(renamed, content.visibleTagNames)
            }
        }

    @Test
    @Ignore("Deferred UI behavior: new rename ancestors are not automatically revealed")
    fun `new rename ancestors can be collapsed without reopening on refresh`() =
        runTest {
            addTags("source")
            withViewModel(ManageTagsViewModel()) {
                renameTag("source", "source::of").join()
                assertEquals(listOf("source", "source::of"), content.visibleTagNames)
                toggleCollapsed("source").join()
                assertEquals(listOf("source"), content.visibleTagNames)
                refreshTags().join()
                assertEquals(listOf("source"), content.visibleTagNames)
            }
            withViewModel(ManageTagsViewModel()) {
                assertEquals(listOf("source"), content.visibleTagNames)
                assertTrue(content.visibleNodes.single().collapsed)
            }
        }

    @Test
    fun `merging an expanded source preserves a collapsed destination`() =
        runTest {
            addTags("source::child", "destination::existing")
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed("source").join()
                renameTag("source", "destination").join()
                assertEquals(listOf("destination"), content.visibleTagNames)
                assertTrue(content.visibleNodes.single().collapsed)
            }
        }

    @Test
    fun `merging a collapsed search result preserves an expanded destination`() =
        runTest {
            addTags("source::leaf", "target::leaf")
            withViewModel(ManageTagsViewModel()) {
                filter("leaf")
                toggleCollapsed("source").join()
                renameTag("source", "target").join()
                assertEquals(listOf("target", "target::leaf"), content.visibleTagNames)
            }
        }

    private fun addTags(vararg tags: String) = addBasicNote().update { tags.forEach { addTag(it) } }

    private fun addUnusedTags(vararg names: String) = addTags(*names).update { names.forEach(::removeTag) }

    private val ManageTagsViewModel.content: ManageTagsState.Content
        get() = state.value as ManageTagsState.Content
}
