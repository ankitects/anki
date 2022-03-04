// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::mem;

use itertools::Itertools;

use super::{writer::write_nodes, Node, SearchNode, StateKind, TemplateKind};
use crate::{prelude::*, text::escape_anki_wildcards_for_search_node};

pub trait Negated {
    fn negated(self) -> Node;
}

impl<T: Into<Node>> Negated for T {
    fn negated(self) -> Node {
        let node: Node = self.into();
        if let Node::Not(inner) = node {
            *inner
        } else {
            Node::Not(Box::new(node))
        }
    }
}

/// Helper to programmatically build searches.
#[derive(Debug, PartialEq, Clone)]
pub struct SearchBuilder(Vec<Node>);

impl SearchBuilder {
    pub fn new() -> Self {
        Self(vec![])
    }

    /// Construct [SearchBuilder] with this [Node], or its inner [Node]s,
    /// if it is a [Node::Group]
    pub fn from_root(node: Node) -> Self {
        match node {
            Node::Group(nodes) => Self(nodes),
            _ => Self(vec![node]),
        }
    }

    /// Construct [SearchBuilder] where given [Node]s are joined by [Node::And].
    pub fn all(iter: impl IntoIterator<Item = impl Into<Node>>) -> Self {
        Self(Itertools::intersperse(iter.into_iter().map(Into::into), Node::And).collect())
    }

    /// Construct [SearchBuilder] where given [Node]s are joined by [Node::Or].
    pub fn any(iter: impl IntoIterator<Item = impl Into<Node>>) -> Self {
        Self(Itertools::intersperse(iter.into_iter().map(Into::into), Node::Or).collect())
    }

    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }

    pub fn len(&self) -> usize {
        self.0.len()
    }

    pub fn and<N: Into<Node>>(mut self, node: N) -> Self {
        if !self.is_empty() {
            self.0.push(Node::And)
        }
        self.0.push(node.into());
        self
    }

    pub fn or<N: Into<Node>>(mut self, node: N) -> Self {
        if !self.is_empty() {
            self.0.push(Node::Or)
        }
        self.0.push(node.into());
        self
    }

    /// Wrap [Node]s in [Node::Group] if there is more than 1.
    pub fn group(mut self) -> Self {
        if self.len() > 1 {
            self.0 = vec![Node::Group(mem::take(&mut self.0))];
        }
        self
    }

    /// Concatenate [Node]s of `other`, inserting [Node::And] if appropriate.
    /// No implicit grouping is done.
    pub fn and_join(mut self, other: &mut Self) -> Self {
        if !(self.is_empty() || other.is_empty()) {
            self.0.push(Node::And);
        }
        self.0.append(&mut other.0);
        self
    }

    /// Concatenate [Node]s of `other`, inserting [Node::Or] if appropriate.
    /// No implicit grouping is done.
    pub fn or_join(mut self, other: &mut Self) -> Self {
        if !(self.is_empty() || other.is_empty()) {
            self.0.push(Node::And);
        }
        self.0.append(&mut other.0);
        self
    }

    pub fn write(&self) -> String {
        write_nodes(&self.0)
    }
}

impl<T: Into<Node>> From<T> for SearchBuilder {
    fn from(node: T) -> Self {
        Self(vec![node.into()])
    }
}

impl TryIntoSearch for SearchBuilder {
    fn try_into_search(self) -> Result<Node, AnkiError> {
        Ok(self.group().0.remove(0))
    }
}

impl Default for SearchBuilder {
    fn default() -> Self {
        Self::new()
    }
}

impl SearchNode {
    /// Construct [SearchNode] from an unescaped deck name.
    pub fn from_deck_name(name: &str) -> Self {
        Self::Deck(escape_anki_wildcards_for_search_node(name))
    }

    /// Construct [SearchNode] from an unescaped tag name.
    pub fn from_tag_name(name: &str) -> Self {
        Self::Tag {
            tag: escape_anki_wildcards_for_search_node(name),
            is_re: false,
        }
    }

    /// Construct [SearchNode] from an unescaped notetype name.
    pub fn from_notetype_name(name: &str) -> Self {
        Self::Notetype(escape_anki_wildcards_for_search_node(name))
    }

    /// Construct [SearchNode] from an unescaped template name.
    pub fn from_template_name(name: &str) -> Self {
        Self::CardTemplate(TemplateKind::Name(escape_anki_wildcards_for_search_node(
            name,
        )))
    }
}

impl<T: Into<SearchNode>> From<T> for Node {
    fn from(node: T) -> Self {
        Self::Search(node.into())
    }
}

impl From<NotetypeId> for SearchNode {
    fn from(id: NotetypeId) -> Self {
        SearchNode::NotetypeId(id)
    }
}

impl From<TemplateKind> for SearchNode {
    fn from(k: TemplateKind) -> Self {
        SearchNode::CardTemplate(k)
    }
}

impl From<NoteId> for SearchNode {
    fn from(n: NoteId) -> Self {
        SearchNode::NoteIds(format!("{}", n))
    }
}

impl From<StateKind> for SearchNode {
    fn from(k: StateKind) -> Self {
        SearchNode::State(k)
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn negating() {
        let node = Node::Search(SearchNode::UnqualifiedText("foo".to_string()));
        let neg_node = Node::Not(Box::new(Node::Search(SearchNode::UnqualifiedText(
            "foo".to_string(),
        ))));
        assert_eq!(node.clone().negated(), neg_node);
        assert_eq!(node.clone().negated().negated(), node);

        assert_eq!(
            StateKind::Due.negated(),
            Node::Not(Box::new(Node::Search(SearchNode::State(StateKind::Due))))
        )
    }
}
