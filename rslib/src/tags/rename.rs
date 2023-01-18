// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::is_tag_separator;
use super::matcher::TagMatcher;
use crate::prelude::*;
use crate::tags::register::normalize_tag_name;

impl Collection {
    /// Rename a given tag and its children on all notes that reference it,
    /// returning changed note count.
    pub fn rename_tag(&mut self, old_prefix: &str, new_prefix: &str) -> Result<OpOutput<usize>> {
        self.transact(Op::RenameTag, |col| {
            col.rename_tag_inner(old_prefix, new_prefix)
        })
    }
}

impl Collection {
    fn rename_tag_inner(&mut self, old_prefix: &str, new_prefix: &str) -> Result<usize> {
        require!(
            !new_prefix.contains(is_tag_separator),
            "replacement name can not contain a space",
        );
        require!(
            !new_prefix.trim().is_empty(),
            "replacement name must not be empty",
        );

        let usn = self.usn()?;

        // ensure normalized+matching parent case, but not case of existing tag.
        // The matching of parent case is mainly to be consistent with the way
        // decks are handled.
        let new_prefix = normalize_tag_name(new_prefix)?;
        let new_prefix = self
            .adjusted_case_for_parents(&new_prefix)?
            .map(Into::into)
            .unwrap_or(new_prefix);

        // gather tags that need replacing
        let mut re = TagMatcher::new(old_prefix)?;
        let matched_notes = self
            .storage
            .get_note_tags_by_predicate(|tags| re.is_match(tags))?;
        let match_count = matched_notes.len();
        if match_count == 0 {
            // no matches; exit early so we don't clobber the empty tag entries
            return Ok(0);
        }

        // remove old prefix from the tag list
        for tag in self.storage.get_tags_by_predicate(|tag| re.is_match(tag))? {
            self.remove_single_tag_undoable(tag)?;
        }

        // replace tags
        for mut note in matched_notes {
            let original = note.clone();
            note.tags = re.replace(&note.tags, &new_prefix);
            note.set_modified(usn);
            self.update_note_tags_undoable(&note, original)?;
        }

        // update tag list
        for tag in re.into_new_tags() {
            self.register_tag_string(tag, usn)?;
        }

        Ok(match_count)
    }
}
