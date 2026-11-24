// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.lifecycle.ViewModelStore
import androidx.test.filters.MediumTest
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.dialogs.utils.AnKingTags
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.observability.ChangeCounter
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.ensureOpsExecuted
import com.ichi2.anki.observability.undoableOp
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import com.ichi2.testutils.withViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.junit.Rule
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Integration tests for [ManageTagsViewModel] with a real collection */
class ManageTagsViewModelTest : JvmTest() {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    fun `rename can be repeatedly undone and redone without losing tags`() =
        runTest {
            val note = addTags("source::child", "unrelated")
            withViewModel {
                renameTag("source", "source::renamed").join()
                repeat(3) {
                    ensureOpsExecuted(1) { undo().join() }
                    assertEquals(setOf("source::child", "unrelated"), col.getNote(note.id).tags.toSet())
                    assertTrue(tagExists(TagName.asValid("source::child")))
                    assertNotNull(loadedState.redoLabel)

                    ensureOpsExecuted(1) { redo().join() }
                    assertEquals(setOf("source::renamed::child", "unrelated"), col.getNote(note.id).tags.toSet())
                    assertTrue(tagExists(TagName.asValid("source::renamed::child")))
                    assertNotNull(loadedState.undoLabel)
                    assertNull(loadedState.redoLabel)
                }
                undo().join()
                removeTag("source").join()
                assertNull(loadedState.redoLabel)
                val beforeRedo = col.undoStatus()
                redo().join()
                assertEquals(beforeRedo, col.undoStatus())
            }
        }

    @Test
    fun `undo stops before changes made before opening this screen`() =
        runTest {
            val note = addTags("source::child", "earlier")
            col.tags.remove("earlier")
            val historyBefore = col.undoStatus()
            withViewModel {
                assertNull(loadedState.undoLabel)
                undo().join()
                assertEquals(historyBefore, col.undoStatus())

                renameTag("source", "renamed").join()
                removeTag("renamed").join()
                assertEquals(emptyList(), col.getNote(note.id).tags)
                assertNotNull(loadedState.undoLabel)

                ensureOpsExecuted(1) { undo().join() }
                assertEquals(listOf("renamed::child"), col.getNote(note.id).tags)
                assertTrue(tagExists(TagName.asValid("renamed::child")))
                assertNotNull(loadedState.undoLabel)

                undo().join()
                assertEquals(listOf("source::child"), col.getNote(note.id).tags)
                assertNull(loadedState.undoLabel)
                val restored = col.undoStatus()
                undo().join()
                assertEquals(restored, col.undoStatus())
                assertEquals(historyBefore.undo, restored.undo)
                assertEquals(listOf("source::child"), col.getNote(note.id).tags)
                assertNotNull(loadedState.redoLabel)

                redo().join()
                assertEquals(listOf("renamed::child"), col.getNote(note.id).tags)
                redo().join()
                assertEquals(emptyList(), col.getNote(note.id).tags)
                assertNull(loadedState.redoLabel)
            }
        }

    @Test
    fun `undo restores unused tags even when no notes were affected`() =
        runTest {
            addUnusedTags("unused::child")
            withViewModel {
                removeTag("unused").join()
                assertEquals(0, loadedState.totalTagCount)
                assertNotNull(loadedState.undoLabel)
                undo().join()
                assertTrue(tagExists(TagName.asValid("unused::child")))
                assertNull(loadedState.undoLabel)

                clearUnusedTags().join()
                assertEquals(0, loadedState.totalTagCount)
                assertNotNull(loadedState.undoLabel)
                undo().join()
                assertTrue(tagExists(TagName.asValid("unused::child")))
                assertNull(loadedState.undoLabel)
            }
        }

    @Test
    fun `no-op actions do not expose earlier history or consume a local undo`() =
        runTest {
            val note = addTags("used")
            addUnusedTags("unused")
            withViewModel {
                renameTag("unused", "unused::child").join()
                assertNull(loadedState.undoLabel)
                removeTag("used").join()
                renameTag("unused", "unused::child").join()
                removeTag("missing").join()
                moveTag("unused", null).join()
                assertNotNull(loadedState.undoLabel)
                undo().join()
                assertEquals(listOf("used"), col.getNote(note.id).tags)
                assertNull(loadedState.undoLabel)
            }
        }

    @Test
    fun `existing undo and redo stay disabled through navigation and failed actions`() =
        runTest {
            addTags("parent::child")
            col.tags.remove("parent")
            col.undo()
            val historyBefore = col.undoStatus()
            assertNotNull(historyBefore.undo)
            assertNotNull(historyBefore.redo)
            withViewModel {
                assertNull(loadedState.undoLabel)
                assertNull(loadedState.redoLabel)
                undo().join()
                redo().join()
                assertEquals(historyBefore, col.undoStatus())

                toggleCollapsed("parent").join()
                filter("child")
                withLockedCollection { removeTag("parent").join() }
                assertIs<CollectionLockedException>(loadedState.error)
                refreshTags().join()
                assertNull(loadedState.undoLabel)
                assertNull(loadedState.redoLabel)
            }
        }

    @Test
    fun `expanding filtering and refreshing do not consume undo`() =
        runTest {
            val note = addTags("source::child")
            withViewModel {
                renameTag("source", "renamed").join()
                toggleCollapsed("renamed").join()
                filter("child")
                refreshTags().join()
                assertNotNull(loadedState.undoLabel)
                undo().join()
                assertEquals(listOf("source::child"), col.getNote(note.id).tags)
                assertEquals("child", searchQuery.value)
                assertNull(loadedState.undoLabel)
            }
        }

    @Test
    fun `expanding and collapsing update redo availability without exposing earlier undo`() =
        runTest {
            val note = addTags("source", "parent::child")
            withViewModel {
                for (collapsed in listOf(false, true)) {
                    removeTag("source").join()
                    undo().join()
                    assertNotNull(col.undoStatus().redo)
                    assertNotNull(loadedState.redoLabel)

                    toggleCollapsed("parent").join()

                    assertEquals(collapsed, ManageTagsViewModel.flattenTree(col.tags.tree()).first().collapsed)
                    assertNull(col.undoStatus().redo)
                    assertNull(loadedState.redoLabel)
                    assertNotNull(col.undoStatus().undo)
                    assertNull(loadedState.undoLabel)
                    assertEquals(listOf("parent::child", "source"), col.getNote(note.id).tags)
                }
            }
        }

    @Test
    fun `another caller changing history permits shared undo and redo including earlier actions`() =
        runTest {
            val note = addTags("first", "second", "third", "earlier")
            col.tags.remove("earlier")
            withViewModel {
                removeTag("first").join()
                col.tags.remove("second")
                undo().join()
                assertEquals(listOf("second", "third"), col.getNote(note.id).tags)
                assertNotNull(loadedState.undoLabel)
                assertNotNull(loadedState.redoLabel)

                redo().join()
                assertEquals(listOf("third"), col.getNote(note.id).tags)
                undo().join()
                undo().join()
                assertEquals(listOf("first", "second", "third"), col.getNote(note.id).tags)
                undo().join()
                assertEquals(setOf("first", "second", "third", "earlier"), col.getNote(note.id).tags.toSet())
            }
        }

    @Test
    fun `external change notifications keep mixed actions undoable`() =
        runTest {
            val note = addTags("first", "second")
            withViewModel {
                removeTag("first").join()
                undoableOp { tags.remove("second") }
                assertNotNull(loadedState.undoLabel)
                assertEquals(0, loadedState.totalTagCount)
                undo().join()
                assertEquals(listOf("second"), col.getNote(note.id).tags)
                undo().join()
                assertEquals(listOf("first", "second"), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `batched API note updates can be undone alongside tag actions`() =
        runTest {
            val note = addTags("source")
            val otherNote = addBasicNote()
            val originalText = col.getNote(note.id).fields[0]
            val otherOriginalText = col.getNote(otherNote.id).fields[0]
            withViewModel {
                assertNull(loadedState.undoLabel)
                // The content provider can make several backend changes before publishing one notification.
                col.getNote(note.id).also {
                    it.setField(0, "First API update")
                    assertTrue(col.updateNote(it).note)
                }
                col.getNote(otherNote.id).also {
                    it.setField(0, "Second API update")
                    assertTrue(col.updateNote(it).note)
                }
                ChangeManager.notifySubscribersAllValuesChanged()
                assertNotNull(loadedState.undoLabel)

                removeTag("source").join()
                undo().join()
                assertEquals(listOf("source"), col.getNote(note.id).tags)
                undo().join()
                assertEquals("First API update", col.getNote(note.id).fields[0])
                assertEquals(otherOriginalText, col.getNote(otherNote.id).fields[0])
                undo().join()
                assertEquals(originalText, col.getNote(note.id).fields[0])
                redo().join()
                redo().join()
                assertEquals("First API update", col.getNote(note.id).fields[0])
                assertEquals("Second API update", col.getNote(otherNote.id).fields[0])
                redo().join()
                assertEquals(emptyList(), col.getNote(note.id).tags)
                assertNull(loadedState.redoLabel)
            }
        }

    @Test
    fun `a notification without a history change keeps the screen boundary`() =
        runTest {
            val note = addTags("source")
            withViewModel {
                removeTag("source").join()
                ChangeManager.notifySubscribersAllValuesChanged()
                undo().join()
                assertEquals(listOf("source"), col.getNote(note.id).tags)
                assertNull(loadedState.undoLabel)
                val history = col.undoStatus()
                undo().join()
                assertEquals(history, col.undoStatus())
            }
        }

    @Test
    fun `reopening starts a new undo scope`() =
        runTest {
            val note = addTags("source")
            withViewModel { removeTag("source").join() }
            val before = col.undoStatus()
            withViewModel {
                assertNull(loadedState.undoLabel)
                undo().join()
                assertEquals(before, col.undoStatus())
                assertEquals(emptyList(), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `undo can be retried after collection access fails`() =
        runTest {
            val note = addTags("source")
            withViewModel {
                removeTag("source").join()
                withLockedCollection { undo().join() }
                assertIs<CollectionLockedException>(loadedState.error)
                undo().join()
                assertNull(loadedState.error)
                assertNull(loadedState.undoLabel)
                assertEquals(listOf("source"), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `rename availability includes used descendants and does not change the collection`() =
        runTest {
            addTags("parent::used", "abc", "a1", "a.b")
            addUnusedTags("parent::unused", "empty::child", "none", "a*", "a?", "a[b]")
            val before = col.undoStatus()
            withViewModel {
                for (name in listOf("parent", "parent::used", "a.b")) {
                    assertEquals(true, canRenameTag(TagName.asValid(name)), name)
                }
                for (name in listOf("parent::unused", "empty", "empty::child", "none", "a*", "a?", "a[b]", "missing")) {
                    assertEquals(false, canRenameTag(TagName.asValid(name)), name)
                }
                assertEquals(before, col.undoStatus())
                assertTrue(pendingMessages.value.isEmpty())
            }
        }

    @Test
    fun `rename availability failure reports an error instead of claiming the tag is unused`() =
        runTest {
            addTags("used")
            withViewModel {
                withLockedCollection { assertNull(canRenameTag(TagName.asValid("used"))) }
                assertIs<CollectionLockedException>(loadedState.error)
                refreshTags().join()
                assertEquals(true, canRenameTag(TagName.asValid("used")))
                assertNull(loadedState.error)
            }
        }

    @Test
    fun `successful rename availability check clears an earlier check failure`() =
        runTest {
            addTags("used")
            addUnusedTags("unused")
            withViewModel {
                for ((name, expected) in listOf("used" to true, "unused" to false)) {
                    val tag = TagName.asValid(name)
                    withLockedCollection { assertNull(canRenameTag(tag)) }
                    assertIs<CollectionLockedException>(loadedState.error)
                    assertEquals(expected, canRenameTag(tag), name)
                    assertNull(loadedState.error, name)
                }
            }
        }

    @Test
    fun `row equality preserves parent spelling while tag identity ignores case`() {
        val original = tagListItem("parent::child", "child", 1, hasChildren = false, collapsed = false)
        val renamed = original.copy(fullTagName = "PARENT::child")

        assertEquals(original.fullTag, renamed.fullTag)
        assertEquals(original.displayName, renamed.displayName)
        assertNotEquals(original, renamed)
        assertEquals("PARENT::child", renamed.fullTag.value)
    }

    @Test
    fun `tagExists follows backend identity for hidden Unicode tags`() =
        runTest {
            addTags("i", "Straße::café")
            withViewModel {
                for (query in listOf("", "missing")) {
                    filter(query)
                    assertTrue(tagExists(TagName.asValid("I")))
                    assertTrue(!tagExists(TagName.asValid("ı")))
                    assertTrue(tagExists(TagName.asValid("STRASSE::cafe\u0301")))
                }
            }
        }

    @Test
    fun `move uses backend identity to reject its own subtree`() =
        runTest {
            for ((source, parent) in listOf(
                "café" to "cafe\u0301",
                "café" to "cafe\u0301::child",
                "Straße" to "STRASSE",
                "Straße" to "STRASSE::child",
            )) {
                val note = addTags(source)
                val undoBefore = col.undoStatus()
                withViewModel {
                    moveTag(source, parent).join()
                    assertIs<IllegalArgumentException>(loadedState.error, "$source / $parent")
                    assertEquals(listOf(source), col.getNote(note.id).tags)
                    assertEquals(undoBefore, col.undoStatus())
                }
            }
        }

    @Test
    fun `move uses backend identity to distinguish Turkish parents`() =
        runTest {
            val note = addTags("i::child")
            addTags("ı")
            withViewModel {
                moveTag("i::child", "ı").join()
                assertEquals(null, loadedState.error)
                assertEquals(listOf("ı::child"), col.getNote(note.id).tags)
                col.undo()
                assertEquals(listOf("i::child"), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `move uses backend identity to detect an unchanged parent`() =
        runTest {
            val note = addTags("Straße::child")
            val undoBefore = col.undoStatus()
            withViewModel {
                moveTag("Straße::child", "STRASSE").join()
                assertEquals(null, loadedState.error)
                assertEquals(listOf("Straße::child"), col.getNote(note.id).tags)
                assertEquals(undoBefore, col.undoStatus())
                assertEquals(UserMessage.TagMoved(0), pendingMessages.value.single().message)
            }
        }

    @Test
    fun `renaming preserves unrelated backend distinct ligature tags`() =
        runTest {
            val original = setOf("source", "ffi", "ﬃ")
            val note = addTags(*original.toTypedArray())
            val unrelated = addTags("ffi", "ﬃ")
            val untouched = col.backend.getNote(unrelated.id)
            assertEquals(3, col.getNote(note.id).tags.size)
            withViewModel {
                ensureOpsExecuted(1) { renameTag("source", "renamed").join() }
                val expected = setOf("renamed", "ffi", "ﬃ")
                assertEquals(expected, col.getNote(note.id).tags.toSet())
                assertEquals(3, col.getNote(note.id).tags.size)
                assertEquals(untouched, col.backend.getNote(unrelated.id))
                col.undo()
                assertEquals(original, col.getNote(note.id).tags.toSet())
                col.redo()
                assertEquals(expected, col.getNote(note.id).tags.toSet())
            }
        }

    @Test
    fun `merging deduplicates ligatures using backend identity`() =
        runTest {
            val note = addTags("source", "iff")
            withViewModel {
                ensureOpsExecuted(1) { renameTag("source", "ﬃ").join() }
                assertEquals(listOf("iff"), col.getNote(note.id).tags)
                col.undo()
                assertEquals(setOf("source", "iff"), col.getNote(note.id).tags.toSet())
                col.redo()
                assertEquals(listOf("iff"), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `merge deduplicates using backend tag identity`() =
        runTest {
            val note = addBasicNote().update { listOf("source", "STRASSE", "i", "ı").forEach(::addTag) }
            withViewModel {
                ensureOpsExecuted(1) { renameTag("source", "Straße").join() }
                assertEquals(null, loadedState.error)
                assertThat(col.getNote(note.id).tags, containsInAnyOrder("STRASSE", "i", "ı"))
                col.undo()
                assertThat(col.getNote(note.id).tags, containsInAnyOrder("source", "STRASSE", "i", "ı"))
                col.redo()
                assertThat(col.getNote(note.id).tags, containsInAnyOrder("STRASSE", "i", "ı"))
            }
        }

    @Test
    fun `refreshTags loads empty collection`() =
        runTest {
            withViewModel {
                assertIs<ManageTagsState.Content>(state.value)
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `refreshTags loads tags from notes`() =
        runTest {
            addTags("science")
            withViewModel {
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `refreshTags loads hierarchical tags`() =
        runTest {
            addTags("science::biology", "science::chemistry")
            withViewModel {
                // "science" parent is collapsed by default, so only it should be visible
                assertThat(loadedState.visibleNodes, hasSize(1))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[0].hasChildren, equalTo(true))
            }
        }

    @Test
    fun `refreshTags loads multiple tags from multiple notes`() =
        runTest {
            addTags("math")
            addTags("history")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(
                    loadedState.visibleTagNames,
                    containsInAnyOrder("history", "math"),
                )
            }
        }

    @Test
    fun `refreshTags preserves search query`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                filter("sci")
                refreshTags()
                assertThat(searchQuery.value, equalTo("sci"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `removeTag keeps existing content visible until completion`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                val previousTags = loadedState.visibleNodes
                withQueuedCollectionAccess {
                    val deletion = removeTag("science")
                    assertThat(loadedState.isWorking, equalTo(true))
                    assertThat(loadedState.visibleNodes, equalTo(previousTags))

                    deletion.join()

                    assertThat(loadedState.isWorking, equalTo(false))
                    assertThat(loadedState.error, nullValue())
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
                }
            }
        }

    @Test
    fun `operations requested while busy do not mutate tags`() =
        runTest {
            addTags("science::biology", "history")
            addUnusedTags("unused")
            withViewModel {
                withQueuedCollectionAccess {
                    val refresh = refreshTags()
                    assertThat(refresh.isCompleted, equalTo(false))

                    assertThat(removeTag("science"), sameInstance(refresh))
                    assertThat(renameTag("history", "past"), sameInstance(refresh))
                    assertThat(clearUnusedTags(), sameInstance(refresh))
                    assertThat(toggleCollapsed("science"), sameInstance(refresh))
                    assertThat(moveTag("history", "science"), sameInstance(refresh))
                    assertThat(refreshTags(), sameInstance(refresh))

                    refresh.join()
                }
                assertThat(loadedState.visibleTagNames, containsInAnyOrder("science", "history", "unused"))
                assertThat(loadedState.visibleNodes.single { it.fullTagName == "science" }.collapsed, equalTo(true))

                removeTag("science").join()
                assertThat(loadedState.visibleTagNames, containsInAnyOrder("history", "unused"))
            }
        }

    @Test
    fun `search updates visible content while refresh is running`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                withQueuedCollectionAccess {
                    val refresh = refreshTags()
                    filter("hist")

                    assertThat(loadedState.isWorking, equalTo(true))
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))

                    refresh.join()
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
                }
            }
        }

    @Test
    fun `operation failure preserves content and retry clears error`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                val previousTags = loadedState.visibleNodes
                withLockedCollection {
                    removeTag("science").join()
                }

                assertThat(loadedState.visibleNodes, equalTo(previousTags))
                assertThat(loadedState.isWorking, equalTo(false))
                assertThat(loadedState.error, instanceOf(CollectionLockedException::class.java))

                withQueuedCollectionAccess {
                    val retry = removeTag("science")
                    assertThat(loadedState.isWorking, equalTo(true))
                    assertThat(loadedState.error, nullValue())

                    retry.join()
                    assertThat(loadedState.isWorking, equalTo(false))
                    assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
                }
            }
        }

    @Test
    fun `initial load failure allows search input and retry`() =
        runTest {
            addTags("science", "history")
            withViewModel(withLockedCollection { ManageTagsViewModel() }) {
                val failure = state.value
                assertThat(failure, instanceOf(ManageTagsState.Error::class.java))
                assertThat((failure as ManageTagsState.Error).error, instanceOf(CollectionLockedException::class.java))

                filter("hist")
                assertThat(searchQuery.value, equalTo("hist"))

                withQueuedCollectionAccess {
                    val retry = refreshTags()
                    assertThat(state.value, equalTo(ManageTagsState.Loading))
                    retry.join()
                }
                assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
            }
        }

    @Test
    fun `cancelled operation clears progress and permits another operation`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                val previous = loadedState
                withQueuedCollectionAccess {
                    val deletion = removeTag("science")
                    assertThat(loadedState.isWorking, equalTo(true))

                    deletion.cancelAndJoin()

                    assertThat(loadedState, equalTo(previous))
                    val refresh = refreshTags()
                    assertThat(loadedState.isWorking, equalTo(true))
                    refresh.join()
                    assertThat(loadedState, equalTo(previous))
                }
            }
        }

    @Test
    fun `closing after a tag deletion commits still notifies collection observers`() =
        runTest {
            val note = addTags("source")
            val collection = col
            val model = ManageTagsViewModel()
            val store = ViewModelStore().apply { put("tags", model) }
            val observer = ChangeCounter()
            ChangeManager.subscribe(observer)
            val backendScheduler = TestCoroutineScheduler()
            val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(backendScheduler), useReentrantLock = false)
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val deletion = model.removeTag("source")
                runCurrent()
                backendScheduler.runCurrent()
                assertEquals(emptyList(), collection.getNote(note.id).tags)
                assertEquals(0, observer.changeCount)

                store.clear()
                runCurrent()

                assertTrue(deletion.isCancelled)
                assertEquals(1, observer.changeCount, "The browser must be notified about the committed deletion after navigation")
            } finally {
                store.clear()
                CollectionManager.setTestDispatcher(previousQueue)
                Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
                ChangeManager.unsubscribe(observer)
            }
        }

    @Test
    fun `initial state is Loaded after construction`() =
        runTest {
            withViewModel {
                assertIs<ManageTagsState.Content>(state.value)
            }
        }

    @Test
    fun `filter narrows visible tags`() =
        runTest {
            addTags("science", "history", "math")
            withViewModel {
                filter("sci")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
                assertThat(searchQuery.value, equalTo("sci"))
            }
        }

    @Test
    fun `filter updates query in an empty collection`() =
        runTest {
            withViewModel {
                filter("sci")
                assertThat(searchQuery.value, equalTo("sci"))
                assertThat(loadedState.visibleNodes, hasSize(0))

                addTags("science", "history")
                refreshTags()
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
            }
        }

    @Test
    fun `filter during initial loading applies latest query when tags arrive`() =
        runTest {
            addTags("science", "history")
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            withViewModel {
                assertThat(state.value, equalTo(ManageTagsState.Loading))
                filter("sci")
                filter("hist")
                assertThat(searchQuery.value, equalTo("hist"))

                runCurrent()

                assertThat(searchQuery.value, equalTo("hist"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
            }
        }

    @Test
    fun `filter during refresh is not overwritten when refresh completes`() =
        runTest {
            addTags("science", "history")
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            withViewModel {
                runCurrent()
                filter("sci")
                val refresh = refreshTags()
                filter("hist")
                assertThat(searchQuery.value, equalTo("hist"))

                refresh.join()

                assertThat(searchQuery.value, equalTo("hist"))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("history")))
            }
        }

    @Test
    fun `filter can be cleared after deleting the last tag`() =
        runTest {
            addTags("science")
            withViewModel {
                filter("sci")
                removeTag("science")
                filter("")

                assertThat(searchQuery.value, equalTo(""))
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `filter with empty query shows all tags`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                filter("sci")
                assertThat(loadedState.visibleNodes, hasSize(1))

                filter("")
                assertThat(loadedState.visibleNodes, hasSize(2))
            }
        }

    @Test
    fun `search preserves collapsed matches and allows manual expansion`() =
        runTest {
            addTags("a::b::c::d", "other")
            withViewModel {
                filter("a")
                assertEquals(listOf("a"), loadedState.visibleTagNames)
                assertTrue(loadedState.visibleNodes.first().collapsed)

                toggleCollapsed("a").join()
                assertEquals(listOf("a", "a::b"), loadedState.visibleTagNames)
                assertTrue(loadedState.visibleNodes[1].collapsed)

                filter("c")
                assertEquals(listOf("a", "a::b", "a::b::c"), loadedState.visibleTagNames)

                filter("")
                assertEquals(listOf("a", "a::b", "other"), loadedState.visibleTagNames)
                assertTrue(loadedState.visibleNodes[1].collapsed)

                filter("a")
                toggleCollapsed("a").join()
                assertEquals(listOf("a"), loadedState.visibleTagNames)
                refreshTags().join()
                assertEquals(listOf("a"), loadedState.visibleTagNames)
            }
        }

    @Test
    fun `search-expanded ancestors can collapse and expand regardless of saved state`() =
        runTest {
            addTags("parent::child")
            for (initiallyCollapsed in listOf(true, false)) {
                col.tags.setCollapsed(TagName.asValid("parent"), initiallyCollapsed)
                withViewModel {
                    filter("child")
                    assertFalse(loadedState.visibleNodes.first().collapsed)

                    toggleCollapsed("PARENT").join()
                    assertEquals(listOf("parent"), loadedState.visibleTagNames)
                    assertTrue(loadedState.visibleNodes.single().collapsed)
                    filter("child")
                    refreshTags().join()
                    assertEquals(listOf("parent"), loadedState.visibleTagNames)
                    assertTrue(ManageTagsViewModel.flattenTree(col.tags.tree()).first().collapsed)

                    toggleCollapsed("parent").join()
                    assertEquals(listOf("parent", "parent::child"), loadedState.visibleTagNames)
                    assertFalse(loadedState.visibleNodes.first().collapsed)
                    assertFalse(ManageTagsViewModel.flattenTree(col.tags.tree()).first().collapsed)
                }
            }
        }

    @Test
    fun `changing search reveals matches beneath manually collapsed ancestors`() =
        runTest {
            addTags("parent::child::leaf")
            withViewModel {
                filter("leaf")
                toggleCollapsed("parent::child").join()
                assertEquals(listOf("parent", "parent::child"), loadedState.visibleTagNames)
                assertTrue(loadedState.visibleNodes.last().collapsed)

                filter("lea")
                assertEquals(listOf("parent", "parent::child", "parent::child::leaf"), loadedState.visibleTagNames)
                assertFalse(loadedState.visibleNodes[1].collapsed)

                filter("")
                assertEquals(listOf("parent"), loadedState.visibleTagNames)
                toggleCollapsed("parent").join()
                assertEquals(listOf("parent", "parent::child"), loadedState.visibleTagNames)
                assertTrue(loadedState.visibleNodes.last().collapsed)
            }
        }

    @Test
    fun `filter shows ancestors of matching nested tags`() =
        runTest {
            addTags("science::biology", "history")
            withViewModel {
                filter("bio")
                // "science" (ancestor) + "science::biology" (match)
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[1].fullTagName, equalTo("science::biology"))
            }
        }

    @Test
    fun `filter with no matches returns empty`() =
        runTest {
            addTags("science")
            withViewModel {
                filter("zzz")
                assertThat(loadedState.visibleNodes, hasSize(0))
            }
        }

    @Test
    fun `expansion is immediate and rapid taps persist in order without blocking actions`() =
        runTest {
            addTags("science::biology", "history::ancient")
            withViewModel {
                withQueuedCollectionAccess {
                    val expand = toggleCollapsed("science")
                    assertFalse(expand.isCompleted)
                    assertFalse(loadedState.isWorking)
                    assertTrue("science::biology" in loadedState.visibleTagNames)

                    val collapse = toggleCollapsed("science")
                    assertFalse(collapse.isCompleted)
                    assertFalse(loadedState.isWorking)
                    assertFalse("science::biology" in loadedState.visibleTagNames)

                    toggleCollapsed("history")
                    assertTrue("history::ancient" in loadedState.visibleTagNames)
                    // Reloading must wait for every queued preference save.
                    refreshTags().join()
                }
                assertEquals(listOf("history", "history::ancient", "science"), loadedState.visibleTagNames)
                assertFalse(loadedState.isWorking)
            }
        }

    @Test
    fun `closing the screen keeps queued expansion saves ahead of a reopened screen`() =
        runTest {
            addTags("science::biology", "history::ancient")
            val closing = ManageTagsViewModel()
            withQueuedCollectionAccess {
                withViewModel(closing) {
                    toggleCollapsed("science")
                    toggleCollapsed("history")
                    toggleCollapsed("history")
                    assertEquals(listOf("history", "science", "science::biology"), loadedState.visibleTagNames)
                }
                withViewModel {
                    refreshTags().join()
                    assertEquals(listOf("history", "science", "science::biology"), loadedState.visibleTagNames)
                }
            }
        }

    @Test
    fun `completed expansion saves survive closing and reopening the screen`() =
        runTest {
            addTags("science::biology")
            withViewModel { toggleCollapsed("science").join() }
            withViewModel {
                assertEquals(listOf("science", "science::biology"), loadedState.visibleTagNames)
                assertFalse(loadedState.visibleNodes.first().collapsed)
            }
        }

    @Test
    fun `deleting a tag waits for expansion saves so they cannot recreate it`() =
        runTest {
            addTags("science::biology", "history")
            withViewModel {
                withQueuedCollectionAccess {
                    toggleCollapsed("science")
                    toggleCollapsed("science")
                    removeTag("science").join()
                }
                assertEquals(listOf("history"), loadedState.visibleTagNames)
                assertEquals(listOf("history"), col.tags.all())
            }
        }

    @Test
    fun `failed expansion save retains the visible hierarchy and reports the error`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                withLockedCollection { toggleCollapsed("science").join() }
                assertEquals(listOf("science", "science::biology"), loadedState.visibleTagNames)
                assertFalse(loadedState.isWorking)
                assertIs<CollectionLockedException>(loadedState.error)
                toggleCollapsed("science").join()
                assertNull(loadedState.error)
                refreshTags().join()
                assertEquals(listOf("science"), loadedState.visibleTagNames)
            }
        }

    @Test
    fun `toggleCollapsed expands a collapsed tag`() =
        runTest {
            addTags("science::biology", "science::chemistry")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(1))

                toggleCollapsed("science")

                assertThat(loadedState.visibleNodes, hasSize(3))
                assertThat(loadedState.visibleNodes[0].fullTagName, equalTo("science"))
                assertThat(loadedState.visibleNodes[0].collapsed, equalTo(false))
                assertThat(loadedState.visibleNodes[1].fullTagName, equalTo("science::biology"))
                assertThat(loadedState.visibleNodes[2].fullTagName, equalTo("science::chemistry"))
            }
        }

    @Test
    fun `toggleCollapsed collapses an expanded tag`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                toggleCollapsed("science")
                assertThat(loadedState.visibleNodes, hasSize(2))

                toggleCollapsed("science")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("science")))
                assertThat(loadedState.visibleNodes[0].collapsed, equalTo(true))
            }
        }

    @Test
    fun `expanding one distinct root leaves the other collapsed`() =
        runTest {
            addBasicNote().update {
                addTag("ffi::child")
                addTag("ﬃ::child")
            }
            withViewModel {
                assertThat(loadedState.visibleTagNames, equalTo(listOf("ffi", "ﬃ")))

                toggleCollapsed("ﬃ").join()

                assertThat(loadedState.visibleTagNames, equalTo(listOf("ffi", "ﬃ", "ﬃ::child")))
            }
        }

    @Test
    fun `toggleCollapsed on unknown tag queues UnexpectedError`() =
        runTest {
            addTags("science")
            withViewModel {
                toggleCollapsed("nonexistent")
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.UnexpectedError))
            }
        }

    @Test
    fun `removeTag removes a single tag`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                removeTag("science")
            }
            checkCollectionTags { tags ->
                assertThat(tags, not(hasItem("science")))
                assertThat(tags, hasItem("history"))
            }
        }

    @Test
    fun `removeTag removes tag and children`() =
        runTest {
            addTags("science::biology", "science::chemistry", "history")
            withViewModel {
                removeTag("science")
            }
            checkCollectionTags { tags ->
                assertThat(tags, not(hasItem("science::biology")))
                assertThat(tags, not(hasItem("science::chemistry")))
                assertThat(tags, hasItem("history"))
            }
        }

    @Test
    fun `renameTag updates tag name`() =
        runTest {
            addTags("science")
            withViewModel {
                renameTag("science", "physics")
            }
            checkCollectionTags { tags ->
                assertThat(tags, hasItem("physics"))
                assertThat(tags, not(hasItem("science")))
            }
        }

    @Test
    fun `renameTag updates hierarchical tags`() =
        runTest {
            addTags("science::biology")
            withViewModel {
                renameTag("science", "studies")
            }
            checkCollectionTags { tags ->
                assertThat(tags, hasItem("studies::biology"))
                assertThat(tags, not(hasItem("science::biology")))
            }
        }

    @Test
    fun `merge removes duplicates according to backend identity`() =
        runTest {
            val note =
                addBasicNote().update {
                    addTag("source")
                    addTag("ﬃ")
                }
            assertEquals(1, col.db.queryScalar("select ? = ? collate unicase", "ﬃ", "iff"))
            withViewModel {
                renameTag("source", "iff").join()

                assertEquals(1, col.getNote(note.id).tags.size)
            }
        }

    @Test
    fun `unrelated rename preserves backend-distinct tags`() =
        runTest {
            val note =
                addBasicNote().update {
                    addTag("source")
                    addTag("ffi")
                    addTag("ﬃ")
                }
            assertEquals(setOf("source", "ffi", "ﬃ"), col.getNote(note.id).tags.toSet())

            withViewModel {
                renameTag("source", "renamed").join()

                assertEquals(setOf("renamed", "ffi", "ﬃ"), col.getNote(note.id).tags.toSet())
            }
        }

    @Test
    fun `clearUnusedTags removes tags not on any note`() =
        runTest {
            addTags("used")
            addUnusedTags("unused")
            withViewModel {
                assertThat(loadedState.visibleTagNames, hasItem("unused"))

                clearUnusedTags()

                assertThat(loadedState.visibleTagNames, equalTo(listOf("used")))
            }
        }

    @Test
    fun `tag totals include hidden descendants and stay stable during search`() =
        runTest {
            addTags("parent::child::leaf", "unrelated")
            withViewModel {
                assertThat(loadedState.visibleNodes, hasSize(2))
                assertThat(loadedState.totalTagCount, equalTo(4))
                filter("leaf")
                assertThat(loadedState.visibleNodes, hasSize(3))
                assertThat(loadedState.totalTagCount, equalTo(4))
                filter("absent")
                assertThat(loadedState.visibleNodes, hasSize(0))
                assertThat(loadedState.totalTagCount, equalTo(4))
                removeTag("parent").join()
                assertThat(loadedState.totalTagCount, equalTo(1))
            }
        }

    @Test
    fun `moveTag updates descendants on notes and reports affected notes`() =
        runTest {
            val first = addTags("source::child", "unrelated")
            val second = addTags("source", "source::other")
            withViewModel {
                moveTag("source", "target").join()

                assertThat(col.getNote(first.id).tags, containsInAnyOrder("target::source::child", "unrelated"))
                assertThat(col.getNote(second.id).tags, containsInAnyOrder("target::source", "target::source::other"))
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagMoved(2)))
                assertThat(loadedState.visibleTagNames, containsInAnyOrder("target", "unrelated"))
                assertThat(loadedState.totalTagCount, equalTo(5))
            }
        }

    @Test
    fun `moveTag to the top level preserves descendants and siblings`() =
        runTest {
            val note = addTags("parent::topic::child", "parent::sibling")
            withViewModel {
                moveTag("parent::topic", null).join()

                assertThat(col.getNote(note.id).tags, containsInAnyOrder("topic::child", "parent::sibling"))
                assertThat(loadedState.visibleTagNames, containsInAnyOrder("parent", "topic"))
            }
        }

    @Test
    fun `moveTag emits changes and can be undone`() =
        runTest {
            val note = addTags("source::child")
            withViewModel {
                ensureOpsExecuted(1) { moveTag("source", "target").join() }
                assertThat(col.getNote(note.id).tags, equalTo(listOf("target::source::child")))

                col.undo()
                refreshTags().join()

                assertThat(col.getNote(note.id).tags, equalTo(listOf("source::child")))
                assertThat(loadedState.visibleTagNames, equalTo(listOf("source")))
            }
        }

    @Test
    fun `clearUnusedTags queues message with count`() =
        runTest {
            addTags("used")
            addUnusedTags("unused")
            withViewModel {
                clearUnusedTags()
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.ClearedUnusedTags(1)))
            }
        }

    @Test
    fun `removeTag queues message with affected note count`() =
        runTest {
            // add 'science to 2 notes - ensure that the return value is the notes affected
            addTags("science")
            addTags("science")
            withViewModel {
                removeTag("science")
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagRemoved(2)))
            }
        }

    @Test
    fun `renameTag queues message with affected note count`() =
        runTest {
            // add 'science to 2 notes - ensure that the return value is the notes affected
            addTags("science")
            addTags("science")
            withViewModel {
                renameTag("science", "physics")
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagRenamed(2)))
            }
        }

    @Test
    fun `messageShown removes the displayed message`() =
        runTest {
            addTags("science")
            withViewModel {
                removeTag("science").join()

                messageShown(pendingMessages.value.single().id)

                assertThat(pendingMessages.value, hasSize(0))
            }
        }

    @Test
    fun `pending messages survive refresh failures and retries`() =
        runTest {
            addTags("science", "history")
            withViewModel {
                removeTag("science").join()
                val pending = pendingMessages.value
                withLockedCollection {
                    refreshTags().join()
                }
                assertThat(loadedState.error, instanceOf(CollectionLockedException::class.java))
                assertThat(pendingMessages.value, equalTo(pending))

                refreshTags().join()
                filter("hist")
                assertThat(loadedState.error, nullValue())
                assertThat(pendingMessages.value, equalTo(pending))
            }
        }

    @Test
    fun `removeTag fires opChanges`() =
        runTest {
            addTags("science")
            ensureOpsExecuted(1) {
                withViewModel { removeTag("science") }
            }
        }

    @Test
    fun `renameTag fires opChanges`() =
        runTest {
            addTags("science")
            ensureOpsExecuted(1) {
                withViewModel { renameTag("science", "physics") }
            }
        }

    @Test
    fun `clearUnusedTags fires opChanges`() =
        runTest {
            addTags("used")
            addUnusedTags("unused")
            ensureOpsExecuted(1) {
                withViewModel { clearUnusedTags() }
            }
        }

    @Test
    @MediumTest
    fun `toggleCollapsed performance with AnKing tags`() =
        runTest {
            val expected = 1.seconds

            fun ManageTagsViewModel.toggleOnOff(tag: String) {
                toggleCollapsed(tag)
                toggleCollapsed(tag)
            }

            val tags = setupAnKing()

            withViewModel {
                val hugeTag = "#AK_Step1_v11" // 10k+ child tags

                // warm up
                toggleOnOff(hugeTag)

                val iterations = 1
                val elapsed =
                    measureTimeMillis {
                        repeat(iterations) {
                            toggleOnOff(hugeTag)
                        }
                    }

                // ~55ms on my M1
                // ~342ms on a Ubuntu CI runner
                val avgMs = (elapsed.toDouble() / (iterations * 2)).milliseconds
                println("toggleCollapsed: ${tags.size} tags, avg ${avgMs}ms over ${iterations * 2} toggles")
                assertTrue(avgMs < expected, "toggleCollapsed took ${avgMs}ms on average, expected < $expected")
            }
        }

    @Test
    fun `rename follows backend removal of unused descendants and remains undoable`() =
        runTest {
            val note = addBasicNote().update { addTag("source::used") }
            addUnusedTags("source", "source::unused", "source::unused::leaf")
            withViewModel {
                val before = col.tags.all().toSet()
                ensureOpsExecuted(1) { renameTag("source", "renamed").join() }
                assertThat(col.tags.all(), equalTo(listOf("renamed::used")))
                assertThat(col.getNote(note.id).tags, equalTo(listOf("renamed::used")))
                col.undo()
                assertThat(col.tags.all().toSet(), equalTo(before))
                assertThat(col.getNote(note.id).tags, equalTo(listOf("source::used")))
                col.redo()
                assertThat(col.tags.all(), equalTo(listOf("renamed::used")))
            }
        }

    @Test
    fun `renaming an unused subtree preserves tags and the previous undo action`() =
        runTest {
            val note = addUnusedTags("unused", "unused::child")
            withViewModel {
                val before = col.tags.all().toSet()
                val undoBefore = col.undoStatus()
                renameTag("UNUSED", "renamed").join()
                assertThat(col.tags.all().toSet(), equalTo(before))
                assertEquals(undoBefore.undo, col.undoStatus().undo)
                assertEquals(undoBefore.redo, col.undoStatus().redo)
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagRenamed(0)))
                col.undo()
                assertEquals(before, col.getNote(note.id).tags.toSet())
                col.redo()
                assertThat(col.getNote(note.id).tags, hasSize(0))
            }
        }

    /**
     * Detects a fix for [Anki issue 5870](https://github.com/ankitects/anki/issues/5870) so
     * [UserMessage.UnusedTagNotMoved] and its handling can be removed. Call native reparent directly
     * because [moveTagTree] currently implements moves using rename.
     */
    @Test
    fun `backend reparent still requires UnusedTagNotMoved`() =
        runTest {
            val sourceTags = setOf("parent::child", "parent::child::leaf")
            addUnusedTags(*sourceTags.toTypedArray())
            assertEquals(sourceTags, col.tags.all().toSet())

            col.tags.reparent(listOf("parent::child"), "target")

            assertEquals(
                sourceTags,
                col.tags.all().toSet(),
                "Anki issue 5870 changed: update moveTagTree to use the fixed backend operation " +
                    "and remove ${UserMessage.UnusedTagNotMoved}.",
            )
        }

    @Test
    fun `moving an unused subtree preserves tags and the previous undo action`() =
        runTest {
            val note = addUnusedTags("parent", "parent::child", "parent::child::leaf", "parent::sibling")
            withViewModel {
                val before = col.tags.all().toSet()
                val undoBefore = col.undoStatus()
                for (destination in listOf("target", null)) {
                    moveTag("parent::child", destination).join()
                    assertThat(col.tags.all().toSet(), equalTo(before))
                    assertEquals(undoBefore.undo, col.undoStatus().undo)
                    assertEquals(undoBefore.redo, col.undoStatus().redo)
                    val pending = pendingMessages.value.single()
                    assertThat(pending.message, equalTo(UserMessage.UnusedTagNotMoved))
                    messageShown(pending.id)
                }
                col.undo()
                assertEquals(before, col.getNote(note.id).tags.toSet())
                col.redo()
                assertThat(col.getNote(note.id).tags, hasSize(0))
            }
        }

    @Test
    fun `moving a tag onto an existing subtree deduplicates notes and is undoable`() =
        runTest {
            val original = listOf("source", "source::child", "target::source", "target::source::child", "unrelated")
            val note = addBasicNote().update { original.forEach(::addTag) }
            withViewModel {
                ensureOpsExecuted(1) { moveTag("source", "target").join() }
                assertThat(col.getNote(note.id).tags, containsInAnyOrder("target::source", "target::source::child", "unrelated"))
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagMoved(1)))
                col.undo()
                assertThat(col.getNote(note.id).tags, containsInAnyOrder(*original.toTypedArray()))
                col.redo()
                assertThat(col.getNote(note.id).tags, containsInAnyOrder("target::source", "target::source::child", "unrelated"))
            }
        }

    @Test
    fun `move to a sibling whose name starts with the source preserves notes`() =
        runTest {
            val note = addBasicNote().update { addTag("parent::a::child") }
            withViewModel {
                moveTag("parent::a", "parent::ab").join()
                assertThat(col.getNote(note.id).tags, equalTo(listOf("parent::ab::a::child")))
            }
        }

    @Test
    fun `merge uses literal tag names and one undo step`() =
        runTest {
            for (source in listOf("none", "_*", "a*b", "a_b", "a\"b", "a\\b", "a.b")) {
                val note = addBasicNote().update { listOf(source, "target").forEach(::addTag) }
                withViewModel {
                    ensureOpsExecuted(1) { renameTag(source, "target").join() }
                    assertThat(col.getNote(note.id).tags, equalTo(listOf("target")))
                    col.undo()
                    assertThat(col.getNote(note.id).tags, containsInAnyOrder(source, "target"))
                    col.redo()
                    assertThat(col.getNote(note.id).tags, equalTo(listOf("target")))
                }
            }
        }

    @Test
    fun `merge updates descendants and deduplicates tags on notes`() =
        runTest {
            val note = addBasicNote().update { listOf("source", "source::child", "target", "target::child", "unrelated").forEach(::addTag) }
            withViewModel {
                renameTag("source", "target").join()

                assertThat(col.getNote(note.id).tags, containsInAnyOrder("target", "target::child", "unrelated"))
                assertThat(pendingMessages.value.single().message, equalTo(UserMessage.TagRenamed(1)))
            }
        }

    @Test
    fun `tagExists includes collapsed and filtered tags regardless of case`() =
        runTest {
            addTags("parent::child", "other")
            withViewModel {
                assertThat(loadedState.visibleTagNames, not(hasItem("parent::child")))
                assertTrue(tagExists(TagName.asValid("PARENT::CHILD")))
                filter("other")
                assertThat(loadedState.visibleTagNames, equalTo(listOf("other")))
                assertTrue(tagExists(TagName.asValid("parent::CHILD")))
                assertTrue(!tagExists(TagName.asValid("missing")))
            }
        }

    @Test
    fun `rename refreshes hidden lookups and totals through undo and redo`() =
        runTest {
            val note = addTags("source::leaf", "unrelated")
            withViewModel {
                filter("no-match")
                assertEquals(3, loadedState.totalTagCount)

                renameTag("source", "target::topic").join()

                assertNull(loadedState.error)
                assertEquals(4, loadedState.totalTagCount)
                assertTrue(loadedState.visibleNodes.isEmpty())
                assertFalse(tagExists(TagName.asValid("source")))
                assertFalse(tagExists(TagName.asValid("source::leaf")))
                assertTrue(tagExists(TagName.asValid("target")))
                assertTrue(tagExists(TagName.asValid("TARGET::TOPIC::LEAF")))
                assertEquals(setOf("target::topic::leaf", "unrelated"), col.getNote(note.id).tags.toSet())

                col.undo()
                refreshTags().join()
                assertEquals(3, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("source::leaf")))
                assertFalse(tagExists(TagName.asValid("target")))

                col.redo()
                refreshTags().join()
                assertEquals(4, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("target::topic::leaf")))
                assertFalse(tagExists(TagName.asValid("source")))
            }
        }

    @Test
    fun `merge refreshes hidden lookups and totals and undo restores both trees`() =
        runTest {
            val note = addTags("source::child", "target::child", "other")
            withViewModel {
                filter("other")
                assertEquals(5, loadedState.totalTagCount)

                renameTag("source", "target").join()

                assertNull(loadedState.error)
                assertEquals(3, loadedState.totalTagCount)
                assertFalse(tagExists(TagName.asValid("source::child")))
                assertTrue(tagExists(TagName.asValid("target::child")))
                assertEquals(setOf("other", "target::child"), col.getNote(note.id).tags.toSet())
                assertEquals(2, col.getNote(note.id).tags.size)

                col.undo()
                refreshTags().join()
                assertEquals(5, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("source::child")))
                assertTrue(tagExists(TagName.asValid("target::child")))
                assertEquals(3, col.getNote(note.id).tags.size)
            }
        }

    @Test
    fun `move refreshes hidden destinations without losing sibling lookups`() =
        runTest {
            val note = addTags("old::topic::child", "old::keep", "unrelated")
            withViewModel {
                filter("unrelated")
                assertEquals(5, loadedState.totalTagCount)

                moveTag("old::topic", "new").join()

                assertNull(loadedState.error)
                assertEquals(6, loadedState.totalTagCount)
                assertFalse(tagExists(TagName.asValid("old::topic")))
                assertTrue(tagExists(TagName.asValid("old::keep")))
                assertTrue(tagExists(TagName.asValid("new::topic::child")))
                assertEquals(listOf("unrelated"), loadedState.visibleTagNames)
                assertEquals(setOf("new::topic::child", "old::keep", "unrelated"), col.getNote(note.id).tags.toSet())

                col.undo()
                refreshTags().join()
                assertEquals(5, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("old::topic::child")))
                assertFalse(tagExists(TagName.asValid("new")))
            }
        }

    @Test
    fun `invalid move preserves state and retry refreshes lookups`() =
        runTest {
            val note = addTags("source::child")
            withViewModel {
                val before = loadedState
                val undoBefore = col.undoStatus()

                moveTag("source", "source::child").join()

                assertIs<IllegalArgumentException>(loadedState.error)
                assertFalse(loadedState.isWorking)
                assertEquals(before.visibleNodes, loadedState.visibleNodes)
                assertEquals(before.totalTagCount, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("source::child")))
                assertEquals(listOf("source::child"), col.getNote(note.id).tags)
                assertEquals(undoBefore, col.undoStatus())
                assertTrue(pendingMessages.value.isEmpty())

                moveTag("source", "target").join()

                assertNull(loadedState.error)
                assertEquals(3, loadedState.totalTagCount)
                assertFalse(tagExists(TagName.asValid("source")))
                assertTrue(tagExists(TagName.asValid("target::source::child")))
                assertEquals(
                    UserMessage.TagMoved(1),
                    pendingMessages.value
                        .single()
                        .message,
                )
            }
        }

    @Test
    fun `unchanged move preserves an available redo`() =
        runTest {
            val note = addTags("source::child")
            withViewModel {
                moveTag("source", "target").join()
                col.undo()
                refreshTags().join()
                messageShown(
                    pendingMessages.value
                        .single()
                        .id,
                )
                val undoBefore = col.undoStatus()
                assertNotNull(undoBefore.redo)

                moveTag("source", null).join()

                assertNull(loadedState.error)
                assertEquals(undoBefore, col.undoStatus())
                assertEquals(2, loadedState.totalTagCount)
                assertEquals(
                    UserMessage.TagMoved(0),
                    pendingMessages.value
                        .single()
                        .message,
                )
                col.redo()
                assertEquals(listOf("target::source::child"), col.getNote(note.id).tags)
            }
        }

    @Test
    fun `clear unused refreshes cached descendants and their implicit parents`() =
        runTest {
            addTags("used::leaf", "unused::leaf").update { removeTag("unused::leaf") }
            withViewModel {
                filter("used")
                assertEquals(4, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("unused::leaf")))

                clearUnusedTags().join()

                assertNull(loadedState.error)
                assertEquals(2, loadedState.totalTagCount)
                assertFalse(tagExists(TagName.asValid("unused")))
                assertFalse(tagExists(TagName.asValid("unused::leaf")))
                assertTrue(tagExists(TagName.asValid("used")))
                assertTrue(tagExists(TagName.asValid("used::leaf")))

                col.undo()
                refreshTags().join()
                assertEquals(4, loadedState.totalTagCount)
                assertTrue(tagExists(TagName.asValid("unused::leaf")))
            }
        }

    @Test
    fun `backend rename rejection preserves visible state and permits a valid retry`() =
        runTest {
            val note = addTags("source::child")
            withViewModel {
                val before = loadedState

                renameTag("source", "invalid\u3000target").join()

                assertNotNull(loadedState.error)
                assertFalse(loadedState.isWorking)
                assertEquals(before.visibleNodes, loadedState.visibleNodes)
                assertEquals(before.totalTagCount, loadedState.totalTagCount)
                assertEquals(listOf("source::child"), col.getNote(note.id).tags)
                assertTrue(pendingMessages.value.isEmpty())

                renameTag("source", "valid").join()

                assertNull(loadedState.error)
                assertFalse(tagExists(TagName.asValid("source")))
                assertTrue(tagExists(TagName.asValid("valid::child")))
                assertEquals(
                    UserMessage.TagRenamed(1),
                    pendingMessages.value
                        .single()
                        .message,
                )
            }
        }

    @Test
    fun `lookup recognizes backend-equivalent spelling`() =
        runTest {
            addBasicNote().update { addTag("ﬃ") }
            assertEquals(1, col.db.queryScalar("select ? = ? collate unicase", "ﬃ", "iff"))
            withViewModel {
                assertTrue(tagExists(TagName.asValid("iff")))
            }
        }

    private suspend fun <T> withLockedCollection(block: suspend () -> T): T {
        CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
        try {
            return block()
        } finally {
            CollectionManager.emulatedOpenFailure = null
        }
    }

    /** Suspend backend access so assertions can observe an operation in progress. */
    private suspend fun TestScope.withQueuedCollectionAccess(block: suspend () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private suspend fun withViewModel(block: suspend ManageTagsViewModel.() -> Unit) = withViewModel(ManageTagsViewModel(), block)

    /** Helper abstracting [com.ichi2.anki.libanki.Tags.all] */
    private fun checkCollectionTags(block: (List<String>) -> Unit) = block(col.tags.all())

    /** Adds 17k tags to a note */
    private fun setupAnKing(): List<String> =
        AnKingTags.value.also { tags ->
            addBasicNote().update { tags.forEach { addTag(it) } }
        }

    private fun addTags(vararg tags: String) = addBasicNote().update { tags.forEach { addTag(it) } }

    /** Removing tags from notes leaves their entries in the collection's tag cache. */
    private fun addUnusedTags(vararg names: String) = addTags(*names).update { names.forEach(::removeTag) }
}

private val ManageTagsViewModel.loadedState: ManageTagsState.Content
    get() = state.value as ManageTagsState.Content
