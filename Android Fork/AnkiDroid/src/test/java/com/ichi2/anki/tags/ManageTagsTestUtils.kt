// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

internal val ManageTagsState.Content.visibleTagNames: List<String>
    get() = visibleNodes.map { it.fullTagName }

internal fun ManageTagsViewModel.toggleCollapsed(tag: String) = toggleCollapsed(TagName.asValid(tag))

internal fun ManageTagsViewModel.removeTag(tag: String) = removeTag(TagName.asValid(tag))

internal fun ManageTagsViewModel.renameTag(
    oldName: String,
    newName: String,
) = renameTag(TagName.asValid(oldName), TagName.asValid(newName))

internal fun ManageTagsViewModel.moveTag(
    tag: String,
    newParent: String?,
) = moveTag(TagName.asValid(tag), newParent?.let(TagName::asValid))

/** Creates a [TagListItemState] from a plain [String] tag */
internal fun tagListItem(
    fullTag: String,
    displayName: String,
    level: Int,
    hasChildren: Boolean,
    collapsed: Boolean,
) = TagListItemState(
    fullTagName = fullTag,
    displayName = displayName,
    level = level,
    hasChildren = hasChildren,
    collapsed = collapsed,
)

fun TagName.Companion.asValid(name: String) = TagName.build(name)!!
