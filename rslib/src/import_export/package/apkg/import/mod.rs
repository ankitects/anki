// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cards;
mod decks;
mod media;
mod notes;

use std::{fs::File, io, path::Path};

pub(crate) use notes::NoteMeta;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use crate::{
    collection::CollectionBuilder,
    import_export::{gather::ExchangeData, package::Meta},
    prelude::*,
    search::SearchNode,
};

struct Context<'a> {
    target_col: &'a mut Collection,
    archive: ZipArchive<File>,
    data: ExchangeData,
    usn: Usn,
}

impl Collection {
    pub fn import_apkg(&mut self, path: impl AsRef<Path>) -> Result<OpOutput<()>> {
        let file = File::open(path)?;
        let archive = ZipArchive::new(file)?;

        self.transact(Op::Import, |col| {
            let mut ctx = Context::new(archive, col)?;
            ctx.import()
        })
    }
}

impl<'a> Context<'a> {
    fn new(mut archive: ZipArchive<File>, target_col: &'a mut Collection) -> Result<Self> {
        let data =
            ExchangeData::gather_from_archive(&mut archive, SearchNode::WholeCollection, true)?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            archive,
            data,
            usn,
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
        let mut zip_file = archive.by_name(Meta::new_legacy().collection_filename())?;
        let mut tempfile = NamedTempFile::new()?;
        io::copy(&mut zip_file, &mut tempfile)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;

        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling)?;

        Ok(data)
    }
}
