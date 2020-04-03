// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection,
    define_newtype,
    err::{AnkiError, Result},
    serde::default_on_invalid,
    timestamp::{TimestampMillis, TimestampSecs},
    types::Usn,
};
use serde_aux::field_attributes::{deserialize_bool_from_anything, deserialize_number_from_string};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use serde_repr::{Deserialize_repr, Serialize_repr};
use serde_tuple::Serialize_tuple;
use std::collections::HashMap;

define_newtype!(DeckConfID, i64);

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DeckConf {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: DeckConfID,
    #[serde(rename = "mod", deserialize_with = "deserialize_number_from_string")]
    pub(crate) mtime: TimestampSecs,
    pub(crate) name: String,
    pub(crate) usn: Usn,
    max_taken: i32,
    autoplay: bool,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    timer: bool,
    #[serde(default)]
    replayq: bool,
    pub(crate) new: NewConf,
    pub(crate) rev: RevConf,
    pub(crate) lapse: LapseConf,
    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NewConf {
    #[serde(default)]
    bury: bool,
    #[serde(deserialize_with = "default_on_invalid")]
    delays: Vec<f32>,
    initial_factor: u16,
    #[serde(deserialize_with = "default_on_invalid")]
    ints: NewCardIntervals,
    #[serde(deserialize_with = "default_on_invalid")]
    order: NewCardOrder,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) per_day: u32,

    // unused, can remove in the future
    #[serde(default)]
    separate: bool,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize_tuple, Deserialize, Debug, PartialEq, Clone)]
pub struct NewCardIntervals {
    good: u16,
    easy: u16,
    _unused: u16,
}

impl Default for NewCardIntervals {
    fn default() -> Self {
        Self {
            good: 1,
            easy: 4,
            _unused: 7,
        }
    }
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Clone)]
#[repr(u8)]
pub enum NewCardOrder {
    Random = 0,
    Due = 1,
}

impl Default for NewCardOrder {
    fn default() -> Self {
        Self::Due
    }
}

fn hard_factor_default() -> f32 {
    1.2
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct RevConf {
    #[serde(default)]
    bury: bool,
    ease4: f32,
    ivl_fct: f32,
    max_ivl: u32,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) per_day: u32,
    #[serde(default = "hard_factor_default")]
    hard_factor: f32,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, Clone)]
#[repr(u8)]
pub enum LeechAction {
    Suspend = 0,
    TagOnly = 1,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LapseConf {
    #[serde(deserialize_with = "default_on_invalid")]
    delays: Vec<f32>,
    #[serde(deserialize_with = "default_on_invalid")]
    leech_action: LeechAction,
    leech_fails: u32,
    min_int: u32,
    mult: f32,

    #[serde(flatten)]
    other: HashMap<String, Value>,
}

impl Default for LeechAction {
    fn default() -> Self {
        LeechAction::Suspend
    }
}

impl Default for RevConf {
    fn default() -> Self {
        RevConf {
            bury: false,
            ease4: 1.3,
            ivl_fct: 1.0,
            max_ivl: 36500,
            per_day: 200,
            hard_factor: 1.2,
            other: Default::default(),
        }
    }
}

impl Default for NewConf {
    fn default() -> Self {
        NewConf {
            bury: false,
            delays: vec![1.0, 10.0],
            initial_factor: 2500,
            ints: NewCardIntervals::default(),
            order: NewCardOrder::default(),
            per_day: 20,
            separate: true,
            other: Default::default(),
        }
    }
}

impl Default for LapseConf {
    fn default() -> Self {
        LapseConf {
            delays: vec![10.0],
            leech_action: LeechAction::default(),
            leech_fails: 8,
            min_int: 1,
            mult: 0.0,
            other: Default::default(),
        }
    }
}

impl Default for DeckConf {
    fn default() -> Self {
        DeckConf {
            id: DeckConfID(0),
            mtime: TimestampSecs(0),
            name: "Default".to_string(),
            usn: Usn(0),
            max_taken: 60,
            autoplay: true,
            timer: false,
            replayq: true,
            new: Default::default(),
            rev: Default::default(),
            lapse: Default::default(),
            other: Default::default(),
        }
    }
}

impl Collection {
    pub fn get_deck_config(&self, dcid: DeckConfID, fallback: bool) -> Result<Option<DeckConf>> {
        if let Some(conf) = self.storage.get_deck_config(dcid)? {
            return Ok(Some(conf));
        }
        if fallback {
            if let Some(conf) = self.storage.get_deck_config(DeckConfID(1))? {
                return Ok(Some(conf));
            }
            // if even the default deck config is missing, just return the defaults
            Ok(Some(DeckConf::default()))
        } else {
            Ok(None)
        }
    }

    pub(crate) fn add_or_update_deck_config(
        &self,
        conf: &mut DeckConf,
        preserve_usn_and_mtime: bool,
    ) -> Result<()> {
        if !preserve_usn_and_mtime {
            conf.mtime = TimestampSecs::now();
            conf.usn = self.usn()?;
        }
        let orig = self.storage.get_deck_config(conf.id)?;
        if let Some(_orig) = orig {
            self.storage.update_deck_conf(&conf)
        } else {
            if conf.id.0 == 0 {
                conf.id.0 = TimestampMillis::now().0;
            }
            self.storage.add_deck_conf(conf)
        }
    }

    pub(crate) fn remove_deck_config(&self, dcid: DeckConfID) -> Result<()> {
        if dcid.0 == 1 {
            return Err(AnkiError::invalid_input("can't delete default conf"));
        }
        self.ensure_schema_modified()?;
        self.storage.remove_deck_conf(dcid)
    }
}
