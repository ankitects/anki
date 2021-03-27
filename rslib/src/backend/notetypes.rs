// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::{
    backend_proto as pb,
    notetype::{all_stock_notetypes, Notetype, NotetypeSchema11},
    prelude::*,
};
pub(super) use pb::notetypes_service::Service as NotetypesService;

impl NotetypesService for Backend {
    fn add_or_update_notetype(&self, input: pb::AddOrUpdateNotetypeIn) -> Result<pb::NotetypeId> {
        self.with_col(|col| {
            let legacy: NotetypeSchema11 = serde_json::from_slice(&input.json)?;
            let mut nt: Notetype = legacy.into();
            if nt.id.0 == 0 {
                col.add_notetype(&mut nt)?;
            } else {
                col.update_notetype(&mut nt, input.preserve_usn_and_mtime)?;
            }
            Ok(pb::NotetypeId { ntid: nt.id.0 })
        })
    }

    fn get_stock_notetype_legacy(&self, input: pb::StockNotetype) -> Result<pb::Json> {
        // fixme: use individual functions instead of full vec
        let mut all = all_stock_notetypes(&self.tr);
        let idx = (input.kind as usize).min(all.len() - 1);
        let nt = all.swap_remove(idx);
        let schema11: NotetypeSchema11 = nt.into();
        serde_json::to_vec(&schema11)
            .map_err(Into::into)
            .map(Into::into)
    }

    fn get_notetype_legacy(&self, input: pb::NotetypeId) -> Result<pb::Json> {
        self.with_col(|col| {
            let schema11: NotetypeSchema11 = col
                .storage
                .get_notetype(input.into())?
                .ok_or(AnkiError::NotFound)?
                .into();
            Ok(serde_json::to_vec(&schema11)?).map(Into::into)
        })
    }

    fn get_notetype_names(&self, _input: pb::Empty) -> Result<pb::NotetypeNames> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_all_notetype_names()?
                .into_iter()
                .map(|(id, name)| pb::NotetypeNameId { id: id.0, name })
                .collect();
            Ok(pb::NotetypeNames { entries })
        })
    }

    fn get_notetype_names_and_counts(&self, _input: pb::Empty) -> Result<pb::NotetypeUseCounts> {
        self.with_col(|col| {
            let entries: Vec<_> = col
                .storage
                .get_notetype_use_counts()?
                .into_iter()
                .map(|(id, name, use_count)| pb::NotetypeNameIdUseCount {
                    id: id.0,
                    name,
                    use_count,
                })
                .collect();
            Ok(pb::NotetypeUseCounts { entries })
        })
    }

    fn get_notetype_id_by_name(&self, input: pb::String) -> Result<pb::NotetypeId> {
        self.with_col(|col| {
            col.storage
                .get_notetype_id(&input.val)
                .and_then(|nt| nt.ok_or(AnkiError::NotFound))
                .map(|ntid| pb::NotetypeId { ntid: ntid.0 })
        })
    }

    fn remove_notetype(&self, input: pb::NotetypeId) -> Result<pb::Empty> {
        self.with_col(|col| col.remove_notetype(input.into()))
            .map(Into::into)
    }
}
