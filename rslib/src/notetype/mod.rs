// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod field;
mod template;

pub use field::NoteFieldSchema11;
pub use template::CardTemplateSchema11;

use crate::{
    backend_proto::{
        CardRequirement as CardRequirementProto, NoteType as NoteTypeProto, NoteTypeConfig,
    },
    decks::DeckID,
    define_newtype,
    serde::{default_on_invalid, deserialize_number_from_string},
    text::ensure_string_in_nfc,
    timestamp::TimestampSecs,
    types::Usn,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use serde_repr::{Deserialize_repr, Serialize_repr};
use serde_tuple::Serialize_tuple;
use std::collections::{HashMap, HashSet};
use unicase::UniCase;

define_newtype!(NoteTypeID, i64);

pub(crate) const DEFAULT_CSS: &str = "\
.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
";

pub(crate) const DEFAULT_LATEX_HEADER: &str = r#"\documentclass[12pt]{article}
\special{papersize=3in,5in}
\usepackage[utf8]{inputenc}
\usepackage{amssymb,amsmath}
\pagestyle{empty}
\setlength{\parindent}{0in}
\begin{document}
"#;

pub(crate) const DEFAULT_LATEX_FOOTER: &str = r#"\end{document}"#;

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
    #[serde(rename = "sortf")]
    pub(crate) sort_field_idx: u16,
    #[serde(rename = "did", deserialize_with = "default_on_invalid")]
    pub(crate) deck_id_for_adding: Option<DeckID>,
    #[serde(rename = "tmpls")]
    pub(crate) templates: Vec<CardTemplateSchema11>,
    #[serde(rename = "flds")]
    pub(crate) fields: Vec<NoteFieldSchema11>,
    #[serde(deserialize_with = "default_on_invalid")]
    pub(crate) css: String,
    #[serde(default)]
    pub(crate) latex_pre: String,
    #[serde(default)]
    pub(crate) latex_post: String,
    #[serde(rename = "latexsvg", default)]
    pub latex_svg: bool,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) req: CardRequirements,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub(crate) struct CardRequirements(pub(crate) Vec<CardRequirement>);

impl Default for CardRequirements {
    fn default() -> Self {
        CardRequirements(vec![])
    }
}

#[derive(Serialize_tuple, Deserialize, Debug, Clone)]
pub(crate) struct CardRequirement {
    pub(crate) card_ord: u16,
    pub(crate) kind: FieldRequirementKind,
    pub(crate) field_ords: Vec<u16>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "lowercase")]
pub enum FieldRequirementKind {
    Any,
    All,
    None,
}

impl Default for NoteTypeSchema11 {
    fn default() -> Self {
        Self {
            id: NoteTypeID(0),
            name: String::new(),
            kind: NoteTypeKind::Standard,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            sort_field_idx: 0,
            deck_id_for_adding: None,
            fields: vec![],
            templates: vec![],
            css: DEFAULT_CSS.to_owned(),
            latex_pre: DEFAULT_LATEX_HEADER.to_owned(),
            latex_post: DEFAULT_LATEX_FOOTER.to_owned(),
            req: Default::default(),
            latex_svg: false,
            other: Default::default(),
        }
    }
}

impl NoteTypeSchema11 {
    pub fn latex_uses_svg(&self) -> bool {
        self.latex_svg
    }
}

impl From<NoteTypeSchema11> for NoteTypeProto {
    fn from(nt: NoteTypeSchema11) -> Self {
        NoteTypeProto {
            id: nt.id.0,
            name: nt.name,
            mtime_secs: nt.mtime.0 as u32,
            usn: nt.usn.0,
            config: Some(NoteTypeConfig {
                kind: nt.kind as i32,
                sort_field_idx: nt.sort_field_idx as u32,
                css: nt.css,
                target_deck_id: nt.deck_id_for_adding.unwrap_or(DeckID(0)).0,
                latex_pre: nt.latex_pre,
                latex_post: nt.latex_post,
                latex_svg: nt.latex_svg,
                reqs: nt.req.0.into_iter().map(Into::into).collect(),
                other: serde_json::to_vec(&nt.other).unwrap(),
            }),
            fields: nt.fields.into_iter().map(Into::into).collect(),
            templates: nt.templates.into_iter().map(Into::into).collect(),
        }
    }
}

impl From<NoteTypeProto> for NoteTypeSchema11 {
    fn from(p: NoteTypeProto) -> Self {
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
            sort_field_idx: c.sort_field_idx as u16,
            deck_id_for_adding: if c.target_deck_id == 0 {
                None
            } else {
                Some(DeckID(c.target_deck_id))
            },
            templates: p.templates.into_iter().map(Into::into).collect(),
            fields: p.fields.into_iter().map(Into::into).collect(),
            css: c.css,
            latex_pre: c.latex_pre,
            latex_post: c.latex_post,
            latex_svg: c.latex_svg,
            req: CardRequirements(c.reqs.into_iter().map(Into::into).collect()),
            other: serde_json::from_slice(&c.other).unwrap_or_default(),
        }
    }
}

impl From<CardRequirement> for CardRequirementProto {
    fn from(r: CardRequirement) -> Self {
        CardRequirementProto {
            card_ord: r.card_ord as u32,
            kind: r.kind as u32,
            field_ords: r.field_ords.into_iter().map(|n| n as u32).collect(),
        }
    }
}

impl From<CardRequirementProto> for CardRequirement {
    fn from(p: CardRequirementProto) -> Self {
        CardRequirement {
            card_ord: p.card_ord as u16,
            kind: match p.kind {
                0 => FieldRequirementKind::Any,
                1 => FieldRequirementKind::All,
                _ => FieldRequirementKind::None,
            },
            field_ords: p.field_ords.into_iter().map(|n| n as u16).collect(),
        }
    }
}

impl NoteTypeProto {
    pub(crate) fn ensure_names_unique(&mut self) {
        let mut names = HashSet::new();
        for t in &mut self.templates {
            loop {
                let name = UniCase::new(t.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                t.name.push('_');
            }
        }
        names.clear();
        for t in &mut self.fields {
            loop {
                let name = UniCase::new(t.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                t.name.push('_');
            }
        }
    }

    pub(crate) fn normalize_names(&mut self) {
        ensure_string_in_nfc(&mut self.name);
        for f in &mut self.fields {
            ensure_string_in_nfc(&mut f.name);
        }
        for t in &mut self.templates {
            ensure_string_in_nfc(&mut t.name);
        }
    }
}
