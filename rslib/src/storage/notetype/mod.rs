// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    backend_proto::{CardTemplate, CardTemplateConfig, NoteField, NoteFieldConfig, NoteTypeConfig},
    err::{AnkiError, DBErrorKind, Result},
    notetype::{NoteType, NoteTypeID, NoteTypeSchema11},
    timestamp::TimestampMillis,
};
use prost::Message;
use rusqlite::{params, Row, NO_PARAMS};
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

impl SqliteStorage {
    fn get_notetype_core(&self, ntid: NoteTypeID) -> Result<Option<NoteType>> {
        self.db
            .prepare_cached(concat!(include_str!("get_notetype.sql"), " where id = ?"))?
            .query_and_then(&[ntid], row_to_notetype_core)?
            .next()
            .transpose()
    }

    pub(crate) fn get_all_notetype_core(&self) -> Result<HashMap<NoteTypeID, NoteType>> {
        self.db
            .prepare_cached(include_str!("get_notetype.sql"))?
            .query_and_then(NO_PARAMS, row_to_notetype_core)?
            .map(|ntres| ntres.map(|nt| (nt.id, nt)))
            .collect()
    }

    pub(crate) fn get_notetype_fields(&self, ntid: NoteTypeID) -> Result<Vec<NoteField>> {
        self.db
            .prepare_cached(include_str!("get_fields.sql"))?
            .query_and_then(&[ntid], |row| {
                let config = NoteFieldConfig::decode(row.get_raw(2).as_blob()?)?;
                Ok(NoteField {
                    ord: row.get(0)?,
                    name: row.get(1)?,
                    config: Some(config),
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
                    config: Some(config),
                })
            })?
            .collect()
    }

    fn get_full_notetype(&self, ntid: NoteTypeID) -> Result<Option<NoteType>> {
        match self.get_notetype_core(ntid)? {
            Some(mut nt) => {
                nt.fields = self.get_notetype_fields(ntid)?;
                nt.templates = self.get_notetype_templates(ntid)?;
                Ok(Some(nt))
            }
            None => Ok(None),
        }
    }

    #[allow(dead_code)]
    fn get_all_notetype_names(&self) -> Result<Vec<(NoteTypeID, String)>> {
        self.db
            .prepare_cached(include_str!("get_notetype_names.sql"))?
            .query_and_then(NO_PARAMS, |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect()
    }

    pub(crate) fn get_all_notetypes_as_schema11(
        &self,
    ) -> Result<HashMap<NoteTypeID, NoteTypeSchema11>> {
        let mut nts = HashMap::new();
        for (ntid, _name) in self.get_all_notetype_core()? {
            let full = self.get_full_notetype(ntid)?.unwrap();
            nts.insert(ntid, full.into());
        }
        Ok(nts)
    }

    fn update_notetype_fields(&self, ntid: NoteTypeID, fields: &[NoteField]) -> Result<()> {
        self.db
            .prepare_cached("delete from fields where ntid=?")?
            .execute(&[ntid])?;
        let mut stmt = self.db.prepare_cached(include_str!("update_fields.sql"))?;
        for (ord, field) in fields.iter().enumerate() {
            let mut config_bytes = vec![];
            field.config.as_ref().unwrap().encode(&mut config_bytes)?;
            stmt.execute(params![ntid, ord as u32, field.name, config_bytes,])?;
        }

        Ok(())
    }

    fn update_notetype_templates(
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
            template
                .config
                .as_ref()
                .unwrap()
                .encode(&mut config_bytes)?;
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

    fn update_notetype_config(&self, nt: &NoteType) -> Result<()> {
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

    // Upgrading/downgrading

    pub(crate) fn upgrade_notetypes_to_schema15(&self) -> Result<()> {
        let nts = self.get_schema11_notetypes()?;
        let mut names = HashSet::new();
        for (ntid, nt) in nts {
            let mut nt = NoteType::from(nt);
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
