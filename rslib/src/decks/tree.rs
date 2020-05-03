// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Deck;
use crate::{backend_proto::DeckTreeNode, collection::Collection, decks::DeckID, err::Result};
use std::{
    collections::{HashMap, HashSet},
    iter::Peekable,
};
use unicase::UniCase;

// fixme: handle mixed case of parents

fn deck_names_to_tree(names: Vec<(DeckID, String)>) -> DeckTreeNode {
    let mut top = DeckTreeNode::default();
    let mut it = names.into_iter().peekable();

    add_child_nodes(&mut it, &mut top);

    top
}

fn add_child_nodes(
    names: &mut Peekable<impl Iterator<Item = (DeckID, String)>>,
    parent: &mut DeckTreeNode,
) {
    while let Some((id, name)) = names.peek() {
        let split_name: Vec<_> = name.split("::").collect();
        match split_name.len() as u32 {
            l if l <= parent.level => {
                // next item is at a higher level
                return;
            }
            l if l == parent.level + 1 => {
                // next item is an immediate descendent of parent
                parent.children.push(DeckTreeNode {
                    deck_id: id.0,
                    name: (*split_name.last().unwrap()).into(),
                    children: vec![],
                    level: parent.level + 1,
                    ..Default::default()
                });
                names.next();
            }
            _ => {
                // next item is at a lower level
                if let Some(last_child) = parent.children.last_mut() {
                    add_child_nodes(names, last_child)
                } else {
                    // immediate parent is missing, skip the deck until a DB check is run
                    names.next();
                }
            }
        }
    }
}

fn add_collapsed(node: &mut DeckTreeNode, decks: &HashMap<DeckID, Deck>, browser: bool) {
    if let Some(deck) = decks.get(&DeckID(node.deck_id)) {
        node.collapsed = if browser {
            deck.common.browser_collapsed
        } else {
            deck.common.study_collapsed
        };
    }
    for child in &mut node.children {
        add_collapsed(child, decks, browser);
    }
}

impl Collection {
    pub fn deck_tree(&self) -> Result<DeckTreeNode> {
        let names = self.storage.get_all_deck_names()?;
        let mut tree = deck_names_to_tree(names);

        let decks_map: HashMap<_, _> = self
            .storage
            .get_all_decks()?
            .into_iter()
            .map(|d| (d.id, d))
            .collect();

        add_collapsed(&mut tree, &decks_map, true);

        Ok(tree)
    }

    pub(crate) fn add_missing_decks(&mut self, names: &[(DeckID, String)]) -> Result<()> {
        let mut parents = HashSet::new();
        for (_id, name) in names {
            parents.insert(UniCase::new(name.as_str()));
            if let Some(immediate_parent) = name.rsplitn(2, "::").nth(1) {
                let immediate_parent_uni = UniCase::new(immediate_parent);
                if !parents.contains(&immediate_parent_uni) {
                    self.get_or_create_normal_deck(immediate_parent)?;
                    parents.insert(immediate_parent_uni);
                }
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::{collection::open_test_collection, err::Result};

    #[test]
    fn wellformed() -> Result<()> {
        let mut col = open_test_collection();

        col.get_or_create_normal_deck("1")?;
        col.get_or_create_normal_deck("2")?;
        col.get_or_create_normal_deck("2::a")?;
        col.get_or_create_normal_deck("2::b")?;
        col.get_or_create_normal_deck("2::c")?;
        col.get_or_create_normal_deck("2::c::A")?;
        col.get_or_create_normal_deck("3")?;

        let tree = col.deck_tree()?;

        // 4 including default
        assert_eq!(tree.children.len(), 4);

        assert_eq!(tree.children[1].name, "2");
        assert_eq!(tree.children[1].children[0].name, "a");
        assert_eq!(tree.children[1].children[2].name, "c");
        assert_eq!(tree.children[1].children[2].children[0].name, "A");

        Ok(())
    }

    #[test]
    fn malformed() -> Result<()> {
        let mut col = open_test_collection();

        col.get_or_create_normal_deck("1")?;
        col.get_or_create_normal_deck("2::3::4")?;

        // remove the top parent and middle parent
        col.storage.remove_deck(col.get_deck_id("2")?.unwrap())?;
        col.storage.remove_deck(col.get_deck_id("2::3")?.unwrap())?;

        let tree = col.deck_tree()?;
        assert_eq!(tree.children.len(), 2);

        Ok(())
    }
}
