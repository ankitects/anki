// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, collections::HashMap, sync::Arc};

use crate::{
    card::{CardQueue, CardType},
    import_export::{
        text::{
            DupeResolution, ForeignCard, ForeignData, ForeignNote, ForeignNotetype, ForeignTemplate,
        },
        NoteLog,
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
            ctx.import_foreign_notes(self.notes, &self.global_tags)
        })
    }
}

impl NoteLog {
    fn new(dupe_resolution: DupeResolution) -> Self {
        Self {
            dupe_resolution: dupe_resolution as i32,
            ..Default::default()
        }
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
    today: u32,
    dupe_resolution: DupeResolution,
    card_gen_ctxs: HashMap<(NotetypeId, DeckId), CardGenContext<Arc<Notetype>>>,
    existing_notes: HashMap<(NotetypeId, u32), Vec<NoteId>>,
    //progress: IncrementableProgress<ImportProgress>,
}

impl NoteLog {
    fn log_note(&mut self, note: Note, unique: bool) {
        match unique {
            true => &mut self.new,
            false => &mut self.first_field_match,
        }
        .push(note.into_log_note());
    }
}

impl<'a> Context<'a> {
    fn new(data: &ForeignData, col: &'a mut Collection) -> Result<Self> {
        let usn = col.usn()?;
        let normalize_notes = col.get_config_bool(BoolKey::NormalizeNoteText);
        let today = col.timing_today()?.days_elapsed;
        let mut notetypes = HashMap::new();
        notetypes.insert(
            String::new(),
            col.notetype_for_string(&data.default_notetype)?,
        );
        let mut deck_ids = HashMap::new();
        deck_ids.insert(String::new(), col.deck_id_for_string(&data.default_deck)?);
        let existing_notes = col.storage.all_notes_by_type_and_checksum()?;
        Ok(Self {
            col,
            usn,
            normalize_notes,
            today,
            dupe_resolution: data.dupe_resolution,
            notetypes,
            deck_ids,
            card_gen_ctxs: HashMap::new(),
            existing_notes,
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
        global_tags: &[String],
    ) -> Result<NoteLog> {
        let mut log = NoteLog::new(self.dupe_resolution);
        for foreign in notes {
            if let Some(notetype) = self.notetype_for_note(&foreign)? {
                if let Some(deck_id) = self.deck_id_for_note(&foreign)? {
                    self.import_foreign_note(foreign, notetype, deck_id, global_tags, &mut log)?;
                } else {
                    log.missing_deck.push(foreign.into_log_note());
                }
            } else {
                log.missing_notetype.push(foreign.into_log_note());
            }
        }
        Ok(log)
    }

    fn import_foreign_note(
        &mut self,
        foreign: ForeignNote,
        notetype: Arc<Notetype>,
        deck_id: DeckId,
        global_tags: &[String],
        log: &mut NoteLog,
    ) -> Result<()> {
        let (mut note, mut cards) =
            foreign.into_native(&notetype, deck_id, self.today, global_tags);
        let unique = self.import_note(&mut note, &notetype)?;
        if !note_was_ignored(unique, self.dupe_resolution) {
            self.import_cards(&mut cards, note.id)?;
            self.generate_missing_cards(notetype, deck_id, &note)?;
        }
        // FIXME: There may be multiple duplicates that got updated, but this logs only the last one.
        // If more than were logged, the total of all logged notes would no longer match the number
        // of notes in the imported file.
        log.log_note(note, unique);
        Ok(())
    }

    /// True if note was unique.
    fn import_note(&mut self, note: &mut Note, notetype: &Notetype) -> Result<bool> {
        note.prepare_for_update(notetype, self.normalize_notes)?;
        let dupes = self.find_duplicates(notetype, note)?;
        match self.dupe_resolution {
            _ if dupes.is_empty() => self.col.add_prepared_note(note, self.usn)?,
            DupeResolution::Add => self.col.add_prepared_note(note, self.usn)?,
            DupeResolution::Ignore => (),
            DupeResolution::Update => self.col.update_with_prepared_note(note, &dupes, self.usn)?,
        }
        Ok(dupes.is_empty())
    }

    fn find_duplicates(&mut self, notetype: &Notetype, note: &mut Note) -> Result<Vec<Note>> {
        let checksum = note
            .checksum
            .ok_or_else(|| AnkiError::invalid_input("note unprepared"))?;
        self.existing_notes
            .get(&(notetype.id, checksum))
            .map(|dupe_ids| self.col.get_full_duplicates(note, dupe_ids))
            .unwrap_or_else(|| Ok(vec![]))
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

fn note_was_ignored(unique: bool, dupe_resolution: DupeResolution) -> bool {
    !unique && matches!(dupe_resolution, DupeResolution::Ignore)
}

impl Note {
    fn first_field_stripped(&self) -> Cow<str> {
        strip_html_preserving_media_filenames(&self.fields()[0])
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

    fn add_prepared_note(&mut self, note: &mut Note, usn: Usn) -> Result<()> {
        self.canonify_note_tags(note, usn)?;
        note.usn = usn;
        self.add_note_only_undoable(note)
    }

    fn update_with_prepared_note(
        &mut self,
        note: &mut Note,
        dupes: &[Note],
        usn: Usn,
    ) -> Result<()> {
        self.canonify_note_tags(note, usn)?;
        note.set_modified(usn);
        for dupe in dupes {
            note.id = dupe.id;
            self.update_note_undoable(note, dupe)?;
        }
        Ok(())
    }

    fn get_full_duplicates(&mut self, note: &Note, dupe_ids: &[NoteId]) -> Result<Vec<Note>> {
        let first_field = note.first_field_stripped();
        dupe_ids
            .iter()
            .filter_map(|&dupe_id| self.storage.get_note(dupe_id).transpose())
            .filter(|res| match res {
                Ok(dupe) => dupe.first_field_stripped() == first_field,
                Err(_) => true,
            })
            .collect()
    }
}

impl ForeignNote {
    fn into_native(
        self,
        notetype: &Notetype,
        deck_id: DeckId,
        today: u32,
        extra_tags: &[String],
    ) -> (Note, Vec<Card>) {
        // TODO: Handle new and learning cards
        let mut note = Note::new(notetype);
        note.tags = self.tags;
        note.tags.extend(extra_tags.iter().cloned());
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

    #[test]
    fn should_recognize_normalized_duplicate_only_if_normalization_is_enabled() {
        let mut col = open_test_collection();
        col.add_new_note_with_fields("Basic", &["神", "old"]);
        let mut data = ForeignData::with_defaults();
        data.dupe_resolution = DupeResolution::Update;
        data.add_note(&["神", "new"]);

        data.clone().import(&mut col).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].fields(), &["神", "new"]);

        col.set_config_bool(BoolKey::NormalizeNoteText, false, false)
            .unwrap();
        data.import(&mut col).unwrap();
        let notes = col.storage.get_all_notes();
        assert_eq!(notes[0].fields(), &["神", "new"]);
        assert_eq!(notes[1].fields(), &["神", "new"]);
    }

    #[test]
    fn should_add_global_tags() {
        let mut col = open_test_collection();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["foo"]);
        data.notes[0].tags = vec![String::from("bar")];
        data.global_tags = vec![String::from("baz")];

        data.import(&mut col).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].tags, ["bar", "baz"]);
    }
}
