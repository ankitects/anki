// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod card;
mod config;
mod deck;
mod deckconf;
mod notetype;
mod sqlite;
mod tag;
mod upgrades;

pub(crate) use sqlite::SqliteStorage;
