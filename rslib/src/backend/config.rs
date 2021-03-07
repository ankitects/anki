// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, config::BoolKey};
use pb::config::bool::Key;

impl From<Key> for BoolKey {
    fn from(k: Key) -> Self {
        match k {
            Key::BrowserSortBackwards => BoolKey::BrowserSortBackwards,
            Key::PreviewBothSides => BoolKey::PreviewBothSides,
            Key::CollapseTags => BoolKey::CollapseTags,
            Key::CollapseNotetypes => BoolKey::CollapseNotetypes,
            Key::CollapseDecks => BoolKey::CollapseDecks,
            Key::CollapseSavedSearches => BoolKey::CollapseSavedSearches,
            Key::CollapseToday => BoolKey::CollapseToday,
            Key::CollapseCardState => BoolKey::CollapseCardState,
            Key::CollapseFlags => BoolKey::CollapseFlags,
            Key::Sched2021 => BoolKey::Sched2021,
        }
    }
}
