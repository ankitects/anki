// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![deny(unused_must_use)]

pub mod adding;
pub mod backend;
mod backend_proto;
pub mod browser_table;
pub mod card;
pub mod cloze;
pub mod collection;
pub mod config;
pub mod dbcheck;
pub mod deckconfig;
pub mod decks;
pub mod error;
pub mod findreplace;
pub mod i18n;
pub mod latex;
pub mod links;
pub mod log;
mod markdown;
pub mod media;
pub mod notes;
pub mod notetype;
pub mod ops;
mod preferences;
pub mod prelude;
pub mod revlog;
pub mod scheduler;
pub mod search;
pub mod serde;
mod stats;
pub mod storage;
mod sync;
pub mod tags;
pub mod template;
pub mod template_filters;
pub mod text;
pub mod timestamp;
pub mod types;
pub mod undo;
pub mod version;

use std::env;

use lazy_static::lazy_static;

lazy_static! {
    pub(crate) static ref PYTHON_UNIT_TESTS: bool = env::var("ANKI_TEST_MODE").is_ok();
}
