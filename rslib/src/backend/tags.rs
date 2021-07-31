// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{notes::to_note_ids, Backend};
pub(super) use crate::backend_proto::tags_service::Service as TagsService;
use crate::{backend_proto as pb, prelude::*};

impl TagsService for Backend {
    fn clear_unused_tags(&self, _input: pb::Empty) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| col.clear_unused_tags().map(Into::into))
    }

    fn all_tags(&self, _input: pb::Empty) -> Result<pb::StringList> {
        Ok(pb::StringList {
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

    fn remove_tags(&self, tags: pb::String) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| col.remove_tags(tags.val.as_str()).map(Into::into))
    }

    fn set_tag_collapsed(&self, input: pb::SetTagCollapsedRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| {
            col.set_tag_collapsed(&input.name, input.collapsed)
                .map(Into::into)
        })
    }

    fn tag_tree(&self, _input: pb::Empty) -> Result<pb::TagTreeNode> {
        self.with_col(|col| col.tag_tree())
    }

    fn reparent_tags(&self, input: pb::ReparentTagsRequest) -> Result<pb::OpChangesWithCount> {
        let source_tags = input.tags;
        let target_tag = if input.new_parent.is_empty() {
            None
        } else {
            Some(input.new_parent)
        };
        self.with_col(|col| col.reparent_tags(&source_tags, target_tag))
            .map(Into::into)
    }

    fn rename_tags(&self, input: pb::RenameTagsRequest) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| col.rename_tag(&input.current_prefix, &input.new_prefix))
            .map(Into::into)
    }

    fn add_note_tags(&self, input: pb::NoteIdsAndTagsRequest) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| {
            col.add_tags_to_notes(&to_note_ids(input.note_ids), &input.tags)
                .map(Into::into)
        })
    }

    fn remove_note_tags(&self, input: pb::NoteIdsAndTagsRequest) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| {
            col.remove_tags_from_notes(&to_note_ids(input.note_ids), &input.tags)
                .map(Into::into)
        })
    }

    fn find_and_replace_tag(
        &self,
        input: pb::FindAndReplaceTagRequest,
    ) -> Result<pb::OpChangesWithCount> {
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
}
