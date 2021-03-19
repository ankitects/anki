// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Add/update/remove tags on selected notes

use crate::{notes::TransformNoteOutput, prelude::*, text::to_re};

use regex::{NoExpand, Regex, Replacer};

use super::split_tags;

impl Collection {
    fn replace_tags_for_notes_inner<R: Replacer>(
        &mut self,
        nids: &[NoteID],
        tags: &[Regex],
        mut repl: R,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::UpdateTag, |col| {
            col.transform_notes(nids, |note, _nt| {
                let mut changed = false;
                for re in tags {
                    if note.replace_tags(re, repl.by_ref()) {
                        changed = true;
                    }
                }

                Ok(TransformNoteOutput {
                    changed,
                    generate_cards: false,
                    mark_modified: true,
                })
            })
        })
    }

    /// Apply the provided list of regular expressions to note tags,
    /// saving any modified notes.
    pub fn replace_tags_for_notes(
        &mut self,
        nids: &[NoteID],
        tags: &str,
        repl: &str,
        regex: bool,
    ) -> Result<OpOutput<usize>> {
        // generate regexps
        let tags = split_tags(tags)
            .map(|tag| {
                let tag = if regex { tag.into() } else { to_re(tag) };
                Regex::new(&format!("(?i)^{}(::.*)?$", tag))
                    .map_err(|_| AnkiError::invalid_input("invalid regex"))
            })
            .collect::<Result<Vec<Regex>>>()?;
        if !regex {
            self.replace_tags_for_notes_inner(nids, &tags, NoExpand(repl))
        } else {
            self.replace_tags_for_notes_inner(nids, &tags, repl)
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::tags::Tag;
    use crate::{collection::open_test_collection, decks::DeckID};

    #[test]
    fn bulk() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("test".into());
        col.add_note(&mut note, DeckID(1))?;

        col.replace_tags_for_notes(&[note.id], "foo test", "bar", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.replace_tags_for_notes(&[note.id], "b.r", "baz", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.replace_tags_for_notes(&[note.id], "b*r", "baz", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "baz");

        col.replace_tags_for_notes(&[note.id], "b.r", "baz", true)?;
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
        col.replace_tags_for_notes(&[note.id], "b.* .*ye", "", true)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["cee"]);

        let mut note = col.storage.get_note(note.id)?.unwrap();
        note.tags = vec![
            "foo::bar".into(),
            "foo::bar::foo".into(),
            "bar::foo".into(),
            "bar::foo::bar".into(),
        ];
        col.update_note(&mut note)?;
        col.replace_tags_for_notes(&[note.id], "bar::foo", "foo::bar", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["foo::bar", "foo::bar::bar", "foo::bar::foo",]);

        // ensure replacements fully match
        let mut note = col.storage.get_note(note.id)?.unwrap();
        note.tags = vec!["foobar".into(), "barfoo".into(), "foo".into()];
        col.update_note(&mut note)?;
        col.replace_tags_for_notes(&[note.id], "foo", "", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["barfoo", "foobar"]);

        // tag children are also cleared when clearing their parent
        col.storage.clear_all_tags()?;
        for name in vec!["a", "a::b", "A::b::c"] {
            col.register_tag(&mut Tag::new(name.to_string(), Usn(0)))?;
        }
        col.storage.clear_tag_and_children("a")?;
        assert_eq!(col.storage.all_tags()?, vec![]);

        Ok(())
    }
}
