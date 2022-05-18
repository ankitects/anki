// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, sync::Arc};

use crate::{
    card::{CardQueue, CardType},
    import_export::{
        text::{
            DupeResolution, ForeignCard, ForeignData, ForeignNote, ForeignNotetype, ForeignTemplate,
        },
        LogNote, NoteLog,
    },
    notetype::{CardGenContext, CardTemplate, NoteField, NotetypeConfig},
    prelude::*,
    text::strip_html_preserving_media_filenames,
};

impl ForeignData {
    pub fn import(self, col: &mut Collection) -> Result<OpOutput<NoteLog>> {
        col.transact(Op::Import, |col| {
            let mut ctx = Context::new(&self, col)?;
            ctx.import_foreign_notetypes(self.notetypes)?;
            ctx.import_foreign_notes(self.notes, self.dupe_resolution)
        })
    }
}

struct Context<'a> {
    col: &'a mut Collection,
    /// Notetypes by their name or id as string. The empty string yields the
    /// default notetype (which may be [None]).
    notetypes: HashMap<String, Option<Arc<Notetype>>>,
    /// Deck ids by their decks' name or id as string. The empty string yields
    /// the default deck (which may be [None]).
    deck_ids: HashMap<String, Option<DeckId>>,
    usn: Usn,
    normalize_notes: bool,
    card_gen_ctxs: HashMap<(NotetypeId, DeckId), CardGenContext<Arc<Notetype>>>,
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
            card_gen_ctxs: HashMap::new(),
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

    fn import_foreign_notes(
        &mut self,
        notes: Vec<ForeignNote>,
        dupe_resolution: DupeResolution,
    ) -> Result<NoteLog> {
        let mut log = NoteLog::default();
        for foreign in notes {
            if let Some(notetype) = self.notetype_for_note(&foreign)? {
                if let Some(deck_id) = self.deck_id_for_note(&foreign)? {
                    let log_note =
                        self.import_foreign_note(foreign, notetype, deck_id, dupe_resolution)?;
                    log.new.push(log_note);
                }
            }
        }
        Ok(log)
    }

    fn import_foreign_note(
        &mut self,
        foreign: ForeignNote,
        notetype: Arc<Notetype>,
        deck_id: DeckId,
        dupe_resolution: DupeResolution,
    ) -> Result<LogNote> {
        let today = self.col.timing_today()?.days_elapsed;
        let (mut note, mut cards) = foreign.into_native(&notetype, deck_id, today);
        self.import_note(&mut note, &notetype, dupe_resolution)?;
        self.import_cards(&mut cards, note.id)?;
        self.generate_missing_cards(notetype, deck_id, &note)?;
        Ok(note.into_log_note())
    }

    /// Returns the imported notes.
    fn import_note(
        &mut self,
        note: &mut Note,
        notetype: &Notetype,
        dupe_resolution: DupeResolution,
    ) -> Result<()> {
        note.prepare_for_update(notetype, self.normalize_notes)?;

        match dupe_resolution {
            DupeResolution::Add => self.add_prepared_note(note),
            DupeResolution::Ignore => match self.note_duplicate(note)? {
                Some(_) => Ok(()),
                None => self.add_prepared_note(note),
            },
            DupeResolution::Update => match self.note_duplicate(note)? {
                Some(dupe_id) => self.update_with_prepared_note(note, dupe_id),
                None => self.add_prepared_note(note),
            },
        }
    }

    /// Caller must have called [Note::prepare_for_update()].
    fn note_duplicate(&mut self, note: &Note) -> Result<Option<NoteId>> {
        let first_stripped = strip_html_preserving_media_filenames(&note.fields()[0]);
        Ok(self
            .col
            .storage
            .note_fields_by_checksum(note.notetype_id, note.checksum.expect("set by caller"))?
            .into_iter()
            .filter(|(_, field)| strip_html_preserving_media_filenames(field) == first_stripped)
            .map(|(nid, _)| nid)
            // FIXME: There may be multiple duplicates and the old Python code updated them all in this
            // case. However, this is probably not generally useful and worth the additional complexity.
            // The note should probably be skipped, or an error could be raised.
            .next())
    }

    fn add_prepared_note(&mut self, note: &mut Note) -> Result<()> {
        self.col.canonify_note_tags(note, self.usn)?;
        note.usn = self.usn;
        self.col.add_note_only_undoable(note)
    }

    fn update_with_prepared_note(&mut self, note: &mut Note, dupe_id: NoteId) -> Result<()> {
        let dupe = self
            .col
            .storage
            .get_note(dupe_id)?
            .ok_or(AnkiError::NotFound)?;
        self.col.canonify_note_tags(note, self.usn)?;
        note.set_modified(self.usn);
        note.id = dupe.id;
        self.col.update_note_undoable(note, &dupe)?;
        Ok(())
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
        notetype: Arc<Notetype>,
        deck_id: DeckId,
        note: &Note,
    ) -> Result<()> {
        let card_gen_context = self
            .card_gen_ctxs
            .entry((notetype.id, deck_id))
            .or_insert_with(|| CardGenContext::new(notetype, Some(deck_id), self.usn));
        self.col
            .generate_cards_for_existing_note(card_gen_context, note)
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

    pub(super) fn notetype_for_string(
        &mut self,
        name_or_id: &str,
    ) -> Result<Option<Arc<Notetype>>> {
        if let Some(nt) = self.get_notetype_for_id_string(name_or_id)? {
            Ok(Some(nt))
        } else {
            self.get_notetype_by_name(name_or_id)
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
    fn into_native(self, notetype: &Notetype, deck_id: DeckId, today: u32) -> (Note, Vec<Card>) {
        // TODO: Handle new and learning cards
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
            .map(|(idx, c)| c.into_native(NoteId(0), idx as u16, deck_id, today))
            .collect();
        (note, cards)
    }
}

impl ForeignCard {
    fn into_native(self, note_id: NoteId, template_idx: u16, deck_id: DeckId, today: u32) -> Card {
        Card {
            note_id,
            template_idx,
            deck_id,
            due: self.native_due(today),
            interval: self.interval,
            ease_factor: (self.ease_factor * 1000.).round() as u16,
            reps: self.reps,
            lapses: self.lapses,
            ctype: CardType::Review,
            queue: CardQueue::Review,
            ..Default::default()
        }
    }

    fn native_due(self, today: u32) -> i32 {
        let remaining_secs = self.interval as i64 - TimestampSecs::now().0;
        let remaining_days = remaining_secs / (60 * 60 * 24);
        0.max(remaining_days as i32 + today as i32)
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

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;

    impl ForeignData {
        fn with_defaults() -> Self {
            Self {
                default_notetype: "Basic".to_string(),
                default_deck: "1".to_string(),
                ..Default::default()
            }
        }

        fn add_note(&mut self, fields: &[&str]) {
            self.notes.push(ForeignNote {
                fields: fields.iter().map(ToString::to_string).collect(),
                ..Default::default()
            });
        }
    }

    #[test]
    fn should_always_add_note_if_dupe_mode_is_add() {
        let mut col = open_test_collection();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Add;

        data.clone().import(&mut col).unwrap();
        data.import(&mut col).unwrap();
        assert_eq!(col.storage.notes_table_len(), 2);
    }

    #[test]
    fn should_add_or_ignore_note_if_dupe_mode_is_ignore() {
        let mut col = open_test_collection();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Ignore;

        data.clone().import(&mut col).unwrap();
        assert_eq!(col.storage.notes_table_len(), 1);

        data.notes[0].fields[1] = "new".to_string();
        data.import(&mut col).unwrap();
        let notes = col.storage.get_all_notes();
        assert_eq!(notes.len(), 1);
        assert_eq!(notes[0].fields()[1], "old");
    }

    #[test]
    fn should_update_or_add_note_if_dupe_mode_is_update() {
        let mut col = open_test_collection();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Update;

        data.clone().import(&mut col).unwrap();
        assert_eq!(col.storage.notes_table_len(), 1);

        data.notes[0].fields[1] = "new".to_string();
        data.import(&mut col).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].fields()[1], "new");
    }
}
