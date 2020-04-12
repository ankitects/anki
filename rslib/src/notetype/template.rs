// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto::{CardTemplate as CardTemplateProto, CardTemplateConfig},
    decks::DeckID,
    serde::default_on_invalid,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Serialize, Deserialize, Debug, Default, Clone)]
pub struct CardTemplateSchema11 {
    pub(crate) name: String,
    pub(crate) ord: u16,
    pub(crate) qfmt: String,
    #[serde(default)]
    pub(crate) afmt: String,
    #[serde(default)]
    pub(crate) bqfmt: String,
    #[serde(default)]
    pub(crate) bafmt: String,
    #[serde(rename = "did", deserialize_with = "default_on_invalid")]
    pub(crate) override_did: Option<DeckID>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bfont: String,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bsize: u8,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

impl From<CardTemplateSchema11> for CardTemplateProto {
    fn from(t: CardTemplateSchema11) -> Self {
        CardTemplateProto {
            ord: t.ord as u32,
            name: t.name,
            mtime_secs: 0,
            usn: 0,
            config: Some(CardTemplateConfig {
                q_format: t.qfmt,
                a_format: t.afmt,
                q_format_browser: t.bqfmt,
                a_format_browser: t.bafmt,
                target_deck_id: t.override_did.unwrap_or(DeckID(0)).0,
                browser_font_name: t.bfont,
                browser_font_size: t.bsize as u32,
                other: serde_json::to_vec(&t.other).unwrap(),
            }),
        }
    }
}

impl From<CardTemplateProto> for CardTemplateSchema11 {
    fn from(p: CardTemplateProto) -> Self {
        let conf = p.config.unwrap();
        CardTemplateSchema11 {
            name: p.name,
            ord: p.ord as u16,
            qfmt: conf.q_format,
            afmt: conf.a_format,
            bqfmt: conf.q_format_browser,
            bafmt: conf.a_format_browser,
            override_did: if conf.target_deck_id > 0 {
                Some(DeckID(conf.target_deck_id))
            } else {
                None
            },
            bfont: conf.browser_font_name,
            bsize: conf.browser_font_size as u8,
            other: serde_json::from_slice(&conf.other).unwrap(),
        }
    }
}
