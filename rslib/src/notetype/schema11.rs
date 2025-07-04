// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use phf::phf_set;
use phf::Set;
use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;
use serde_repr::Deserialize_repr;
use serde_repr::Serialize_repr;
use serde_tuple::Serialize_tuple;

use super::CardRequirementKind;
use super::NotetypeId;
use crate::decks::DeckId;
use crate::notetype::CardRequirement;
use crate::notetype::CardTemplate;
use crate::notetype::CardTemplateConfig;
use crate::notetype::NoteField;
use crate::notetype::NoteFieldConfig;
use crate::notetype::Notetype;
use crate::notetype::NotetypeConfig;
use crate::serde::default_on_invalid;
use crate::serde::deserialize_bool_from_anything;
use crate::serde::deserialize_number_from_string;
use crate::serde::is_default;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;

#[derive(Serialize_repr, Deserialize_repr, PartialEq, Eq, Debug, Clone)]
#[repr(u8)]
pub enum NotetypeKind {
    Standard = 0,
    Cloze = 1,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NotetypeSchema11 {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: NotetypeId,
    pub(crate) name: String,
    #[serde(rename = "type")]
    pub(crate) kind: NotetypeKind,
    #[serde(rename = "mod")]
    pub(crate) mtime: TimestampSecs,
    pub(crate) usn: Usn,
    pub(crate) sortf: u16,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) did: Option<DeckId>,
    pub(crate) tmpls: Vec<CardTemplateSchema11>,
    pub(crate) flds: Vec<NoteFieldSchema11>,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) css: String,
    #[serde(default)]
    pub(crate) latex_pre: String,
    #[serde(default)]
    pub(crate) latex_post: String,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub latexsvg: bool,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) req: CardRequirementsSchema11,
    #[serde(default, skip_serializing_if = "is_default")]
    pub(crate) original_stock_kind: i32,
    #[serde(default, skip_serializing_if = "is_default")]
    pub(crate) original_id: Option<i64>,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

#[derive(Serialize, Deserialize, Debug, Default, Clone)]
pub(crate) struct CardRequirementsSchema11(pub(crate) Vec<CardRequirementSchema11>);

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

impl NotetypeSchema11 {
    pub fn latex_uses_svg(&self) -> bool {
        self.latexsvg
    }
}

impl From<NotetypeSchema11> for Notetype {
    fn from(nt: NotetypeSchema11) -> Self {
        Notetype {
            id: nt.id,
            name: nt.name,
            mtime_secs: nt.mtime,
            usn: nt.usn,
            config: NotetypeConfig {
                kind: nt.kind as i32,
                sort_field_idx: nt.sortf as u32,
                css: nt.css,
                target_deck_id_unused: nt.did.unwrap_or(DeckId(0)).0,
                latex_pre: nt.latex_pre,
                latex_post: nt.latex_post,
                latex_svg: nt.latexsvg,
                reqs: nt.req.0.into_iter().map(Into::into).collect(),
                original_stock_kind: nt.original_stock_kind,
                original_id: nt.original_id,
                other: other_to_bytes(&nt.other),
            },
            fields: nt.flds.into_iter().map(Into::into).collect(),
            templates: nt.tmpls.into_iter().map(Into::into).collect(),
        }
    }
}

fn other_to_bytes(other: &HashMap<String, Value>) -> Vec<u8> {
    if other.is_empty() {
        vec![]
    } else {
        serde_json::to_vec(other).unwrap_or_else(|e| {
            // theoretically should never happen
            println!("serialization failed for {other:?}: {e}");
            vec![]
        })
    }
}

pub(crate) fn parse_other_fields(
    bytes: &[u8],
    reserved: &Set<&'static str>,
) -> HashMap<String, Value> {
    if bytes.is_empty() {
        Default::default()
    } else {
        let mut map: HashMap<String, Value> = serde_json::from_slice(bytes).unwrap_or_else(|e| {
            println!("deserialization failed for other: {e}");
            Default::default()
        });
        map.retain(|k, _v| !reserved.contains(k));
        map
    }
}

impl From<Notetype> for NotetypeSchema11 {
    fn from(p: Notetype) -> Self {
        let c = p.config;
        NotetypeSchema11 {
            id: p.id,
            name: p.name,
            kind: if c.kind == 1 {
                NotetypeKind::Cloze
            } else {
                NotetypeKind::Standard
            },
            mtime: p.mtime_secs,
            usn: p.usn,
            sortf: c.sort_field_idx as u16,
            did: if c.target_deck_id_unused == 0 {
                None
            } else {
                Some(DeckId(c.target_deck_id_unused))
            },
            tmpls: p.templates.into_iter().map(Into::into).collect(),
            flds: p.fields.into_iter().map(Into::into).collect(),
            css: c.css,
            latex_pre: c.latex_pre,
            latex_post: c.latex_post,
            latexsvg: c.latex_svg,
            req: CardRequirementsSchema11(c.reqs.into_iter().map(Into::into).collect()),
            original_stock_kind: c.original_stock_kind,
            original_id: c.original_id,
            other: parse_other_fields(&c.other, &RESERVED_NOTETYPE_KEYS),
        }
    }
}

static RESERVED_NOTETYPE_KEYS: Set<&'static str> = phf_set! {
    "latexPost",
    "flds",
    "css",
    "originalStockKind",
    "originalId",
    "id",
    "usn",
    "mod",
    "req",
    "latexPre",
    "name",
    "did",
    "tmpls",
    "type",
    "sortf",
    "latexsvg"
};

impl From<CardRequirementSchema11> for CardRequirement {
    fn from(r: CardRequirementSchema11) -> Self {
        CardRequirement {
            card_ord: r.card_ord as u32,
            kind: match r.kind {
                FieldRequirementKindSchema11::Any => CardRequirementKind::Any,
                FieldRequirementKindSchema11::All => CardRequirementKind::All,
                FieldRequirementKindSchema11::None => CardRequirementKind::None,
            } as i32,
            field_ords: r.field_ords.into_iter().map(|n| n as u32).collect(),
        }
    }
}

impl From<CardRequirement> for CardRequirementSchema11 {
    fn from(p: CardRequirement) -> Self {
        CardRequirementSchema11 {
            card_ord: p.card_ord as u16,
            kind: match p.kind() {
                CardRequirementKind::Any => FieldRequirementKindSchema11::Any,
                CardRequirementKind::All => FieldRequirementKindSchema11::All,
                CardRequirementKind::None => FieldRequirementKindSchema11::None,
            },
            field_ords: p.field_ords.into_iter().map(|n| n as u16).collect(),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct NoteFieldSchema11 {
    pub(crate) name: String,
    pub(crate) ord: Option<u16>,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    pub(crate) sticky: bool,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    pub(crate) rtl: bool,
    pub(crate) font: String,
    pub(crate) size: u16,

    // These were not in schema 11, but need to be listed here so that the setting is not lost
    // on downgrade/upgrade.
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) description: String,

    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) plain_text: bool,

    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) collapsed: bool,

    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) exclude_from_search: bool,

    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) id: Option<i64>,

    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) tag: Option<u32>,

    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) prevent_deletion: bool,

    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

impl Default for NoteFieldSchema11 {
    fn default() -> Self {
        Self {
            name: String::new(),
            ord: None,
            sticky: false,
            rtl: false,
            plain_text: false,
            font: "Arial".to_string(),
            size: 20,
            description: String::new(),
            collapsed: false,
            exclude_from_search: false,
            id: None,
            tag: None,
            prevent_deletion: false,
            other: Default::default(),
        }
    }
}

impl From<NoteFieldSchema11> for NoteField {
    fn from(f: NoteFieldSchema11) -> Self {
        NoteField {
            ord: f.ord.map(|o| o as u32),
            name: f.name,
            config: NoteFieldConfig {
                sticky: f.sticky,
                rtl: f.rtl,
                plain_text: f.plain_text,
                font_name: f.font,
                font_size: f.size as u32,
                description: f.description,
                collapsed: f.collapsed,
                exclude_from_search: f.exclude_from_search,
                id: f.id,
                tag: f.tag,
                prevent_deletion: f.prevent_deletion,
                other: other_to_bytes(&f.other),
            },
        }
    }
}

impl From<NoteField> for NoteFieldSchema11 {
    fn from(p: NoteField) -> Self {
        let conf = p.config;
        NoteFieldSchema11 {
            name: p.name,
            ord: p.ord.map(|o| o as u16),
            sticky: conf.sticky,
            rtl: conf.rtl,
            plain_text: conf.plain_text,
            font: conf.font_name,
            size: conf.font_size as u16,
            description: conf.description,
            collapsed: conf.collapsed,
            exclude_from_search: conf.exclude_from_search,
            id: conf.id,
            tag: conf.tag,
            prevent_deletion: conf.prevent_deletion,
            other: parse_other_fields(&conf.other, &RESERVED_FIELD_KEYS),
        }
    }
}

static RESERVED_FIELD_KEYS: Set<&'static str> = phf_set! {
    "name",
    "ord",
    "sticky",
    "rtl",
    "plainText",
    "font",
    "size",
    "collapsed",
    "description",
    "excludeFromSearch",
    "id",
    "tag",
    "preventDeletion",
};

#[derive(Serialize, Deserialize, Debug, Default, Clone)]
pub struct CardTemplateSchema11 {
    pub(crate) name: String,
    pub(crate) ord: Option<u16>,
    pub(crate) qfmt: String,
    #[serde(default)]
    pub(crate) afmt: String,
    #[serde(default)]
    pub(crate) bqfmt: String,
    #[serde(default)]
    pub(crate) bafmt: String,
    #[serde(deserialize_with = "default_on_invalid", default)]
    pub(crate) did: Option<DeckId>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bfont: String,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bsize: u8,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) id: Option<i64>,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

impl From<CardTemplateSchema11> for CardTemplate {
    fn from(t: CardTemplateSchema11) -> Self {
        CardTemplate {
            ord: t.ord.map(|t| t as u32),
            name: t.name,
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            config: CardTemplateConfig {
                q_format: t.qfmt,
                a_format: t.afmt,
                q_format_browser: t.bqfmt,
                a_format_browser: t.bafmt,
                target_deck_id: t.did.unwrap_or(DeckId(0)).0,
                browser_font_name: t.bfont,
                browser_font_size: t.bsize as u32,
                id: t.id,
                other: other_to_bytes(&t.other),
            },
        }
    }
}

impl From<CardTemplate> for CardTemplateSchema11 {
    fn from(p: CardTemplate) -> Self {
        let conf = p.config;
        CardTemplateSchema11 {
            name: p.name,
            ord: p.ord.map(|o| o as u16),
            qfmt: conf.q_format,
            afmt: conf.a_format,
            bqfmt: conf.q_format_browser,
            bafmt: conf.a_format_browser,
            did: if conf.target_deck_id > 0 {
                Some(DeckId(conf.target_deck_id))
            } else {
                None
            },
            bfont: conf.browser_font_name,
            bsize: conf.browser_font_size as u8,
            id: conf.id,
            other: parse_other_fields(&conf.other, &RESERVED_TEMPLATE_KEYS),
        }
    }
}

static RESERVED_TEMPLATE_KEYS: Set<&'static str> = phf_set! {
    "name",
    "ord",
    "did",
    "afmt",
    "bafmt",
    "qfmt",
    "bqfmt",
    "bfont",
    "bsize",
    "id",
};

#[cfg(test)]
mod tests {
    use itertools::Itertools;

    use super::*;
    use crate::notetype::stock::basic;
    use crate::prelude::*;

    #[test]
    fn all_reserved_fields_are_removed() -> Result<()> {
        let mut nt = basic(&I18n::template_only());

        let key_source = NotetypeSchema11::from(nt.clone());
        nt.config.other = serde_json::to_vec(&key_source)?;
        nt.fields[0].config.other = serde_json::to_vec(&key_source.flds[0])?;
        nt.templates[0].config.other = serde_json::to_vec(&key_source.tmpls[0])?;
        let s11 = NotetypeSchema11::from(nt);

        let empty: &[&String] = &[];
        assert_eq!(&s11.other.keys().collect_vec(), empty);
        assert_eq!(&s11.flds[0].other.keys().collect_vec(), empty);
        assert_eq!(&s11.tmpls[0].other.keys().collect_vec(), empty);

        Ok(())
    }
}
