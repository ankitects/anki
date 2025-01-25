// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::sync::Arc;

use unicase::UniCase;

use super::NameOrId;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::config::I32ConfigKey;
use crate::import_export::text::DupeResolution;
use crate::import_export::text::ForeignCard;
use crate::import_export::text::ForeignData;
use crate::import_export::text::ForeignNote;
use crate::import_export::text::ForeignNotetype;
use crate::import_export::text::ForeignTemplate;
use crate::import_export::text::MatchScope;
use crate::import_export::ImportProgress;
use crate::import_export::NoteLog;
use crate::notes::field_checksum;
use crate::notes::normalize_field;
use crate::notetype::CardGenContext;
use crate::notetype::CardTemplate;
use crate::notetype::NoteField;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::scheduler::timing::SchedTimingToday;
use crate::text::strip_html_preserving_media_filenames;

impl ForeignData {
    pub fn import(
        self,
        col: &mut Collection,
        mut progress: ThrottlingProgressHandler<ImportProgress>,
    ) -> Result<OpOutput<NoteLog>> {
        progress.set(ImportProgress::File)?;
        col.transact(Op::Import, |col| {
            self.update_config(col)?;
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

    fn update_config(&self, col: &mut Collection) -> Result<()> {
        col.set_config_i32_inner(
            I32ConfigKey::CsvDuplicateResolution,
            self.dupe_resolution as i32,
        )?;
        col.set_config_i32_inner(I32ConfigKey::MatchScope, self.match_scope as i32)?;
        Ok(())
    }
}

fn new_note_log(dupe_resolution: DupeResolution, found_notes: u32) -> NoteLog {
    NoteLog {
        dupe_resolution: dupe_resolution as i32,
        found_notes,
        ..Default::default()
    }
}

struct Context<'a> {
    col: &'a mut Collection,
    /// Contains the optional default notetype with the default key.
    notetypes: HashMap<NameOrId, Option<Arc<Notetype>>>,
    deck_ids: DeckIdsByNameOrId,
    usn: Usn,
    normalize_notes: bool,
    timing: SchedTimingToday,
    dupe_resolution: DupeResolution,
    card_gen_ctxs: HashMap<(NotetypeId, DeckId), CardGenContext<Arc<Notetype>>>,
    existing_checksums: ExistingChecksums,
    existing_guids: HashMap<String, NoteId>,
}

struct DeckIdsByNameOrId {
    ids: HashSet<DeckId>,
    names: HashMap<UniCase<String>, DeckId>,
    default: Option<DeckId>,
}

/// Notes in the collection indexed by notetype, checksum and optionally deck.
/// With deck, a note will be included in as many entries as its cards
/// have different original decks.
#[derive(Debug)]
enum ExistingChecksums {
    ByNotetype(HashMap<(NotetypeId, u32), Vec<NoteId>>),
    ByNotetypeAndDeck(HashMap<(NotetypeId, u32, DeckId), Vec<NoteId>>),
}

impl ExistingChecksums {
    fn new(col: &mut Collection, match_scope: MatchScope) -> Result<Self> {
        match match_scope {
            MatchScope::Notetype => col
                .storage
                .all_notes_by_type_and_checksum()
                .map(Self::ByNotetype),
            MatchScope::NotetypeAndDeck => col
                .storage
                .all_notes_by_type_checksum_and_deck()
                .map(Self::ByNotetypeAndDeck),
        }
    }

    fn get(&self, notetype: NotetypeId, checksum: u32, deck: DeckId) -> Option<&Vec<NoteId>> {
        match self {
            Self::ByNotetype(map) => map.get(&(notetype, checksum)),
            Self::ByNotetypeAndDeck(map) => map.get(&(notetype, checksum, deck)),
        }
    }
}

struct NoteContext<'a> {
    note: ForeignNote,
    dupes: Vec<Duplicate>,
    notetype: Arc<Notetype>,
    deck_id: DeckId,
    global_tags: &'a [String],
    updated_tags: &'a [String],
}

struct Duplicate {
    note: Note,
    identical: bool,
    first_field_match: bool,
}

impl Duplicate {
    fn new(dupe: Note, original: &ForeignNote, first_field_match: bool) -> Self {
        let identical = original.equal_fields_and_tags(&dupe);
        Self {
            note: dupe,
            identical,
            first_field_match,
        }
    }
}

impl DeckIdsByNameOrId {
    fn new(col: &mut Collection, default: &NameOrId) -> Result<Self> {
        let names: HashMap<UniCase<String>, DeckId> = col
            .get_all_normal_deck_names(false)?
            .into_iter()
            .map(|(id, name)| (UniCase::new(name), id))
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
            NameOrId::Id(id) => self
                .ids
                .get(&DeckId(*id))
                // try treating it as a numeric deck name
                .or_else(|| self.names.get(&UniCase::new(id.to_string())))
                .copied(),
            NameOrId::Name(name) => self.names.get(&UniCase::new(name.to_string())).copied(),
        }
    }

    fn insert(&mut self, deck_id: DeckId, name: String) {
        self.ids.insert(deck_id);
        self.names.insert(UniCase::new(name), deck_id);
    }
}

impl<'a> Context<'a> {
    fn new(data: &ForeignData, col: &'a mut Collection) -> Result<Self> {
        let usn = col.usn()?;
        let normalize_notes = col.get_config_bool(BoolKey::NormalizeNoteText);
        let timing = col.timing_today()?;
        let mut notetypes = HashMap::new();
        notetypes.insert(
            NameOrId::default(),
            col.notetype_by_name_or_id(&data.default_notetype)?,
        );
        let deck_ids = DeckIdsByNameOrId::new(col, &data.default_deck)?;
        let existing_checksums = ExistingChecksums::new(col, data.match_scope)?;
        let existing_guids = col.storage.all_notes_by_guid()?;

        Ok(Self {
            col,
            usn,
            normalize_notes,
            timing,
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
        progress: &mut ThrottlingProgressHandler<ImportProgress>,
    ) -> Result<NoteLog> {
        let mut incrementor = progress.incrementor(ImportProgress::Notes);
        let mut log = new_note_log(self.dupe_resolution, notes.len() as u32);
        for foreign in notes {
            incrementor.increment()?;
            if foreign.first_field_is_the_empty_string() {
                log.empty_first_field.push(foreign.into_log_note());
                continue;
            }
            if let Some(notetype) = self.notetype_for_note(&foreign)? {
                if let Some(deck_id) = self.get_or_create_deck_id(&foreign.deck)? {
                    let ctx = self.build_note_context(
                        foreign,
                        notetype,
                        deck_id,
                        global_tags,
                        updated_tags,
                    )?;
                    self.import_note(ctx, &mut log)?;
                } else {
                    log.missing_deck.push(foreign.into_log_note());
                }
            } else {
                log.missing_notetype.push(foreign.into_log_note());
            }
        }
        Ok(log)
    }

    fn get_or_create_deck_id(&mut self, deck: &NameOrId) -> Result<Option<DeckId>> {
        Ok(if let Some(did) = self.deck_ids.get(deck) {
            Some(did)
        } else if let NameOrId::Name(name) = deck {
            let mut deck = Deck::new_normal();
            deck.name = NativeDeckName::from_human_name(name);
            self.col.add_deck_inner(&mut deck, self.usn)?;
            self.deck_ids.insert(deck.id, deck.human_name());
            Some(deck.id)
        } else {
            None
        })
    }

    fn build_note_context<'tags>(
        &mut self,
        mut note: ForeignNote,
        notetype: Arc<Notetype>,
        deck_id: DeckId,
        global_tags: &'tags [String],
        updated_tags: &'tags [String],
    ) -> Result<NoteContext<'tags>> {
        self.prepare_foreign_note(&mut note)?;
        let dupes = self.find_duplicates(&notetype, &note, deck_id)?;
        Ok(NoteContext {
            note,
            dupes,
            notetype,
            deck_id,
            global_tags,
            updated_tags,
        })
    }

    fn prepare_foreign_note(&mut self, note: &mut ForeignNote) -> Result<()> {
        note.normalize_fields(self.normalize_notes);
        self.col.canonify_foreign_tags(note, self.usn)
    }

    fn find_duplicates(
        &self,
        notetype: &Notetype,
        note: &ForeignNote,
        deck_id: DeckId,
    ) -> Result<Vec<Duplicate>> {
        if note.guid.is_empty() {
            if let Some(nids) = note
                .checksum()
                .and_then(|csum| self.existing_checksums.get(notetype.id, csum, deck_id))
            {
                return self.get_first_field_dupes(note, nids);
            }
        } else if let Some(nid) = self.existing_guids.get(&note.guid) {
            return self.get_guid_dupe(*nid, note).map(|dupe| vec![dupe]);
        }
        Ok(Vec::new())
    }

    fn get_guid_dupe(&self, nid: NoteId, original: &ForeignNote) -> Result<Duplicate> {
        self.col
            .storage
            .get_note(nid)?
            .or_not_found(nid)
            .map(|dupe| Duplicate::new(dupe, original, false))
    }

    fn get_first_field_dupes(&self, note: &ForeignNote, nids: &[NoteId]) -> Result<Vec<Duplicate>> {
        Ok(self
            .col
            .get_full_duplicates(note, nids)?
            .into_iter()
            .map(|dupe| Duplicate::new(dupe, note, true))
            .collect())
    }

    fn import_note(&mut self, ctx: NoteContext, log: &mut NoteLog) -> Result<()> {
        match self.dupe_resolution {
            _ if ctx.dupes.is_empty() => self.add_note(ctx, log)?,
            DupeResolution::Duplicate if ctx.is_guid_dupe() => log
                .duplicate
                .push(ctx.dupes.into_iter().next().unwrap().note.into_log_note()),
            DupeResolution::Duplicate if !ctx.has_first_field() => {
                log.empty_first_field.push(ctx.note.into_log_note())
            }
            DupeResolution::Duplicate => self.add_note(ctx, log)?,
            DupeResolution::Update => self.update_with_note(ctx, log)?,
            DupeResolution::Preserve => log
                .first_field_match
                .push(ctx.dupes.into_iter().next().unwrap().note.into_log_note()),
        }
        Ok(())
    }

    fn add_note(&mut self, ctx: NoteContext, log: &mut NoteLog) -> Result<()> {
        let mut note = Note::new(&ctx.notetype);
        let mut cards = ctx
            .note
            .into_native(&mut note, ctx.deck_id, &self.timing, ctx.global_tags);
        self.prepare_note(&mut note, &ctx.notetype)?;
        self.col.add_note_only_undoable(&mut note)?;
        self.add_cards(&mut cards, &note, ctx.deck_id, ctx.notetype)?;

        if ctx.dupes.is_empty() {
            log.new.push(note.into_log_note());
        } else {
            log.first_field_match.push(note.into_log_note());
        }

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

    fn update_with_note(&mut self, ctx: NoteContext, log: &mut NoteLog) -> Result<()> {
        let mut update_result = DuplicateUpdateResult::None;
        for dupe in ctx.dupes {
            if dupe.note.notetype_id != ctx.notetype.id {
                update_result.update(DuplicateUpdateResult::Conflicting(dupe));
                continue;
            }

            let mut note = dupe.note.clone();
            let mut cards = ctx.note.clone().into_native(
                &mut note,
                ctx.deck_id,
                &self.timing,
                ctx.global_tags.iter().chain(ctx.updated_tags.iter()),
            );

            if dupe.identical {
                update_result.update(DuplicateUpdateResult::Identical(dupe));
            } else {
                self.prepare_note(&mut note, &ctx.notetype)?;
                self.col.update_note_undoable(&note, &dupe.note)?;
                update_result.update(DuplicateUpdateResult::Update(dupe));
            }
            self.add_cards(&mut cards, &note, ctx.deck_id, ctx.notetype.clone())?;
        }
        update_result.log(log);

        Ok(())
    }

    fn prepare_note(&mut self, note: &mut Note, notetype: &Notetype) -> Result<()> {
        note.prepare_for_update(notetype, self.normalize_notes)?;
        self.col.canonify_note_tags(note, self.usn)?;
        note.set_modified(self.usn);
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

/// Helper enum to decide which result to log if multiple duplicates were found
/// for a single incoming note.
enum DuplicateUpdateResult {
    None,
    Conflicting(Duplicate),
    Identical(Duplicate),
    Update(Duplicate),
}

impl DuplicateUpdateResult {
    fn priority(&self) -> u8 {
        match self {
            DuplicateUpdateResult::None => 0,
            DuplicateUpdateResult::Conflicting(_) => 1,
            DuplicateUpdateResult::Identical(_) => 2,
            DuplicateUpdateResult::Update(_) => 3,
        }
    }

    fn update(&mut self, new: Self) {
        if self.priority() < new.priority() {
            *self = new;
        }
    }

    fn log(self, log: &mut NoteLog) {
        match self {
            DuplicateUpdateResult::None => (),
            DuplicateUpdateResult::Conflicting(dupe) => {
                log.conflicting.push(dupe.note.into_log_note())
            }
            DuplicateUpdateResult::Identical(dupe) => log.duplicate.push(dupe.note.into_log_note()),
            DuplicateUpdateResult::Update(dupe) if dupe.first_field_match => {
                log.first_field_match.push(dupe.note.into_log_note())
            }
            DuplicateUpdateResult::Update(dupe) => log.updated.push(dupe.note.into_log_note()),
        }
    }
}

impl NoteContext<'_> {
    fn is_guid_dupe(&self) -> bool {
        self.dupes
            .first()
            .map_or(false, |d| d.note.guid == self.note.guid)
    }

    fn has_first_field(&self) -> bool {
        self.note.first_field_is_unempty()
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
            NameOrId::Id(id) => Ok({
                match self.get_deck(DeckId(*id))?.map(|d| d.id) {
                    did @ Some(_) => did,
                    // try treating it as a numeric deck name
                    _ => self.get_deck_id(&id.to_string())?,
                }
            }),
            NameOrId::Name(name) => self.get_deck_id(name),
        }
    }

    pub(super) fn notetype_by_name_or_id(
        &mut self,
        notetype: &NameOrId,
    ) -> Result<Option<Arc<Notetype>>> {
        match notetype {
            NameOrId::Id(id) => Ok({
                match self.get_notetype(NotetypeId(*id))? {
                    nt @ Some(_) => nt,
                    // try treating it as a numeric notetype name
                    _ => self.get_notetype_by_name(&id.to_string())?,
                }
            }),
            NameOrId::Name(name) => self.get_notetype_by_name(name),
        }
    }

    fn canonify_foreign_tags(&mut self, note: &mut ForeignNote, usn: Usn) -> Result<()> {
        if let Some(tags) = note.tags.take() {
            note.tags
                .replace(self.canonify_tags_without_registering(tags, usn)?);
        }
        Ok(())
    }

    fn get_full_duplicates(&self, note: &ForeignNote, dupe_ids: &[NoteId]) -> Result<Vec<Note>> {
        let first_field = note.first_field_stripped().or_invalid("no first field")?;
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
    /// Updates a native note with the foreign data and returns its new cards.
    fn into_native<'tags>(
        self,
        note: &mut Note,
        deck_id: DeckId,
        timing: &SchedTimingToday,
        extra_tags: impl IntoIterator<Item = &'tags String>,
    ) -> Vec<Card> {
        // TODO: Handle new and learning cards
        if !self.guid.is_empty() {
            note.guid = self.guid;
        }
        if let Some(tags) = self.tags {
            note.tags = tags;
        }
        note.tags.extend(extra_tags.into_iter().cloned());
        note.fields_mut()
            .iter_mut()
            .zip(self.fields)
            .for_each(|(field, new)| {
                if let Some(s) = new {
                    *field = s;
                }
            });
        self.cards
            .into_iter()
            .enumerate()
            .map(|(idx, c)| c.into_native(NoteId(0), idx as u16, deck_id, timing))
            .collect()
    }

    fn first_field_is_the_empty_string(&self) -> bool {
        matches!(self.fields.first(), Some(Some(s)) if s.is_empty())
    }

    fn first_field_is_unempty(&self) -> bool {
        matches!(self.fields.first(), Some(Some(s)) if !s.is_empty())
    }

    fn normalize_fields(&mut self, normalize_text: bool) {
        for field in self.fields.iter_mut().flatten() {
            normalize_field(field, normalize_text);
        }
    }

    /// Expects normalized form.
    fn equal_fields_and_tags(&self, other: &Note) -> bool {
        self.tags.as_ref().map_or(true, |tags| *tags == other.tags)
            && self
                .fields
                .iter()
                .zip(other.fields())
                .all(|(opt, field)| opt.as_ref().map(|s| s == field).unwrap_or(true))
    }

    fn first_field_stripped(&self) -> Option<Cow<str>> {
        self.fields
            .first()
            .and_then(|s| s.as_ref())
            .map(|field| strip_html_preserving_media_filenames(field.as_str()))
    }

    /// If the first field is set, returns its checksum. Field is expected to be
    /// normalized.
    fn checksum(&self) -> Option<u32> {
        self.first_field_stripped()
            .map(|field| field_checksum(&field))
    }
}

impl ForeignCard {
    fn into_native(
        self,
        note_id: NoteId,
        template_idx: u16,
        deck_id: DeckId,
        timing: &SchedTimingToday,
    ) -> Card {
        Card {
            note_id,
            template_idx,
            deck_id,
            due: self.native_due(timing),
            interval: self.interval,
            ease_factor: (self.ease_factor * 1000.).round() as u16,
            reps: self.reps,
            lapses: self.lapses,
            ctype: CardType::Review,
            queue: CardQueue::Review,
            ..Default::default()
        }
    }

    fn native_due(self, timing: &SchedTimingToday) -> i32 {
        let day_start = timing.next_day_at.0 - 86_400;
        let due_delta = (self.due - day_start) / 86_400;
        due_delta as i32 + timing.days_elapsed as i32
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
                Notetype::new_cloze_config()
            } else {
                Notetype::new_config()
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
    use crate::tests::DeckAdder;
    use crate::tests::NoteAdder;

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
                fields: fields.iter().map(ToString::to_string).map(Some).collect(),
                ..Default::default()
            });
        }
    }

    #[test]
    fn should_always_add_note_if_dupe_mode_is_add() {
        let mut col = Collection::new();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Duplicate;

        let progress = col.new_progress_handler();
        data.clone().import(&mut col, progress).unwrap();
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        assert_eq!(col.storage.notes_table_len(), 2);
    }

    #[test]
    fn should_add_or_ignore_note_if_dupe_mode_is_ignore() {
        let mut col = Collection::new();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Preserve;
        let progress = col.new_progress_handler();
        data.clone().import(&mut col, progress).unwrap();
        assert_eq!(col.storage.notes_table_len(), 1);

        data.notes[0].fields[1].replace("new".to_string());
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        let notes = col.storage.get_all_notes();
        assert_eq!(notes.len(), 1);
        assert_eq!(notes[0].fields()[1], "old");
    }

    #[test]
    fn should_update_or_add_note_if_dupe_mode_is_update() {
        let mut col = Collection::new();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "old"]);
        data.dupe_resolution = DupeResolution::Update;
        let progress = col.new_progress_handler();
        data.clone().import(&mut col, progress).unwrap();
        assert_eq!(col.storage.notes_table_len(), 1);

        data.notes[0].fields[1].replace("new".to_string());
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].fields()[1], "new");
    }

    #[test]
    fn should_keep_old_field_content_if_no_new_one_is_supplied() {
        let mut col = Collection::new();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["same", "unchanged"]);
        data.add_note(&["same", "unchanged"]);
        data.dupe_resolution = DupeResolution::Update;
        let progress = col.new_progress_handler();
        data.clone().import(&mut col, progress).unwrap();
        assert_eq!(col.storage.notes_table_len(), 2);

        data.notes[0].fields[1] = None;
        data.notes[1].fields.pop();
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        let notes = col.storage.get_all_notes();
        assert_eq!(notes[0].fields(), &["same", "unchanged"]);
        assert_eq!(notes[0].fields(), &["same", "unchanged"]);
    }

    #[test]
    fn should_recognize_normalized_duplicate_only_if_normalization_is_enabled() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col)
            .fields(&["神", "old"])
            .add(&mut col);
        let mut data = ForeignData::with_defaults();
        data.dupe_resolution = DupeResolution::Update;
        data.add_note(&["神", "new"]);
        let progress = col.new_progress_handler();

        data.clone().import(&mut col, progress).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].fields(), &["神", "new"]);

        col.set_config_bool(BoolKey::NormalizeNoteText, false, false)
            .unwrap();
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        let notes = col.storage.get_all_notes();
        assert_eq!(notes[0].fields(), &["神", "new"]);
        assert_eq!(notes[1].fields(), &["神", "new"]);
    }

    #[test]
    fn should_add_global_tags() {
        let mut col = Collection::new();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["foo"]);
        data.notes[0].tags.replace(vec![String::from("bar")]);
        data.global_tags = vec![String::from("baz")];
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].tags, ["bar", "baz"]);
    }

    #[test]
    fn should_match_note_with_same_guid() {
        let mut col = Collection::new();
        let mut data = ForeignData::with_defaults();
        data.add_note(&["foo"]);
        data.notes[0].tags.replace(vec![String::from("bar")]);
        data.global_tags = vec![String::from("baz")];
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        assert_eq!(col.storage.get_all_notes()[0].tags, ["bar", "baz"]);
    }

    #[test]
    fn should_only_update_duplicates_in_same_deck_if_limit_is_enabled() {
        let mut col = Collection::new();
        let other_deck_id = DeckAdder::new("other").add(&mut col).id;
        NoteAdder::basic(&mut col)
            .fields(&["foo", "old"])
            .add(&mut col);
        NoteAdder::basic(&mut col)
            .fields(&["foo", "old"])
            .deck(other_deck_id)
            .add(&mut col);
        let mut data = ForeignData::with_defaults();
        data.match_scope = MatchScope::NotetypeAndDeck;
        data.add_note(&["foo", "new"]);
        let progress = col.new_progress_handler();
        data.import(&mut col, progress).unwrap();
        let notes = col.storage.get_all_notes();
        // same deck, should be updated
        assert_eq!(notes[0].fields()[1], "new");
        // other deck, should be unchanged
        assert_eq!(notes[1].fields()[1], "old");
    }
}
