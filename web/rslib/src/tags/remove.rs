// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use unicase::UniCase;

use super::matcher::TagMatcher;
use crate::prelude::*;

impl Collection {
    /// Take tags as a whitespace-separated string and remove them from all
    /// notes and the tag list.
    pub fn remove_tags(&mut self, tags: &str) -> Result<OpOutput<usize>> {
        self.transact(Op::RemoveTag, |col| col.remove_tags_inner(tags))
    }

    /// Remove whitespace-separated tags from provided notes.
    pub fn remove_tags_from_notes(
        &mut self,
        nids: &[NoteId],
        tags: &str,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::RemoveTag, |col| {
            col.remove_tags_from_notes_inner(nids, tags)
        })
    }

    /// Remove tags not referenced by notes, returning removed count.
    pub fn clear_unused_tags(&mut self) -> Result<OpOutput<usize>> {
        self.transact(Op::ClearUnusedTags, |col| col.clear_unused_tags_inner())
    }
}

impl Collection {
    fn remove_tags_inner(&mut self, tags: &str) -> Result<usize> {
        let usn = self.usn()?;

        // gather tags that need removing
        let mut re = TagMatcher::new(tags)?;
        let matched_notes = self
            .storage
            .get_note_tags_by_predicate(|tags| re.is_match(tags))?;
        let match_count = matched_notes.len();

        // remove from the tag list
        for tag in self.storage.get_tags_by_predicate(|tag| re.is_match(tag))? {
            self.remove_single_tag_undoable(tag)?;
        }

        // replace tags
        for mut note in matched_notes {
            let original = note.clone();
            note.tags = re.remove(&note.tags);
            note.set_modified(usn);
            self.update_note_tags_undoable(&note, original)?;
        }

        Ok(match_count)
    }

    fn remove_tags_from_notes_inner(&mut self, nids: &[NoteId], tags: &str) -> Result<usize> {
        let usn = self.usn()?;

        let mut re = TagMatcher::new(tags)?;
        let mut match_count = 0;
        let notes = self.storage.get_note_tags_by_id_list(nids)?;

        for mut note in notes {
            if !re.is_match(&note.tags) {
                continue;
            }

            match_count += 1;
            let original = note.clone();
            note.tags = re.remove(&note.tags);
            note.set_modified(usn);
            self.update_note_tags_undoable(&note, original)?;
        }

        Ok(match_count)
    }

    fn clear_unused_tags_inner(&mut self) -> Result<usize> {
        let mut count = 0;
        let in_notes = self.storage.all_tags_in_notes()?;
        let need_remove = self
            .storage
            .all_tags()?
            .into_iter()
            .filter(|tag| !in_notes.contains(&UniCase::new(tag.name.clone())));
        for tag in need_remove {
            self.remove_single_tag_undoable(tag)?;
            count += 1;
        }

        Ok(count)
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::tags::Tag;

    #[test]
    fn clearing() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("one".into());
        note.tags.push("two".into());
        col.add_note(&mut note, DeckId(1))?;

        col.set_tag_collapsed("one", false)?;
        col.clear_unused_tags()?;
        assert!(col.storage.get_tag("one")?.unwrap().expanded);
        assert!(!col.storage.get_tag("two")?.unwrap().expanded);

        // tag children are also cleared when clearing their parent
        col.storage.clear_all_tags()?;
        for name in &["a", "a::b", "A::b::c"] {
            col.register_tag(&mut Tag::new(name.to_string(), Usn(0)))?;
        }
        col.remove_tags("a")?;
        assert_eq!(col.storage.all_tags()?, vec![]);

        Ok(())
    }
}
