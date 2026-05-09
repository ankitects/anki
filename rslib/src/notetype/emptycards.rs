// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::fmt::Write;

use super::cardgen::group_generated_cards_by_note;
use super::CardGenContext;
use super::Notetype;
use super::NotetypeId;
use super::NotetypeKind;
use crate::card::CardId;
use crate::collection::Collection;
use crate::error::Result;
use crate::notes::NoteId;

pub struct EmptyCardsForNote {
    pub nid: NoteId,
    // (ordinal, card id)
    pub empty: Vec<(u32, CardId)>,
    pub current_count: usize,
}

impl Collection {
    fn empty_cards_for_notetype(&self, nt: &Notetype) -> Result<Vec<EmptyCardsForNote>> {
        let last_deck = self.get_last_deck_added_to_for_notetype(nt.id);
        let ctx = CardGenContext::new(nt, last_deck, self.usn()?);
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

    pub fn empty_cards(&mut self) -> Result<Vec<(NotetypeId, Vec<EmptyCardsForNote>)>> {
        self.storage
            .get_all_notetype_names()?
            .into_iter()
            .map(|(id, _name)| {
                let nt = self.get_notetype(id)?.unwrap();
                self.empty_cards_for_notetype(&nt).map(|v| (id, v))
            })
            .collect()
    }

    /// Create a report on empty cards. Mutates the provided data to sort
    /// ordinals.
    pub fn empty_cards_report(
        &mut self,
        empty: &mut [(NotetypeId, Vec<EmptyCardsForNote>)],
    ) -> Result<String> {
        let nts = self.get_all_notetypes()?;
        let mut buf = String::new();
        for (ntid, notes) in empty {
            if !notes.is_empty() {
                let nt = nts.iter().find(|nt| nt.id == *ntid).unwrap();
                write!(
                    buf,
                    "<div><b>{}</b></div><ol>",
                    self.tr.empty_cards_for_note_type(nt.name.clone())
                )
                .unwrap();

                for note in notes {
                    note.empty.sort_unstable();
                    let templates = match nt.config.kind() {
                        // "Front, Back"
                        NotetypeKind::Normal => note
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
                        NotetypeKind::Cloze => format!(
                            "{} {}",
                            self.tr.notetypes_cloze_name(),
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
                        self.tr.empty_cards_count_line(
                            note.empty.len(),
                            note.current_count,
                            templates
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
