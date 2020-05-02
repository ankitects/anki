// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    define_newtype,
    serde::{default_on_invalid, deserialize_bool_from_anything, deserialize_number_from_string},
    timestamp::TimestampSecs,
    types::Usn,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use serde_tuple::Serialize_tuple;
use std::collections::HashMap;

define_newtype!(DeckID, i64);

#[derive(Serialize, PartialEq, Debug, Clone)]
#[serde(untagged)]
pub enum Deck {
    Normal(NormalDeck),
    Filtered(FilteredDeck),
}

// serde doesn't support integer/bool enum tags, so we manually pick the correct variant
mod dynfix {
    use super::{Deck, FilteredDeck, NormalDeck};
    use serde::de::{self, Deserialize, Deserializer};
    use serde_json::{Map, Value};

    impl<'de> Deserialize<'de> for Deck {
        fn deserialize<D>(deserializer: D) -> Result<Deck, D::Error>
        where
            D: Deserializer<'de>,
        {
            let mut map = Map::deserialize(deserializer)?;

            let (is_dyn, needs_fix) = map
                .get("dyn")
                .ok_or_else(|| de::Error::missing_field("dyn"))
                .and_then(|v| {
                    Ok(match v {
                        Value::Bool(b) => (*b, true),
                        Value::Number(n) => (n.as_i64().unwrap_or(0) == 1, false),
                        _ => {
                            // invalid type
                            return Err(de::Error::custom("dyn was wrong type"));
                        }
                    })
                })?;

            if needs_fix {
                map.insert(
                    "dyn".into(),
                    Value::Number((if is_dyn { 1 } else { 0 }).into()),
                );
            }

            // remove an obsolete key
            map.remove("return");

            let rest = Value::Object(map);
            if is_dyn {
                FilteredDeck::deserialize(rest)
                    .map(Deck::Filtered)
                    .map_err(de::Error::custom)
            } else {
                NormalDeck::deserialize(rest)
                    .map(Deck::Normal)
                    .map_err(de::Error::custom)
            }
        }
    }
}

#[derive(Serialize, Deserialize, PartialEq, Debug, Clone)]
pub struct DeckCommon {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: DeckID,
    #[serde(
        rename = "mod",
        deserialize_with = "deserialize_number_from_string",
        default
    )]
    pub(crate) mtime: TimestampSecs,
    pub(crate) name: String,
    pub(crate) usn: Usn,
    #[serde(flatten)]
    pub(crate) today: DeckToday,
    collapsed: bool,
    #[serde(default)]
    desc: String,
    #[serde(rename = "dyn")]
    dynamic: u8,
    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize, Deserialize, PartialEq, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NormalDeck {
    #[serde(flatten)]
    pub(crate) common: DeckCommon,

    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) conf: i64,
    #[serde(default, deserialize_with = "default_on_invalid")]
    extend_new: i32,
    #[serde(default, deserialize_with = "default_on_invalid")]
    extend_rev: i32,
}

#[derive(Serialize, Deserialize, PartialEq, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct FilteredDeck {
    #[serde(flatten)]
    common: DeckCommon,

    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    resched: bool,
    terms: Vec<FilteredSearch>,

    // unused, but older clients require its existence
    #[serde(default)]
    separate: bool,

    // old scheduler
    #[serde(default, deserialize_with = "default_on_invalid")]
    delays: Option<Vec<f32>>,

    // new scheduler
    #[serde(default)]
    preview_delay: u16,
}
#[derive(Serialize, Deserialize, Debug, PartialEq, Default, Clone)]
pub struct DeckToday {
    #[serde(rename = "lrnToday")]
    pub(crate) lrn: TodayAmount,
    #[serde(rename = "revToday")]
    pub(crate) rev: TodayAmount,
    #[serde(rename = "newToday")]
    pub(crate) new: TodayAmount,
    #[serde(rename = "timeToday")]
    pub(crate) time: TodayAmount,
}

#[derive(Serialize_tuple, Deserialize, Debug, PartialEq, Default, Clone)]
#[serde(from = "Vec<Value>")]
pub struct TodayAmount {
    day: i32,
    amount: i32,
}

impl From<Vec<Value>> for TodayAmount {
    fn from(mut v: Vec<Value>) -> Self {
        let amt = v.pop().and_then(|v| v.as_i64()).unwrap_or(0);
        let day = v.pop().and_then(|v| v.as_i64()).unwrap_or(0);
        TodayAmount {
            amount: amt as i32,
            day: day as i32,
        }
    }
}
#[derive(Serialize_tuple, Deserialize, Debug, PartialEq, Clone)]
pub struct FilteredSearch {
    search: String,
    #[serde(deserialize_with = "deserialize_number_from_string")]
    limit: i32,
    order: i8,
}

impl Deck {
    pub fn common(&self) -> &DeckCommon {
        match self {
            Deck::Normal(d) => &d.common,
            Deck::Filtered(d) => &d.common,
        }
    }

    // pub(crate) fn common_mut(&mut self) -> &mut DeckCommon {
    //     match self {
    //         Deck::Normal(d) => &mut d.common,
    //         Deck::Filtered(d) => &mut d.common,
    //     }
    // }

    pub fn id(&self) -> DeckID {
        self.common().id
    }

    pub fn name(&self) -> &str {
        &self.common().name
    }
}

impl Default for Deck {
    fn default() -> Self {
        Deck::Normal(NormalDeck::default())
    }
}

impl Default for NormalDeck {
    fn default() -> Self {
        NormalDeck {
            common: DeckCommon {
                id: DeckID(0),
                mtime: TimestampSecs(0),
                name: "".to_string(),
                usn: Usn(0),
                collapsed: false,
                desc: "".to_string(),
                today: Default::default(),
                other: Default::default(),
                dynamic: 0,
            },
            conf: 1,
            extend_new: 0,
            extend_rev: 0,
        }
    }
}

pub(crate) fn child_ids<'a>(decks: &'a [Deck], name: &str) -> impl Iterator<Item = DeckID> + 'a {
    let prefix = format!("{}::", name.to_ascii_lowercase());
    decks
        .iter()
        .filter(move |d| d.name().to_ascii_lowercase().starts_with(&prefix))
        .map(|d| d.id())
}

pub(crate) fn get_deck(decks: &[Deck], id: DeckID) -> Option<&Deck> {
    for d in decks {
        if d.id() == id {
            return Some(d);
        }
    }

    None
}
