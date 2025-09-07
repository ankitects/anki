// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![deny(unused_must_use)]

pub mod adding;
pub(crate) mod ankidroid;
pub mod ankihub;
pub mod backend;
pub mod browser_table;
pub mod card;
pub mod card_rendering;
pub mod cloze;
pub mod collection;
pub mod config;
pub mod dbcheck;
pub mod deckconfig;
pub mod decks;
pub mod editor;
pub mod error;
pub mod findreplace;
pub mod i18n;
pub mod image_occlusion;
pub mod import_export;
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
mod progress;
pub mod revlog;
pub mod scheduler;
pub mod search;
pub mod serde;
pub mod services;
mod stats;
pub mod storage;
pub mod sync;
pub mod tags;
pub mod template;
pub mod template_filters;
pub(crate) mod tests;
pub mod text;
pub mod timestamp;
mod typeanswer;
pub mod types;
pub mod undo;
pub mod version;

use std::env;
use std::sync::LazyLock;

pub(crate) static PYTHON_UNIT_TESTS: LazyLock<bool> =
    LazyLock::new(|| env::var("ANKI_TEST_MODE").is_ok());
