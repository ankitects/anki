// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod field;
mod template;

pub use field::NoteField;
pub use template::CardTemplate;

use crate::{
    decks::DeckID,
    define_newtype,
    serde::{default_on_invalid, deserialize_number_from_string},
    timestamp::TimestampSecs,
    types::Usn,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use serde_repr::{Deserialize_repr, Serialize_repr};
use serde_tuple::Serialize_tuple;
use std::collections::HashMap;

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
pub struct NoteType {
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
    pub(crate) templates: Vec<CardTemplate>,
    #[serde(rename = "flds")]
    pub(crate) fields: Vec<NoteField>,
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
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) tags: Vec<String>,
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

impl Default for NoteType {
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
            tags: vec![],
            latex_svg: false,
            other: Default::default(),
        }
    }
}

impl NoteType {
    pub fn latex_uses_svg(&self) -> bool {
        self.latex_svg
    }
}
