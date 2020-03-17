// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::types::ObjID;
use serde_derive::Deserialize;

#[derive(Deserialize)]
pub struct Config {
    #[serde(rename = "curDeck")]
    pub(crate) current_deck_id: ObjID,
}
