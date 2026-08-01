/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
package com.ichi2.anki.dialogs.tags

import androidx.lifecycle.ViewModel
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.asyncIO
import com.ichi2.anki.libanki.NoteId
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * @param noteIds IDs of notes whose tags should bfe retrieved and marked as "checked"
 * @param checkedTags additional list of checked tags.
 * These tags coming from EXTRAS are treated as absolute checked and cannot be indeterminate.
 * @param isCustomStudying true if all inputs are to be handled as unchecked tags, false otherwise(
 * this is a temporary parameter until custom study by tags is modified)
 *  They are joined with the tags retrieved from noteIds
 *
 *  @see <a href="https://github.com/ankidroid/Anki-Android/pull/19499#discussion_r2532184695">Extra checked tags</>
 */
class TagsDialogViewModel(
    noteIds: Collection<NoteId> = emptyList(),
    checkedTags: Collection<String> = emptyList(),
    isCustomStudying: Boolean = false,
) : ViewModel() {
    val tags: Deferred<TagsList>

    val initProgress: StateFlow<InitProgress>
        field = MutableStateFlow<InitProgress>(InitProgress.Processing)

    init {
        tags =
            asyncIO {
                val allTags = withCol { tags.all() }
                val allCheckedTags = mutableSetOf<String>()
                val uncheckedTags = mutableSetOf<String>()
                // For each note, put the checked tag in checked list and unchecked tags in unchecked list.
                // This will result in few tags being present in both lists, checked and
                // unchecked as they might be present in one note but absent in any other.
                // Such tags are referred as `indeterminateTags` in [TagsList]
                noteIds.forEachIndexed { index, nid ->
                    // TODO: Lift up withCol{ } call out of loop. Performs `N` expensive db queries.
                    val noteTags = withCol { getNote(nid) }.tags
                    initProgress.emit(InitProgress.FetchingNoteTags(index + 1, noteIds.size))
                    val (checked, unchecked) = allTags.partition { noteTags.contains(it) }
                    allCheckedTags.addAll(checked)
                    uncheckedTags.addAll(unchecked)
                }
                // add the extra checked tags, these are to be shown as `checked` and cannot be indeterminate
                val extraCheckedTags = checkedTags.toSet()
                allCheckedTags.addAll(extraCheckedTags)
                uncheckedTags.removeAll(extraCheckedTags)
                initProgress.emit(InitProgress.Processing)
                if (isCustomStudying) {
                    TagsList(
                        allTags = allCheckedTags,
                        checkedTags = emptyList(),
                        uncheckedTags = allCheckedTags,
                    )
                } else {
                    TagsList(
                        allTags = allTags,
                        checkedTags = allCheckedTags,
                        uncheckedTags = uncheckedTags,
                    )
                }.also {
                    initProgress.emit(InitProgress.Finished)
                }
            }
    }

    sealed interface InitProgress {
        data object Processing : InitProgress

        class FetchingNoteTags(
            val noteNumber: Int,
            val noteCount: Int,
        ) : InitProgress

        data object Finished : InitProgress
    }
}
