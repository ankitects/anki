// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::{backend_proto as pb, prelude::*};
pub(super) use pb::tags_service::Service as TagsService;

impl TagsService for Backend {
    fn clear_unused_tags(&self, _input: pb::Empty) -> Result<pb::Empty> {
        self.with_col(|col| col.transact_no_undo(|col| col.clear_unused_tags().map(Into::into)))
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

    fn expunge_tags(&self, tags: pb::String) -> Result<pb::UInt32> {
        self.with_col(|col| col.expunge_tags(tags.val.as_str()).map(Into::into))
    }

    fn set_tag_expanded(&self, input: pb::SetTagExpandedIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                col.set_tag_expanded(&input.name, input.expanded)?;
                Ok(().into())
            })
        })
    }

    fn clear_tag(&self, tag: pb::String) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                col.storage.clear_tag_and_children(tag.val.as_str())?;
                Ok(().into())
            })
        })
    }

    fn tag_tree(&self, _input: pb::Empty) -> Result<pb::TagTreeNode> {
        self.with_col(|col| col.tag_tree())
    }

    fn drag_drop_tags(&self, input: pb::DragDropTagsIn) -> Result<pb::Empty> {
        let source_tags = input.source_tags;
        let target_tag = if input.target_tag.is_empty() {
            None
        } else {
            Some(input.target_tag)
        };
        self.with_col(|col| col.drag_drop_tags(&source_tags, target_tag))
            .map(Into::into)
    }
}
