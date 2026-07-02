// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cards;
mod decks;
mod media;
mod notes;

use std::fs::File;
use std::path::Path;

use anki_io::new_tempfile;
use anki_io::open_file;
use anki_io::FileIoSnafu;
use anki_io::FileOp;
pub(crate) use notes::NoteMeta;
use rusqlite::OptionalExtension;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use super::super::meta::MetaExt;
use crate::collection::CollectionBuilder;
use crate::config::ConfigKey;
use crate::import_export::gather::ExchangeData;
use crate::import_export::package::ImportAnkiPackageOptions;
use crate::import_export::package::Meta;
use crate::import_export::package::UpdateCondition;
use crate::import_export::ImportProgress;
use crate::import_export::NoteLog;
use crate::media::MediaManager;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::search::SearchNode;

/// A map of old to new template indices for a given notetype.
type TemplateMap = std::collections::HashMap<u16, u16>;

struct Context<'a> {
    target_col: &'a mut Collection,
    merge_notetypes: bool,
    update_notes: UpdateCondition,
    update_notetypes: UpdateCondition,
    media_manager: MediaManager,
    archive: ZipArchive<File>,
    meta: Meta,
    data: ExchangeData,
    usn: Usn,
    progress: ThrottlingProgressHandler<ImportProgress>,
}

impl Collection {
    pub fn import_apkg(
        &mut self,
        path: impl AsRef<Path>,
        options: ImportAnkiPackageOptions,
    ) -> Result<OpOutput<NoteLog>> {
        let file = open_file(path)?;
        let archive = ZipArchive::new(file)?;
        let progress = self.new_progress_handler();

        self.transact(Op::Import, |col| {
            col.set_config(BoolKey::MergeNotetypes, &options.merge_notetypes)?;
            col.set_config(BoolKey::WithScheduling, &options.with_scheduling)?;
            col.set_config(BoolKey::WithDeckConfigs, &options.with_deck_configs)?;
            col.set_config(ConfigKey::UpdateNotes, &options.update_notes())?;
            col.set_config(ConfigKey::UpdateNotetypes, &options.update_notetypes())?;
            let mut ctx = Context::new(archive, col, options, progress)?;
            ctx.import()
        })
    }
}

impl<'a> Context<'a> {
    fn new(
        mut archive: ZipArchive<File>,
        target_col: &'a mut Collection,
        options: ImportAnkiPackageOptions,
        mut progress: ThrottlingProgressHandler<ImportProgress>,
    ) -> Result<Self> {
        let media_manager = target_col.media()?;
        let meta = Meta::from_archive(&mut archive)?;
        let data = ExchangeData::gather_from_archive(
            &mut archive,
            &meta,
            SearchNode::WholeCollection,
            &mut progress,
            options.with_scheduling,
            options.with_deck_configs,
        )?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            merge_notetypes: options.merge_notetypes,
            update_notes: options.update_notes(),
            update_notetypes: options.update_notetypes(),
            media_manager,
            archive,
            meta,
            data,
            usn,
            progress,
        })
    }

    fn import(&mut self) -> Result<NoteLog> {
        let notetypes = self
            .data
            .notes
            .iter()
            .map(|n| (n.id, n.notetype_id))
            .collect();
        let mut media_map = self.prepare_media()?;
        let note_imports = self.import_notes_and_notetypes(&mut media_map)?;
        let imported_decks = self.import_decks_and_configs()?;
        self.import_cards_and_revlog(
            &note_imports.id_map,
            &notetypes,
            &note_imports.remapped_templates,
            &imported_decks,
        )?;
        self.copy_media(&mut media_map)?;
        Ok(note_imports.log)
    }
}

impl ExchangeData {
    fn gather_from_archive(
        archive: &mut ZipArchive<File>,
        meta: &Meta,
        search: impl TryIntoSearch,
        progress: &mut ThrottlingProgressHandler<ImportProgress>,
        with_scheduling: bool,
        with_deck_configs: bool,
    ) -> Result<Self> {
        let tempfile = collection_to_tempfile(meta, archive)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;
        col.maybe_fix_invalid_ids()?;
        col.maybe_upgrade_scheduler()?;

        progress.set(ImportProgress::Gathering)?;
        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling, with_deck_configs)?;

        Ok(data)
    }
}

fn collection_to_tempfile(meta: &Meta, archive: &mut ZipArchive<File>) -> Result<NamedTempFile> {
    let mut zip_file = archive.by_name(meta.collection_filename())?;
    let mut tempfile = new_tempfile()?;
    meta.copy(&mut zip_file, &mut tempfile)
        .with_context(|_| FileIoSnafu {
            path: tempfile.path(),
            op: FileOp::copy(zip_file.name()),
        })?;

    Ok(tempfile)
}

impl Collection {
    fn maybe_upgrade_scheduler(&mut self) -> Result<()> {
        if self.scheduling_included()? {
            self.upgrade_to_v2_scheduler()?;
        }
        Ok(())
    }

    fn scheduling_included(&mut self) -> Result<bool> {
        const SQL: &str = "SELECT 1 FROM cards WHERE queue != 0";
        Ok(self
            .storage
            .db
            .query_row(SQL, [], |_| Ok(()))
            .optional()?
            .is_some())
    }
}
