// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    mem,
    sync::Arc,
};

use super::NameOrId;
use crate::{
    card::{CardQueue, CardType},
    config::I32ConfigKey,
    import_export::{
        text::{
            DupeResolution, ForeignCard, ForeignData, ForeignNote, ForeignNotetype, ForeignTemplate,
        },
        ImportProgress, IncrementableProgress, LogNote, NoteLog,
    },
    notetype::{CardGenContext, CardTemplate, NoteField, NotetypeConfig},
    prelude::*,
    text::strip_html_preserving_media_filenames,
};

impl ForeignData {
    pub fn import(
        self,
        col: &mut Collection,
        progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
    ) -> Result<OpOutput<NoteLog>> {
        let mut progress = IncrementableProgress::new(progress_fn);
        progress.call(ImportProgress::File)?;
        col.transact(Op::Import, |col| {
            col.set_config_i32_inner(
                I32ConfigKey::CsvDuplicateResolution,
                self.dupe_resolution as i32,
            )?;
            let mut ctx = Context::new(&self, col)?;
            ctx.import_foreign_notetypes(self.notetypes)?;
            ctx.import_foreign_notes(
                self.notes,
                &self.global_tags,
                &self.updated_tags,
                &mut progress,
            )
        })
    }
}

impl NoteLog {
    fn new(dupe_resolution: DupeResolution, found_notes: u32) -> Self {
        Self {
            dupe_resolution: dupe_resolution as i32,
            found_notes,
            ..Default::default()
        }
    }
}

struct Context<'a> {
    col: &'a mut Collection,
    /// Contains the optional default notetype with the default key.
    notetypes: HashMap<NameOrId, Option<Arc<Notetype>>>,
    deck_ids: DeckIdsByNameOrId,
    usn: Usn,
    normalize_notes: bool,
    today: u32,
    dupe_resolution: DupeResolution,
    card_gen_ctxs: HashMap<(NotetypeId, DeckId), CardGenContext<Arc<Notetype>>>,
    existing_checksums: HashMap<(NotetypeId, u32), Vec<NoteId>>,
    existing_guids: HashMap<String, NoteId>,
}

struct DeckIdsByNameOrId {
    ids: HashSet<DeckId>,
    names: HashMap<String, DeckId>,
    default: Option<DeckId>,
}

struct NoteContext {
    /// Prepared and with canonified tags.
    note: Note,
    dupes: Vec<Duplicate>,
    cards: Vec<Card>,
    notetype: Arc<Notetype>,
    deck_id: DeckId,
}

struct Duplicate {
    note: Note,
    identical: bool,
    first_field_match: bool,
}

impl Duplicate {
    fn new(dupe: Note, original: &Note, first_field_match: bool) -> Self {
        let identical = dupe.equal_fields_and_tags(original);
        Self {
            note: dupe,
            identical,
            first_field_match,
        }
    }
}

impl DeckIdsByNameOrId {
    fn new(col: &mut Collection, default: &NameOrId) -> Result<Self> {
        let names: HashMap<String, DeckId> = col
            .get_all_normal_deck_names()?
            .into_iter()
            .map(|(id, name)| (name, id))
            .collect();
        let ids = names.values().copied().collect();
        let mut new = Self {
            ids,
            names,
            default: None,
        };
        new.default = new.get(default);

        Ok(new)
    }

    fn get(&self, name_or_id: &NameOrId) -> Option<DeckId> {
        match name_or_id {
            _ if *name_or_id == NameOrId::default() => self.default,
            NameOrId::Id(id) => self.ids.get(&DeckId(*id)).copied(),
            NameOrId::Name(name) => self.names.get(name).copied(),
        }
    }
}

impl<'a> Context<'a> {
    fn new(data: &ForeignData, col: &'a mut Collection) -> Result<Self> {
        let usn = col.usn()?;
        let normalize_notes = col.get_config_bool(BoolKey::NormalizeNoteText);
        let today = col.timing_today()?.days_elapsed;
        let mut notetypes = HashMap::new();
        notetypes.insert(
            NameOrId::default(),
            col.notetype_by_name_or_id(&data.default_notetype)?,
        );
        let deck_ids = DeckIdsByNameOrId::new(col, &data.default_deck)?;
        let existing_checksums = col.storage.all_notes_by_type_and_checksum()?;
        let existing_guids = col.storage.all_notes_by_guid()?;

        Ok(Self {
            col,
            usn,
            normalize_notes,
            today,
            dupe_resolution: data.dupe_resolution,
            notetypes,
            deck_ids,
            card_gen_ctxs: HashMap::new(),
            existing_checksums,
            existing_guids,
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
            let nt = self.col.notetype_by_name_or_id(&note.notetype)?;
            self.notetypes.insert(note.notetype.clone(), nt.clone());
            nt
        })
    }

    fn import_foreign_notes(
        &mut self,
        notes: Vec<ForeignNote>,
        global_tags: &[String],
        updated_tags: &[String],
        progress: &mut IncrementableProgress<ImportProgress>,
    ) -> Result<NoteLog> {
        let mut incrementor = progress.incrementor(ImportProgress::Notes);
        let mut log = NoteLog::new(self.dupe_resolution, notes.len() as u32);
        for foreign in notes {
            incrementor.increment()?;
            if foreign.first_field_is_empty() {
                log.empty_first_field.push(foreign.into_log_note());
                continue;
            }
            if let Some(notetype) = self.notetype_for_note(&foreign)? {
                if let Some(deck_id) = self.deck_ids.get(&foreign.deck) {
                    let ctx = self.build_note_context(foreign, notetype, deck_id, global_tags)?;
                    self.import_note(ctx, updated_tags, &mut log)?;
                } else {
                    log.missing_deck.push(foreign.into_log_note());
                }
            } else {
                log.missing_notetype.push(foreign.into_log_note());
            }
        }
        Ok(log)
    }

    fn build_note_context(
        &mut self,
        foreign: ForeignNote,
        notetype: Arc<Notetype>,
        deck_id: DeckId,
        global_tags: &[String],
    ) -> Result<NoteContext> {
        let (mut note, cards) = foreign.into_native(&notetype, deck_id, self.today, global_tags);
        note.prepare_for_update(&notetype, self.normalize_notes)?;
        self.col.canonify_note_tags(&mut note, self.usn)?;
        let dupes = self.find_duplicates(&notetype, &note)?;

        Ok(NoteContext {
            note,
            dupes,
            cards,
            notetype,
            deck_id,
        })
    }

    fn find_duplicates(&self, notetype: &Notetype, note: &Note) -> Result<Vec<Duplicate>> {
        let checksum = note
            .checksum
            .ok_or_else(|| AnkiError::invalid_input("note unprepared"))?;
        if let Some(nid) = self.existing_guids.get(&note.guid) {
            self.get_guid_dupe(*nid, note).map(|dupe| vec![dupe])
        } else if let Some(nids) = self.existing_checksums.get(&(notetype.id, checksum)) {
            self.get_first_field_dupes(note, nids)
        } else {
            Ok(Vec::new())
        }
    }

    fn get_guid_dupe(&self, nid: NoteId, original: &Note) -> Result<Duplicate> {
        self.col
            .storage
            .get_note(nid)?
            .ok_or(AnkiError::NotFound)
            .map(|dupe| Duplicate::new(dupe, original, false))
    }

    fn get_first_field_dupes(&self, note: &Note, nids: &[NoteId]) -> Result<Vec<Duplicate>> {
        Ok(self
            .col
            .get_full_duplicates(note, nids)?
            .into_iter()
            .map(|dupe| Duplicate::new(dupe, note, true))
            .collect())
    }

    fn import_note(
        &mut self,
        ctx: NoteContext,
        updated_tags: &[String],
        log: &mut NoteLog,
    ) -> Result<()> {
        match self.dupe_resolution {
            _ if ctx.dupes.is_empty() => self.add_note(ctx, &mut log.new)?,
            DupeResolution::Add => self.add_note(ctx, &mut log.first_field_match)?,
            DupeResolution::Update => self.update_with_note(ctx, updated_tags, log)?,
            DupeResolution::Ignore => log.first_field_match.push(ctx.note.into_log_note()),
        }
        Ok(())
    }

    fn add_note(&mut self, mut ctx: NoteContext, log_queue: &mut Vec<LogNote>) -> Result<()> {
        ctx.note.usn = self.usn;
        self.col.add_note_only_undoable(&mut ctx.note)?;
        self.add_cards(&mut ctx.cards, &ctx.note, ctx.deck_id, ctx.notetype)?;
        log_queue.push(ctx.note.into_log_note());
        Ok(())
    }

    fn add_cards(
        &mut self,
        cards: &mut [Card],
        note: &Note,
        deck_id: DeckId,
        notetype: Arc<Notetype>,
    ) -> Result<()> {
        self.import_cards(cards, note.id)?;
        self.generate_missing_cards(notetype, deck_id, note)
    }

    fn update_with_note(
        &mut self,
        mut ctx: NoteContext,
        updated_tags: &[String],
        log: &mut NoteLog,
    ) -> Result<()> {
        self.prepare_note_for_update(&mut ctx.note, updated_tags)?;
        for dupe in mem::take(&mut ctx.dupes) {
            self.maybe_update_dupe(dupe, &mut ctx, log)?;
        }
        Ok(())
    }

    fn prepare_note_for_update(&mut self, note: &mut Note, updated_tags: &[String]) -> Result<()> {
        if !updated_tags.is_empty() {
            note.tags.extend(updated_tags.iter().cloned());
            self.col.canonify_note_tags(note, self.usn)?;
        }
        note.set_modified(self.usn);
        Ok(())
    }

    fn maybe_update_dupe(
        &mut self,
        dupe: Duplicate,
        ctx: &mut NoteContext,
        log: &mut NoteLog,
    ) -> Result<()> {
        if dupe.note.notetype_id != ctx.notetype.id {
            log.conflicting.push(dupe.note.into_log_note());
            return Ok(());
        }
        if dupe.identical {
            log.duplicate.push(dupe.note.into_log_note());
        } else {
            self.update_dupe(dupe, ctx, log)?;
        }
        self.add_cards(&mut ctx.cards, &ctx.note, ctx.deck_id, ctx.notetype.clone())
    }

    fn update_dupe(
        &mut self,
        dupe: Duplicate,
        ctx: &mut NoteContext,
        log: &mut NoteLog,
    ) -> Result<()> {
        ctx.note.id = dupe.note.id;
        ctx.note.guid = dupe.note.guid.clone();
        self.col.update_note_undoable(&ctx.note, &dupe.note)?;
        if dupe.first_field_match {
            log.first_field_match.push(dupe.note.into_log_note());
        } else {
            log.updated.push(dupe.note.into_log_note());
        }
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

impl Note {
    fn first_field_stripped(&self) -> Cow<str> {
        strip_html_preserving_media_filenames(&self.fields()[0])
    }
}

impl Collection {
    pub(super) fn deck_id_by_name_or_id(&mut self, deck: &NameOrId) -> Result<Option<DeckId>> {
        match deck {
            NameOrId::Id(id) => Ok(self.get_deck(DeckId(*id))?.map(|_| DeckId(*id))),
            NameOrId::Name(name) => self.get_deck_id(name),
        }
    }

    pub(super) fn notetype_by_name_or_id(
        &mut self,
        notetype: &NameOrId,
    ) -> Result<Option<Arc<Notetype>>> {
        match notetype {
            NameOrId::Id(id) => self.get_notetype(NotetypeId(*id)),
            NameOrId::Name(name) => self.get_notetype_by_name(name),
        }
    }

    fn get_full_duplicates(&self, note: &Note, dupe_ids: &[NoteId]) -> Result<Vec<Note>> {
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
        if !self.guid.is_empty() {
            note.guid = self.guid;
        }
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

    fn first_field_is_empty(&self) -> bool {
        self.fields.get(0).map(String::is_empty).unwrap_or(true)
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

impl Note {
    fn equal_fields_and_tags(&self, other: &Self) -> bool {
        self.fields() == other.fields() && self.tags == other.tags
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;

    impl ForeignData {
        fn with_defaults() -> Self {
            Self {
                default_notetype: NameOrId::Name("Basic".to_string()),
                default_deck: NameOrId::Id(1),
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

        data.clone().import(&mut col, |_, _| true).unwrap();
        data.import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.notes_table_len(), 2);
    }

    #[test]
    fn should_add_or_ignore_note_if_dupe_mode_is_ignore() {
        let mut col = open_test_collection();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Ignore;

        data.clone().import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.notes_table_len(), 1);

        data.notes[0].fields[1] = "new".to_string();
        data.import(&mut col, |_, _| true).unwrap();
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

        data.clone().import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.notes_table_len(), 1);

        data.notes[0].fields[1] = "new".to_string();
        data.import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].fields()[1], "new");
    }

    #[test]
    fn should_recognize_normalized_duplicate_only_if_normalization_is_enabled() {
        let mut col = open_test_collection();
        col.add_new_note_with_fields("Basic", &["神", "old"]);
        let mut data = ForeignData::with_defaults();
        data.dupe_resolution = DupeResolution::Update;
        data.add_note(&["神", "new"]);

        data.clone().import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].fields(), &["神", "new"]);

        col.set_config_bool(BoolKey::NormalizeNoteText, false, false)
            .unwrap();
        data.import(&mut col, |_, _| true).unwrap();
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

        data.import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].tags, ["bar", "baz"]);
    }

    #[test]
    fn should_match_note_with_same_guid() {
        let mut col = open_test_collection();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["foo"]);
        data.notes[0].tags = vec![String::from("bar")];
        data.global_tags = vec![String::from("baz")];

        data.import(&mut col, |_, _| true).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].tags, ["bar", "baz"]);
    }
}
