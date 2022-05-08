// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, sync::Arc};

use crate::{
    import_export::{
        text::{ForeignCard, ForeignData, ForeignNote, ForeignNotetype, ForeignTemplate},
        LogNote, NoteLog,
    },
    notetype::{CardGenContext, CardTemplate, NoteField, NotetypeConfig},
    prelude::*,
};

impl ForeignData {
    pub fn import(self, col: &mut Collection) -> Result<OpOutput<NoteLog>> {
        col.transact(Op::Import, |col| {
            let mut ctx = Context::new(&self, col)?;
            ctx.import_foreign_notetypes(self.notetypes)?;
            ctx.import_foreign_notes(self.notes)
        })
    }
}

struct Context<'a> {
    col: &'a mut Collection,
    notetypes: HashMap<String, Option<Arc<Notetype>>>,
    deck_ids: HashMap<String, Option<DeckId>>,
    usn: Usn,
    normalize_notes: bool,
    //progress: IncrementableProgress<ImportProgress>,
}

impl<'a> Context<'a> {
    fn new(data: &ForeignData, col: &'a mut Collection) -> Result<Self> {
        let usn = col.usn()?;
        let normalize_notes = col.get_config_bool(BoolKey::NormalizeNoteText);
        let mut notetypes = HashMap::new();
        notetypes.insert(
            String::new(),
            col.notetype_for_string(&data.default_notetype)?,
        );
        let mut deck_ids = HashMap::new();
        deck_ids.insert(String::new(), col.deck_id_for_string(&data.default_deck)?);
        Ok(Self {
            col,
            usn,
            normalize_notes,
            notetypes,
            deck_ids,
        })
    }

    fn import_foreign_notetypes(&mut self, notetypes: Vec<ForeignNotetype>) -> Result<()> {
        for foreign in notetypes {
            let mut notetype = foreign.into_native();
            notetype.usn = self.usn;
            self.col
                .add_notetype_inner(&mut notetype, self.usn, false)?;
        }
        Ok(())
    }
    fn notetype_for_note(&mut self, note: &ForeignNote) -> Result<Option<Arc<Notetype>>> {
        Ok(if let Some(nt) = self.notetypes.get(&note.notetype) {
            nt.clone()
        } else {
            let nt = self.col.notetype_for_string(&note.notetype)?;
            self.notetypes.insert(note.notetype.clone(), nt.clone());
            nt
        })
    }

    fn deck_id_for_note(&mut self, note: &ForeignNote) -> Result<Option<DeckId>> {
        Ok(if let Some(did) = self.deck_ids.get(&note.deck) {
            *did
        } else {
            let did = self.col.deck_id_for_string(&note.deck)?;
            self.deck_ids.insert(note.deck.clone(), did);
            did
        })
    }

    fn import_foreign_notes(&mut self, notes: Vec<ForeignNote>) -> Result<NoteLog> {
        let mut log = NoteLog::default();
        for foreign in notes {
            if let Some(notetype) = self.notetype_for_note(&foreign)? {
                if let Some(deck_id) = self.deck_id_for_note(&foreign)? {
                    let log_note = self.import_foreign_note(foreign, &notetype, deck_id)?;
                    log.new.push(log_note);
                }
            }
        }
        Ok(log)
    }

    fn import_foreign_note(
        &mut self,
        foreign: ForeignNote,
        notetype: &Notetype,
        deck_id: DeckId,
    ) -> Result<LogNote> {
        let (mut note, mut cards) = foreign.into_native(notetype, deck_id);
        self.import_note(&mut note, notetype)?;
        self.import_cards(&mut cards, note.id)?;
        self.generate_missing_cards(notetype, deck_id, &note)?;
        Ok(note.into_log_note())
    }

    fn import_note(&mut self, note: &mut Note, notetype: &Notetype) -> Result<()> {
        self.col.canonify_note_tags(note, self.usn)?;
        note.prepare_for_update(notetype, self.normalize_notes)?;
        note.usn = self.usn;
        self.col.add_note_only_undoable(note)
    }

    fn import_cards(&mut self, cards: &mut [Card], note_id: NoteId) -> Result<()> {
        for card in cards {
            card.note_id = note_id;
            self.col.add_card(card)?;
        }
        Ok(())
    }

    fn generate_missing_cards(
        &mut self,
        notetype: &Notetype,
        deck_id: DeckId,
        note: &Note,
    ) -> Result<()> {
        let card_gen_context = CardGenContext::new(notetype, Some(deck_id), self.usn);
        self.col
            .generate_cards_for_existing_note(&card_gen_context, note)
    }
}

impl Collection {
    pub(super) fn deck_id_for_string(&mut self, deck: &str) -> Result<Option<DeckId>> {
        if let Ok(did) = deck.parse::<DeckId>() {
            if self.get_deck(did)?.is_some() {
                return Ok(Some(did));
            }
        }
        self.get_deck_id(deck)
    }

    pub(super) fn notetype_for_string(&mut self, notetype: &str) -> Result<Option<Arc<Notetype>>> {
        if let Some(nt) = self.get_notetype_for_id_string(notetype)? {
            Ok(Some(nt))
        } else {
            self.get_notetype_by_name(notetype)
        }
    }

    fn get_notetype_for_id_string(&mut self, notetype: &str) -> Result<Option<Arc<Notetype>>> {
        notetype
            .parse::<NotetypeId>()
            .ok()
            .map(|ntid| self.get_notetype(ntid))
            .unwrap_or(Ok(None))
    }
}

impl ForeignNote {
    fn into_native(self, notetype: &Notetype, deck_id: DeckId) -> (Note, Vec<Card>) {
        let mut note = Note::new(notetype);
        note.tags = self.tags;
        note.fields_mut()
            .iter_mut()
            .zip(self.fields.into_iter())
            .for_each(|(field, value)| *field = value);
        let cards = self
            .cards
            .into_iter()
            .enumerate()
            .map(|(idx, c)| c.into_native(NoteId(0), idx as u16, deck_id))
            .collect();
        (note, cards)
    }
}

impl ForeignCard {
    fn into_native(self, note_id: NoteId, template_idx: u16, deck_id: DeckId) -> Card {
        let mut card = Card::new(note_id, template_idx, deck_id, self.due);
        card.interval = self.ivl;
        card.ease_factor = self.factor;
        card.reps = self.reps;
        card.lapses = self.lapses;
        card
    }
}

impl ForeignNotetype {
    fn into_native(self) -> Notetype {
        Notetype {
            name: self.name,
            fields: self.fields.into_iter().map(NoteField::new).collect(),
            templates: self
                .templates
                .into_iter()
                .map(ForeignTemplate::into_native)
                .collect(),
            config: if self.is_cloze {
                NotetypeConfig::new_cloze()
            } else {
                NotetypeConfig::new()
            },
            ..Notetype::default()
        }
    }
}

impl ForeignTemplate {
    fn into_native(self) -> CardTemplate {
        CardTemplate::new(self.name, self.qfmt, self.afmt)
    }
}
