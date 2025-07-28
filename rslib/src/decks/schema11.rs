// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::decks::deck::normal::DayLimit;
use phf::phf_set;
use phf::Set;
use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;
use serde_tuple::Serialize_tuple;

use super::DeckCommon;
use super::FilteredDeck;
use super::FilteredSearchTerm;
use super::NormalDeck;
use crate::notetype::schema11::parse_other_fields;
use crate::prelude::*;
use crate::serde::default_on_invalid;
use crate::serde::deserialize_bool_from_anything;
use crate::serde::deserialize_number_from_string;

#[derive(Serialize, PartialEq, Debug, Clone)]
#[serde(untagged)]
pub enum DeckSchema11 {
    Normal(NormalDeckSchema11),
    Filtered(FilteredDeckSchema11),
}

// serde doesn't support integer/bool enum tags, so we manually pick the correct
// variant
mod dynfix {
    use serde::de;
    use serde::de::Deserialize;
    use serde::de::Deserializer;
    use serde_json::Map;
    use serde_json::Value;

    use super::DeckSchema11;
    use super::FilteredDeckSchema11;
    use super::NormalDeckSchema11;

    impl<'de> Deserialize<'de> for DeckSchema11 {
        fn deserialize<D>(deserializer: D) -> Result<DeckSchema11, D::Error>
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
                map.insert("dyn".into(), Value::Number(u8::from(is_dyn).into()));
            }

            // remove an obsolete key
            map.remove("return");

            let rest = Value::Object(map);
            if is_dyn {
                FilteredDeckSchema11::deserialize(rest)
                    .map(DeckSchema11::Filtered)
                    .map_err(de::Error::custom)
            } else {
                NormalDeckSchema11::deserialize(rest)
                    .map(DeckSchema11::Normal)
                    .map_err(de::Error::custom)
            }
        }
    }
}

fn is_false(b: &bool) -> bool {
    !b
}

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug, Clone)]
pub struct DeckCommonSchema11 {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: DeckId,
    #[serde(
        rename = "mod",
        deserialize_with = "deserialize_number_from_string",
        default
    )]
    pub(crate) mtime: TimestampSecs,
    pub(crate) name: String,
    pub(crate) usn: Usn,
    #[serde(flatten)]
    pub(crate) today: DeckTodaySchema11,
    #[serde(rename = "collapsed")]
    study_collapsed: bool,
    #[serde(default, rename = "browserCollapsed")]
    browser_collapsed: bool,
    #[serde(default)]
    desc: String,
    #[serde(default, rename = "md", skip_serializing_if = "is_false")]
    markdown_description: bool,
    #[serde(rename = "dyn")]
    dynamic: u8,
    #[serde(flatten)]
    other: HashMap<String, Value>,
}

#[derive(Serialize, Deserialize, PartialEq, Eq, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NormalDeckSchema11 {
    #[serde(flatten)]
    pub(crate) common: DeckCommonSchema11,

    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) conf: i64,
    #[serde(default, deserialize_with = "default_on_invalid")]
    extend_new: i32,
    #[serde(default, deserialize_with = "default_on_invalid")]
    extend_rev: i32,
    #[serde(default, deserialize_with = "default_on_invalid")]
    review_limit: Option<u32>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    new_limit: Option<u32>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    review_limit_today: Option<DayLimit>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    new_limit_today: Option<DayLimit>,
}

#[derive(Serialize, Deserialize, PartialEq, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct FilteredDeckSchema11 {
    #[serde(flatten)]
    common: DeckCommonSchema11,

    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    resched: bool,
    terms: Vec<FilteredSearchTermSchema11>,

    // unused, but older clients require its existence
    #[serde(default)]
    separate: bool,

    // old scheduler
    #[serde(default, deserialize_with = "default_on_invalid")]
    delays: Option<Vec<f32>>,
    // old scheduler
    #[serde(default)]
    preview_delay: u32,

    // new scheduler
    #[serde(default)]
    preview_again_secs: u32,
    #[serde(default)]
    preview_hard_secs: u32,
    #[serde(default)]
    preview_good_secs: u32,
}
#[derive(Serialize, Deserialize, Debug, PartialEq, Eq, Default, Clone)]
pub struct DeckTodaySchema11 {
    #[serde(rename = "lrnToday")]
    pub(crate) lrn: TodayAmountSchema11,
    #[serde(rename = "revToday")]
    pub(crate) rev: TodayAmountSchema11,
    #[serde(rename = "newToday")]
    pub(crate) new: TodayAmountSchema11,
    #[serde(rename = "timeToday")]
    pub(crate) time: TodayAmountSchema11,
}

#[derive(Serialize_tuple, Deserialize, Debug, PartialEq, Eq, Default, Clone)]
#[serde(from = "Vec<Value>")]
pub struct TodayAmountSchema11 {
    day: i32,
    amount: i32,
}

impl From<Vec<Value>> for TodayAmountSchema11 {
    fn from(mut v: Vec<Value>) -> Self {
        let amt = v.pop().and_then(|v| v.as_i64()).unwrap_or(0);
        let day = v.pop().and_then(|v| v.as_i64()).unwrap_or(0);
        TodayAmountSchema11 {
            amount: amt as i32,
            day: day as i32,
        }
    }
}
#[derive(Serialize_tuple, Deserialize, Debug, PartialEq, Eq, Clone)]
pub struct FilteredSearchTermSchema11 {
    search: String,
    #[serde(deserialize_with = "deserialize_number_from_string")]
    limit: i32,
    order: i32,
}

impl DeckSchema11 {
    pub fn common(&self) -> &DeckCommonSchema11 {
        match self {
            DeckSchema11::Normal(d) => &d.common,
            DeckSchema11::Filtered(d) => &d.common,
        }
    }

    pub fn id(&self) -> DeckId {
        self.common().id
    }

    pub fn name(&self) -> &str {
        &self.common().name
    }
}

impl Default for DeckSchema11 {
    fn default() -> Self {
        DeckSchema11::Normal(NormalDeckSchema11::default())
    }
}

impl Default for NormalDeckSchema11 {
    fn default() -> Self {
        NormalDeckSchema11 {
            common: DeckCommonSchema11 {
                id: DeckId(0),
                mtime: TimestampSecs(0),
                name: "".to_string(),
                usn: Usn(0),
                study_collapsed: false,
                browser_collapsed: false,
                desc: "".to_string(),
                today: Default::default(),
                other: Default::default(),
                dynamic: 0,
                markdown_description: false,
            },
            conf: 1,
            extend_new: 0,
            extend_rev: 0,
            review_limit: None,
            new_limit: None,
            review_limit_today: None,
            new_limit_today: None,
        }
    }
}

// schema 11 -> latest

impl From<DeckSchema11> for Deck {
    fn from(deck: DeckSchema11) -> Self {
        match deck {
            DeckSchema11::Normal(d) => Deck {
                id: d.common.id,
                name: NativeDeckName::from_human_name(&d.common.name),
                mtime_secs: d.common.mtime,
                usn: d.common.usn,
                common: (&d.common).into(),
                kind: DeckKind::Normal(d.into()),
            },
            DeckSchema11::Filtered(d) => Deck {
                id: d.common.id,
                name: NativeDeckName::from_human_name(&d.common.name),
                mtime_secs: d.common.mtime,
                usn: d.common.usn,
                common: (&d.common).into(),
                kind: DeckKind::Filtered(d.into()),
            },
        }
    }
}

impl From<&DeckCommonSchema11> for DeckCommon {
    fn from(common: &DeckCommonSchema11) -> Self {
        let other = if common.other.is_empty() {
            vec![]
        } else {
            serde_json::to_vec(&common.other).unwrap_or_default()
        };
        // since we're combining the day values into a single value,
        // any items from an earlier day need to be reset
        let mut today = common.today.clone();
        // study will always update 'time', but custom study may only update
        // 'rev' or 'new'
        let max_day = today.time.day.max(today.new.day).max(today.rev.day);
        if today.lrn.day != max_day {
            today.lrn.amount = 0;
        }
        if today.rev.day != max_day {
            today.rev.amount = 0;
        }
        if today.new.day != max_day {
            today.new.amount = 0;
        }
        DeckCommon {
            study_collapsed: common.study_collapsed,
            browser_collapsed: common.browser_collapsed,
            last_day_studied: max_day as u32,
            new_studied: today.new.amount,
            review_studied: today.rev.amount,
            learning_studied: today.lrn.amount,
            milliseconds_studied: common.today.time.amount,
            other,
        }
    }
}

impl From<NormalDeckSchema11> for NormalDeck {
    fn from(deck: NormalDeckSchema11) -> Self {
        NormalDeck {
            config_id: deck.conf,
            extend_new: deck.extend_new.max(0) as u32,
            extend_review: deck.extend_rev.max(0) as u32,
            markdown_description: deck.common.markdown_description,
            description: deck.common.desc,
            review_limit: deck.review_limit,
            new_limit: deck.new_limit,
            review_limit_today: deck.review_limit_today,
            new_limit_today: deck.new_limit_today,
            desired_retention: None,
        }
    }
}

impl From<FilteredDeckSchema11> for FilteredDeck {
    fn from(deck: FilteredDeckSchema11) -> Self {
        FilteredDeck {
            reschedule: deck.resched,
            search_terms: deck.terms.into_iter().map(Into::into).collect(),
            delays: deck.delays.unwrap_or_default(),
            preview_delay: deck.preview_delay,
            preview_again_secs: deck.preview_again_secs,
            preview_hard_secs: deck.preview_hard_secs,
            preview_good_secs: deck.preview_good_secs,
        }
    }
}

impl From<FilteredSearchTermSchema11> for FilteredSearchTerm {
    fn from(term: FilteredSearchTermSchema11) -> Self {
        FilteredSearchTerm {
            search: term.search,
            limit: term.limit.max(0) as u32,
            order: term.order,
        }
    }
}

// latest -> schema 11

impl From<Deck> for DeckSchema11 {
    fn from(deck: Deck) -> Self {
        match deck.kind {
            DeckKind::Normal(ref norm) => DeckSchema11::Normal(NormalDeckSchema11 {
                conf: norm.config_id,
                extend_new: norm.extend_new as i32,
                extend_rev: norm.extend_review as i32,
                review_limit: norm.review_limit,
                new_limit: norm.new_limit,
                review_limit_today: norm.review_limit_today,
                new_limit_today: norm.new_limit_today,
                common: deck.into(),
            }),
            DeckKind::Filtered(ref filt) => DeckSchema11::Filtered(FilteredDeckSchema11 {
                resched: filt.reschedule,
                terms: filt.search_terms.iter().map(|v| v.clone().into()).collect(),
                separate: true,
                delays: if filt.delays.is_empty() {
                    None
                } else {
                    Some(filt.delays.clone())
                },
                preview_delay: filt.preview_delay,
                preview_again_secs: filt.preview_again_secs,
                preview_hard_secs: filt.preview_hard_secs,
                preview_good_secs: filt.preview_good_secs,
                common: deck.into(),
            }),
        }
    }
}

impl From<Deck> for DeckCommonSchema11 {
    fn from(deck: Deck) -> Self {
        DeckCommonSchema11 {
            id: deck.id,
            mtime: deck.mtime_secs,
            name: deck.human_name(),
            usn: deck.usn,
            today: (&deck).into(),
            study_collapsed: deck.common.study_collapsed,
            browser_collapsed: deck.common.browser_collapsed,
            dynamic: matches!(deck.kind, DeckKind::Filtered(_)).into(),
            markdown_description: match &deck.kind {
                DeckKind::Normal(n) => n.markdown_description,
                DeckKind::Filtered(_) => false,
            },
            desc: match deck.kind {
                DeckKind::Normal(n) => n.description,
                DeckKind::Filtered(_) => String::new(),
            },
            other: parse_other_fields(&deck.common.other, &RESERVED_DECK_KEYS),
        }
    }
}

static RESERVED_DECK_KEYS: Set<&'static str> = phf_set! {
    "usn",
    "revToday",
    "newLimit",
    "dyn",
    "reviewLimit",
    "newToday",
    "timeToday",
    "reviewLimitToday",
    "extendNew",
    "mod",
    "newLimitToday",
    "desc",
    "name",
    "lrnToday",
    "conf",
    "browserCollapsed",
    "extendRev",
    "id",
    "collapsed"
};

impl From<&Deck> for DeckTodaySchema11 {
    fn from(deck: &Deck) -> Self {
        let day = deck.common.last_day_studied as i32;
        let c = &deck.common;
        DeckTodaySchema11 {
            lrn: TodayAmountSchema11 {
                day,
                amount: c.learning_studied,
            },
            rev: TodayAmountSchema11 {
                day,
                amount: c.review_studied,
            },
            new: TodayAmountSchema11 {
                day,
                amount: c.new_studied,
            },
            time: TodayAmountSchema11 {
                day,
                amount: c.milliseconds_studied,
            },
        }
    }
}

impl From<FilteredSearchTerm> for FilteredSearchTermSchema11 {
    fn from(term: FilteredSearchTerm) -> Self {
        FilteredSearchTermSchema11 {
            search: term.search,
            limit: term.limit as i32,
            order: term.order,
        }
    }
}

#[cfg(test)]
mod tests {
    use itertools::Itertools;

    use super::*;

    #[test]
    fn all_reserved_fields_are_removed() -> Result<()> {
        let key_source = DeckSchema11::default();
        let mut deck = Deck::new_normal();
        deck.common.other = serde_json::to_vec(&key_source)?;
        let DeckSchema11::Normal(s11) = DeckSchema11::from(deck) else {
            panic!()
        };

        let empty: &[&String] = &[];
        assert_eq!(&s11.common.other.keys().collect_vec(), empty);

        Ok(())
    }
}
