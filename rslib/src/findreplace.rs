// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection,
    err::{AnkiError, Result},
    notes::{NoteID, TransformNoteOutput},
    text::normalize_to_nfc,
};
use regex::Regex;
use std::borrow::Cow;

pub struct FindReplaceContext {
    nids: Vec<NoteID>,
    search: Regex,
    replacement: String,
    field_name: Option<String>,
}

impl FindReplaceContext {
    pub fn new(
        nids: Vec<NoteID>,
        search_re: &str,
        repl: impl Into<String>,
        field_name: Option<String>,
    ) -> Result<Self> {
        Ok(FindReplaceContext {
            nids,
            search: Regex::new(search_re).map_err(|_| AnkiError::invalid_input("invalid regex"))?,
            replacement: repl.into(),
            field_name,
        })
    }

    fn replace_text<'a>(&self, text: &'a str) -> Cow<'a, str> {
        self.search.replace_all(text, self.replacement.as_str())
    }
}

impl Collection {
    pub fn find_and_replace(
        &mut self,
        nids: Vec<NoteID>,
        search_re: &str,
        repl: &str,
        field_name: Option<String>,
    ) -> Result<usize> {
        self.transact(None, |col| {
            let norm = col.normalize_note_text();
            let search = if norm {
                normalize_to_nfc(search_re)
            } else {
                search_re.into()
            };
            let ctx = FindReplaceContext::new(nids, &search, repl, field_name)?;
            col.find_and_replace_inner(ctx)
        })
    }

    fn find_and_replace_inner(&mut self, ctx: FindReplaceContext) -> Result<usize> {
        let mut last_ntid = None;
        let mut field_ord = None;
        self.transform_notes(&ctx.nids, |note, nt| {
            if last_ntid != Some(nt.id) {
                field_ord = ctx.field_name.as_ref().and_then(|n| nt.get_field_ord(n));
                last_ntid = Some(nt.id);
            }

            let mut changed = false;
            match field_ord {
                None => {
                    // all fields
                    for txt in &mut note.fields {
                        if let Cow::Owned(otxt) = ctx.replace_text(txt) {
                            changed = true;
                            *txt = otxt;
                        }
                    }
                }
                Some(ord) => {
                    // single field
                    if let Some(txt) = note.fields.get_mut(ord) {
                        if let Cow::Owned(otxt) = ctx.replace_text(txt) {
                            changed = true;
                            *txt = otxt;
                        }
                    }
                }
            }

            Ok(TransformNoteOutput {
                changed,
                generate_cards: true,
                mark_modified: true,
            })
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, decks::DeckID};

    #[test]
    fn findreplace() -> Result<()> {
        let mut col = open_test_collection();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.fields[0] = "one aaa".into();
        note.fields[1] = "two aaa".into();
        col.add_note(&mut note, DeckID(1))?;

        let nt = col.get_notetype_by_name("Cloze")?.unwrap();
        let mut note2 = nt.new_note();
        note2.fields[0] = "three aaa".into();
        col.add_note(&mut note2, DeckID(1))?;

        let nids = col.search_notes("")?;
        let cnt = col.find_and_replace(nids.clone(), "(?i)AAA", "BBB", None)?;
        assert_eq!(cnt, 2);

        let note = col.storage.get_note(note.id)?.unwrap();
        // but the update should be limited to the specified field when it was available
        assert_eq!(&note.fields, &["one BBB", "two BBB"]);

        let note2 = col.storage.get_note(note2.id)?.unwrap();
        assert_eq!(&note2.fields, &["three BBB", ""]);

        assert_eq!(
            col.storage.field_names_for_notes(&nids)?,
            vec![
                "Back".to_string(),
                "Back Extra".into(),
                "Front".into(),
                "Text".into()
            ]
        );
        let cnt = col.find_and_replace(nids, "BBB", "ccc", Some("Front".into()))?;
        // still 2, as the caller is expected to provide only note ids that have
        // that field, and if we can't find the field we fall back on all fields
        assert_eq!(cnt, 2);

        let note = col.storage.get_note(note.id)?.unwrap();
        // but the update should be limited to the specified field when it was available
        assert_eq!(&note.fields, &["one ccc", "two BBB"]);

        Ok(())
    }
}
