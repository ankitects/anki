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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::prelude::*;
    use crate::services::TagsService;

    fn add_note_with_tags(col: &mut Collection, tags: &[&str]) -> Result<NoteId> {
        let notetype = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = notetype.new_note();
        note.tags = tags.iter().map(ToString::to_string).collect();
        col.add_note(&mut note, DeckId(1))?;
        Ok(note.id)
    }

    fn stored_tags(col: &Collection, note_id: NoteId) -> Result<Vec<String>> {
        let mut tags = col.storage.get_note(note_id)?.unwrap().tags;
        tags.sort();
        Ok(tags)
    }

    fn registered_tags(col: &Collection) -> Result<Vec<String>> {
        let mut tags = col
            .storage
            .all_tags()?
            .into_iter()
            .map(|tag| tag.name)
            .collect::<Vec<_>>();
        tags.sort();
        Ok(tags)
    }

    #[test]
    fn add_note_tags_canonifies_and_persists_names() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, &["Parent"])?;
        let note_id = add_note_with_tags(&mut col, &[])?;

        let response = TagsService::add_note_tags(
            &mut col,
            anki_proto::tags::NoteIdsAndTagsRequest {
                note_ids: vec![note_id.0],
                tags: "parent::Child PARENT::child Extra".into(),
            },
        )?;

        assert_eq!(response.count, 1);
        assert!(response.changes.is_some());
        assert_eq!(stored_tags(&col, note_id)?, ["Extra", "Parent::Child"]);
        assert_eq!(registered_tags(&col)?, ["Extra", "Parent", "Parent::Child"]);
        Ok(())
    }

    #[test]
    fn all_tags_returns_registered_names() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, &["second", "first"])?;

        let mut response = TagsService::all_tags(&mut col)?.vals;
        response.sort();

        assert_eq!(response, ["first", "second"]);
        Ok(())
    }

    #[test]
    fn remove_note_tags_only_changes_requested_notes() -> Result<()> {
        let mut col = Collection::new();
        let selected = add_note_with_tags(&mut col, &["keep", "shared"])?;
        let unselected = add_note_with_tags(&mut col, &["keep", "shared"])?;

        let response = TagsService::remove_note_tags(
            &mut col,
            anki_proto::tags::NoteIdsAndTagsRequest {
                note_ids: vec![selected.0],
                tags: "shared".into(),
            },
        )?;

        assert_eq!(response.count, 1);
        assert!(response.changes.is_some());
        assert_eq!(stored_tags(&col, selected)?, ["keep"]);
        assert_eq!(stored_tags(&col, unselected)?, ["keep", "shared"]);
        Ok(())
    }

    #[test]
    fn remove_tags_removes_tag_from_all_notes_and_registry() -> Result<()> {
        let mut col = Collection::new();
        let first = add_note_with_tags(&mut col, &["keep", "target"])?;
        let second = add_note_with_tags(&mut col, &["second", "target"])?;

        let response = TagsService::remove_tags(
            &mut col,
            generic::String {
                val: "target".into(),
            },
        )?;

        assert_eq!(response.count, 2);
        assert!(response.changes.is_some());
        assert_eq!(stored_tags(&col, first)?, ["keep"]);
        assert_eq!(stored_tags(&col, second)?, ["second"]);
        assert_eq!(registered_tags(&col)?, ["keep", "second"]);
        Ok(())
    }

    #[test]
    fn clear_unused_tags_preserves_referenced_tags() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, &["used"])?;
        col.set_tag_collapsed("unused", true)?;

        let response = TagsService::clear_unused_tags(&mut col)?;

        assert_eq!(response.count, 1);
        assert!(response.changes.is_some());
        assert_eq!(registered_tags(&col)?, ["used"]);
        Ok(())
    }

    #[test]
    fn rename_tags_updates_prefix_and_children() -> Result<()> {
        let mut col = Collection::new();
        let note_id = add_note_with_tags(&mut col, &["keep", "old", "old::Child"])?;

        let response = TagsService::rename_tags(
            &mut col,
            anki_proto::tags::RenameTagsRequest {
                current_prefix: "old".into(),
                new_prefix: "New".into(),
            },
        )?;

        assert_eq!(response.count, 1);
        assert!(response.changes.is_some());
        assert_eq!(stored_tags(&col, note_id)?, ["New", "New::Child", "keep"]);
        assert_eq!(registered_tags(&col)?, ["New", "New::Child", "keep"]);
        Ok(())
    }

    #[test]
    fn reparent_tags_moves_tag_under_parent() -> Result<()> {
        let mut col = Collection::new();
        let note_id = add_note_with_tags(&mut col, &["Source::Leaf"])?;

        let response = TagsService::reparent_tags(
            &mut col,
            anki_proto::tags::ReparentTagsRequest {
                tags: vec!["Source::Leaf".into()],
                new_parent: "Target".into(),
            },
        )?;

        assert_eq!(response.count, 1);
        assert_eq!(stored_tags(&col, note_id)?, ["Target::Leaf"]);
        Ok(())
    }

    #[test]
    fn reparent_tags_moves_tag_to_root_when_parent_is_empty() -> Result<()> {
        let mut col = Collection::new();
        let note_id = add_note_with_tags(&mut col, &["Target::Leaf"])?;

        let response = TagsService::reparent_tags(
            &mut col,
            anki_proto::tags::ReparentTagsRequest {
                tags: vec!["Target::Leaf".into()],
                new_parent: String::new(),
            },
        )?;

        assert_eq!(response.count, 1);
        assert_eq!(stored_tags(&col, note_id)?, ["Leaf"]);
        Ok(())
    }

    #[test]
    fn set_tag_collapsed_is_reflected_in_tag_tree() -> Result<()> {
        let mut col = Collection::new();

        let changes = TagsService::set_tag_collapsed(
            &mut col,
            anki_proto::tags::SetTagCollapsedRequest {
                name: "Parent".into(),
                collapsed: true,
            },
        )?;
        let tree = TagsService::tag_tree(&mut col)?;

        assert!(changes.tag);
        assert_eq!(tree.children.len(), 1);
        let parent = &tree.children[0];
        assert_eq!(parent.name, "Parent");
        assert_eq!(parent.level, 1);
        assert!(parent.collapsed);
        Ok(())
    }

    #[test]
    fn find_and_replace_tag_targets_all_notes_when_ids_are_empty() -> Result<()> {
        let mut col = Collection::new();
        let first = add_note_with_tags(&mut col, &["Alpha"])?;
        let second = add_note_with_tags(&mut col, &["Alpha::Child"])?;

        let response = TagsService::find_and_replace_tag(
            &mut col,
            anki_proto::tags::FindAndReplaceTagRequest {
                note_ids: vec![],
                search: "alpha".into(),
                replacement: "Beta".into(),
                regex: false,
                match_case: false,
            },
        )?;

        assert_eq!(response.count, 2);
        assert_eq!(stored_tags(&col, first)?, ["Beta"]);
        assert_eq!(stored_tags(&col, second)?, ["Beta::Child"]);
        Ok(())
    }

    #[test]
    fn find_and_replace_tag_limits_changes_to_explicit_ids() -> Result<()> {
        let mut col = Collection::new();
        let selected = add_note_with_tags(&mut col, &["Target"])?;
        let unselected = add_note_with_tags(&mut col, &["Target"])?;

        let response = TagsService::find_and_replace_tag(
            &mut col,
            anki_proto::tags::FindAndReplaceTagRequest {
                note_ids: vec![selected.0],
                search: "target".into(),
                replacement: "Done".into(),
                regex: false,
                match_case: false,
            },
        )?;

        assert_eq!(response.count, 1);
        assert_eq!(stored_tags(&col, selected)?, ["Done"]);
        assert_eq!(stored_tags(&col, unselected)?, ["Target"]);
        Ok(())
    }

    #[test]
    fn complete_tag_returns_registered_canonical_matches() -> Result<()> {
        let mut col = Collection::new();
        add_note_with_tags(&mut col, &["Biochemistry", "Biology::Cell"])?;

        let response = TagsService::complete_tag(
            &mut col,
            anki_proto::tags::CompleteTagRequest {
                input: "bio::ce".into(),
                match_limit: 10,
            },
        )?;

        assert_eq!(response.tags, ["Biology::Cell"]);
        Ok(())
    }

    #[test]
    fn rename_tags_rejects_blank_new_prefix_without_changing_state() -> Result<()> {
        let mut col = Collection::new();
        let note_id = add_note_with_tags(&mut col, &["old"])?;

        let error = TagsService::rename_tags(
            &mut col,
            anki_proto::tags::RenameTagsRequest {
                current_prefix: "old".into(),
                new_prefix: "   ".into(),
            },
        )
        .unwrap_err();

        assert!(matches!(error, AnkiError::InvalidInput { .. }));
        assert_eq!(stored_tags(&col, note_id)?, ["old"]);
        assert_eq!(registered_tags(&col)?, ["old"]);
        Ok(())
    }

    #[test]
    fn find_and_replace_tag_reports_invalid_regex_without_changing_state() -> Result<()> {
        let mut col = Collection::new();
        let note_id = add_note_with_tags(&mut col, &["keep"])?;

        let error = TagsService::find_and_replace_tag(
            &mut col,
            anki_proto::tags::FindAndReplaceTagRequest {
                note_ids: vec![note_id.0],
                search: "[".into(),
                replacement: "done".into(),
                regex: true,
                match_case: false,
            },
        )
        .unwrap_err();

        assert!(matches!(error, AnkiError::InvalidRegex { .. }));
        assert_eq!(stored_tags(&col, note_id)?, ["keep"]);
        Ok(())
    }
}
