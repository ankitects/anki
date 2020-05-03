// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pb;
pub use crate::backend_proto::{
    deck_kind::Kind as DeckKind, Deck as DeckProto, DeckCommon, DeckKind as DeckKindProto,
    FilteredDeck, FilteredSearchOrder, FilteredSearchTerm, NormalDeck,
};
use crate::{
    card::CardID,
    collection::Collection,
    define_newtype,
    err::{AnkiError, Result},
    text::normalize_to_nfc,
    timestamp::TimestampSecs,
    types::Usn,
};
mod counts;
mod schema11;
mod tree;
pub(crate) use counts::DueCounts;
pub use schema11::DeckSchema11;
use std::{borrow::Cow, sync::Arc};

define_newtype!(DeckID, i64);

#[derive(Debug)]
pub struct Deck {
    pub id: DeckID,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub common: DeckCommon,
    pub kind: DeckKind,
}

impl Deck {
    pub fn new_normal() -> Deck {
        let mut norm = NormalDeck::default();
        norm.config_id = 1;

        Deck {
            id: DeckID(0),
            name: "".into(),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            common: DeckCommon::default(),
            kind: DeckKind::Normal(norm),
        }
    }

    pub fn new_filtered() -> Deck {
        let mut filt = FilteredDeck::default();
        filt.search_terms.push(FilteredSearchTerm {
            search: "".into(),
            limit: 100,
            order: 0,
        });
        filt.preview_delay = 10;
        filt.reschedule = true;
        Deck {
            id: DeckID(0),
            name: "".into(),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            common: DeckCommon::default(),
            kind: DeckKind::Filtered(filt),
        }
    }
}

impl Deck {
    pub(crate) fn is_filtered(&self) -> bool {
        matches!(self.kind, DeckKind::Filtered(_))
    }

    pub(crate) fn prepare_for_update(&mut self) {
        // fixme - we currently only do this when converting from human; should be done in pub methods instead

        // if self.name.contains(invalid_char_for_deck_component) {
        //     self.name = self.name.replace(invalid_char_for_deck_component, "");
        // }
        // ensure_string_in_nfc(&mut self.name);
    }

    // fixme: unify with prepare for update
    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime_secs = TimestampSecs::now();
        self.usn = usn;
    }

    /// Return the studied counts if studied today.
    /// May be negative if user has extended limits.
    pub(crate) fn new_rev_counts(&self, today: u32) -> (i32, i32) {
        if self.common.last_day_studied == today {
            (self.common.new_studied, self.common.review_studied)
        } else {
            (0, 0)
        }
    }
}

// fixme: need to bump usn on upgrade if we rename
fn invalid_char_for_deck_component(c: char) -> bool {
    c.is_ascii_control() || c == '"'
}

fn normalized_deck_name_component(comp: &str) -> Cow<str> {
    let mut out = normalize_to_nfc(comp);
    if out.contains(invalid_char_for_deck_component) {
        out = out.replace(invalid_char_for_deck_component, "").into();
    }
    let trimmed = out.trim();
    if trimmed.is_empty() {
        "blank".into()
    } else if trimmed.len() != out.len() {
        // fixme: trimming leading/trailing spaces may break old clients if we don't bump mod
        trimmed.to_string().into()
    } else {
        out
    }
}

pub(crate) fn human_deck_name_to_native(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    for comp in name.split("::") {
        out.push_str(&normalized_deck_name_component(comp));
        out.push('\x1f');
    }
    out.trim_end_matches('\x1f').into()
}

impl Collection {
    // fixme: this cache may belong in CardGenContext?
    pub(crate) fn get_deck(&mut self, did: DeckID) -> Result<Option<Arc<Deck>>> {
        if let Some(deck) = self.state.deck_cache.get(&did) {
            return Ok(Some(deck.clone()));
        }
        if let Some(deck) = self.storage.get_deck(did)? {
            let deck = Arc::new(deck);
            self.state.deck_cache.insert(did, deck.clone());
            Ok(Some(deck))
        } else {
            Ok(None)
        }
    }
}

impl From<Deck> for DeckProto {
    fn from(d: Deck) -> Self {
        DeckProto {
            id: d.id.0,
            name: d.name,
            mtime_secs: d.mtime_secs.0 as u32,
            usn: d.usn.0,
            common: Some(d.common),
            kind: Some(d.kind.into()),
        }
    }
}

impl From<DeckKind> for pb::deck::Kind {
    fn from(k: DeckKind) -> Self {
        match k {
            DeckKind::Normal(n) => pb::deck::Kind::Normal(n),
            DeckKind::Filtered(f) => pb::deck::Kind::Filtered(f),
        }
    }
}

fn immediate_parent_name(machine_name: &str) -> Option<&str> {
    machine_name.rsplitn(2, '\x1f').nth(1)
}

impl Collection {
    pub(crate) fn add_or_update_deck(&mut self, deck: &mut Deck, preserve_usn: bool) -> Result<()> {
        // fixme: vet cache clearing
        self.state.deck_cache.clear();

        self.transact(None, |col| {
            let usn = col.usn()?;

            deck.prepare_for_update();

            // fixme: bail
            assert!(!deck.name.contains("::"));

            // fixme: check deck name is not duplicate
            // handle blank deck name, etc

            if !preserve_usn {
                deck.set_modified(usn);
            }

            if deck.id.0 == 0 {
                col.match_or_create_parents(deck)?;
                col.storage.add_deck(deck)
            } else {
                if let Some(existing_deck) = col.storage.get_deck(deck.id)? {
                    if existing_deck.name != deck.name {
                        return col.update_renamed_deck(existing_deck, deck, usn);
                    }
                } else {
                    // fixme: this should only happen in the syncing case, and we should
                    // ensure there are no missing parents at the end of the sync
                }
                col.storage.update_deck(deck)
            }
        })
    }

    pub fn get_or_create_normal_deck(&mut self, human_name: &str) -> Result<Deck> {
        let native_name = human_deck_name_to_native(human_name);
        if let Some(did) = self.storage.get_deck_id(&native_name)? {
            self.storage.get_deck(did).map(|opt| opt.unwrap())
        } else {
            let mut deck = Deck::new_normal();
            deck.name = native_name;
            self.add_or_update_deck(&mut deck, false)?;
            Ok(deck)
        }
    }

    fn update_renamed_deck(&mut self, existing: Deck, updated: &mut Deck, usn: Usn) -> Result<()> {
        // new name should not conflict with a different deck
        if let Some(other_did) = self.storage.get_deck_id(&updated.name)? {
            if other_did != updated.id {
                // fixme: this could break when syncing
                return Err(AnkiError::Existing);
            }
        }

        self.match_or_create_parents(updated)?;
        self.storage.update_deck(updated)?;
        self.rename_child_decks(&existing, &updated.name, usn)
    }

    // fixme: make sure this handles foo::bar and FOO::baz
    fn rename_child_decks(&mut self, old: &Deck, new_name: &str, usn: Usn) -> Result<()> {
        let children = self.storage.child_decks(old)?;
        let old_component_count = old.name.matches('\x1f').count() + 1;

        for mut child in children {
            let child_components: Vec<_> = child.name.split('\x1f').collect();
            let child_only = &child_components[old_component_count..];
            let new_name = format!("{}\x1f{}", new_name, child_only.join("\x1f"));
            child.name = new_name;
            child.set_modified(usn);
            self.storage.update_deck(&child)?;
        }

        Ok(())
    }

    /// Add a single, normal deck with the provided name for a child deck.
    /// Caller must have done necessarily validation on name.
    fn add_parent_deck(&self, machine_name: &str) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.name = machine_name.into();
        // fixme: undo
        self.storage.add_deck(&mut deck)
    }

    /// If parent deck(s) exist, rewrite name to match their case.
    /// If they don't exist, create them.
    /// Returns an error if a DB operation fails, or if the first existing parent is a filtered deck.
    fn match_or_create_parents(&mut self, deck: &mut Deck) -> Result<()> {
        let child_split: Vec<_> = deck.name.split('\x1f').collect();
        if let Some(parent_deck) = self.first_existing_parent(&deck.name, 0)? {
            if parent_deck.is_filtered() {
                return Err(AnkiError::DeckIsFiltered);
            }
            let parent_count = parent_deck.name.matches('\x1f').count() + 1;
            let need_create = parent_count != child_split.len() - 1;
            deck.name = format!(
                "{}\x1f{}",
                parent_deck.name,
                &child_split[parent_count..].join("\x1f")
            );
            if need_create {
                self.create_missing_parents(&deck.name)?;
            }
            Ok(())
        } else if child_split.len() == 1 {
            // no parents required
            Ok(())
        } else {
            // no existing parents
            self.create_missing_parents(&deck.name)
        }
    }

    fn create_missing_parents(&self, mut machine_name: &str) -> Result<()> {
        while let Some(parent_name) = immediate_parent_name(machine_name) {
            if self.storage.get_deck_id(parent_name)?.is_none() {
                self.add_parent_deck(parent_name)?;
            }
            machine_name = parent_name;
        }
        Ok(())
    }

    fn first_existing_parent(
        &self,
        machine_name: &str,
        recursion_level: usize,
    ) -> Result<Option<Deck>> {
        if recursion_level > 10 {
            return Err(AnkiError::invalid_input("deck nesting level too deep"));
        }
        if let Some(parent_name) = immediate_parent_name(machine_name) {
            if let Some(parent_did) = self.storage.get_deck_id(parent_name)? {
                self.storage.get_deck(parent_did)
            } else {
                self.first_existing_parent(parent_name, recursion_level + 1)
            }
        } else {
            Ok(None)
        }
    }

    /// Get a deck based on its human name. If you have a machine name,
    /// use the method in storage instead.
    pub(crate) fn get_deck_id(&self, human_name: &str) -> Result<Option<DeckID>> {
        let machine_name = human_deck_name_to_native(&human_name);
        self.storage.get_deck_id(&machine_name)
    }

    pub fn remove_deck_and_child_decks(&mut self, did: DeckID) -> Result<()> {
        self.transact(None, |col| {
            let usn = col.usn()?;

            if let Some(deck) = col.storage.get_deck(did)? {
                let child_decks = col.storage.child_decks(&deck)?;

                // top level
                col.remove_single_deck(&deck, usn)?;

                // remove children
                for deck in child_decks {
                    col.remove_single_deck(&deck, usn)?;
                }
            }
            Ok(())
        })
    }

    pub(crate) fn remove_single_deck(&mut self, deck: &Deck, usn: Usn) -> Result<()> {
        // fixme: undo
        match deck.kind {
            DeckKind::Normal(_) => self.delete_all_cards_in_normal_deck(deck.id)?,
            DeckKind::Filtered(_) => self.return_all_cards_in_filtered_deck(deck.id)?,
        }
        self.storage.remove_deck(deck.id)?;
        self.storage.add_deck_grave(deck.id, usn)
    }

    fn delete_all_cards_in_normal_deck(&mut self, did: DeckID) -> Result<()> {
        let cids = self.storage.all_cards_in_single_deck(did)?;
        self.remove_cards_inner(&cids)
    }

    fn return_all_cards_in_filtered_deck(&mut self, did: DeckID) -> Result<()> {
        let cids = self.storage.all_cards_in_single_deck(did)?;
        self.return_cards_to_home_deck(&cids)
    }

    fn return_cards_to_home_deck(&mut self, cids: &[CardID]) -> Result<()> {
        let sched = self.sched_ver();
        for cid in cids {
            if let Some(mut card) = self.storage.get_card(*cid)? {
                // fixme: undo
                card.return_home(sched);
                self.storage.update_card(&card)?;
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::{human_deck_name_to_native, immediate_parent_name};
    use crate::{
        collection::{open_test_collection, Collection},
        err::Result,
    };

    fn sorted_names(col: &Collection) -> Vec<String> {
        col.storage
            .get_all_deck_names()
            .unwrap()
            .into_iter()
            .map(|d| d.1)
            .collect()
    }

    #[test]
    fn parent() {
        assert_eq!(immediate_parent_name("foo"), None);
        assert_eq!(immediate_parent_name("foo\x1fbar"), Some("foo"));
        assert_eq!(
            immediate_parent_name("foo\x1fbar\x1fbaz"),
            Some("foo\x1fbar")
        );
    }

    #[test]
    fn from_human() {
        assert_eq!(&human_deck_name_to_native("foo"), "foo");
        assert_eq!(&human_deck_name_to_native("foo::bar"), "foo\x1fbar");
        assert_eq!(&human_deck_name_to_native("fo\x1fo::ba\nr"), "foo\x1fbar");
        assert_eq!(
            &human_deck_name_to_native("foo::::baz"),
            "foo\x1fblank\x1fbaz"
        );
    }

    #[test]
    fn adding_updating() -> Result<()> {
        let mut col = open_test_collection();

        let deck1 = col.get_or_create_normal_deck("foo")?;
        let deck2 = col.get_or_create_normal_deck("FOO")?;
        assert_eq!(deck1.id, deck2.id);
        assert_eq!(sorted_names(&col), vec!["Default", "foo"]);

        // missing parents should be automatically created, and case should match
        // existing parents
        let _deck3 = col.get_or_create_normal_deck("FOO::BAR::BAZ")?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "foo", "foo::BAR", "foo::BAR::BAZ"]
        );

        Ok(())
    }

    #[test]
    fn renaming() -> Result<()> {
        let mut col = open_test_collection();

        let _ = col.get_or_create_normal_deck("foo::bar::baz")?;
        let mut top_deck = col.get_or_create_normal_deck("foo")?;
        top_deck.name = "other".into();
        col.add_or_update_deck(&mut top_deck, false)?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "other", "other::bar", "other::bar::baz"]
        );

        Ok(())
    }
}
