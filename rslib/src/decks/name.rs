// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::borrow::Cow;

use itertools::Itertools;

use crate::{prelude::*, text::normalize_to_nfc};

#[derive(Debug, Clone, PartialEq)]
pub struct NativeDeckName(String);

impl NativeDeckName {
    /// Create from a '::'-separated string
    pub fn from_human_name(name: &str) -> Self {
        NativeDeckName(
            name.split("::")
                .map(normalized_deck_name_component)
                .join("\x1f"),
        )
    }

    /// Return a '::'-separated string.
    pub fn human_name(&self) -> String {
        self.0.replace('\x1f', "::")
    }

    pub(crate) fn add_suffix(&mut self, suffix: &str) {
        self.0 += suffix
    }

    /// Create from an '\x1f'-separated string
    pub(crate) fn from_native_str<N: Into<String>>(name: N) -> Self {
        NativeDeckName(name.into())
    }

    /// Return a reference to the inner '\x1f'-separated string.
    pub(crate) fn as_native_str(&self) -> &str {
        &self.0
    }

    pub(crate) fn components(&self) -> std::str::Split<'_, char> {
        self.0.split('\x1f')
    }

    /// Normalize the name's components if necessary. True if mutation took place.
    pub(crate) fn maybe_normalize(&mut self) -> bool {
        let needs_normalization = self
            .components()
            .any(|comp| matches!(normalized_deck_name_component(comp), Cow::Owned(_)));
        if needs_normalization {
            self.0 = self
                .components()
                .map(normalized_deck_name_component)
                .join("\x1f");
        }
        needs_normalization
    }

    /// Determine name to rename a deck to, when `self` is dropped on `target`.
    /// `target` being unset represents a drop at the top or bottom of the deck list.
    /// The returned name should be used to replace `self`.
    pub(crate) fn reparented_name(&self, target: Option<&NativeDeckName>) -> Option<Self> {
        let dragged_base = self.0.rsplit('\x1f').next().unwrap();
        if let Some(target) = target {
            if target.0.starts_with(&self.0) {
                // foo onto foo::bar, or foo onto itself -> no-op
                None
            } else {
                // foo::bar onto baz -> baz::bar
                Some(NativeDeckName(format!("{}\x1f{}", target.0, dragged_base)))
            }
        } else {
            // foo::bar onto top level -> bar
            Some(NativeDeckName(dragged_base.into()))
        }
    }

    /// Replace the old parent's name with the new parent's name in self's name, where the old
    /// parent's name is expected to be a prefix.
    fn reparent(&mut self, old_parent: &NativeDeckName, new_parent: &NativeDeckName) {
        self.0 = std::iter::once(new_parent.as_native_str())
            .chain(self.components().skip(old_parent.components().count()))
            .join("\x1f")
    }
}

impl std::fmt::Display for NativeDeckName {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        self.0.fmt(f)
    }
}

impl Deck {
    pub fn human_name(&self) -> String {
        self.name.human_name()
    }
}

impl Collection {
    pub fn get_all_normal_deck_names(&mut self) -> Result<Vec<(DeckId, String)>> {
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

    pub fn rename_deck(&mut self, did: DeckId, new_human_name: &str) -> Result<OpOutput<()>> {
        self.transact(Op::RenameDeck, |col| {
            let existing_deck = col.storage.get_deck(did)?.ok_or(AnkiError::NotFound)?;
            let mut deck = existing_deck.clone();
            deck.name = NativeDeckName::from_human_name(new_human_name);
            col.update_deck_inner(&mut deck, existing_deck, col.usn()?)
        })
    }

    pub(super) fn rename_child_decks(
        &mut self,
        old: &Deck,
        new_name: &NativeDeckName,
        usn: Usn,
    ) -> Result<()> {
        let children = self.storage.child_decks(old)?;
        for mut child in children {
            let original = child.clone();
            child.name.reparent(&old.name, new_name);
            child.set_modified(usn);
            self.update_single_deck_undoable(&mut child, original)?;
        }

        Ok(())
    }

    pub(crate) fn ensure_deck_name_unique(&self, deck: &mut Deck, usn: Usn) -> Result<()> {
        loop {
            match self.storage.get_deck_id(deck.name.as_native_str())? {
                Some(did) if did == deck.id => break,
                None => break,
                _ => (),
            }
            deck.name.add_suffix("+");
            deck.set_modified(usn);
        }

        Ok(())
    }

    pub fn get_all_deck_names(&self, skip_empty_default: bool) -> Result<Vec<(DeckId, String)>> {
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

pub(crate) fn immediate_parent_name(machine_name: &str) -> Option<&str> {
    machine_name.rsplitn(2, '\x1f').nth(1)
}

#[cfg(test)]
mod test {
    use super::*;

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
        fn native_name(name: &str) -> String {
            NativeDeckName::from_human_name(name).0
        }

        assert_eq!(native_name("foo"), "foo");
        assert_eq!(native_name("foo::bar"), "foo\x1fbar");
        assert_eq!(native_name("foo::::baz"), "foo\x1fblank\x1fbaz");
        // implicitly normalize
        assert_eq!(native_name("fo\x1fo::ba\nr"), "foo\x1fbar");
        assert_eq!(native_name("fo\u{a}o\x1fbar"), "foobar");
    }

    #[test]
    fn normalize() {
        fn normalize_res(name: &str) -> (bool, String) {
            let mut name = NativeDeckName::from_native_str(name);
            (name.maybe_normalize(), name.0)
        }

        assert_eq!(normalize_res("foo\x1fbar"), (false, "foo\x1fbar".into()));
        assert_eq!(
            normalize_res("fo\x1fo::ba\nr"),
            (true, "fo\x1fo::bar".into())
        );
        assert_eq!(normalize_res("fo\u{a}obar"), (true, "foobar".into()));
    }

    #[test]
    fn drag_drop() {
        // use custom separator to make the tests easier to read
        fn n(s: &str) -> NativeDeckName {
            NativeDeckName(s.replace(":", "\x1f"))
        }

        #[allow(clippy::unnecessary_wraps)]
        fn n_opt(s: &str) -> Option<NativeDeckName> {
            Some(n(s))
        }

        fn reparented_name(drag: &str, drop: Option<&str>) -> Option<NativeDeckName> {
            n(drag).reparented_name(drop.map(n).as_ref())
        }

        assert_eq!(reparented_name("drag", Some("drop")), n_opt("drop:drag"));
        assert_eq!(reparented_name("drag", None), n_opt("drag"));
        assert_eq!(reparented_name("drag:child", None), n_opt("child"));
        assert_eq!(
            reparented_name("drag:child", Some("drop:deck")),
            n_opt("drop:deck:child")
        );
        assert_eq!(
            reparented_name("drag:child", Some("drag")),
            n_opt("drag:child")
        );
        assert_eq!(
            reparented_name("drag:child:grandchild", Some("drag")),
            n_opt("drag:grandchild")
        );
        // drops to child not supported
        assert_eq!(reparented_name("drag", Some("drag:child:grandchild")), None);
        // name doesn't change when deck dropped on itself
        assert_eq!(reparented_name("foo:bar", Some("foo:bar")), None);
    }
}
