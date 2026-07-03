// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use anki_proto::notetypes::stock_notetype::Kind as StockKind;

use crate::collection::Collection;
use crate::config::get_aux_notetype_config_key;
use crate::error;
use crate::error::OrInvalid;
use crate::error::OrNotFound;
use crate::notes::NoteId;
use crate::notetype::stock::get_stock_notetype;
use crate::notetype::ChangeNotetypeInput;
use crate::notetype::Notetype;
use crate::notetype::NotetypeChangeInfo;
use crate::notetype::NotetypeId;
use crate::notetype::NotetypeSchema11;
use crate::prelude::IntoNewtypeVec;

impl crate::services::NotetypesService for Collection {
    fn add_notetype(
        &mut self,
        input: anki_proto::notetypes::Notetype,
    ) -> error::Result<anki_proto::collection::OpChangesWithId> {
        let mut notetype: Notetype = input.into();

        Ok(self
            .add_notetype(&mut notetype, false)?
            .map(|_| notetype.id.0)
            .into())
    }

    fn update_notetype(
        &mut self,
        input: anki_proto::notetypes::Notetype,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let mut notetype: Notetype = input.into();
        self.update_notetype(&mut notetype, false).map(Into::into)
    }

    fn add_notetype_legacy(
        &mut self,
        input: generic::Json,
    ) -> error::Result<anki_proto::collection::OpChangesWithId> {
        let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
        let mut notetype: Notetype = legacy.into();

        Ok(self
            .add_notetype(&mut notetype, false)?
            .map(|_| notetype.id.0)
            .into())
    }

    fn update_notetype_legacy(
        &mut self,
        input: anki_proto::notetypes::UpdateNotetypeLegacyRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
        let mut notetype: Notetype = legacy.into();
        self.update_notetype(&mut notetype, input.skip_checks)
            .map(Into::into)
    }

    fn add_or_update_notetype(
        &mut self,
        input: anki_proto::notetypes::AddOrUpdateNotetypeRequest,
    ) -> error::Result<anki_proto::notetypes::NotetypeId> {
        let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
        let mut nt: Notetype = legacy.into();
        if !input.preserve_usn_and_mtime {
            nt.set_modified(self.usn()?);
        }
        if nt.id.0 == 0 {
            self.add_notetype(&mut nt, input.skip_checks)?;
        } else if !input.preserve_usn_and_mtime {
            self.update_notetype(&mut nt, input.skip_checks)?;
        } else {
            self.add_or_update_notetype_with_existing_id(&mut nt, input.skip_checks)?;
        }
        Ok(anki_proto::notetypes::NotetypeId { ntid: nt.id.0 })
    }

    fn get_stock_notetype_legacy(
        &mut self,
        input: anki_proto::notetypes::StockNotetype,
    ) -> error::Result<generic::Json> {
        let nt = get_stock_notetype(input.kind(), &self.tr);
        let schema11: NotetypeSchema11 = nt.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_notetype(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<anki_proto::notetypes::Notetype> {
        let ntid = input.into();

        self.storage
            .get_notetype(ntid)?
            .or_not_found(ntid)
            .map(Into::into)
    }

    fn get_notetype_legacy(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<generic::Json> {
        let ntid = input.into();

        let schema11: NotetypeSchema11 =
            self.storage.get_notetype(ntid)?.or_not_found(ntid)?.into();
        Ok(serde_json::to_vec(&schema11)?.into())
    }

    fn get_notetype_names(&mut self) -> error::Result<anki_proto::notetypes::NotetypeNames> {
        let entries: Vec<_> = self
            .storage
            .get_all_notetype_names()?
            .into_iter()
            .map(|(id, name)| anki_proto::notetypes::NotetypeNameId { id: id.0, name })
            .collect();
        Ok(anki_proto::notetypes::NotetypeNames { entries })
    }

    fn get_notetype_names_and_counts(
        &mut self,
    ) -> error::Result<anki_proto::notetypes::NotetypeUseCounts> {
        let entries: Vec<_> = self
            .storage
            .get_notetype_use_counts()?
            .into_iter()
            .map(
                |(id, name, use_count)| anki_proto::notetypes::NotetypeNameIdUseCount {
                    id: id.0,
                    name,
                    use_count,
                },
            )
            .collect();
        Ok(anki_proto::notetypes::NotetypeUseCounts { entries })
    }

    fn get_notetype_id_by_name(
        &mut self,
        input: generic::String,
    ) -> error::Result<anki_proto::notetypes::NotetypeId> {
        self.storage
            .get_notetype_id(&input.val)
            .and_then(|nt| nt.or_not_found(input.val))
            .map(|ntid| anki_proto::notetypes::NotetypeId { ntid: ntid.0 })
    }

    fn remove_notetype(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.remove_notetype(input.into()).map(Into::into)
    }

    fn get_aux_notetype_config_key(
        &mut self,
        input: anki_proto::notetypes::GetAuxConfigKeyRequest,
    ) -> error::Result<generic::String> {
        Ok(get_aux_notetype_config_key(input.id.into(), &input.key).into())
    }

    fn get_aux_template_config_key(
        &mut self,
        input: anki_proto::notetypes::GetAuxTemplateConfigKeyRequest,
    ) -> error::Result<generic::String> {
        self.get_aux_template_config_key(
            input.notetype_id.into(),
            input.card_ordinal as usize,
            &input.key,
        )
        .map(Into::into)
    }

    fn get_change_notetype_info(
        &mut self,
        input: anki_proto::notetypes::GetChangeNotetypeInfoRequest,
    ) -> error::Result<anki_proto::notetypes::ChangeNotetypeInfo> {
        self.notetype_change_info(input.old_notetype_id.into(), input.new_notetype_id.into())
            .map(Into::into)
    }

    fn change_notetype(
        &mut self,
        input: anki_proto::notetypes::ChangeNotetypeRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        self.change_notetype_of_notes(input.into()).map(Into::into)
    }

    fn get_field_names(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<generic::StringList> {
        self.storage.get_field_names(input.into()).map(Into::into)
    }

    fn restore_notetype_to_stock(
        &mut self,
        input: anki_proto::notetypes::RestoreNotetypeToStockRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let force_kind = input.force_kind.and_then(|s| StockKind::try_from(s).ok());

        self.restore_notetype_to_stock(
            input.notetype_id.or_invalid("missing notetype id")?.into(),
            force_kind,
        )
        .map(Into::into)
    }

    fn get_cloze_field_ords(
        &mut self,
        input: anki_proto::notetypes::NotetypeId,
    ) -> error::Result<anki_proto::notetypes::GetClozeFieldOrdsResponse> {
        Ok(anki_proto::notetypes::GetClozeFieldOrdsResponse {
            ords: self
                .get_notetype(input.into())?
                .unwrap()
                .cloze_fields()
                .iter()
                .map(|ord| (*ord) as u32)
                .collect(),
        })
    }
}

impl From<anki_proto::notetypes::Notetype> for Notetype {
    fn from(n: anki_proto::notetypes::Notetype) -> Self {
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

impl From<NotetypeChangeInfo> for anki_proto::notetypes::ChangeNotetypeInfo {
    fn from(i: NotetypeChangeInfo) -> Self {
        anki_proto::notetypes::ChangeNotetypeInfo {
            old_notetype_name: i.old_notetype_name,
            old_field_names: i.old_field_names,
            old_template_names: i.old_template_names,
            new_field_names: i.new_field_names,
            new_template_names: i.new_template_names,
            input: Some(i.input.into()),
        }
    }
}

impl From<anki_proto::notetypes::ChangeNotetypeRequest> for ChangeNotetypeInput {
    fn from(i: anki_proto::notetypes::ChangeNotetypeRequest) -> Self {
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

impl From<ChangeNotetypeInput> for anki_proto::notetypes::ChangeNotetypeRequest {
    fn from(i: ChangeNotetypeInput) -> Self {
        anki_proto::notetypes::ChangeNotetypeRequest {
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
            is_cloze: i.new_templates.is_none(),
            new_templates: i
                .new_templates
                .unwrap_or_default()
                .into_iter()
                .map(|idx| idx.map(|v| v as i32).unwrap_or(-1))
                .collect(),
        }
    }
}

impl From<anki_proto::notetypes::NotetypeId> for NotetypeId {
    fn from(ntid: anki_proto::notetypes::NotetypeId) -> Self {
        NotetypeId(ntid.ntid)
    }
}

impl From<NotetypeId> for anki_proto::notetypes::NotetypeId {
    fn from(ntid: NotetypeId) -> Self {
        anki_proto::notetypes::NotetypeId { ntid: ntid.0 }
    }
}

#[cfg(test)]
mod tests {
    use anki_proto::generic;

    use super::*;
    use crate::prelude::*;
    use crate::services::NotetypesService;
    use crate::tests::NoteAdder;

    fn basic_notetype_id(col: &mut Collection) -> i64 {
        NotetypesService::get_notetype_names(col)
            .unwrap()
            .entries
            .into_iter()
            .find(|e| e.name == "Basic")
            .unwrap()
            .id
    }

    // --- From<proto::NotetypeId> for NotetypeId ---

    #[test]
    fn proto_notetype_id_converts_to_domain() {
        let proto = anki_proto::notetypes::NotetypeId { ntid: 42 };
        let domain: NotetypeId = proto.into();
        assert_eq!(domain, NotetypeId(42));
    }

    #[test]
    fn notetype_id_round_trips_through_proto() {
        let original = NotetypeId(99);
        let proto: anki_proto::notetypes::NotetypeId = original.into();
        let back: NotetypeId = proto.into();
        assert_eq!(back, original);
    }

    // --- From<ChangeNotetypeRequest> for ChangeNotetypeInput ---

    #[test]
    fn change_notetype_request_maps_minus_one_to_none() {
        let req = anki_proto::notetypes::ChangeNotetypeRequest {
            new_fields: vec![-1, 0, 2],
            new_templates: vec![],
            ..Default::default()
        };
        let input: ChangeNotetypeInput = req.into();
        assert!(input.new_fields[0].is_none(), "expected None for -1");
        assert_eq!(input.new_fields[1], Some(0), "expected Some(0) for 0");
        assert_eq!(input.new_fields[2], Some(2), "expected Some(2) for 2");
        assert!(
            input.new_templates.is_none(),
            "expected None for empty templates vec"
        );
    }

    #[test]
    fn change_notetype_request_maps_minus_one_in_templates_to_none() {
        let req = anki_proto::notetypes::ChangeNotetypeRequest {
            new_templates: vec![-1, 0, 1],
            ..Default::default()
        };
        let input: ChangeNotetypeInput = req.into();
        let templates = input
            .new_templates
            .expect("expected Some for non-empty templates");
        assert!(templates[0].is_none(), "expected None for -1");
        assert_eq!(templates[1], Some(0), "expected Some(0) for 0");
        assert_eq!(templates[2], Some(1), "expected Some(1) for 1");
    }

    // --- From<ChangeNotetypeInput> for ChangeNotetypeRequest ---

    #[test]
    fn change_notetype_input_to_proto_sets_is_cloze_when_no_templates() {
        let input = ChangeNotetypeInput {
            current_schema: TimestampMillis(0),
            note_ids: vec![],
            old_notetype_name: "Old".to_string(),
            old_notetype_id: NotetypeId(1),
            new_notetype_id: NotetypeId(2),
            new_fields: vec![],
            new_templates: None,
        };
        let proto: anki_proto::notetypes::ChangeNotetypeRequest = input.into();
        assert!(
            proto.is_cloze,
            "expected is_cloze=true when new_templates is None"
        );
    }

    #[test]
    fn change_notetype_input_to_proto_maps_none_to_minus_one_and_sets_is_cloze_false() {
        let input = ChangeNotetypeInput {
            current_schema: TimestampMillis(0),
            note_ids: vec![],
            old_notetype_name: String::new(),
            old_notetype_id: NotetypeId(1),
            new_notetype_id: NotetypeId(2),
            new_fields: vec![None, Some(1)],
            new_templates: Some(vec![None, Some(0)]),
        };
        let proto: anki_proto::notetypes::ChangeNotetypeRequest = input.into();
        assert_eq!(
            proto.new_fields,
            vec![-1, 1],
            "None→-1 and Some(1)→1 in fields"
        );
        assert!(
            !proto.is_cloze,
            "expected is_cloze=false when new_templates is Some"
        );
        assert_eq!(
            proto.new_templates,
            vec![-1, 0],
            "None→-1 and Some(0)→0 in templates"
        );
    }

    // --- Service: get_notetype_legacy / get_stock_notetype_legacy ---

    #[test]
    fn get_notetype_legacy_returns_json_with_expected_name() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let result = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let json: serde_json::Value = serde_json::from_slice(&result.json).unwrap();
        assert_eq!(json["name"], "Basic");
    }

    #[test]
    fn get_stock_notetype_legacy_returns_valid_json_for_all_kinds() {
        use anki_proto::notetypes::stock_notetype::Kind;
        let cases: &[(Kind, &str)] = &[
            (Kind::Basic, "Basic"),
            (Kind::BasicAndReversed, "Basic (and reversed card)"),
            (
                Kind::BasicOptionalReversed,
                "Basic (optional reversed card)",
            ),
            (Kind::BasicTyping, "Basic (type in the answer)"),
            (Kind::Cloze, "Cloze"),
            (Kind::ImageOcclusion, "Image Occlusion"),
        ];
        for (kind, expected_name) in cases {
            let mut col = Collection::new();
            let result = NotetypesService::get_stock_notetype_legacy(
                &mut col,
                anki_proto::notetypes::StockNotetype { kind: *kind as i32 },
            )
            .unwrap();
            let json: serde_json::Value = serde_json::from_slice(&result.json).unwrap();
            assert_eq!(
                json["name"], *expected_name,
                "wrong name for kind {:?}",
                kind
            );
        }
    }

    // --- Service: add_notetype_legacy ---

    #[test]
    fn add_notetype_legacy_assigns_non_zero_id() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let legacy_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let mut json: serde_json::Value = serde_json::from_slice(&legacy_json.json).unwrap();
        json["id"] = serde_json::json!(0);
        json["name"] = serde_json::json!("LegacyAdded");
        let result = NotetypesService::add_notetype_legacy(
            &mut col,
            generic::Json {
                json: serde_json::to_vec(&json).unwrap(),
            },
        )
        .unwrap();
        assert!(
            result.id > 0,
            "expected non-zero ID from add_notetype_legacy"
        );
    }

    // --- Service: update_notetype_legacy ---

    #[test]
    fn update_notetype_legacy_persists_name_change() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let legacy_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let mut json: serde_json::Value = serde_json::from_slice(&legacy_json.json).unwrap();
        json["name"] = serde_json::json!("BasicRenamed");
        let _ = NotetypesService::update_notetype_legacy(
            &mut col,
            anki_proto::notetypes::UpdateNotetypeLegacyRequest {
                json: serde_json::to_vec(&json).unwrap(),
                skip_checks: false,
            },
        )
        .unwrap();
        let updated_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let updated: serde_json::Value = serde_json::from_slice(&updated_json.json).unwrap();
        assert_eq!(updated["name"], "BasicRenamed");
    }

    // --- Service: add_or_update_notetype (branch: id=0 → add) ---

    #[test]
    fn add_or_update_notetype_adds_when_id_is_zero() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let legacy_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let mut json: serde_json::Value = serde_json::from_slice(&legacy_json.json).unwrap();
        json["id"] = serde_json::json!(0);
        json["name"] = serde_json::json!("AddOrUpdateAdded");
        let result = NotetypesService::add_or_update_notetype(
            &mut col,
            anki_proto::notetypes::AddOrUpdateNotetypeRequest {
                json: serde_json::to_vec(&json).unwrap(),
                preserve_usn_and_mtime: false,
                skip_checks: false,
            },
        )
        .unwrap();
        assert!(
            result.ntid > 0,
            "expected non-zero ID from add_or_update_notetype"
        );
    }

    // --- Service: add_or_update_notetype (branch: id!=0, !preserve_usn_and_mtime →
    // update) ---

    #[test]
    fn add_or_update_notetype_updates_when_id_is_nonzero() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let legacy_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let mut json: serde_json::Value = serde_json::from_slice(&legacy_json.json).unwrap();
        json["name"] = serde_json::json!("UpdatedViaAddOrUpdate");
        let _ = NotetypesService::add_or_update_notetype(
            &mut col,
            anki_proto::notetypes::AddOrUpdateNotetypeRequest {
                json: serde_json::to_vec(&json).unwrap(),
                preserve_usn_and_mtime: false,
                skip_checks: false,
            },
        )
        .unwrap();
        let updated_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let updated: serde_json::Value = serde_json::from_slice(&updated_json.json).unwrap();
        assert_eq!(updated["name"], "UpdatedViaAddOrUpdate");
    }

    // --- Service: add_or_update_notetype (branch: id!=0, preserve_usn_and_mtime →
    // add_or_update_with_existing_id) ---

    #[test]
    fn add_or_update_notetype_preserves_usn_when_flag_is_set() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let legacy_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let mut json: serde_json::Value = serde_json::from_slice(&legacy_json.json).unwrap();
        json["name"] = serde_json::json!("UpdatedWithPreservedUsn");
        let _ = NotetypesService::add_or_update_notetype(
            &mut col,
            anki_proto::notetypes::AddOrUpdateNotetypeRequest {
                json: serde_json::to_vec(&json).unwrap(),
                preserve_usn_and_mtime: true,
                skip_checks: false,
            },
        )
        .unwrap();
        let updated_json = NotetypesService::get_notetype_legacy(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        let updated: serde_json::Value = serde_json::from_slice(&updated_json.json).unwrap();
        assert_eq!(updated["name"], "UpdatedWithPreservedUsn");
    }

    // --- Service: get_notetype_names ---

    #[test]
    fn get_notetype_names_includes_stock_notetypes() {
        let mut col = Collection::new();
        let names = NotetypesService::get_notetype_names(&mut col).unwrap();
        let name_list: Vec<&str> = names.entries.iter().map(|e| e.name.as_str()).collect();
        assert!(name_list.contains(&"Basic"), "expected Basic in names");
        assert!(name_list.contains(&"Cloze"), "expected Cloze in names");
    }

    // --- Service: add_notetype ---

    #[test]
    fn add_notetype_returns_non_zero_id() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let mut proto = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        proto.id = 0;
        proto.name = "TestType".to_string();
        let result = NotetypesService::add_notetype(&mut col, proto).unwrap();
        assert!(result.id > 0, "expected non-zero ID after add");
    }

    #[test]
    fn add_notetype_is_retrievable_by_id() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let mut proto = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        proto.id = 0;
        proto.name = "RetrieveById".to_string();
        let result = NotetypesService::add_notetype(&mut col, proto).unwrap();
        let fetched = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: result.id },
        )
        .unwrap();
        assert_eq!(fetched.name, "RetrieveById");
    }

    #[test]
    fn add_notetype_is_retrievable_by_name() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let mut proto = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        proto.id = 0;
        proto.name = "RetrieveByName".to_string();
        let _ = NotetypesService::add_notetype(&mut col, proto).unwrap();
        let result = NotetypesService::get_notetype_id_by_name(
            &mut col,
            generic::String {
                val: "RetrieveByName".to_string(),
            },
        )
        .unwrap();
        assert!(
            result.ntid > 0,
            "expected non-zero ID from get_notetype_id_by_name"
        );
    }

    // --- Service: get_notetype error path ---

    #[test]
    fn get_notetype_returns_error_for_unknown_id() {
        let mut col = Collection::new();
        let result = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: 999_999 },
        );
        assert!(result.is_err(), "expected Err for unknown notetype ID");
    }

    // --- Service: get_notetype_id_by_name error path ---

    #[test]
    fn get_notetype_id_by_name_returns_error_for_unknown_name() {
        let mut col = Collection::new();
        let result = NotetypesService::get_notetype_id_by_name(
            &mut col,
            generic::String {
                val: "NonExistentNotetype".to_string(),
            },
        );
        assert!(result.is_err(), "expected Err for unknown notetype name");
    }

    // --- Service: get_field_names ---

    #[test]
    fn get_field_names_returns_basic_fields() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let result = NotetypesService::get_field_names(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        assert_eq!(result.vals, vec!["Front", "Back"]);
    }

    // --- Service: update_notetype ---

    #[test]
    fn update_notetype_field_name_persists() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let mut proto = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        proto.fields[0].name = "Question".to_string();
        let _ = NotetypesService::update_notetype(&mut col, proto).unwrap();
        let updated = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        assert_eq!(
            updated.fields[0].name, "Question",
            "expected renamed field to persist after update"
        );
    }

    #[test]
    fn update_notetype_adding_template_generates_new_card() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col)
            .fields(&["front text", "back text"])
            .add(&mut col);
        assert_eq!(
            col.storage.get_all_cards().len(),
            1,
            "expected 1 card before adding template"
        );
        let basic_id = basic_notetype_id(&mut col);
        let mut proto = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        proto
            .templates
            .push(anki_proto::notetypes::notetype::Template {
                name: "Card 2".to_string(),
                config: Some(anki_proto::notetypes::notetype::template::Config {
                    q_format: "{{Back}}".to_string(),
                    a_format: "{{FrontSide}}<hr id=answer>{{Front}}".to_string(),
                    ..Default::default()
                }),
                ..Default::default()
            });
        let _ = NotetypesService::update_notetype(&mut col, proto).unwrap();
        assert_eq!(
            col.storage.get_all_cards().len(),
            2,
            "expected 2 cards after adding template"
        );
    }

    // --- Service: remove_notetype ---

    #[test]
    fn remove_notetype_not_in_names_list() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let mut proto = NotetypesService::get_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: basic_id },
        )
        .unwrap();
        proto.id = 0;
        proto.name = "ToRemove".to_string();
        let added = NotetypesService::add_notetype(&mut col, proto).unwrap();
        let _ = NotetypesService::remove_notetype(
            &mut col,
            anki_proto::notetypes::NotetypeId { ntid: added.id },
        )
        .unwrap();
        let names = NotetypesService::get_notetype_names(&mut col).unwrap();
        let name_list: Vec<&str> = names.entries.iter().map(|e| e.name.as_str()).collect();
        assert!(
            !name_list.contains(&"ToRemove"),
            "expected removed notetype to be absent from names"
        );
    }

    // --- Service: get_notetype_names_and_counts ---

    #[test]
    fn get_notetype_names_and_counts_returns_zero_for_unused_notetypes() {
        let mut col = Collection::new();
        let counts = NotetypesService::get_notetype_names_and_counts(&mut col).unwrap();
        let basic = counts
            .entries
            .iter()
            .find(|e| e.name == "Basic")
            .expect("Basic notetype not found");
        assert_eq!(
            basic.use_count, 0,
            "expected use_count 0 for Basic with no notes"
        );
    }

    #[test]
    fn get_notetype_names_and_counts_increments_after_adding_note() {
        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);
        let counts = NotetypesService::get_notetype_names_and_counts(&mut col).unwrap();
        let basic = counts
            .entries
            .iter()
            .find(|e| e.name == "Basic")
            .expect("Basic notetype not found");
        assert_eq!(
            basic.use_count, 1,
            "expected use_count 1 after adding one Basic note"
        );
    }

    // --- Service: get_aux_notetype_config_key ---

    #[test]
    fn get_aux_notetype_config_key_formats_key_with_notetype_id() {
        let mut col = Collection::new();
        let result = NotetypesService::get_aux_notetype_config_key(
            &mut col,
            anki_proto::notetypes::GetAuxConfigKeyRequest {
                id: 42,
                key: "test".to_string(),
            },
        )
        .unwrap();
        assert_eq!(result.val, "_nt_42_test");
    }

    // --- Service: get_aux_template_config_key ---

    #[test]
    fn get_aux_template_config_key_uses_card_ordinal_for_normal_notetype() {
        let mut col = Collection::new();
        let basic_id = basic_notetype_id(&mut col);
        let result = NotetypesService::get_aux_template_config_key(
            &mut col,
            anki_proto::notetypes::GetAuxTemplateConfigKeyRequest {
                notetype_id: basic_id,
                card_ordinal: 1,
                key: "test".to_string(),
            },
        )
        .unwrap();
        assert!(
            result.val.ends_with("_test_1"),
            "expected ordinal 1 in key for normal notetype, got: {}",
            result.val
        );
    }

    #[test]
    fn get_aux_template_config_key_uses_zero_ordinal_for_cloze_notetype() {
        let mut col = Collection::new();
        let cloze_id = NotetypesService::get_notetype_names(&mut col)
            .unwrap()
            .entries
            .into_iter()
            .find(|e| e.name == "Cloze")
            .unwrap()
            .id;
        let result = NotetypesService::get_aux_template_config_key(
            &mut col,
            anki_proto::notetypes::GetAuxTemplateConfigKeyRequest {
                notetype_id: cloze_id,
                card_ordinal: 5,
                key: "test".to_string(),
            },
        )
        .unwrap();
        assert!(
            result.val.ends_with("_test_0"),
            "expected ordinal 0 in key for Cloze notetype regardless of input, got: {}",
            result.val
        );
    }

    #[test]
    fn get_aux_template_config_key_returns_error_for_unknown_notetype_id() {
        let mut col = Collection::new();
        let result = NotetypesService::get_aux_template_config_key(
            &mut col,
            anki_proto::notetypes::GetAuxTemplateConfigKeyRequest {
                notetype_id: 999_999,
                card_ordinal: 0,
                key: "test".to_string(),
            },
        );
        assert!(result.is_err(), "expected Err for unknown notetype ID");
    }
}
