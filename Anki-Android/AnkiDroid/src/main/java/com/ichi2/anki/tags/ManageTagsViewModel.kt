/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tags

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Tags
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.tags.ManageTagsState.Error
import com.ichi2.anki.tags.UserMessage.ClearedUnusedTags
import com.ichi2.anki.tags.UserMessage.TagRemoved
import com.ichi2.anki.tags.UserMessage.TagRenamed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import anki.tags.TagTreeNode as BackendTagTreeNode

/**
 * ViewModel for the Manage Tags screen.
 *
 * Handles display of hierarchical tags with expand/collapse and a search filter.
 * Tag operations (rename, delete) are applied to individual tags.
 *
 * This must handle a huge amount of tags, AnKing v11 contains ~17k tags.
 *
 * @see Tags for backend functions.
 * @see TagListItemState for display.
 * @see ManageTagsState for UI state.
 * @see events for one-shot events.
 */
class ManageTagsViewModel : ViewModel() {
    val state: StateFlow<ManageTagsState>
        field = MutableStateFlow<ManageTagsState>(ManageTagsState.Loading)

    private val _events = Channel<DisplayMessage>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Cached flat list of all tags, rebuilt when the backend tree changes */
    private var tagList: List<TagListItemState> = emptyList()

    init {
        refreshTags()
    }

    /** Reloads the tag tree from the backend. Preserves the current search query if any */
    fun refreshTags() =
        launchTagOpAndRefresh {
            Timber.i("Refreshing tags")
        }

    /**
     * Filters visible tags by [query]. Ancestors of matches are shown to preserve hierarchy.
     *
     * WARN: Mutliselect was not implemented on this screen due to the confusing combination
     * of having a filtered tag selected.
     */
    fun filter(query: String) {
        if (tagList.isEmpty()) return
        updateState { loaded ->
            loaded.copy(
                searchQuery = query,
                visibleNodes = computeVisibleNodes(query),
            )
        }
    }

    /** Returns the visible node for [tag], or null with a warning if not found or not loaded */
    private fun findVisibleNode(tag: TagName): TagListItemState? {
        val loaded = state.value as? ManageTagsState.Content
        if (loaded == null) {
            Timber.w("findVisibleNode called while not loaded")
            return null
        }
        return loaded.visibleNodes.firstOrNull { it.fullTag == tag }.also {
            if (it == null) {
                Timber.w("Tag not found in visible nodes")
                Timber.d("Tag: %s", tag)
            }
        }
    }

    /**
     * Expands or collapses [tag] in the tree. Persists the state to the backend.
     *
     * PERF: ~55ms to process 10k+ tags on my M1; ~350ms to process 10k+ tags in CI
     *
     * @see Tags.setCollapsed
     */
    fun toggleCollapsed(tag: TagName) =
        viewModelScope.launch {
            try {
                val node =
                    findVisibleNode(tag) ?: run {
                        _events.send(DisplayMessage(UserMessage.UnexpectedError))
                        return@launch
                    }
                val newCollapsed = !node.collapsed
                withCol { tags.setCollapsed(tag, newCollapsed) }
                // Update the in-memory list with the new collapsed state
                tagList =
                    tagList.map {
                        if (it.fullTag == tag) it.copy(collapsed = newCollapsed) else it
                    }
                updateState { it.copy(visibleNodes = computeVisibleNodes(it.searchQuery)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to toggle collapsed state")
                state.value = Error(e)
            }
        }

    /**
     * Deletes [tag] and its children from all notes.
     * @see Tags.remove
     */
    fun removeTag(tag: TagName) =
        launchTagOpAndRefresh {
            val result = undoableOp { tags.remove(tag) }
            _events.send(DisplayMessage(TagRemoved(result.count)))
        }

    /**
     * Renames [oldName] to [newName], updating all notes and child tags.
     * @see Tags.rename
     */
    fun renameTag(
        oldName: TagName,
        newName: TagName,
    ) = launchTagOpAndRefresh {
        val result = undoableOp { tags.rename(oldName, newName) }
        _events.send(DisplayMessage(TagRenamed(result.count)))
    }

    /**
     * Removes tags not present on any note. Emits a [ClearedUnusedTags] event with the count.
     * @see Tags.clearUnusedTags
     */
    fun clearUnusedTags() =
        launchTagOpAndRefresh {
            val result = undoableOp { tags.clearUnusedTags() }
            Timber.i("Deleted %d unused tags", result.count)
            _events.send(DisplayMessage(ClearedUnusedTags(result.count)))
        }

    private suspend fun loadTags(searchQuery: String) {
        Timber.i("Loading tags from collection")
        tagList = flattenTree(withCol { tags.tree() })
        state.value =
            ManageTagsState.Content(
                visibleNodes = computeVisibleNodes(searchQuery),
                searchQuery = searchQuery,
            )
    }

    /**
     * Sets [ManageTagsState.Loading], runs [block], then [reloads][loadTags].
     * On failure, transitions to [Error]
     */
    private fun launchTagOpAndRefresh(block: suspend () -> Unit = { }): Job {
        val previousLoaded = state.value as? ManageTagsState.Content
        state.value = ManageTagsState.Loading
        return viewModelScope.launch {
            try {
                block()
                loadTags(searchQuery = previousLoaded?.searchQuery ?: "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "launchTagOperation failed")
                state.value = Error(e)
            }
        }
    }

    private fun computeVisibleNodes(searchQuery: String): List<TagListItemState> =
        if (searchQuery.isBlank()) {
            // filter out the collapsed nodes from the results
            applyCollapsedVisibility(tagList)
        } else {
            // collapsed nodes can be re-expanded if searchQuery matches the leaf nodes
            applySearchFilter(tagList, searchQuery)
        }

    /** Applies [transform] only if the current state is [ManageTagsState.Content]; no-ops otherwise */
    private inline fun updateState(transform: (ManageTagsState.Content) -> ManageTagsState.Content) {
        state.update { current ->
            when (current) {
                is ManageTagsState.Content -> transform(current)
                else -> {
                    Timber.w("updateState called while in %s; ignoring", current::class.simpleName)
                    current
                }
            }
        }
    }

    companion object {
        /**
         * Converts the backend's recursive [BackendTagTreeNode] into a flat list of all
         * [TagListItemState]s in pre-order. Always recurses into all children regardless of
         * collapsed state; the `collapsed` flag is preserved on each item for later filtering.
         */
        @VisibleForTesting
        fun flattenTree(root: BackendTagTreeNode): List<TagListItemState> {
            val result = mutableListOf<TagListItemState>()

            fun traverse(
                node: BackendTagTreeNode,
                parentFullTag: String,
            ) {
                for (child in node.childrenList) {
                    val fullTag = if (parentFullTag.isEmpty()) child.name else "$parentFullTag::${child.name}"
                    result.add(
                        TagListItemState(
                            fullTag = TagName.build(fullTag) ?: error("Invalid tag provided"),
                            displayName = child.name,
                            level = child.level - 1,
                            hasChildren = child.childrenList.isNotEmpty(),
                            collapsed = child.collapsed,
                        ),
                    )
                    if (child.childrenList.isNotEmpty()) {
                        traverse(child, fullTag)
                    }
                }
            }

            traverse(root, "")
            return result
        }

        /**
         * Filters a flat tag list to hide children of collapsed nodes.
         * Expects [allNodes] in pre-order (parent before children).
         */
        @VisibleForTesting
        fun applyCollapsedVisibility(allNodes: List<TagListItemState>): List<TagListItemState> =
            buildList {
                var skipBelowLevel: Int? = null
                for (item in allNodes) {
                    if (skipBelowLevel != null && item.level > skipBelowLevel) {
                        continue
                    }
                    skipBelowLevel = if (item.collapsed && item.hasChildren) item.level else null
                    add(item)
                }
            }

        /**
         * Filters a flat tag list to items matching [searchQuery] (case-insensitive substring),
         * plus their ancestors to preserve hierarchy.
         * Collapsed nodes are expanded if their children match, ensuring leaf matches are visible.
         *
         * O(n*d) where n = number of tags and d = maximum tag depth.
         */
        @VisibleForTesting
        fun applySearchFilter(
            allNodes: List<TagListItemState>,
            searchQuery: String,
        ): List<TagListItemState> {
            val queryLowercase = searchQuery.lowercase()
            val visibleTags = mutableSetOf<String>()
            val parentsWithVisibleChildren = mutableSetOf<String>()
            for (item in allNodes) {
                if (item.containsLowercase(queryLowercase)) {
                    visibleTags.add(item.fullTag.value)
                    for (ancestor in item.fullTag.ancestors) {
                        visibleTags.add(ancestor)
                        parentsWithVisibleChildren.add(ancestor)
                    }
                }
            }
            return allNodes
                .filter { it.fullTag.value in visibleTags }
                .map {
                    // expand a collapsed parent if a child matches the search
                    if (it.collapsed && it.fullTag.value in parentsWithVisibleChildren) {
                        it.copy(collapsed = false)
                    } else {
                        it
                    }
                }
        }
    }
}

/**
 * A flattened representation of a tag for display in a RecyclerView.
 *
 * @param fullTag full hierarchical tag path, e.g. "science::biology"
 * @param displayName leaf name only, e.g. "biology"
 * @param level tree depth (0 = top-level)
 */
data class TagListItemState(
    val fullTag: TagName,
    val displayName: String,
    val level: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
) {
    // cache the tag name to support fast case-insensitive searches
    private val fullTagLower: String = fullTag.value.lowercase()

    /** Case-insensitive check against the full tag path. [query] must be pre-lowercased. */
    fun containsLowercase(query: String): Boolean = fullTagLower.contains(query)
}

sealed class ManageTagsState {
    data object Loading : ManageTagsState()

    data class Content(
        val visibleNodes: List<TagListItemState>,
        val searchQuery: String = "",
    ) : ManageTagsState()

    data class Error(
        val error: Throwable,
    ) : ManageTagsState()
}

data class DisplayMessage(
    val message: UserMessage,
)

sealed interface UserMessage {
    /** @param notesAffected number of notes the tag was removed from */
    data class TagRemoved(
        val notesAffected: Int,
    ) : UserMessage

    /** @param notesAffected number of notes whose tags were updated */
    data class TagRenamed(
        val notesAffected: Int,
    ) : UserMessage

    /** @param count number of unused tags removed from the collection */
    data class ClearedUnusedTags(
        val count: Int,
    ) : UserMessage

    /** A generic error message for unexpected failures */
    data object UnexpectedError : UserMessage
}
