// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::config::get_aux_notetype_config_key;
use crate::notetype::all_stock_notetypes;
use crate::notetype::ChangeNotetypeInput;
use crate::notetype::Notetype;
use crate::notetype::NotetypeChangeInfo;
use crate::notetype::NotetypeSchema11;
use crate::pb;
pub(super) use crate::pb::notetypes::notetypes_service::Service as NotetypesService;
use crate::prelude::*;

impl NotetypesService for Backend {
    fn add_notetype(
        &self,
        input: pb::notetypes::Notetype,
    ) -> Result<pb::collection::OpChangesWithId> {
        let mut notetype: Notetype = input.into();
        self.with_col(|col| {
            Ok(col
                .add_notetype(&mut notetype, false)?
                .map(|_| notetype.id.0)
                .into())
        })
    }

    fn update_notetype(&self, input: pb::notetypes::Notetype) -> Result<pb::collection::OpChanges> {
        let mut notetype: Notetype = input.into();
        self.with_col(|col| col.update_notetype(&mut notetype, false))
            .map(Into::into)
    }

    fn add_notetype_legacy(
        &self,
        input: pb::generic::Json,
    ) -> Result<pb::collection::OpChangesWithId> {
        let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
        let mut notetype: Notetype = legacy.into();
        self.with_col(|col| {
            Ok(col
                .add_notetype(&mut notetype, false)?
                .map(|_| notetype.id.0)
                .into())
        })
    }

    fn update_notetype_legacy(
        &self,
        input: pb::generic::Json,
    ) -> Result<pb::collection::OpChanges> {
        let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
        let mut notetype: Notetype = legacy.into();
        self.with_col(|col| col.update_notetype(&mut notetype, false))
            .map(Into::into)
    }

    fn add_or_update_notetype(
        &self,
        input: pb::notetypes::AddOrUpdateNotetypeRequest,
    ) -> Result<pb::notetypes::NotetypeId> {
        self.with_col(|col| {
            let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
            let mut nt: Notetype = legacy.into();
            if !input.preserve_usn_and_mtime {
                nt.set_modified(col.usn()?);
            }
            if nt.id.0 == 0 {
                col.add_notetype(&mut nt, input.skip_checks)?;
            } else if !input.preserve_usn_and_mtime {
                col.update_notetype(&mut nt, input.skip_checks)?;
            } else {
                col.add_or_update_notetype_with_existing_id(&mut nt, input.skip_checks)?;
            }
            Ok(pb::notetypes::NotetypeId { ntid: nt.id.0 })
        })
    }

    fn get_stock_notetype_legacy(
        &self,
        input: pb::notetypes::StockNotetype,
    ) -> Result<pb::generic::Json> {
        // fixme: use individual functions instead of full vec
        let mut all = all_stock_notetypes(&self.tr);
        let idx = (input.kind as usize).min(all.len() - 1);
        let nt = all.swap_remove(idx);
        let schema11: NotetypeSchema11 = nt.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_notetype(&self, input: pb::notetypes::NotetypeId) -> Result<pb::notetypes::Notetype> {
        let ntid = input.into();
        self.with_col(|col| {
            col.storage
                .get_notetype(ntid)?
                .or_not_found(ntid)
                .map(Into::into)
        })
    }

    fn get_notetype_legacy(&self, input: pb::notetypes::NotetypeId) -> Result<pb::generic::Json> {
        let ntid = input.into();
        self.with_col(|col| {
            let schema11: NotetypeSchema11 =
                col.storage.get_notetype(ntid)?.or_not_found(ntid)?.into();
            Ok(serde_json::to_vec(&schema11)?).map(Into::into)
        })
    }

    fn get_notetype_names(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::notetypes::NotetypeNames> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_all_notetype_names()?
                .into_iter()
                .map(|(id, name)| pb::notetypes::NotetypeNameId { id: id.0, name })
                .collect();
            Ok(pb::notetypes::NotetypeNames { entries })
        })
    }

    fn get_notetype_names_and_counts(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::notetypes::NotetypeUseCounts> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_notetype_use_counts()?
                .into_iter()
                .map(
                    |(id, name, use_count)| pb::notetypes::NotetypeNameIdUseCount {
                        id: id.0,
                        name,
                        use_count,
                    },
                )
                .collect();
            Ok(pb::notetypes::NotetypeUseCounts { entries })
        })
    }

    fn get_notetype_id_by_name(
        &self,
        input: pb::generic::String,
    ) -> Result<pb::notetypes::NotetypeId> {
        self.with_col(|col| {
            col.storage
                .get_notetype_id(&input.val)
                .and_then(|nt| nt.or_not_found(input.val))
                .map(|ntid| pb::notetypes::NotetypeId { ntid: ntid.0 })
        })
    }

    fn remove_notetype(
        &self,
        input: pb::notetypes::NotetypeId,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.remove_notetype(input.into()))
            .map(Into::into)
    }

    fn get_aux_notetype_config_key(
        &self,
        input: pb::notetypes::GetAuxConfigKeyRequest,
    ) -> Result<pb::generic::String> {
        Ok(get_aux_notetype_config_key(input.id.into(), &input.key).into())
    }

    fn get_aux_template_config_key(
        &self,
        input: pb::notetypes::GetAuxTemplateConfigKeyRequest,
    ) -> Result<pb::generic::String> {
        self.with_col(|col| {
            col.get_aux_template_config_key(
                input.notetype_id.into(),
                input.card_ordinal as usize,
                &input.key,
            )
            .map(Into::into)
        })
    }

    fn get_change_notetype_info(
        &self,
        input: pb::notetypes::GetChangeNotetypeInfoRequest,
    ) -> Result<pb::notetypes::ChangeNotetypeInfo> {
        self.with_col(|col| {
            col.notetype_change_info(input.old_notetype_id.into(), input.new_notetype_id.into())
                .map(Into::into)
        })
    }

    fn change_notetype(
        &self,
        input: pb::notetypes::ChangeNotetypeRequest,
    ) -> Result<pb::collection::OpChanges> {
        self.with_col(|col| col.change_notetype_of_notes(input.into()).map(Into::into))
    }

    fn get_field_names(&self, input: pb::notetypes::NotetypeId) -> Result<pb::generic::StringList> {
        self.with_col(|col| col.storage.get_field_names(input.into()))
            .map(Into::into)
    }
}

impl From<pb::notetypes::Notetype> for Notetype {
    fn from(n: pb::notetypes::Notetype) -> Self {
        Notetype {
            id: n.id.into(),
            name: n.name,
            mtime_secs: n.mtime_secs.into(),
            usn: n.usn.into(),
            fields: n.fields.into_iter().map(Into::into).collect(),
            templates: n.templates.into_iter().map(Into::into).collect(),
            config: n.config.unwrap_or_default(),
        }
    }
}

impl From<NotetypeChangeInfo> for pb::notetypes::ChangeNotetypeInfo {
    fn from(i: NotetypeChangeInfo) -> Self {
        pb::notetypes::ChangeNotetypeInfo {
            old_notetype_name: i.old_notetype_name,
            old_field_names: i.old_field_names,
            old_template_names: i.old_template_names,
            new_field_names: i.new_field_names,
            new_template_names: i.new_template_names,
            input: Some(i.input.into()),
        }
    }
}

impl From<pb::notetypes::ChangeNotetypeRequest> for ChangeNotetypeInput {
    fn from(i: pb::notetypes::ChangeNotetypeRequest) -> Self {
        ChangeNotetypeInput {
            current_schema: i.current_schema.into(),
            note_ids: i.note_ids.into_newtype(NoteId),
            old_notetype_name: i.old_notetype_name,
            old_notetype_id: i.old_notetype_id.into(),
            new_notetype_id: i.new_notetype_id.into(),
            new_fields: i
                .new_fields
                .into_iter()
                .map(|v| if v == -1 { None } else { Some(v as usize) })
                .collect(),
            new_templates: {
                let v: Vec<_> = i
                    .new_templates
                    .into_iter()
                    .map(|v| if v == -1 { None } else { Some(v as usize) })
                    .collect();
                if v.is_empty() {
                    None
                } else {
                    Some(v)
                }
            },
        }
    }
}

impl From<ChangeNotetypeInput> for pb::notetypes::ChangeNotetypeRequest {
    fn from(i: ChangeNotetypeInput) -> Self {
        pb::notetypes::ChangeNotetypeRequest {
            current_schema: i.current_schema.into(),
            note_ids: i.note_ids.into_iter().map(Into::into).collect(),
            old_notetype_name: i.old_notetype_name,
            old_notetype_id: i.old_notetype_id.into(),
            new_notetype_id: i.new_notetype_id.into(),
            new_fields: i
                .new_fields
                .into_iter()
                .map(|idx| idx.map(|v| v as i32).unwrap_or(-1))
                .collect(),
            new_templates: i
                .new_templates
                .unwrap_or_default()
                .into_iter()
                .map(|idx| idx.map(|v| v as i32).unwrap_or(-1))
                .collect(),
        }
    }
}
