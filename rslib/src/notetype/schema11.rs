// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use crate::{
    decks::DeckID,
    notetype::{
        CardRequirement, CardTemplate, CardTemplateConfig, NoteField, NoteFieldConfig, NoteType,
        NoteTypeConfig,
    },
    serde::{default_on_invalid, deserialize_bool_from_anything, deserialize_number_from_string},
    timestamp::TimestampSecs,
    types::Usn,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use serde_repr::{Deserialize_repr, Serialize_repr};
use serde_tuple::Serialize_tuple;
use std::collections::HashMap;

use super::NoteTypeID;

#[derive(Serialize_repr, Deserialize_repr, PartialEq, Debug, Clone)]
#[repr(u8)]
pub enum NoteTypeKind {
    Standard = 0,
    Cloze = 1,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NoteTypeSchema11 {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: NoteTypeID,
    pub(crate) name: String,
    #[serde(rename = "type")]
    pub(crate) kind: NoteTypeKind,
    #[serde(rename = "mod")]
    pub(crate) mtime: TimestampSecs,
    pub(crate) usn: Usn,
    pub(crate) sortf: u16,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) did: Option<DeckID>,
    pub(crate) tmpls: Vec<CardTemplateSchema11>,
    pub(crate) flds: Vec<NoteFieldSchema11>,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) css: String,
    #[serde(default)]
    pub(crate) latex_pre: String,
    #[serde(default)]
    pub(crate) latex_post: String,
    #[serde(default)]
    pub latexsvg: bool,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) req: CardRequirementsSchema11,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub(crate) struct CardRequirementsSchema11(pub(crate) Vec<CardRequirementSchema11>);

impl Default for CardRequirementsSchema11 {
    fn default() -> Self {
        CardRequirementsSchema11(vec![])
    }
}

#[derive(Serialize_tuple, Deserialize, Debug, Clone)]
pub(crate) struct CardRequirementSchema11 {
    pub(crate) card_ord: u16,
    pub(crate) kind: FieldRequirementKindSchema11,
    pub(crate) field_ords: Vec<u16>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "lowercase")]
pub enum FieldRequirementKindSchema11 {
    Any,
    All,
    None,
}

impl NoteTypeSchema11 {
    pub fn latex_uses_svg(&self) -> bool {
        self.latexsvg
    }
}

impl From<NoteTypeSchema11> for NoteType {
    fn from(nt: NoteTypeSchema11) -> Self {
        NoteType {
            id: nt.id.0,
            name: nt.name,
            mtime_secs: nt.mtime.0 as u32,
            usn: nt.usn.0,
            config: Some(NoteTypeConfig {
                kind: nt.kind as i32,
                sort_field_idx: nt.sortf as u32,
                css: nt.css,
                target_deck_id: nt.did.unwrap_or(DeckID(0)).0,
                latex_pre: nt.latex_pre,
                latex_post: nt.latex_post,
                latex_svg: nt.latexsvg,
                reqs: nt.req.0.into_iter().map(Into::into).collect(),
                other: serde_json::to_vec(&nt.other).unwrap(),
            }),
            fields: nt.flds.into_iter().map(Into::into).collect(),
            templates: nt.tmpls.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<NoteType> for NoteTypeSchema11 {
    fn from(p: NoteType) -> Self {
        let c = p.config.unwrap();
        NoteTypeSchema11 {
            id: NoteTypeID(p.id),
            name: p.name,
            kind: if c.kind == 1 {
                NoteTypeKind::Cloze
            } else {
                NoteTypeKind::Standard
            },
            mtime: TimestampSecs(p.mtime_secs as i64),
            usn: Usn(p.usn),
            sortf: c.sort_field_idx as u16,
            did: if c.target_deck_id == 0 {
                None
            } else {
                Some(DeckID(c.target_deck_id))
            },
            tmpls: p.templates.into_iter().map(Into::into).collect(),
            flds: p.fields.into_iter().map(Into::into).collect(),
            css: c.css,
            latex_pre: c.latex_pre,
            latex_post: c.latex_post,
            latexsvg: c.latex_svg,
            req: CardRequirementsSchema11(c.reqs.into_iter().map(Into::into).collect()),
            other: serde_json::from_slice(&c.other).unwrap_or_default(),
        }
    }
}

impl From<CardRequirementSchema11> for CardRequirement {
    fn from(r: CardRequirementSchema11) -> Self {
        CardRequirement {
            card_ord: r.card_ord as u32,
            kind: r.kind as u32,
            field_ords: r.field_ords.into_iter().map(|n| n as u32).collect(),
        }
    }
}

impl From<CardRequirement> for CardRequirementSchema11 {
    fn from(p: CardRequirement) -> Self {
        CardRequirementSchema11 {
            card_ord: p.card_ord as u16,
            kind: match p.kind {
                0 => FieldRequirementKindSchema11::Any,
                1 => FieldRequirementKindSchema11::All,
                _ => FieldRequirementKindSchema11::None,
            },
            field_ords: p.field_ords.into_iter().map(|n| n as u16).collect(),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct NoteFieldSchema11 {
    pub(crate) name: String,
    pub(crate) ord: u16,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    pub(crate) sticky: bool,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    pub(crate) rtl: bool,
    pub(crate) font: String,
    pub(crate) size: u16,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

impl Default for NoteFieldSchema11 {
    fn default() -> Self {
        Self {
            name: String::new(),
            ord: 0,
            sticky: false,
            rtl: false,
            font: "Arial".to_string(),
            size: 20,
            other: Default::default(),
        }
    }
}

impl From<NoteFieldSchema11> for NoteField {
    fn from(f: NoteFieldSchema11) -> Self {
        NoteField {
            ord: f.ord as u32,
            name: f.name,
            config: Some(NoteFieldConfig {
                sticky: f.sticky,
                rtl: f.rtl,
                font_name: f.font,
                font_size: f.size as u32,
                other: serde_json::to_vec(&f.other).unwrap(),
            }),
        }
    }
}

impl From<NoteField> for NoteFieldSchema11 {
    fn from(p: NoteField) -> Self {
        let conf = p.config.unwrap();
        NoteFieldSchema11 {
            name: p.name,
            ord: p.ord as u16,
            sticky: conf.sticky,
            rtl: conf.rtl,
            font: conf.font_name,
            size: conf.font_size as u16,
            other: serde_json::from_slice(&conf.other).unwrap(),
        }
    }
}

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
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) did: Option<DeckID>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bfont: String,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bsize: u8,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

impl From<CardTemplateSchema11> for CardTemplate {
    fn from(t: CardTemplateSchema11) -> Self {
        CardTemplate {
            ord: t.ord as u32,
            name: t.name,
            mtime_secs: 0,
            usn: 0,
            config: Some(CardTemplateConfig {
                q_format: t.qfmt,
                a_format: t.afmt,
                q_format_browser: t.bqfmt,
                a_format_browser: t.bafmt,
                target_deck_id: t.did.unwrap_or(DeckID(0)).0,
                browser_font_name: t.bfont,
                browser_font_size: t.bsize as u32,
                other: serde_json::to_vec(&t.other).unwrap(),
            }),
        }
    }
}

impl From<CardTemplate> for CardTemplateSchema11 {
    fn from(p: CardTemplate) -> Self {
        let conf = p.config.unwrap();
        CardTemplateSchema11 {
            name: p.name,
            ord: p.ord as u16,
            qfmt: conf.q_format,
            afmt: conf.a_format,
            bqfmt: conf.q_format_browser,
            bafmt: conf.a_format_browser,
            did: if conf.target_deck_id > 0 {
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
