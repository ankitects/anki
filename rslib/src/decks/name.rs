// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use crate::{prelude::*, text::normalize_to_nfc};
use std::borrow::Cow;

impl Deck {
    pub fn human_name(&self) -> String {
        self.name.replace("\x1f", "::")
    }

    // Mutate name to human representation for sharing.
    pub fn with_human_name(mut self) -> Self {
        self.name = self.human_name();
        self
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
            deck.name = human_deck_name_to_native(new_human_name);
            col.update_deck_inner(&mut deck, existing_deck, col.usn()?)
        })
    }

    pub(super) fn rename_child_decks(
        &mut self,
        old: &Deck,
        new_name: &str,
        usn: Usn,
    ) -> Result<()> {
        let children = self.storage.child_decks(old)?;
        let old_component_count = old.name.matches('\x1f').count() + 1;

        for mut child in children {
            let original = child.clone();
            let child_components: Vec<_> = child.name.split('\x1f').collect();
            let child_only = &child_components[old_component_count..];
            let new_name = format!("{}\x1f{}", new_name, child_only.join("\x1f"));
            child.name = new_name;
            child.set_modified(usn);
            self.update_single_deck_undoable(&mut child, original)?;
        }

        Ok(())
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

pub(super) fn normalize_native_name(name: &str) -> Cow<str> {
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

pub(crate) fn native_deck_name_to_human(name: &str) -> String {
    name.replace('\x1f', "::")
}

pub(crate) fn immediate_parent_name(machine_name: &str) -> Option<&str> {
    machine_name.rsplitn(2, '\x1f').nth(1)
}

/// Determine name to rename a deck to, when `dragged` is dropped on `dropped`.
/// `dropped` being unset represents a drop at the top or bottom of the deck list.
/// The returned name should be used to rename `dragged`.
/// Arguments are expected in 'machine' form with an \x1f separator.
pub(crate) fn reparented_name(dragged: &str, dropped: Option<&str>) -> Option<String> {
    let dragged_base = dragged.rsplit('\x1f').next().unwrap();
    if let Some(dropped) = dropped {
        if dropped.starts_with(dragged) {
            // foo onto foo::bar, or foo onto itself -> no-op
            None
        } else {
            // foo::bar onto baz -> baz::bar
            Some(format!("{}\x1f{}", dropped, dragged_base))
        }
    } else {
        // foo::bar onto top level -> bar
        Some(dragged_base.into())
    }
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
    fn drag_drop() {
        // use custom separator to make the tests easier to read
        fn n(s: &str) -> String {
            s.replace(":", "\x1f")
        }

        #[allow(clippy::unnecessary_wraps)]
        fn n_opt(s: &str) -> Option<String> {
            Some(n(s))
        }

        assert_eq!(reparented_name("drag", Some("drop")), n_opt("drop:drag"));
        assert_eq!(reparented_name("drag", None), n_opt("drag"));
        assert_eq!(reparented_name(&n("drag:child"), None), n_opt("child"));
        assert_eq!(
            reparented_name(&n("drag:child"), Some(&n("drop:deck"))),
            n_opt("drop:deck:child")
        );
        assert_eq!(
            reparented_name(&n("drag:child"), Some("drag")),
            n_opt("drag:child")
        );
        assert_eq!(
            reparented_name(&n("drag:child:grandchild"), Some("drag")),
            n_opt("drag:grandchild")
        );
        // drops to child not supported
        assert_eq!(
            reparented_name(&n("drag"), Some(&n("drag:child:grandchild"))),
            None
        );
        // name doesn't change when deck dropped on itself
        assert_eq!(reparented_name(&n("foo:bar"), Some(&n("foo:bar"))), None);
    }
}
