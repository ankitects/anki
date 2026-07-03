// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Adding tags to selected notes in the browse screen.

use std::collections::HashSet;

use unicase::UniCase;

use super::join_tags;
use super::split_tags;
use crate::notes::NoteTags;
use crate::prelude::*;

impl Collection {
    pub fn add_tags_to_notes(&mut self, nids: &[NoteId], tags: &str) -> Result<OpOutput<usize>> {
        self.transact(Op::UpdateTag, |col| col.add_tags_to_notes_inner(nids, tags))
    }
}

impl Collection {
    pub(crate) fn add_tags_to_notes_inner(&mut self, nids: &[NoteId], tags: &str) -> Result<usize> {
        let usn = self.usn()?;

        // will update tag list for any new tags, and match case
        let tags_to_add = self.canonified_tags_as_vec(tags, usn)?;

        // modify notes
        let mut match_count = 0;
        let notes = self.storage.get_note_tags_by_id_list(nids)?;
        for original in notes {
            if let Some(updated_tags) = add_missing_tags(&original.tags, &tags_to_add) {
                match_count += 1;
                let mut note = NoteTags {
                    tags: updated_tags,
                    ..original
                };
                note.set_modified(usn);
                self.update_note_tags_undoable(&note, original)?;
            }
        }

        Ok(match_count)
    }
}

/// Returns the sorted new tag string if any tags were added.
fn add_missing_tags(note_tags: &str, desired: &[UniCase<String>]) -> Option<String> {
    let mut note_tags: HashSet<_> = split_tags(note_tags)
        .map(ToOwned::to_owned)
        .map(UniCase::new)
        .collect();

    let mut modified = false;
    for tag in desired {
        if !note_tags.contains(tag) {
            note_tags.insert(tag.clone());
            modified = true;
        }
    }
    if !modified {
        return None;
    }

    // sort
    let mut tags: Vec<_> = note_tags.into_iter().collect::<Vec<_>>();
    tags.sort_unstable();

    // turn back into a string
    let tags: Vec<_> = tags.into_iter().map(|s| s.into_inner()).collect();
    Some(join_tags(&tags))
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn add_missing() {
        let desired: Vec<_> = ["xyz", "abc", "DEF"]
            .iter()
            .map(|s| UniCase::new(s.to_string()))
            .collect();

        let add_to = |text| add_missing_tags(text, &desired).unwrap();

        assert_eq!(&add_to(""), " abc DEF xyz ");
        assert_eq!(&add_to("XYZ deF aaa"), " aaa abc deF XYZ ");
        assert!(add_missing_tags("def xyz abc", &desired).is_none());
    }
}
