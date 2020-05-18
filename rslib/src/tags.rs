// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection,
    err::{AnkiError, Result},
    notes::{NoteID, TransformNoteOutput},
    {text::normalize_to_nfc, types::Usn},
};
use regex::{NoExpand, Regex, Replacer};
use std::{borrow::Cow, collections::HashSet};
use unicase::UniCase;

pub(crate) fn split_tags(tags: &str) -> impl Iterator<Item = &str> {
    tags.split(|c| c == ' ' || c == '\u{3000}')
        .filter(|tag| !tag.is_empty())
}

pub(crate) fn join_tags(tags: &[String]) -> String {
    if tags.is_empty() {
        "".into()
    } else {
        format!(" {} ", tags.join(" "))
    }
}

impl Collection {
    /// Given a list of tags, fix case, ordering and duplicates.
    /// Returns true if any new tags were added.
    pub(crate) fn canonify_tags(&self, tags: Vec<String>, usn: Usn) -> Result<(Vec<String>, bool)> {
        let mut seen = HashSet::new();
        let mut added = false;

        let tags: Vec<_> = tags
            .iter()
            .flat_map(|t| split_tags(t))
            .map(|s| normalize_to_nfc(&s))
            .collect();

        for tag in &tags {
            if tag.trim().is_empty() {
                continue;
            }
            let tag = self.register_tag(tag, usn)?;
            if matches!(tag, Cow::Borrowed(_)) {
                added = true;
            }
            seen.insert(UniCase::new(tag));
        }

        // exit early if no non-empty tags
        if seen.is_empty() {
            return Ok((vec![], added));
        }

        // return the sorted, canonified tags
        let mut tags = seen.into_iter().collect::<Vec<_>>();
        tags.sort_unstable();
        let tags: Vec<_> = tags
            .into_iter()
            .map(|s| s.into_inner().to_string())
            .collect();

        Ok((tags, added))
    }

    pub(crate) fn register_tag<'a>(&self, tag: &'a str, usn: Usn) -> Result<Cow<'a, str>> {
        if let Some(preferred) = self.storage.preferred_tag_case(tag)? {
            Ok(preferred.into())
        } else {
            self.storage.register_tag(tag, usn)?;
            Ok(tag.into())
        }
    }

    pub(crate) fn register_tags(&self, tags: &str, usn: Usn, clear_first: bool) -> Result<bool> {
        let mut changed = false;
        if clear_first {
            self.storage.clear_tags()?;
        }
        for tag in split_tags(tags) {
            let tag = self.register_tag(tag, usn)?;
            if matches!(tag, Cow::Borrowed(_)) {
                changed = true;
            }
        }
        Ok(changed)
    }

    fn replace_tags_for_notes_inner<R: Replacer>(
        &mut self,
        nids: &[NoteID],
        tags: &[Regex],
        mut repl: R,
    ) -> Result<usize> {
        self.transact(None, |col| {
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
    ) -> Result<usize> {
        // generate regexps
        let tags = split_tags(tags)
            .map(|tag| {
                let tag = if regex {
                    tag.into()
                } else {
                    regex::escape(tag)
                };
                Regex::new(&format!("(?i){}", tag))
                    .map_err(|_| AnkiError::invalid_input("invalid regex"))
            })
            .collect::<Result<Vec<Regex>>>()?;

        if !regex {
            self.replace_tags_for_notes_inner(nids, &tags, NoExpand(repl))
        } else {
            self.replace_tags_for_notes_inner(nids, &tags, repl)
        }
    }

    pub fn add_tags_for_notes(&mut self, nids: &[NoteID], tags: &str) -> Result<usize> {
        let tags: Vec<_> = split_tags(tags).collect();
        let matcher = regex::RegexSet::new(
            tags.iter()
                .map(|s| regex::escape(s))
                .map(|s| format!("(?i){}", s)),
        )
        .map_err(|_| AnkiError::invalid_input("invalid regex"))?;

        self.transact(None, |col| {
            col.transform_notes(nids, |note, _nt| {
                let mut need_to_add = true;
                let mut match_count = 0;
                for tag in &note.tags {
                    if matcher.is_match(tag) {
                        match_count += 1;
                    }
                    if match_count == tags.len() {
                        need_to_add = false;
                        break;
                    }
                }

                if need_to_add {
                    note.tags.extend(tags.iter().map(|&s| s.to_string()))
                }

                Ok(TransformNoteOutput {
                    changed: need_to_add,
                    generate_cards: false,
                    mark_modified: true,
                })
            })
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, decks::DeckID};

    #[test]
    fn tags() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckID(1))?;

        let tags: String = col.storage.db_scalar("select tags from notes")?;
        assert_eq!(tags, "");

        // first instance wins in case of duplicates
        note.tags = vec!["foo".into(), "FOO".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["foo"]);
        let tags: String = col.storage.db_scalar("select tags from notes")?;
        assert_eq!(tags, " foo ");

        // existing case is used if in DB
        note.tags = vec!["FOO".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["foo"]);
        assert_eq!(tags, " foo ");

        // tags are normalized to nfc
        note.tags = vec!["\u{fa47}".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["\u{6f22}"]);

        // if code incorrectly adds a space to a tag, it gets split
        note.tags = vec!["one two".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["one", "two"]);

        Ok(())
    }

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

        col.replace_tags_for_notes(&[note.id], "b.r", "baz", true)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "baz");

        let cnt = col.add_tags_for_notes(&[note.id], "cee aye")?;
        assert_eq!(cnt, 1);
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["aye", "baz", "cee"]);

        // if all tags already on note, it doesn't get updated
        let cnt = col.add_tags_for_notes(&[note.id], "cee aye")?;
        assert_eq!(cnt, 0);

        // empty replacement deletes tag
        col.replace_tags_for_notes(&[note.id], "b.* .*ye", "", true)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["cee"]);

        Ok(())
    }
}
