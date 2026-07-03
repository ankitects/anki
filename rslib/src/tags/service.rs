// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;

use crate::collection::Collection;
use crate::error;
use crate::notes::service::to_note_ids;

impl crate::services::TagsService for Collection {
    fn clear_unused_tags(&mut self) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.clear_unused_tags().map(Into::into)
    }

    fn all_tags(&mut self) -> error::Result<generic::StringList> {
        Ok(generic::StringList {
            vals: self
                .storage
                .all_tags()?
                .into_iter()
                .map(|t| t.name)
                .collect(),
        })
    }

    fn remove_tags(
        &mut self,
        tags: generic::String,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.remove_tags(tags.val.as_str()).map(Into::into)
    }

    fn set_tag_collapsed(
        &mut self,
        input: anki_proto::tags::SetTagCollapsedRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.set_tag_collapsed(&input.name, input.collapsed)
            .map(Into::into)
    }

    fn tag_tree(&mut self) -> error::Result<anki_proto::tags::TagTreeNode> {
        self.tag_tree()
    }

    fn reparent_tags(
        &mut self,
        input: anki_proto::tags::ReparentTagsRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        let source_tags = input.tags;
        let target_tag = if input.new_parent.is_empty() {
            None
        } else {
            Some(input.new_parent)
        };
        self.reparent_tags(&source_tags, target_tag).map(Into::into)
    }

    fn rename_tags(
        &mut self,
        input: anki_proto::tags::RenameTagsRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.rename_tag(&input.current_prefix, &input.new_prefix)
            .map(Into::into)
    }

    fn add_note_tags(
        &mut self,
        input: anki_proto::tags::NoteIdsAndTagsRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.add_tags_to_notes(&to_note_ids(input.note_ids), &input.tags)
            .map(Into::into)
    }

    fn remove_note_tags(
        &mut self,
        input: anki_proto::tags::NoteIdsAndTagsRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.remove_tags_from_notes(&to_note_ids(input.note_ids), &input.tags)
            .map(Into::into)
    }

    fn find_and_replace_tag(
        &mut self,
        input: anki_proto::tags::FindAndReplaceTagRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        let note_ids = if input.note_ids.is_empty() {
            self.search_notes_unordered("")?
        } else {
            to_note_ids(input.note_ids)
        };
        self.find_and_replace_tag(
            &note_ids,
            &input.search,
            &input.replacement,
            input.regex,
            input.match_case,
        )
        .map(Into::into)
    }

    fn complete_tag(
        &mut self,
        input: anki_proto::tags::CompleteTagRequest,
    ) -> error::Result<anki_proto::tags::CompleteTagResponse> {
        let tags = Collection::complete_tag(self, &input.input, input.match_limit as usize)?;
        Ok(anki_proto::tags::CompleteTagResponse { tags })
    }
}
