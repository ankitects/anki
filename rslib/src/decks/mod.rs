// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pb;
pub use crate::backend_proto::{
    deck_kind::Kind as DeckKind, filtered_search_term::FilteredSearchOrder, Deck as DeckProto,
    DeckCommon, DeckKind as DeckKindProto, FilteredDeck, FilteredSearchTerm, NormalDeck,
};
use crate::{
    collection::Collection,
    deckconf::DeckConfID,
    define_newtype,
    err::{AnkiError, Result},
    i18n::TR,
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

#[derive(Debug, Clone, PartialEq)]
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

    fn reset_stats_if_day_changed(&mut self, today: u32) {
        let c = &mut self.common;
        if c.last_day_studied != today {
            c.new_studied = 0;
            c.learning_studied = 0;
            c.review_studied = 0;
            c.milliseconds_studied = 0;
            c.last_day_studied = today;
        }
    }

    /// Returns deck config ID if deck is a normal deck.
    pub(crate) fn config_id(&self) -> Option<DeckConfID> {
        if let DeckKind::Normal(ref norm) = self.kind {
            Some(DeckConfID(norm.config_id))
        } else {
            None
        }
    }

    pub fn human_name(&self) -> String {
        self.name.replace("\x1f", "::")
    }

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
        "blank".to_string().into()
    } else if trimmed.len() != out.len() {
        trimmed.to_string().into()
    } else {
        out
    }
}

fn normalize_native_name(name: &str) -> Cow<str> {
    if name
        .split('\x1f')
        .any(|comp| matches!(normalized_deck_name_component(comp), Cow::Owned(_)))
    {
        let comps: Vec<_> = name
            .split('\x1f')
            .map(normalized_deck_name_component)
            .collect::<Vec<_>>();
        comps.join("\x1f").into()
    } else {
        // no changes required
        name.into()
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

pub(crate) fn immediate_parent_name(machine_name: &str) -> Option<&str> {
    machine_name.rsplitn(2, '\x1f').nth(1)
}

/// Determine name to rename a deck to, when `dragged` is dropped on `dropped`.
/// `dropped` being unset represents a drop at the top or bottom of the deck list.
/// The returned name should be used to rename `dragged`, and may be unchanged.
/// Arguments are expected in 'machine' form with an \x1f separator.
pub(crate) fn drag_drop_deck_name(dragged: &str, dropped: Option<&str>) -> String {
    let dragged_base = dragged.rsplit('\x1f').next().unwrap();
    if let Some(dropped) = dropped {
        if dropped.starts_with(dragged) {
            // foo onto foo::bar, or foo onto itself -> no-op
            dragged.to_string()
        } else {
            // foo::bar onto baz -> baz::bar
            format!("{}\x1f{}", dropped, dragged_base)
        }
    } else {
        // foo::bar onto top level -> bar
        dragged_base.into()
    }
}

impl Collection {
    pub(crate) fn default_deck_is_empty(&self) -> Result<bool> {
        self.storage.deck_is_empty(DeckID(1))
    }

    /// Normalize deck name and rename if not unique. Bumps mtime and usn if
    /// deck was modified.
    fn prepare_deck_for_update(&mut self, deck: &mut Deck, usn: Usn) -> Result<()> {
        if let Cow::Owned(name) = normalize_native_name(&deck.name) {
            deck.name = name;
            deck.set_modified(usn);
        }
        self.ensure_deck_name_unique(deck, usn)
    }

    /// Add or update an existing deck modified by the user. May add parents,
    /// or rename children as required.
    pub(crate) fn add_or_update_deck(&mut self, deck: &mut Deck) -> Result<()> {
        self.state.deck_cache.clear();

        self.transact(None, |col| {
            let usn = col.usn()?;

            deck.set_modified(usn);

            if deck.id.0 == 0 {
                col.prepare_deck_for_update(deck, usn)?;
                col.match_or_create_parents(deck, usn)?;
                col.storage.add_deck(deck)
            } else if let Some(existing_deck) = col.storage.get_deck(deck.id)? {
                if existing_deck.name != deck.name {
                    col.update_renamed_deck(existing_deck, deck, usn)
                } else {
                    col.add_or_update_single_deck(deck, usn)
                }
            } else {
                Err(AnkiError::invalid_input("updating non-existent deck"))
            }
        })
    }

    /// Add/update a single deck when syncing/importing. Ensures name is unique
    /// & normalized, but does not check parents/children or update mtime
    /// (unless the name was changed). Caller must set up transaction.
    pub(crate) fn add_or_update_single_deck(&mut self, deck: &mut Deck, usn: Usn) -> Result<()> {
        self.state.deck_cache.clear();
        self.prepare_deck_for_update(deck, usn)?;
        self.storage.update_deck(deck)
    }

    pub(crate) fn ensure_deck_name_unique(&self, deck: &mut Deck, usn: Usn) -> Result<()> {
        loop {
            match self.storage.get_deck_id(&deck.name)? {
                Some(did) if did == deck.id => {
                    break;
                }
                None => break,
                _ => (),
            }
            deck.name += "+";
            deck.set_modified(usn);
        }

        Ok(())
    }

    pub(crate) fn recover_missing_deck(&mut self, did: DeckID, usn: Usn) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.id = did;
        deck.name = format!("recovered{}", did);
        deck.set_modified(usn);
        self.add_or_update_single_deck(&mut deck, usn)
    }

    pub fn get_or_create_normal_deck(&mut self, human_name: &str) -> Result<Deck> {
        let native_name = human_deck_name_to_native(human_name);
        if let Some(did) = self.storage.get_deck_id(&native_name)? {
            self.storage.get_deck(did).map(|opt| opt.unwrap())
        } else {
            let mut deck = Deck::new_normal();
            deck.name = native_name;
            self.add_or_update_deck(&mut deck)?;
            Ok(deck)
        }
    }

    fn update_renamed_deck(&mut self, existing: Deck, updated: &mut Deck, usn: Usn) -> Result<()> {
        self.state.deck_cache.clear();
        // ensure name normalized
        if let Cow::Owned(name) = normalize_native_name(&updated.name) {
            updated.name = name;
        }
        // match closest parent name
        self.match_or_create_parents(updated, usn)?;
        // ensure new name is unique
        self.ensure_deck_name_unique(updated, usn)?;
        // rename children
        self.rename_child_decks(&existing, &updated.name, usn)?;
        // save deck
        updated.set_modified(usn);
        self.storage.update_deck(updated)?;
        // after updating, we need to ensure all grandparents exist, which may not be the case
        // in the parent->child case
        self.create_missing_parents(&updated.name, usn)
    }

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
    fn add_parent_deck(&self, machine_name: &str, usn: Usn) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.name = machine_name.into();
        deck.set_modified(usn);
        // fixme: undo
        self.storage.add_deck(&mut deck)
    }

    /// If parent deck(s) exist, rewrite name to match their case.
    /// If they don't exist, create them.
    /// Returns an error if a DB operation fails, or if the first existing parent is a filtered deck.
    fn match_or_create_parents(&mut self, deck: &mut Deck, usn: Usn) -> Result<()> {
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
                self.create_missing_parents(&deck.name, usn)?;
            }
            Ok(())
        } else if child_split.len() == 1 {
            // no parents required
            Ok(())
        } else {
            // no existing parents
            self.create_missing_parents(&deck.name, usn)
        }
    }

    fn create_missing_parents(&self, mut machine_name: &str, usn: Usn) -> Result<()> {
        while let Some(parent_name) = immediate_parent_name(machine_name) {
            if self.storage.get_deck_id(parent_name)?.is_none() {
                self.add_parent_deck(parent_name, usn)?;
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
        // fixme: vet cache clearing
        self.state.deck_cache.clear();

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
        if deck.id.0 == 1 {
            let mut deck = deck.to_owned();
            // fixme: separate key
            deck.name = self.i18n.tr(TR::DeckConfigDefaultName).into();
            deck.set_modified(usn);
            self.add_or_update_single_deck(&mut deck, usn)?;
        } else {
            self.storage.remove_deck(deck.id)?;
            self.storage.add_deck_grave(deck.id, usn)?;
        }
        Ok(())
    }

    fn delete_all_cards_in_normal_deck(&mut self, did: DeckID) -> Result<()> {
        let cids = self.storage.all_cards_in_single_deck(did)?;
        self.remove_cards_and_orphaned_notes(&cids)
    }

    pub fn get_all_deck_names(&self, skip_empty_default: bool) -> Result<Vec<(DeckID, String)>> {
        if skip_empty_default && self.default_deck_is_empty()? {
            Ok(self
                .storage
                .get_all_deck_names()?
                .into_iter()
                .filter(|(id, _name)| id.0 != 1)
                .collect())
        } else {
            self.storage.get_all_deck_names()
        }
    }

    pub fn get_all_normal_deck_names(&mut self) -> Result<Vec<(DeckID, String)>> {
        Ok(self
            .storage
            .get_all_deck_names()?
            .into_iter()
            .filter(|(id, _name)| match self.get_deck(*id) {
                Ok(Some(deck)) => !deck.is_filtered(),
                _ => true,
            })
            .collect())
    }

    /// Apply input delta to deck, and its parents.
    /// Caller should ensure transaction.
    pub(crate) fn update_deck_stats(
        &mut self,
        today: u32,
        usn: Usn,
        input: pb::UpdateStatsIn,
    ) -> Result<()> {
        let did = input.deck_id.into();
        let mutator = |c: &mut DeckCommon| {
            c.new_studied += input.new_delta;
            c.review_studied += input.review_delta;
            c.milliseconds_studied += input.millisecond_delta;
        };
        if let Some(mut deck) = self.storage.get_deck(did)? {
            self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            for mut deck in self.storage.parent_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
        }
        Ok(())
    }

    /// Modify the deck's limits by adjusting the 'done today' count.
    /// Positive values increase the limit, negative value decrease it.
    /// Caller should ensure a transaction.
    pub(crate) fn extend_limits(
        &mut self,
        today: u32,
        usn: Usn,
        did: DeckID,
        new_delta: i32,
        review_delta: i32,
    ) -> Result<()> {
        let mutator = |c: &mut DeckCommon| {
            c.new_studied -= new_delta;
            c.review_studied -= review_delta;
        };
        if let Some(mut deck) = self.storage.get_deck(did)? {
            self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            for mut deck in self.storage.parent_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
            for mut deck in self.storage.child_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
        }

        Ok(())
    }

    pub(crate) fn counts_for_deck_today(
        &mut self,
        did: DeckID,
    ) -> Result<pb::CountsForDeckTodayOut> {
        let today = self.current_due_day(0)?;
        let mut deck = self.storage.get_deck(did)?.ok_or(AnkiError::NotFound)?;
        deck.reset_stats_if_day_changed(today);
        Ok(pb::CountsForDeckTodayOut {
            new: deck.common.new_studied,
            review: deck.common.review_studied,
        })
    }

    fn update_deck_stats_single<F>(
        &mut self,
        today: u32,
        usn: Usn,
        deck: &mut Deck,
        mutator: F,
    ) -> Result<()>
    where
        F: FnOnce(&mut DeckCommon),
    {
        deck.reset_stats_if_day_changed(today);
        mutator(&mut deck.common);
        deck.set_modified(usn);
        self.add_or_update_single_deck(deck, usn)
    }

    pub fn drag_drop_decks(
        &mut self,
        source_decks: &[DeckID],
        target: Option<DeckID>,
    ) -> Result<()> {
        self.state.deck_cache.clear();
        let usn = self.usn()?;
        self.transact(None, |col| {
            let target_deck;
            let mut target_name = None;
            if let Some(target) = target {
                if let Some(target) = col.storage.get_deck(target)? {
                    target_deck = target;
                    target_name = Some(target_deck.name.as_str());
                }
            }

            for source in source_decks {
                if let Some(mut source) = col.storage.get_deck(*source)? {
                    let orig = source.clone();
                    let new_name = drag_drop_deck_name(&source.name, target_name);
                    if new_name == source.name {
                        continue;
                    }
                    source.name = new_name;
                    col.ensure_deck_name_unique(&mut source, usn)?;
                    col.rename_child_decks(&orig, &source.name, usn)?;
                    source.set_modified(usn);
                    col.storage.update_deck(&source)?;
                    // after updating, we need to ensure all grandparents exist, which may not be the case
                    // in the parent->child case
                    col.create_missing_parents(&source.name, usn)?;
                }
            }

            Ok(())
        })
    }
}

#[cfg(test)]
mod test {
    use super::{human_deck_name_to_native, immediate_parent_name, normalize_native_name};
    use crate::decks::drag_drop_deck_name;
    use crate::{
        collection::{open_test_collection, Collection},
        err::Result,
        search::SortMode,
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
    fn normalize() {
        assert_eq!(&normalize_native_name("foo\x1fbar"), "foo\x1fbar");
        assert_eq!(&normalize_native_name("fo\u{a}o\x1fbar"), "foo\x1fbar");
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
        col.add_or_update_deck(&mut top_deck)?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "other", "other::bar", "other::bar::baz"]
        );

        // should do the right thing in the middle of the tree as well
        let mut middle = col.get_or_create_normal_deck("other::bar")?;
        middle.name = "quux\x1ffoo".into();
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "other", "quux", "quux::foo", "quux::foo::baz"]
        );

        // add another child
        let _ = col.get_or_create_normal_deck("quux::foo::baz2");

        // quux::foo -> quux::foo::baz::four
        // means quux::foo::baz2 should be quux::foo::baz::four::baz2
        // and a new quux::foo should have been created
        middle.name = "quux\x1ffoo\x1fbaz\x1ffour".into();
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(
            sorted_names(&col),
            vec![
                "Default",
                "other",
                "quux",
                "quux::foo",
                "quux::foo::baz",
                "quux::foo::baz::four",
                "quux::foo::baz::four::baz",
                "quux::foo::baz::four::baz2"
            ]
        );

        // should handle name conflicts
        middle.name = "other".into();
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(middle.name, "other+");

        Ok(())
    }

    #[test]
    fn default() -> Result<()> {
        // deleting the default deck will remove cards, but bring the deck back
        // as a top level deck
        let mut col = open_test_collection();

        let mut default = col.get_or_create_normal_deck("default")?;
        default.name = "one\x1ftwo".into();
        col.add_or_update_deck(&mut default)?;

        // create a non-default deck confusingly named "default"
        let _fake_default = col.get_or_create_normal_deck("default")?;

        // add a card to the real default
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, default.id)?;
        assert_ne!(col.search_cards("", SortMode::NoOrder)?, vec![]);

        // add a subdeck
        let _ = col.get_or_create_normal_deck("one::two::three")?;

        // delete top level
        let top = col.get_or_create_normal_deck("one")?;
        col.remove_deck_and_child_decks(top.id)?;

        // should have come back as "Default+" due to conflict
        assert_eq!(sorted_names(&col), vec!["default", "Default+"]);

        // and the cards it contained should have been removed
        assert_eq!(col.search_cards("", SortMode::NoOrder)?, vec![]);

        Ok(())
    }

    #[test]
    fn drag_drop() {
        // use custom separator to make the tests easier to read
        fn n(s: &str) -> String {
            s.replace(":", "\x1f")
        }
        assert_eq!(drag_drop_deck_name("drag", Some("drop")), n("drop:drag"));
        assert_eq!(&drag_drop_deck_name("drag", None), "drag");
        assert_eq!(&drag_drop_deck_name(&n("drag:child"), None), "child");
        assert_eq!(
            drag_drop_deck_name(&n("drag:child"), Some(&n("drop:deck"))),
            n("drop:deck:child")
        );
        assert_eq!(
            drag_drop_deck_name(&n("drag:child"), Some("drag")),
            n("drag:child")
        );
        assert_eq!(
            drag_drop_deck_name(&n("drag:child:grandchild"), Some("drag")),
            n("drag:grandchild")
        );
        // while the renaming code should be able to cope with renaming a parent to a child,
        // it's not often useful and can be difficult for the user to clean up if done accidentally,
        // so it should be a no-op
        assert_eq!(
            drag_drop_deck_name(&n("drag"), Some(&n("drag:child:grandchild"))),
            n("drag")
        );
        // name doesn't change when deck dropped on itself
        assert_eq!(
            drag_drop_deck_name(&n("foo:bar"), Some(&n("foo:bar"))),
            n("foo:bar")
        );
    }
}
