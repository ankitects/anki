// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::define_newtype;
use serde_aux::field_attributes::deserialize_number_from_string;
use serde_derive::Deserialize;

define_newtype!(NoteTypeID, i64);

#[derive(Deserialize, Debug)]
pub(crate) struct NoteType {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub id: NoteTypeID,
    pub name: String,
    #[serde(rename = "sortf")]
    pub sort_field_idx: u16,
    #[serde(rename = "latexsvg", default)]
    pub latex_svg: bool,
    #[serde(rename = "tmpls")]
    pub templates: Vec<CardTemplate>,
    #[serde(rename = "flds")]
    pub fields: Vec<NoteField>,
}

#[derive(Deserialize, Debug)]
pub(crate) struct CardTemplate {
    pub name: String,
    pub ord: u16,
}

#[derive(Deserialize, Debug)]
pub(crate) struct NoteField {
    pub name: String,
    pub ord: u16,
}

impl NoteType {
    pub fn latex_uses_svg(&self) -> bool {
        self.latex_svg
    }
}
