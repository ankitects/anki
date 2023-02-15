// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cards;
mod decks;
mod media;
mod notes;

use std::collections::HashSet;
use std::fs::File;
use std::path::Path;

pub(crate) use notes::NoteMeta;
use rusqlite::OptionalExtension;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use crate::collection::CollectionBuilder;
use crate::error::FileIoSnafu;
use crate::error::FileOp;
use crate::import_export::gather::ExchangeData;
use crate::import_export::package::Meta;
use crate::import_export::ImportProgress;
use crate::import_export::IncrementableProgress;
use crate::import_export::NoteLog;
use crate::io::new_tempfile;
use crate::io::open_file;
use crate::media::MediaManager;
use crate::prelude::*;
use crate::search::SearchNode;

struct Context<'a> {
    target_col: &'a mut Collection,
    media_manager: MediaManager,
    archive: ZipArchive<File>,
    meta: Meta,
    data: ExchangeData,
    usn: Usn,
    progress: IncrementableProgress<ImportProgress>,
}

impl Collection {
    pub fn import_apkg(
        &mut self,
        path: impl AsRef<Path>,
        progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
    ) -> Result<OpOutput<NoteLog>> {
        let file = open_file(path)?;
        let archive = ZipArchive::new(file)?;

        self.transact(Op::Import, |col| {
            let mut ctx = Context::new(archive, col, progress_fn)?;
            ctx.import()
        })
    }
}

impl<'a> Context<'a> {
    fn new(
        mut archive: ZipArchive<File>,
        target_col: &'a mut Collection,
        progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
    ) -> Result<Self> {
        let mut progress = IncrementableProgress::new(progress_fn);
        progress.call(ImportProgress::Extracting)?;
        let media_manager = target_col.media()?;
        let meta = Meta::from_archive(&mut archive)?;
        let data = ExchangeData::gather_from_archive(
            &mut archive,
            &meta,
            SearchNode::WholeCollection,
            &mut progress,
            true,
        )?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            media_manager,
            archive,
            meta,
            data,
            usn,
            progress,
        })
    }

    fn import(&mut self) -> Result<NoteLog> {
        let mut media_map = self.prepare_media()?;
        let note_imports = self.import_notes_and_notetypes(&mut media_map)?;
        let keep_filtered = self.data.enables_filtered_decks();
        let contains_scheduling = self.data.contains_scheduling();
        let imported_decks = self.import_decks_and_configs(keep_filtered, contains_scheduling)?;
        self.import_cards_and_revlog(&note_imports.id_map, &imported_decks, keep_filtered)?;
        self.copy_media(&mut media_map)?;
        Ok(note_imports.log)
    }
}

impl ExchangeData {
    fn gather_from_archive(
        archive: &mut ZipArchive<File>,
        meta: &Meta,
        search: impl TryIntoSearch,
        progress: &mut IncrementableProgress<ImportProgress>,
        with_scheduling: bool,
    ) -> Result<Self> {
        let tempfile = collection_to_tempfile(meta, archive)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;
        col.maybe_upgrade_scheduler()?;

        progress.call(ImportProgress::Gathering)?;
        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling)?;

        Ok(data)
    }

    fn enables_filtered_decks(&self) -> bool {
        // Earlier versions relied on the importer handling filtered decks by converting
        // them into regular ones, so there is no guarantee that all original decks
        // are included. And the legacy exporter included the default deck config, so we
        // can't use it to determine if scheduling is included.
        self.contains_scheduling()
            && self.contains_all_original_decks()
            && !self.deck_configs.is_empty()
    }

    fn contains_scheduling(&self) -> bool {
        !self.revlog.is_empty()
    }

    fn contains_all_original_decks(&self) -> bool {
        let deck_ids: HashSet<_> = self.decks.iter().map(|d| d.id).collect();
        self.cards
            .iter()
            .all(|c| c.original_deck_id.0 == 0 || deck_ids.contains(&c.original_deck_id))
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
