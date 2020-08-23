// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{ids_to_string, SqliteStorage};
use crate::{
    err::{AnkiError, DBErrorKind, Result},
    notes::NoteID,
    notetype::{
        AlreadyGeneratedCardInfo, CardTemplate, CardTemplateConfig, NoteField, NoteFieldConfig,
        NoteTypeConfig,
    },
    notetype::{NoteType, NoteTypeID, NoteTypeSchema11},
    timestamp::TimestampMillis,
};
use prost::Message;
use rusqlite::{params, OptionalExtension, Row, NO_PARAMS};
use std::collections::{HashMap, HashSet};
use unicase::UniCase;

fn row_to_notetype_core(row: &Row) -> Result<NoteType> {
    let config = NoteTypeConfig::decode(row.get_raw(4).as_blob()?)?;
    Ok(NoteType {
        id: row.get(0)?,
        name: row.get(1)?,
        mtime_secs: row.get(2)?,
        usn: row.get(3)?,
        config,
        fields: vec![],
        templates: vec![],
    })
}

fn row_to_existing_card(row: &Row) -> Result<AlreadyGeneratedCardInfo> {
    Ok(AlreadyGeneratedCardInfo {
        id: row.get(0)?,
        nid: row.get(1)?,
        ord: row.get(2)?,
        original_deck_id: row.get(3)?,
        position_if_new: row.get(4).ok().unwrap_or_default(),
    })
}

impl SqliteStorage {
    pub(crate) fn get_notetype(&self, ntid: NoteTypeID) -> Result<Option<NoteType>> {
        match self.get_notetype_core(ntid)? {
            Some(mut nt) => {
                nt.fields = self.get_notetype_fields(ntid)?;
                nt.templates = self.get_notetype_templates(ntid)?;
                Ok(Some(nt))
            }
            None => Ok(None),
        }
    }

    fn get_notetype_core(&self, ntid: NoteTypeID) -> Result<Option<NoteType>> {
        self.db
            .prepare_cached(concat!(include_str!("get_notetype.sql"), " where id = ?"))?
            .query_and_then(&[ntid], row_to_notetype_core)?
            .next()
            .transpose()
    }

    fn get_notetype_fields(&self, ntid: NoteTypeID) -> Result<Vec<NoteField>> {
        self.db
            .prepare_cached(include_str!("get_fields.sql"))?
            .query_and_then(&[ntid], |row| {
                let config = NoteFieldConfig::decode(row.get_raw(2).as_blob()?)?;
                Ok(NoteField {
                    ord: Some(row.get(0)?),
                    name: row.get(1)?,
                    config,
                })
            })?
            .collect()
    }

    fn get_notetype_templates(&self, ntid: NoteTypeID) -> Result<Vec<CardTemplate>> {
        self.db
            .prepare_cached(include_str!("get_templates.sql"))?
            .query_and_then(&[ntid], |row| {
                let config = CardTemplateConfig::decode(row.get_raw(4).as_blob()?)?;
                Ok(CardTemplate {
                    ord: row.get(0)?,
                    name: row.get(1)?,
                    mtime_secs: row.get(2)?,
                    usn: row.get(3)?,
                    config,
                })
            })?
            .collect()
    }

    pub(crate) fn get_notetype_id(&self, name: &str) -> Result<Option<NoteTypeID>> {
        self.db
            .prepare_cached("select id from notetypes where name = ?")?
            .query_row(params![name], |row| row.get(0))
            .optional()
            .map_err(Into::into)
    }

    pub fn get_all_notetype_names(&self) -> Result<Vec<(NoteTypeID, String)>> {
        self.db
            .prepare_cached(include_str!("get_notetype_names.sql"))?
            .query_and_then(NO_PARAMS, |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect()
    }

    /// Returns list of (id, name, use_count)
    pub fn get_notetype_use_counts(&self) -> Result<Vec<(NoteTypeID, String, u32)>> {
        self.db
            .prepare_cached(include_str!("get_use_counts.sql"))?
            .query_and_then(NO_PARAMS, |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))?
            .collect()
    }

    pub(crate) fn update_notetype_fields(
        &self,
        ntid: NoteTypeID,
        fields: &[NoteField],
    ) -> Result<()> {
        self.db
            .prepare_cached("delete from fields where ntid=?")?
            .execute(&[ntid])?;
        let mut stmt = self.db.prepare_cached(include_str!("update_fields.sql"))?;
        for (ord, field) in fields.iter().enumerate() {
            let mut config_bytes = vec![];
            field.config.encode(&mut config_bytes)?;
            stmt.execute(params![ntid, ord as u32, field.name, config_bytes,])?;
        }

        Ok(())
    }

    /// A sorted list of all field names used by provided notes, for use with
    /// the find&replace feature.
    pub(crate) fn field_names_for_notes(&self, nids: &[NoteID]) -> Result<Vec<String>> {
        let mut sql = include_str!("field_names_for_notes.sql").to_string();
        sql.push(' ');
        ids_to_string(&mut sql, nids);
        sql += ") order by name";
        self.db
            .prepare(&sql)?
            .query_and_then(NO_PARAMS, |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn note_ids_by_notetype(
        &self,
        nids: &[NoteID],
    ) -> Result<Vec<(NoteTypeID, NoteID)>> {
        let mut sql = String::from("select mid, id from notes where id in ");
        ids_to_string(&mut sql, nids);
        sql += " order by mid, id";
        self.db
            .prepare(&sql)?
            .query_and_then(NO_PARAMS, |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect()
    }

    pub(crate) fn all_note_ids_by_notetype(&self) -> Result<Vec<(NoteTypeID, NoteID)>> {
        let sql = String::from("select mid, id from notes order by mid, id");
        self.db
            .prepare(&sql)?
            .query_and_then(NO_PARAMS, |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect()
    }

    pub(crate) fn update_notetype_templates(
        &self,
        ntid: NoteTypeID,
        templates: &[CardTemplate],
    ) -> Result<()> {
        self.db
            .prepare_cached("delete from templates where ntid=?")?
            .execute(&[ntid])?;
        let mut stmt = self
            .db
            .prepare_cached(include_str!("update_templates.sql"))?;
        for (ord, template) in templates.iter().enumerate() {
            let mut config_bytes = vec![];
            template.config.encode(&mut config_bytes)?;
            stmt.execute(params![
                ntid,
                ord as u32,
                template.name,
                template.mtime_secs,
                template.usn,
                config_bytes,
            ])?;
        }

        Ok(())
    }

    pub(crate) fn update_notetype_config(&self, nt: &NoteType) -> Result<()> {
        assert!(nt.id.0 != 0);
        let mut stmt = self
            .db
            .prepare_cached(include_str!("update_notetype_core.sql"))?;
        let mut config_bytes = vec![];
        nt.config.encode(&mut config_bytes)?;
        stmt.execute(params![nt.id, nt.name, nt.mtime_secs, nt.usn, config_bytes])?;

        Ok(())
    }

    pub(crate) fn add_new_notetype(&self, nt: &mut NoteType) -> Result<()> {
        assert!(nt.id.0 == 0);

        let mut stmt = self.db.prepare_cached(include_str!("add_notetype.sql"))?;
        let mut config_bytes = vec![];
        nt.config.encode(&mut config_bytes)?;
        stmt.execute(params![
            TimestampMillis::now(),
            nt.name,
            nt.mtime_secs,
            nt.usn,
            config_bytes
        ])?;
        nt.id.0 = self.db.last_insert_rowid();

        self.update_notetype_fields(nt.id, &nt.fields)?;
        self.update_notetype_templates(nt.id, &nt.templates)?;

        Ok(())
    }

    /// Used for syncing.
    pub(crate) fn add_or_update_notetype(&self, nt: &NoteType) -> Result<()> {
        let mut stmt = self.db.prepare_cached(include_str!("add_or_update.sql"))?;
        let mut config_bytes = vec![];
        nt.config.encode(&mut config_bytes)?;
        stmt.execute(params![nt.id, nt.name, nt.mtime_secs, nt.usn, config_bytes])?;

        self.update_notetype_fields(nt.id, &nt.fields)?;
        self.update_notetype_templates(nt.id, &nt.templates)?;

        Ok(())
    }

    pub(crate) fn remove_cards_for_deleted_templates(
        &self,
        ntid: NoteTypeID,
        ords: &[u32],
    ) -> Result<()> {
        let mut stmt = self
            .db
            .prepare(include_str!("delete_cards_for_template.sql"))?;
        for ord in ords {
            stmt.execute(params![ntid, ord])?;
        }
        Ok(())
    }

    pub(crate) fn remove_notetype(&self, ntid: NoteTypeID) -> Result<()> {
        self.db
            .prepare_cached("delete from cards where nid in (select id from notes where mid=?)")?
            .execute(&[ntid])?;
        self.db
            .prepare_cached("delete from notes where mid=?")?
            .execute(&[ntid])?;
        self.db
            .prepare_cached("delete from templates where ntid=?")?
            .execute(&[ntid])?;
        self.db
            .prepare_cached("delete from fields where ntid=?")?
            .execute(&[ntid])?;
        self.db
            .prepare_cached("delete from notetypes where id=?")?
            .execute(&[ntid])?;

        Ok(())
    }

    pub(crate) fn move_cards_for_repositioned_templates(
        &self,
        ntid: NoteTypeID,
        changes: &[(u32, u32)],
    ) -> Result<()> {
        let case_clauses: Vec<_> = changes
            .iter()
            .map(|(old, new)| format!("when {} then {}", old, new))
            .collect();
        let mut sql = format!(
            "update cards set ord = (case ord {} end) 
where nid in (select id from notes where mid = ?)
and ord in ",
            case_clauses.join(" ")
        );
        ids_to_string(
            &mut sql,
            &changes.iter().map(|(old, _)| old).collect::<Vec<_>>(),
        );
        self.db.prepare(&sql)?.execute(&[ntid])?;
        Ok(())
    }

    pub(crate) fn existing_cards_for_notetype(
        &self,
        ntid: NoteTypeID,
    ) -> Result<Vec<AlreadyGeneratedCardInfo>> {
        self.db
            .prepare_cached(concat!(
                include_str!("existing_cards.sql"),
                " where c.nid in (select id from notes where mid=?)"
            ))?
            .query_and_then(&[ntid], row_to_existing_card)?
            .collect()
    }

    pub(crate) fn existing_cards_for_note(
        &self,
        nid: NoteID,
    ) -> Result<Vec<AlreadyGeneratedCardInfo>> {
        self.db
            .prepare_cached(concat!(
                include_str!("existing_cards.sql"),
                " where c.nid = ?"
            ))?
            .query_and_then(&[nid], row_to_existing_card)?
            .collect()
    }

    pub(crate) fn clear_notetype_usns(&self) -> Result<()> {
        self.db
            .prepare("update notetypes set usn = 0 where usn != 0")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn highest_card_ordinal_for_notetype(&self, ntid: NoteTypeID) -> Result<u16> {
        self.db
            .prepare(include_str!("highest_card_ord.sql"))?
            .query_row(&[ntid], |row| row.get(0))
            .map_err(Into::into)
    }

    // Upgrading/downgrading/legacy

    pub(crate) fn get_all_notetypes_as_schema11(
        &self,
    ) -> Result<HashMap<NoteTypeID, NoteTypeSchema11>> {
        let mut nts = HashMap::new();
        for (ntid, _name) in self.get_all_notetype_names()? {
            let full = self.get_notetype(ntid)?.unwrap();
            nts.insert(ntid, full.into());
        }
        Ok(nts)
    }

    pub(crate) fn upgrade_notetypes_to_schema15(&self) -> Result<()> {
        let nts = self.get_schema11_notetypes()?;
        let mut names = HashSet::new();
        for (mut ntid, nt) in nts {
            let mut nt = NoteType::from(nt);
            // note types with id 0 found in the wild; assign a random ID
            if ntid.0 == 0 {
                ntid.0 = rand::random::<u32>().max(1) as i64;
                nt.id = ntid;
            }
            nt.normalize_names();
            nt.ensure_names_unique();
            loop {
                let name = UniCase::new(nt.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                nt.name.push('_');
            }
            self.update_notetype_config(&nt)?;
            self.update_notetype_fields(ntid, &nt.fields)?;
            self.update_notetype_templates(ntid, &nt.templates)?;
        }
        self.db.execute("update col set models = ''", NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn downgrade_notetypes_from_schema15(&self) -> Result<()> {
        let nts = self.get_all_notetypes_as_schema11()?;
        self.set_schema11_notetypes(nts)
    }

    fn get_schema11_notetypes(&self) -> Result<HashMap<NoteTypeID, NoteTypeSchema11>> {
        let mut stmt = self.db.prepare("select models from col")?;
        let note_types = stmt
            .query_and_then(
                NO_PARAMS,
                |row| -> Result<HashMap<NoteTypeID, NoteTypeSchema11>> {
                    let v: HashMap<NoteTypeID, NoteTypeSchema11> =
                        serde_json::from_str(row.get_raw(0).as_str()?)?;
                    Ok(v)
                },
            )?
            .next()
            .ok_or_else(|| AnkiError::DBError {
                info: "col table empty".to_string(),
                kind: DBErrorKind::MissingEntity,
            })??;
        Ok(note_types)
    }

    pub(crate) fn set_schema11_notetypes(
        &self,
        notetypes: HashMap<NoteTypeID, NoteTypeSchema11>,
    ) -> Result<()> {
        let json = serde_json::to_string(&notetypes)?;
        self.db.execute("update col set models = ?", &[json])?;
        Ok(())
    }
}
