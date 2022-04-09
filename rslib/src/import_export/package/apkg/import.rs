// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    fs::File,
    io, mem,
    path::Path,
    sync::Arc,
};

use sha1::Sha1;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use crate::{
    collection::CollectionBuilder,
    decks::NormalDeck,
    import_export::{
        gather::ExchangeData,
        package::{
            media::{extract_media_entries, safe_normalized_file_name, SafeMediaEntry},
            Meta,
        },
    },
    media::{
        files::{add_hash_suffix_to_file_stem, sha1_of_reader},
        MediaManager,
    },
    prelude::*,
    search::SearchNode,
    text::replace_media_refs,
};

struct Context<'a> {
    target_col: &'a mut Collection,
    archive: ZipArchive<File>,
    guid_map: HashMap<String, NoteMeta>,
    remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
    remapped_notes: HashMap<NoteId, NoteId>,
    existing_notes: HashSet<NoteId>,
    remapped_decks: HashMap<DeckId, DeckId>,
    remapped_deck_configs: HashMap<DeckConfigId, DeckConfigId>,
    data: ExchangeData,
    usn: Usn,
    /// Map of source media files, that do not already exist in the target.
    ///
    /// original, normalized file name → (refererenced on import material,
    /// entry with possibly remapped file name)
    used_media_entries: HashMap<String, (bool, SafeMediaEntry)>,
    /// Source notes that cannot be imported, because notes with the same guid
    /// exist in the target, but their notetypes don't match.
    conflicting_notes: HashSet<NoteId>,
    added_cards: HashSet<CardId>,
    normalize_notes: bool,
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct NoteMeta {
    id: NoteId,
    mtime: TimestampSecs,
    notetype_id: NotetypeId,
}

impl NoteMeta {
    pub(crate) fn new(id: NoteId, mtime: TimestampSecs, notetype_id: NotetypeId) -> Self {
        Self {
            id,
            mtime,
            notetype_id,
        }
    }
}

impl SafeMediaEntry {
    fn with_hash_from_archive(&mut self, archive: &mut ZipArchive<File>) -> Result<()> {
        if self.sha1 == [0; 20] {
            let mut reader = self.fetch_file(archive)?;
            self.sha1 = sha1_of_reader(&mut reader)?;
        }
        Ok(())
    }

    /// Requires sha1 to be set. Returns old file name.
    fn uniquify_name(&mut self) -> String {
        let new_name = add_hash_suffix_to_file_stem(&self.name, &self.sha1);
        mem::replace(&mut self.name, new_name)
    }
}

impl Collection {
    pub fn import_apkg(&mut self, path: impl AsRef<Path>) -> Result<()> {
        let file = File::open(path)?;
        let archive = ZipArchive::new(file)?;
        let mut ctx = Context::new(archive, self)?;
        ctx.import()?;
        Ok(())
    }

    fn all_existing_sha1s(&mut self) -> Result<HashMap<String, [u8; 20]>> {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        mgr.all_checksums(|_| true, &self.log)
    }
}

impl ExchangeData {
    fn gather_from_archive(
        archive: &mut ZipArchive<File>,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<Self> {
        let mut zip_file = archive.by_name(Meta::new_legacy().collection_filename())?;
        let mut tempfile = NamedTempFile::new()?;
        io::copy(&mut zip_file, &mut tempfile)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;

        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling)?;

        Ok(data)
    }
}

impl<'a> Context<'a> {
    fn new(mut archive: ZipArchive<File>, target_col: &'a mut Collection) -> Result<Self> {
        let data =
            ExchangeData::gather_from_archive(&mut archive, SearchNode::WholeCollection, true)?;
        let guid_map = target_col.storage.note_guid_map()?;
        let usn = target_col.usn()?;
        let normalize_notes = target_col.get_config_bool(BoolKey::NormalizeNoteText);
        let existing_notes = target_col.storage.get_all_note_ids()?;
        Ok(Self {
            target_col,
            archive,
            data,
            guid_map,
            usn,
            conflicting_notes: HashSet::new(),
            remapped_notetypes: HashMap::new(),
            remapped_notes: HashMap::new(),
            existing_notes,
            remapped_decks: HashMap::new(),
            remapped_deck_configs: HashMap::new(),
            added_cards: HashSet::new(),
            used_media_entries: HashMap::new(),
            normalize_notes,
        })
    }

    fn import(&mut self) -> Result<()> {
        self.prepare_media()?;
        self.import_notetypes()?;
        self.import_notes()?;
        self.import_deck_configs()?;
        self.import_decks()?;
        self.import_cards()?;
        self.import_revlog()?;
        self.copy_media()
    }

    fn prepare_media(&mut self) -> Result<()> {
        let existing_sha1s = self.target_col.all_existing_sha1s()?;
        for mut entry in extract_media_entries(&Meta::new_legacy(), &mut self.archive)? {
            if let Some(other_sha1) = existing_sha1s.get(&entry.name) {
                entry.with_hash_from_archive(&mut self.archive)?;
                if entry.sha1 != *other_sha1 {
                    let original_name = entry.uniquify_name();
                    self.used_media_entries
                        .insert(original_name, (false, entry));
                }
            } else {
                self.used_media_entries
                    .insert(entry.name.clone(), (false, entry));
            }
        }
        Ok(())
    }

    fn import_notetypes(&mut self) -> Result<()> {
        for mut notetype in std::mem::take(&mut self.data.notetypes) {
            if let Some(existing) = self.target_col.storage.get_notetype(notetype.id)? {
                self.merge_or_remap_notetype(&mut notetype, existing)?;
            } else {
                self.add_notetype(&mut notetype)?;
            }
        }
        Ok(())
    }

    fn merge_or_remap_notetype(
        &mut self,
        incoming: &mut Notetype,
        existing: Notetype,
    ) -> Result<()> {
        if incoming.schema_hash() == existing.schema_hash() {
            if incoming.mtime_secs > existing.mtime_secs {
                self.update_notetype(incoming, existing)?;
            }
        } else {
            self.add_notetype_with_remapped_id(incoming)?;
        }
        Ok(())
    }

    fn add_notetype(&mut self, notetype: &mut Notetype) -> Result<()> {
        notetype.usn = self.usn;
        // TODO: make undoable
        self.target_col
            .add_or_update_notetype_with_existing_id_inner(notetype, None, self.usn, true)
    }

    fn update_notetype(&mut self, notetype: &mut Notetype, original: Notetype) -> Result<()> {
        notetype.usn = self.usn;
        self.target_col
            .add_or_update_notetype_with_existing_id_inner(notetype, Some(original), self.usn, true)
    }

    fn add_notetype_with_remapped_id(&mut self, notetype: &mut Notetype) -> Result<()> {
        let old_id = std::mem::take(&mut notetype.id);
        notetype.usn = self.usn;
        self.target_col
            .add_notetype_inner(notetype, self.usn, true)?;
        self.remapped_notetypes.insert(old_id, notetype.id);
        Ok(())
    }

    fn import_notes(&mut self) -> Result<()> {
        for mut note in mem::take(&mut self.data.notes) {
            if let Some(notetype_id) = self.remapped_notetypes.get(&note.notetype_id) {
                if self.guid_map.contains_key(&note.guid) {
                    self.conflicting_notes.insert(note.id);
                    // TODO: Log ignore
                } else {
                    note.notetype_id = *notetype_id;
                    self.add_note(&mut note)?;
                }
            } else if let Some(&meta) = self.guid_map.get(&note.guid) {
                self.maybe_update_note(&mut note, meta)?;
            } else {
                self.add_note(&mut note)?;
            }
        }
        Ok(())
    }

    fn add_note(&mut self, mut note: &mut Note) -> Result<()> {
        // TODO: Log add
        self.munge_media(note)?;
        self.target_col.canonify_note_tags(note, self.usn)?;
        let notetype = self.get_expected_notetype(note.notetype_id)?;
        note.prepare_for_update(&notetype, self.normalize_notes)?;
        note.usn = self.usn;
        self.uniquify_note_id(note);

        self.target_col.add_note_only_with_id_undoable(note)?;
        self.existing_notes.insert(note.id);
        Ok(())
    }

    fn uniquify_note_id(&mut self, note: &mut Note) {
        let original = note.id;
        while self.existing_notes.contains(&note.id) {
            note.id.0 += 999;
        }
        if original != note.id {
            self.remapped_notes.insert(original, note.id);
        }
    }

    fn get_expected_notetype(&mut self, ntid: NotetypeId) -> Result<Arc<Notetype>> {
        self.target_col
            .get_notetype(ntid)?
            .ok_or(AnkiError::NotFound)
    }

    fn get_expected_note(&mut self, nid: NoteId) -> Result<Note> {
        self.target_col
            .storage
            .get_note(nid)?
            .ok_or(AnkiError::NotFound)
    }

    fn maybe_update_note(&mut self, note: &mut Note, meta: NoteMeta) -> Result<()> {
        if meta.mtime < note.mtime {
            if meta.notetype_id == note.notetype_id {
                self.remapped_notes.insert(note.id, meta.id);
                note.id = meta.id;
                self.update_note(note)?;
            } else {
                self.conflicting_notes.insert(note.id);
                // TODO: Log ignore
            }
        } else {
            // TODO: Log duplicate
        }
        Ok(())
    }

    fn update_note(&mut self, note: &mut Note) -> Result<()> {
        // TODO: Log update
        self.munge_media(note)?;
        let original = self.get_expected_note(note.id)?;
        let notetype = self.get_expected_notetype(note.notetype_id)?;
        self.target_col.update_note_inner_without_cards(
            note,
            &original,
            &notetype,
            self.usn,
            true,
            self.normalize_notes,
            true,
        )
    }

    fn munge_media(&mut self, note: &mut Note) -> Result<()> {
        for field in note.fields_mut() {
            if let Some(new_field) = self.replace_media_refs(field) {
                *field = new_field;
            };
        }
        Ok(())
    }

    fn replace_media_refs(&mut self, field: &mut String) -> Option<String> {
        replace_media_refs(field, |name| {
            if let Ok(normalized) = safe_normalized_file_name(name) {
                if let Some((used, entry)) = self.used_media_entries.get_mut(normalized.as_ref()) {
                    *used = true;
                    if entry.name != name {
                        // name is not normalized, and/or remapped
                        return Some(entry.name.clone());
                    }
                } else if let Cow::Owned(s) = normalized {
                    // no entry; might be a reference to an existing file, so ensure normalization
                    return Some(s);
                }
            }
            None
        })
    }

    fn import_deck_configs(&mut self) -> Result<()> {
        // TODO: keep ids if possible?
        for mut config in mem::take(&mut self.data.deck_configs) {
            let old_id = mem::take(&mut config.id);
            self.target_col
                .add_deck_config_inner(&mut config, Some(self.usn))?;
            self.remapped_deck_configs.insert(old_id, config.id);
        }
        Ok(())
    }

    fn import_decks(&mut self) -> Result<()> {
        // TODO: ensure alphabetical order, so parents are seen first
        let mut renamed_parents = Vec::new();

        for mut deck in mem::take(&mut self.data.decks) {
            deck.maybe_reparent(&renamed_parents);
            self.remap_deck_config_id(&mut deck)?;
            if let Some(original) = self.get_deck_by_name(&deck)? {
                if original.is_filtered() {
                    deck.uniquify_name(&mut renamed_parents);
                    self.add_deck(&mut deck)?;
                } else {
                    self.update_deck(&deck, original)?;
                }
            } else {
                self.add_deck(&mut deck)?;
            }
        }

        Ok(())
    }

    fn remap_deck_config_id(&mut self, deck: &mut Deck) -> Result<()> {
        if let Some(config_id) = self
            .remapped_deck_configs
            .get(&DeckConfigId(deck.normal()?.config_id))
        {
            deck.normal_mut()?.config_id = config_id.0;
        }
        Ok(())
    }

    fn add_deck(&mut self, deck: &mut Deck) -> Result<()> {
        let old_id = mem::take(&mut deck.id);
        self.target_col.add_deck_inner(deck, self.usn)?;
        self.remapped_decks.insert(old_id, deck.id);
        Ok(())
    }

    /// Caller must ensure decks are normal.
    fn update_deck(&mut self, deck: &Deck, original: Deck) -> Result<()> {
        let mut new_deck = original.clone();
        new_deck.normal_mut()?.update_with_other(deck.normal()?);
        self.remapped_decks.insert(deck.id, new_deck.id);
        self.target_col
            .update_deck_inner(&mut new_deck, original, self.usn)
    }

    fn get_deck_by_name(&mut self, deck: &Deck) -> Result<Option<Deck>> {
        self.target_col
            .storage
            .get_deck_by_name(deck.name.as_native_str())
    }

    fn import_cards(&mut self) -> Result<()> {
        let existing = self.target_col.storage.all_cards_as_nid_and_ord()?;
        for mut card in mem::take(&mut self.data.cards) {
            if self.conflicting_notes.contains(&card.note_id) {
                continue;
            }
            self.remap_note_id(&mut card);
            if existing.contains(&(card.note_id, card.template_idx)) {
                // TODO: maybe update
                continue;
            }
            self.remap_deck_id(&mut card);
            // TODO: adjust collection-relative due times
            // TODO: remove cards from filtered decks
            self.add_card(&mut card)?;
        }
        Ok(())
    }

    fn add_card(&mut self, card: &mut Card) -> Result<()> {
        card.usn = self.usn;
        if self.target_col.add_card_if_unique_undoable(card)? {
            self.added_cards.insert(card.id);
        }
        Ok(())
    }

    fn remap_note_id(&self, card: &mut Card) {
        if let Some(nid) = self.remapped_notes.get(&card.note_id) {
            card.note_id = *nid;
        }
    }

    fn remap_deck_id(&self, card: &mut Card) {
        if let Some(did) = self.remapped_decks.get(&card.deck_id) {
            card.deck_id = *did;
        }
    }

    fn import_revlog(&mut self) -> Result<()> {
        for mut entry in mem::take(&mut self.data.revlog) {
            if self.added_cards.contains(&entry.cid) {
                entry.usn = self.usn;
                self.target_col.add_revlog_entry_if_unique_undoable(entry)?;
            }
        }
        Ok(())
    }

    fn copy_media(&mut self) -> Result<()> {
        for (used, entry) in self.used_media_entries.values() {
            if *used {
                entry.copy_from_archive(&mut self.archive, &self.target_col.media_folder)?;
            }
        }
        Ok(())
    }
}

impl Deck {
    fn maybe_reparent(&mut self, renamed_parents: &[(String, String)]) {
        if let Some(new_name) = reparented_name(self.name.as_native_str(), renamed_parents) {
            self.name = NativeDeckName::from_native_str(new_name);
        }
    }

    fn uniquify_name(&mut self, renamed_parents: &mut Vec<(String, String)>) {
        let name = self.name.as_native_str();
        let new_name = format!("{name} {}", TimestampSecs::now());
        renamed_parents.push((format!("{name}\x1f"), format!("{new_name}\x1f")));
        self.name = NativeDeckName::from_native_str(new_name);
    }
}

impl NormalDeck {
    fn update_with_other(&mut self, other: &Self) {
        self.markdown_description = other.markdown_description;
        self.description = other.description.clone();
        if other.config_id != 1 {
            self.config_id = other.config_id;
        }
    }
}

fn reparented_name(name: &str, renamed_parents: &[(String, String)]) -> Option<String> {
    renamed_parents.iter().find_map(|(old_parent, new_parent)| {
        name.starts_with(old_parent)
            .then(|| name.replacen(old_parent, new_parent, 1))
    })
}

impl Notetype {
    fn schema_hash(&self) -> [u8; 20] {
        let mut hasher = Sha1::new();
        for field in &self.fields {
            hasher.update(field.name.as_bytes());
        }
        for template in &self.templates {
            hasher.update(template.name.as_bytes());
        }
        hasher.digest().bytes()
    }
}
