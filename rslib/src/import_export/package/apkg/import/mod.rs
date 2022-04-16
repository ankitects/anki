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

use crate::{
    collection::CollectionBuilder,
    import_export::{gather::ExchangeData, package::Meta, ImportProgress},
    prelude::*,
    search::SearchNode,
};

struct Context<'a, F> {
    target_col: &'a mut Collection,
    archive: ZipArchive<File>,
    data: ExchangeData,
    usn: Usn,
    progress_fn: F,
}

impl Collection {
    pub fn import_apkg(
        &mut self,
        path: impl AsRef<Path>,
        progress_fn: impl FnMut(ImportProgress) -> Result<()>,
    ) -> Result<OpOutput<()>> {
        let file = File::open(path)?;
        let archive = ZipArchive::new(file)?;

        self.transact(Op::Import, |col| {
            let mut ctx = Context::new(archive, col, progress_fn)?;
            ctx.import()
        })
    }
}

impl<'a, F: FnMut(ImportProgress) -> Result<()>> Context<'a, F> {
    fn new(
        mut archive: ZipArchive<File>,
        target_col: &'a mut Collection,
        progress_fn: F,
    ) -> Result<Self> {
        let data =
            ExchangeData::gather_from_archive(&mut archive, SearchNode::WholeCollection, true)?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            archive,
            data,
            usn,
            progress_fn,
        })
    }

    fn import(&mut self) -> Result<()> {
        let mut media_map = self.prepare_media()?;
        let imported_notes = self.import_notes_and_notetypes(&mut media_map)?;
        let imported_decks = self.import_decks_and_configs()?;
        self.import_cards_and_revlog(&imported_notes, &imported_decks)?;
        self.copy_media(&mut media_map)
    }
}

impl ExchangeData {
    fn gather_from_archive(
        archive: &mut ZipArchive<File>,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<Self> {
        let tempfile = collection_to_tempfile(archive)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;
        col.maybe_upgrade_scheduler()?;

        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling)?;

        Ok(data)
    }
}

fn collection_to_tempfile(archive: &mut ZipArchive<File>) -> Result<NamedTempFile> {
    let mut zip_file = archive.by_name(Meta::new_legacy().collection_filename())?;
    let mut tempfile = NamedTempFile::new()?;
    io::copy(&mut zip_file, &mut tempfile)?;
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
