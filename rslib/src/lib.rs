// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![deny(unused_must_use)]

mod backend_proto;

pub fn version() -> &'static str {
    include_str!("../../meta/version").trim()
}

pub mod backend;
pub mod card;
pub mod cloze;
pub mod collection;
pub mod config;
pub mod dbcheck;
pub mod deckconf;
pub mod decks;
pub mod err;
pub mod findreplace;
pub mod i18n;
pub mod latex;
pub mod log;
pub mod media;
pub mod notes;
pub mod notetype;
mod preferences;
pub mod sched;
pub mod search;
pub mod serde;
pub mod storage;
pub mod tags;
pub mod template;
pub mod template_filters;
pub mod text;
pub mod timestamp;
pub mod types;
pub mod undo;
