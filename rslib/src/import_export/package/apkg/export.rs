// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    path::{Path, PathBuf},
    sync::Arc,
};

use rusqlite::{named_params, params};
use tempfile::NamedTempFile;

use crate::{
    collection::CollectionBuilder,
    import_export::package::{
        colpkg::export::{export_collection, MediaIter},
        Meta,
    },
    io::{atomic_rename, tempfile_in_parent_of},
    latex::extract_latex,
    notetype::CardTemplate,
    prelude::*,
    storage::{ids_to_string, SchemaVersion, SqliteStorage},
    tags::matcher::TagMatcher,
    text::{
        extract_media_refs, extract_underscored_css_imports, extract_underscored_references,
        is_remote_filename,
    },
};

impl Collection {
    pub fn export_apkg(
        &mut self,
        out_path: impl AsRef<Path>,
        deck_id: Option<DeckId>,
        include_scheduling: bool,
        include_media: bool,
        progress_fn: impl FnMut(usize),
    ) -> Result<()> {
        let temp_apkg = tempfile_in_parent_of(out_path.as_ref())?;
        let mut temp_col = NamedTempFile::new()?;
        let temp_col_path = temp_col
            .path()
            .to_str()
            .ok_or_else(|| AnkiError::IoError("tempfile with non-unicode name".into()))?;
        let media = self.export_collection_extracting_media(
            temp_col_path,
            deck_id,
            include_scheduling,
            include_media,
        )?;
        let col_size = temp_col.as_file().metadata()?.len() as usize;

        export_collection(
            Meta::new_legacy(),
            temp_apkg.path(),
            &mut temp_col,
            col_size,
            media,
            &self.tr,
            progress_fn,
        )?;
        atomic_rename(temp_apkg, out_path.as_ref(), true)
    }

    fn export_collection_extracting_media(
        &mut self,
        path: &str,
        deck_id: Option<DeckId>,
        include_scheduling: bool,
        include_media: bool,
    ) -> Result<MediaIter> {
        CollectionBuilder::new(path).build()?.close(None)?;
        self.export_into_other(path, deck_id, include_scheduling)?;

        let mut temp_col = CollectionBuilder::new(path).build()?;
        if !include_scheduling {
            temp_col.remove_scheduling_information()?;
        }
        let mut media = HashSet::new();
        if include_media {
            temp_col.extract_media_paths(&mut media)?;
        }
        temp_col.close(Some(SchemaVersion::V11))?;

        Ok(MediaIter::from_file_list(media))
    }

    fn export_into_other(
        &mut self,
        other_path: &str,
        deck_id: Option<DeckId>,
        export_scheduling_tables: bool,
    ) -> Result<()> {
        self.storage
            .db
            .execute("ATTACH ? AS other", params!(other_path))?;
        let res = self.export_into_other_inner(deck_id, export_scheduling_tables);
        self.storage.db.execute_batch("DETACH other")?;

        res
    }

    fn export_into_other_inner(
        &mut self,
        deck_id: Option<DeckId>,
        export_scheduling_tables: bool,
    ) -> Result<()> {
        self.export_decks(deck_id)?;
        self.storage.export_cards(deck_id)?;
        self.storage.export_notes()?;
        self.storage.export_notetypes()?;
        if export_scheduling_tables {
            self.storage.export_revlog()?;
            self.storage.export_deck_configs()?;
        }
        Ok(())
    }

    fn export_decks(&mut self, deck_id: Option<DeckId>) -> Result<()> {
        let sql = if let Some(did) = deck_id {
            self.export_deck_sql(did)?
        } else {
            include_str!("export_decks.sql").into()
        };
        self.storage.db.execute_batch(&sql)?;
        Ok(())
    }

    fn export_deck_sql(&mut self, did: DeckId) -> Result<String> {
        let mut sql = format!("{} AND id IN ", include_str!("export_decks.sql"));
        let deck = self.get_deck(did)?.ok_or(AnkiError::NotFound)?;
        let ids = self.storage.deck_id_with_children(&deck)?;
        ids_to_string(&mut sql, &ids);
        Ok(sql)
    }

    fn remove_scheduling_information(&mut self) -> Result<()> {
        self.storage.remove_system_tags()?;
        self.reset_deck_config_ids()?;
        self.reset_cards()
    }

    fn reset_deck_config_ids(&mut self) -> Result<()> {
        for mut deck in self.storage.get_all_decks()? {
            deck.normal_mut()?.config_id = 1;
            self.update_deck(&mut deck)?;
        }
        Ok(())
    }

    fn reset_cards(&mut self) -> Result<()> {
        let cids = self.storage.get_non_new_card_ids()?;
        self.reschedule_cards_as_new(&cids, false, true, false, None)?;
        self.storage
            .db
            .execute_batch(include_str!("reset_cards.sql"))?;
        Ok(())
    }

    fn extract_media_paths(&mut self, names: &mut HashSet<PathBuf>) -> Result<()> {
        let notetypes = self.get_all_notetypes()?;
        self.extract_media_paths_from_notes(names, &notetypes)?;
        self.extract_media_paths_from_notetypes(names, &notetypes);
        Ok(())
    }

    fn extract_media_paths_from_notes(
        &mut self,
        names: &mut HashSet<PathBuf>,
        notetypes: &HashMap<NotetypeId, Arc<Notetype>>,
    ) -> Result<()> {
        let mut stmt = self.storage.db.prepare("SELECT flds, mid FROM notes")?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let flds = row.get_ref(0)?.as_str()?;
            let notetype_id: NotetypeId = row.get(1)?;
            self.extract_media_paths_from_note(names, flds, notetypes.get(&notetype_id).unwrap());
        }
        Ok(())
    }

    fn extract_media_paths_from_note(
        &self,
        names: &mut HashSet<PathBuf>,
        flds: &str,
        notetype: &Notetype,
    ) {
        self.extract_latex_paths(names, flds, notetype);
        for media_ref in extract_media_refs(flds) {
            if is_local_base_name(&media_ref.fname_decoded) {
                names.insert(self.media_folder.join(media_ref.fname_decoded.as_ref()));
            }
        }
    }

    fn extract_latex_paths(&self, names: &mut HashSet<PathBuf>, flds: &str, notetype: &Notetype) {
        for latex in extract_latex(flds, notetype.config.latex_svg).1 {
            if is_local_base_name(&latex.fname) {
                names.insert(self.media_folder.join(&latex.fname));
            }
        }
    }

    fn extract_media_paths_from_notetypes(
        &mut self,
        names: &mut HashSet<PathBuf>,
        notetypes: &HashMap<NotetypeId, Arc<Notetype>>,
    ) {
        for notetype in notetypes.values() {
            notetype.extract_media_paths(names, &self.media_folder);
        }
    }
}

fn is_local_base_name(name: &str) -> bool {
    !is_remote_filename(name) && Path::new(name).parent().is_none()
}

impl Notetype {
    fn extract_media_paths(&self, names: &mut HashSet<PathBuf>, media_folder: &Path) {
        for name in extract_underscored_css_imports(&self.config.css) {
            if is_local_base_name(name) {
                names.insert(media_folder.join(name));
            }
        }
        for template in &self.templates {
            template.extract_media_paths(names, media_folder);
        }
    }
}

impl CardTemplate {
    fn extract_media_paths(&self, names: &mut HashSet<PathBuf>, media_folder: &Path) {
        for template_side in [&self.config.q_format, &self.config.a_format] {
            for name in extract_underscored_references(template_side) {
                if is_local_base_name(name) {
                    names.insert(media_folder.join(name));
                }
            }
        }
    }
}

impl SqliteStorage {
    fn export_cards(&mut self, deck_id: Option<DeckId>) -> Result<()> {
        self.db.execute_batch(include_str!("export_cards.sql"))?;
        if let Some(did) = deck_id {
            // include siblings outside the exported deck, because they would
            // get created on import anyway
            self.db.execute(
                include_str!("export_siblings.sql"),
                named_params! {"did": did},
            )?;
        }
        Ok(())
    }

    fn export_notes(&mut self) -> Result<()> {
        self.db.execute_batch(include_str!("export_notes.sql"))?;
        Ok(())
    }

    fn export_notetypes(&mut self) -> Result<()> {
        self.db.execute_batch("DELETE FROM other.notetypes")?;
        self.db
            .execute_batch(include_str!("export_notetypes.sql"))?;
        Ok(())
    }

    fn export_revlog(&mut self) -> Result<()> {
        self.db.execute_batch(include_str!("export_revlog.sql"))?;
        Ok(())
    }

    fn export_deck_configs(&mut self) -> Result<()> {
        let id_string = self.exported_deck_config_ids()?;
        self.db.execute(
            include_str!("export_deck_configs.sql"),
            named_params! {"ids": id_string},
        )?;
        Ok(())
    }

    fn exported_deck_config_ids(&mut self) -> Result<String> {
        let all_decks = self.get_all_decks()?;
        let exported_deck_ids = self.exported_deck_ids()?;

        let ids = all_decks
            .iter()
            .filter(|deck| exported_deck_ids.contains(&deck.id))
            .filter_map(|deck| deck.config_id());
        let mut id_string = String::new();
        ids_to_string(&mut id_string, ids);

        Ok(id_string)
    }

    fn exported_deck_ids(&mut self) -> Result<HashSet<DeckId>> {
        self.db
            .prepare("SELECT DISTINCT id FROM other.decks")?
            .query_and_then([], |row| Ok(DeckId(row.get(0)?)))?
            .collect()
    }

    fn remove_system_tags(&mut self) -> Result<()> {
        let mut matcher = TagMatcher::new("marked leech")?;
        let mut rows_stmt = self.db.prepare("SELECT id, tags FROM notes")?;
        let mut update_stmt = self
            .db
            .prepare_cached("UPDATE notes SET tags = ? WHERE id = ?")?;

        let mut rows = rows_stmt.query(params![])?;
        while let Some(row) = rows.next()? {
            let tags = row.get_ref(1)?.as_str()?;
            if matcher.is_match(tags) {
                let new_tags = matcher.remove(tags);
                let note_id: NoteId = row.get(0)?;
                update_stmt.execute(params![new_tags, note_id])?;
            }
        }

        Ok(())
    }

    fn get_non_new_card_ids(&self) -> Result<Vec<CardId>> {
        self.db
            .prepare(include_str!("non_new_cards.sql"))?
            .query_and_then([], |row| Ok(CardId(row.get(0)?)))?
            .collect()
    }
}
