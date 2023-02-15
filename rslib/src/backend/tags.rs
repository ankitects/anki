// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::notes::to_note_ids;
use super::Backend;
use crate::pb;
pub(super) use crate::pb::tags::tags_service::Service as TagsService;
use crate::prelude::*;

impl TagsService for Backend {
    fn clear_unused_tags(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| col.clear_unused_tags().map(Into::into))
    }

    fn all_tags(&self, _input: pb::generic::Empty) -> Result<pb::generic::StringList> {
        Ok(pb::generic::StringList {
            vals: self.with_col(|col| {
                Ok(col
                    .storage
                    .all_tags()?
                    .into_iter()
                    .map(|t| t.name)
                    .collect())
            })?,
        })
    }

    fn remove_tags(&self, tags: pb::generic::String) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| col.remove_tags(tags.val.as_str()).map(Into::into))
    }

    fn set_tag_collapsed(
        &self,
        input: pb::tags::SetTagCollapsedRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| {
            col.set_tag_collapsed(&input.name, input.collapsed)
                .map(Into::into)
        })
    }

    fn tag_tree(&self, _input: pb::generic::Empty) -> Result<pb::tags::TagTreeNode> {
        self.with_col(|col| col.tag_tree())
    }

    fn reparent_tags(
        &self,
        input: pb::tags::ReparentTagsRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
        let source_tags = input.tags;
        let target_tag = if input.new_parent.is_empty() {
            None
        } else {
            Some(input.new_parent)
        };
        self.with_col(|col| col.reparent_tags(&source_tags, target_tag))
            .map(Into::into)
    }

    fn rename_tags(
        &self,
        input: pb::tags::RenameTagsRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| col.rename_tag(&input.current_prefix, &input.new_prefix))
            .map(Into::into)
    }

    fn add_note_tags(
        &self,
        input: pb::tags::NoteIdsAndTagsRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| {
            col.add_tags_to_notes(&to_note_ids(input.note_ids), &input.tags)
                .map(Into::into)
        })
    }

    fn remove_note_tags(
        &self,
        input: pb::tags::NoteIdsAndTagsRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| {
            col.remove_tags_from_notes(&to_note_ids(input.note_ids), &input.tags)
                .map(Into::into)
        })
    }

    fn find_and_replace_tag(
        &self,
        input: pb::tags::FindAndReplaceTagRequest,
    ) -> Result<pb::collection::OpChangesWithCount> {
        self.with_col(|col| {
            let note_ids = if input.note_ids.is_empty() {
                col.search_notes_unordered("")?
            } else {
                to_note_ids(input.note_ids)
            };
            col.find_and_replace_tag(
                &note_ids,
                &input.search,
                &input.replacement,
                input.regex,
                input.match_case,
            )
            .map(Into::into)
        })
    }

    fn complete_tag(
        &self,
        input: pb::tags::CompleteTagRequest,
    ) -> Result<pb::tags::CompleteTagResponse> {
        self.with_col(|col| {
            let tags = col.complete_tag(&input.input, input.match_limit as usize)?;
            Ok(pb::tags::CompleteTagResponse { tags })
        })
    }
}
