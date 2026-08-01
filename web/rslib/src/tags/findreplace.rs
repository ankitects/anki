// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use regex::NoExpand;
use regex::Regex;
use regex::Replacer;

use super::is_tag_separator;
use super::join_tags;
use super::split_tags;
use crate::notes::NoteTags;
use crate::prelude::*;

impl Collection {
    /// Replace occurrences of a search with a new value in tags.
    pub fn find_and_replace_tag(
        &mut self,
        nids: &[NoteId],
        search: &str,
        replacement: &str,
        regex: bool,
        match_case: bool,
    ) -> Result<OpOutput<usize>> {
        require!(
            !replacement.contains(is_tag_separator),
            "replacement name cannot contain a space",
        );

        let mut search = if regex {
            Cow::from(search)
        } else {
            Cow::from(regex::escape(search))
        };

        if !match_case {
            search = format!("(?i){search}").into();
        }

        self.transact(Op::UpdateTag, |col| {
            if regex {
                col.replace_tags_for_notes_inner(nids, Regex::new(&search)?, replacement)
            } else {
                col.replace_tags_for_notes_inner(nids, Regex::new(&search)?, NoExpand(replacement))
            }
        })
    }
}

impl Collection {
    fn replace_tags_for_notes_inner<R: Replacer>(
        &mut self,
        nids: &[NoteId],
        regex: Regex,
        mut repl: R,
    ) -> Result<usize> {
        let usn = self.usn()?;
        let mut match_count = 0;
        let notes = self.storage.get_note_tags_by_id_list(nids)?;

        for original in notes {
            if let Some(updated_tags) = replace_tags(&original.tags, &regex, repl.by_ref()) {
                let (tags, _) = self.canonify_tags(updated_tags, usn)?;

                match_count += 1;
                let mut note = NoteTags {
                    tags: join_tags(&tags),
                    ..original
                };
                note.set_modified(usn);
                self.update_note_tags_undoable(&note, original)?;
            }
        }

        Ok(match_count)
    }
}

/// If any tags are changed, return the new tags list.
/// The returned tags will need to be canonified.
fn replace_tags<R>(tags: &str, regex: &Regex, mut repl: R) -> Option<Vec<String>>
where
    R: Replacer,
{
    let maybe_replaced: Vec<_> = split_tags(tags)
        .map(|tag| regex.replace_all(tag, repl.by_ref()))
        .collect();

    if maybe_replaced
        .iter()
        .any(|cow| matches!(cow, Cow::Owned(_)))
    {
        Some(maybe_replaced.into_iter().map(|s| s.to_string()).collect())
    } else {
        // nothing matched
        None
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::decks::DeckId;

    #[test]
    fn find_replace() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("test".into());
        col.add_note(&mut note, DeckId(1))?;

        col.find_and_replace_tag(&[note.id], "foo|test", "bar", true, false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.find_and_replace_tag(&[note.id], "BAR", "baz", false, true)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.find_and_replace_tag(&[note.id], "b.r", "baz", false, false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.find_and_replace_tag(&[note.id], "b.r", "baz", true, false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "baz");

        let out = col.add_tags_to_notes(&[note.id], "cee aye")?;
        assert_eq!(out.output, 1);
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["aye", "baz", "cee"]);

        // if all tags already on note, it doesn't get updated
        let out = col.add_tags_to_notes(&[note.id], "cee aye")?;
        assert_eq!(out.output, 0);

        // empty replacement deletes tag
        col.find_and_replace_tag(&[note.id], "b.*|.*ye", "", true, false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["cee"]);

        Ok(())
    }
}
