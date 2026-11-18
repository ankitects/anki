// Copyright: Anki Software, LLC and contributors
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

        // remove old prefix from the tag list, noting which tags were expanded
        let mut expanded_tags = vec![];
        for tag in self.storage.get_tags_by_predicate(|tag| re.is_match(tag))? {
            if tag.expanded {
                expanded_tags.push(re.replace(&tag.name, &new_prefix).trim().to_string());
            }
            self.remove_single_tag_undoable(tag)?;
        }

        // replace tags
        for mut note in matched_notes {
            let original = note.clone();
            note.tags = re.replace(&note.tags, &new_prefix);
            note.set_modified(usn);
            self.update_note_tags_undoable(&note, original)?;
        }

        // update tag list, carrying over the expanded state of renamed tags
        for tag in expanded_tags {
            self.register_expanded_tag_string(tag, usn)?;
        }
        for tag in re.into_new_tags() {
            self.register_tag_string(tag, usn)?;
        }

        Ok(match_count)
    }
}

#[cfg(test)]
mod test {
    use super::*;

    fn col_with_expanded_tag(tags: &[&str], expanded: &str) -> Result<Collection> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags = tags.iter().map(ToString::to_string).collect();
        col.add_note(&mut note, DeckId(1))?;
        col.set_tag_collapsed(expanded, false)?;
        Ok(col)
    }

    fn is_expanded(col: &Collection, name: &str) -> Result<bool> {
        Ok(col.storage.get_tag(name)?.is_some_and(|tag| tag.expanded))
    }

    #[test]
    fn rename_keeps_expanded_state() -> Result<()> {
        let mut col = col_with_expanded_tag(&["source", "source::child"], "source")?;
        col.rename_tag("source", "destination")?;
        assert!(is_expanded(&col, "destination")?);

        // undo restores the original name and state
        col.undo()?;
        assert!(is_expanded(&col, "source")?);
        assert!(col.storage.get_tag("destination")?.is_none());

        Ok(())
    }

    #[test]
    fn rename_keeps_expanded_state_of_parent_without_notes() -> Result<()> {
        // only the child is on a note; the parent record exists because it was expanded
        let mut col = col_with_expanded_tag(&["source::child"], "source")?;
        col.rename_tag("source", "destination")?;
        assert!(is_expanded(&col, "destination")?);
        assert!(!is_expanded(&col, "destination::child")?);

        Ok(())
    }

    #[test]
    fn rename_onto_existing_tag_keeps_its_state() -> Result<()> {
        let mut col = col_with_expanded_tag(&["source", "source::child", "existing"], "source")?;
        col.rename_tag("source", "existing")?;
        assert!(!is_expanded(&col, "existing")?);

        Ok(())
    }
}
