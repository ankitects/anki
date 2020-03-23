// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::types::ObjID;
use serde_aux::field_attributes::deserialize_number_from_string;
use serde_derive::Deserialize;

#[derive(Deserialize)]
pub struct Deck {
    #[serde(deserialize_with = "deserialize_number_from_string")]
    pub(crate) id: ObjID,
    pub(crate) name: String,
}

pub(crate) fn child_ids<'a>(decks: &'a [Deck], name: &str) -> impl Iterator<Item = ObjID> + 'a {
    let prefix = format!("{}::", name.to_ascii_lowercase());
    decks
        .iter()
        .filter(move |d| d.name.to_ascii_lowercase().starts_with(&prefix))
        .map(|d| d.id)
}

pub(crate) fn get_deck(decks: &[Deck], id: ObjID) -> Option<&Deck> {
    for d in decks {
        if d.id == id {
            return Some(d);
        }
    }

    None
}
