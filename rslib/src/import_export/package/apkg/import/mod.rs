// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cards;
mod decks;
mod media;
mod notes;

use std::{fs::File, io, path::Path};

pub(crate) use notes::NoteMeta;
use rusqlite::OptionalExtension;
use tempfile::NamedTempFile;
use zip::ZipArchive;
use zstd::stream::copy_decode;

use crate::{
    collection::CollectionBuilder,
    import_export::{
        gather::ExchangeData,
        package::{Meta, NoteLog},
        ImportProgress,
    },
    prelude::*,
    search::SearchNode,
};

type ProgressFn = dyn FnMut(ImportProgress) -> Result<()>;

struct Context<'a> {
    target_col: &'a mut Collection,
    archive: ZipArchive<File>,
    meta: Meta,
    data: ExchangeData,
    usn: Usn,
    progress_fn: &'a mut ProgressFn,
}

impl Collection {
    pub fn import_apkg(
        &mut self,
        path: impl AsRef<Path>,
        progress_fn: &mut ProgressFn,
    ) -> Result<OpOutput<NoteLog>> {
        progress_fn(ImportProgress::Collection)?;
        let file = File::open(path)?;
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
        progress_fn: &'a mut ProgressFn,
    ) -> Result<Self> {
        let meta = Meta::from_archive(&mut archive)?;
        let data = ExchangeData::gather_from_archive(
            &mut archive,
            &meta,
            SearchNode::WholeCollection,
            true,
        )?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            archive,
            meta,
            data,
            usn,
            progress_fn,
        })
    }

    fn import(&mut self) -> Result<NoteLog> {
        let mut media_map = self.prepare_media()?;
        (self.progress_fn)(ImportProgress::Collection)?;
        let note_imports = self.import_notes_and_notetypes(&mut media_map)?;
        let imported_decks = self.import_decks_and_configs()?;
        self.import_cards_and_revlog(&note_imports.id_map, &imported_decks)?;
        self.copy_media(&mut media_map)?;
        Ok(note_imports.log)
    }
}

impl ExchangeData {
    fn gather_from_archive(
        archive: &mut ZipArchive<File>,
        meta: &Meta,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<Self> {
        let tempfile = collection_to_tempfile(meta, archive)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;
        col.maybe_upgrade_scheduler()?;

        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling)?;

        Ok(data)
    }
}

fn collection_to_tempfile(meta: &Meta, archive: &mut ZipArchive<File>) -> Result<NamedTempFile> {
    let mut zip_file = archive.by_name(meta.collection_filename())?;
    let mut tempfile = NamedTempFile::new()?;
    if meta.zstd_compressed() {
        copy_decode(zip_file, &mut tempfile)
    } else {
        io::copy(&mut zip_file, &mut tempfile).map(|_| ())
    }
    .map_err(|err| AnkiError::file_io_error(err, tempfile.path()))?;

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
