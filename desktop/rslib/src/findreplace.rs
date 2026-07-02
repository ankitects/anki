// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use regex::Regex;

use crate::collection::Collection;
use crate::error::Result;
use crate::notes::NoteId;
use crate::notes::TransformNoteOutput;
use crate::prelude::*;
use crate::text::normalize_to_nfc;

pub struct FindReplaceContext {
    nids: Vec<NoteId>,
    search: Regex,
    replacement: String,
    field_name: Option<String>,
}

enum FieldForNotetype {
    Any,
    Index(usize),
    None,
}

impl FindReplaceContext {
    pub fn new(
        nids: Vec<NoteId>,
        search_re: &str,
        repl: impl Into<String>,
        field_name: Option<String>,
    ) -> Result<Self> {
        Ok(FindReplaceContext {
            nids,
            search: Regex::new(search_re)?,
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
        nids: Vec<NoteId>,
        search_re: &str,
        repl: &str,
        field_name: Option<String>,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::FindAndReplace, |col| {
            let norm = col.get_config_bool(BoolKey::NormalizeNoteText);
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
        let mut field_for_notetype = FieldForNotetype::None;
        self.transform_notes(&ctx.nids, |note, nt| {
            if last_ntid != Some(nt.id) {
                field_for_notetype = match ctx.field_name.as_ref() {
                    None => FieldForNotetype::Any,
                    Some(name) => match nt.get_field_ord(name) {
                        None => FieldForNotetype::None,
                        Some(ord) => FieldForNotetype::Index(ord),
                    },
                };
                last_ntid = Some(nt.id);
            }

            let mut changed = false;
            match field_for_notetype {
                FieldForNotetype::Any => {
                    for txt in note.fields_mut() {
                        if let Cow::Owned(otxt) = ctx.replace_text(txt) {
                            changed = true;
                            *txt = otxt;
                        }
                    }
                }
                FieldForNotetype::Index(ord) => {
                    if let Some(txt) = note.fields_mut().get_mut(ord) {
                        if let Cow::Owned(otxt) = ctx.replace_text(txt) {
                            changed = true;
                            *txt = otxt;
                        }
                    }
                }
                FieldForNotetype::None => (),
            }

            Ok(TransformNoteOutput {
                changed,
                generate_cards: true,
                mark_modified: true,
                update_tags: false,
            })
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::decks::DeckId;

    #[test]
    fn findreplace() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "one aaa")?;
        note.set_field(1, "two aaa")?;
        col.add_note(&mut note, DeckId(1))?;

        let nt = col.get_notetype_by_name("Cloze")?.unwrap();
        let mut note2 = nt.new_note();
        note2.set_field(0, "three aaa")?;
        col.add_note(&mut note2, DeckId(1))?;

        let nids = col.search_notes_unordered("")?;
        let out = col.find_and_replace(nids.clone(), "(?i)AAA", "BBB", None)?;
        assert_eq!(out.output, 2);

        let note = col.storage.get_note(note.id)?.unwrap();
        // but the update should be limited to the specified field when it was available
        assert_eq!(&note.fields()[..], &["one BBB", "two BBB"]);

        let note2 = col.storage.get_note(note2.id)?.unwrap();
        assert_eq!(&note2.fields()[..], &["three BBB", ""]);

        assert_eq!(
            col.storage.field_names_for_notes(&nids)?,
            vec![
                "Back".to_string(),
                "Back Extra".into(),
                "Front".into(),
                "Text".into()
            ]
        );
        let out = col.find_and_replace(nids, "BBB", "ccc", Some("Front".into()))?;
        // 1, because notes without the specified field should be skipped
        assert_eq!(out.output, 1);

        let note = col.storage.get_note(note.id)?.unwrap();
        // the update should be limited to the specified field when it was available
        assert_eq!(&note.fields()[..], &["one ccc", "two BBB"]);

        Ok(())
    }
}
