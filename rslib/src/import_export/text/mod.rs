// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#![allow(dead_code, unused_imports, unused_variables)]

pub mod csv;

use crate::prelude::*;

#[derive(Debug)]
pub struct ForeignData {
    default_deck: DeckId,
    default_notetype: NotetypeId,
    notes: Vec<ForeignNote>,
}

#[derive(Debug, PartialEq, Default)]
pub struct ForeignNote {
    fields: Vec<String>,
    tags: Vec<String>,
}
