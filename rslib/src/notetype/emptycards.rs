// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{
    cardgen::group_generated_cards_by_note, CardGenContext, NoteType, NoteTypeID, NoteTypeKind,
};
use crate::{
    card::CardID,
    collection::Collection,
    err::Result,
    i18n::{tr_args, TR},
    notes::NoteID,
};
use std::collections::HashSet;
use std::fmt::Write;

pub struct EmptyCardsForNote {
    pub nid: NoteID,
    // (ordinal, card id)
    pub empty: Vec<(u32, CardID)>,
    pub current_count: usize,
}

impl Collection {
    fn empty_cards_for_notetype(&self, nt: &NoteType) -> Result<Vec<EmptyCardsForNote>> {
        let ctx = CardGenContext::new(nt, self.usn()?);
        let existing_cards = self.storage.existing_cards_for_notetype(nt.id)?;
        let by_note = group_generated_cards_by_note(existing_cards);
        let mut out = Vec::with_capacity(by_note.len());

        for (nid, existing) in by_note {
            let note = self.storage.get_note(nid)?.unwrap();
            let cards = ctx.new_cards_required(&note, &[], false);
            let nonempty_ords: HashSet<_> = cards.into_iter().map(|c| c.ord).collect();
            let current_count = existing.len();
            let empty: Vec<_> = existing
                .into_iter()
                .filter_map(|e| {
                    if !nonempty_ords.contains(&e.ord) {
                        Some((e.ord, e.id))
                    } else {
                        None
                    }
                })
                .collect();
            if !empty.is_empty() {
                out.push(EmptyCardsForNote {
                    nid,
                    empty,
                    current_count,
                })
            }
        }

        Ok(out)
    }

    pub fn empty_cards(&mut self) -> Result<Vec<(NoteTypeID, Vec<EmptyCardsForNote>)>> {
        self.storage
            .get_all_notetype_names()?
            .into_iter()
            .map(|(id, _name)| {
                let nt = self.get_notetype(id)?.unwrap();
                self.empty_cards_for_notetype(&nt).map(|v| (id, v))
            })
            .collect()
    }

    /// Create a report on empty cards. Mutates the provided data to sort ordinals.
    pub fn empty_cards_report(
        &mut self,
        empty: &mut [(NoteTypeID, Vec<EmptyCardsForNote>)],
    ) -> Result<String> {
        let nts = self.get_all_notetypes()?;
        let mut buf = String::new();
        for (ntid, notes) in empty {
            if !notes.is_empty() {
                let nt = nts.get(ntid).unwrap();
                write!(
                    buf,
                    "<div><b>{}</b></div><ol>",
                    self.i18n.trn(
                        TR::EmptyCardsForNoteType,
                        tr_args!["notetype"=>nt.name.clone()],
                    )
                )
                .unwrap();

                for note in notes {
                    note.empty.sort_unstable();
                    let templates = match nt.config.kind() {
                        // "Front, Back"
                        NoteTypeKind::Normal => note
                            .empty
                            .iter()
                            .map(|(ord, _)| {
                                nt.templates
                                    .get(*ord as usize)
                                    .map(|t| t.name.clone())
                                    .unwrap_or_else(|| format!("Card {}", *ord + 1))
                            })
                            .collect::<Vec<_>>()
                            .join(", "),
                        // "Cloze 1, 3"
                        NoteTypeKind::Cloze => format!(
                            "{} {}",
                            self.i18n.tr(TR::NotetypesClozeName),
                            note.empty
                                .iter()
                                .map(|(ord, _)| (ord + 1).to_string())
                                .collect::<Vec<_>>()
                                .join(", ")
                        ),
                    };
                    let class = if note.current_count == note.empty.len() {
                        "allempty"
                    } else {
                        ""
                    };
                    write!(
                        buf,
                        "<li class={}>[anki:nid:{}] {}</li>",
                        class,
                        note.nid,
                        self.i18n.trn(
                            TR::EmptyCardsCountLine,
                            tr_args![
                            "empty_count"=>note.empty.len(),
                            "existing_count"=>note.current_count,
                            "template_names"=>templates
                            ],
                        )
                    )
                    .unwrap();
                }

                buf.push_str("</ol>");
            }
        }

        Ok(buf)
    }
}
