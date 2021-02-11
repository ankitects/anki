// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    decks::DeckID as DeckIDType,
    notetype::NoteTypeID as NoteTypeIDType,
    search::parser::{Node, PropertyKind, RatingKind, SearchNode, StateKind, TemplateKind},
};
use std::mem;

#[derive(Debug, PartialEq)]
pub enum BoolSeparator {
    And,
    Or,
}

/// Take an existing search, and AND/OR it with the provided additional search.
/// This is required because when the user has "a AND b" in an existing search and
/// wants to add "c", we want "a AND b AND c", not "(a AND b) AND C", which is what we'd
/// get if we tried to join the existing search string with a new SearchTerm on the
/// client side.
pub fn concatenate_searches(
    sep: BoolSeparator,
    mut existing: Vec<Node>,
    additional: Node,
) -> String {
    if !existing.is_empty() {
        existing.push(match sep {
            BoolSeparator::And => Node::And,
            BoolSeparator::Or => Node::Or,
        });
    }
    existing.push(additional);
    write_nodes(&existing)
}

/// Given an existing parsed search, if the provided `replacement` is a single search node such
/// as a deck:xxx search, replace any instances of that search in `existing` with the new value.
/// Then return the possibly modified first search as a string.
pub fn replace_search_node(mut existing: Vec<Node>, replacement: Node) -> String {
    if let Node::Search(search_node) = replacement {
        fn update_node_vec(old_nodes: &mut [Node], new_node: &SearchNode) {
            fn update_node(old_node: &mut Node, new_node: &SearchNode) {
                match old_node {
                    Node::Not(n) => update_node(n, new_node),
                    Node::Group(ns) => update_node_vec(ns, new_node),
                    Node::Search(n) => {
                        if mem::discriminant(n) == mem::discriminant(new_node) {
                            *n = new_node.clone();
                        }
                    }
                    _ => (),
                }
            }
            old_nodes.iter_mut().for_each(|n| update_node(n, new_node));
        }
        update_node_vec(&mut existing, &search_node);
    }
    write_nodes(&existing)
}

pub fn write_nodes(nodes: &[Node]) -> String {
    nodes.iter().map(|node| write_node(node)).collect()
}

fn write_node(node: &Node) -> String {
    use Node::*;
    match node {
        And => " AND ".to_string(),
        Or => " OR ".to_string(),
        Not(n) => format!("-{}", write_node(n)),
        Group(ns) => format!("({})", write_nodes(ns)),
        Search(n) => write_search_node(n),
    }
}

fn write_search_node(node: &SearchNode) -> String {
    use SearchNode::*;
    match node {
        UnqualifiedText(s) => quote(&s.replace(":", "\\:")),
        SingleField { field, text, is_re } => write_single_field(field, text, *is_re),
        AddedInDays(u) => format!("\"added:{}\"", u),
        EditedInDays(u) => format!("\"edited:{}\"", u),
        CardTemplate(t) => write_template(t),
        Deck(s) => quote(&format!("deck:{}", s)),
        DeckID(DeckIDType(i)) => format!("\"did:{}\"", i),
        NoteTypeID(NoteTypeIDType(i)) => format!("\"mid:{}\"", i),
        NoteType(s) => quote(&format!("note:{}", s)),
        Rated { days, ease } => write_rated(days, ease),
        Tag(s) => quote(&format!("tag:{}", s)),
        Duplicates { note_type_id, text } => write_dupe(note_type_id, text),
        State(k) => write_state(k),
        Flag(u) => format!("\"flag:{}\"", u),
        NoteIDs(s) => format!("\"nid:{}\"", s),
        CardIDs(s) => format!("\"cid:{}\"", s),
        Property { operator, kind } => write_property(operator, kind),
        WholeCollection => "\"deck:*\"".to_string(),
        Regex(s) => quote(&format!("re:{}", s)),
        NoCombining(s) => quote(&format!("nc:{}", s)),
        WordBoundary(s) => quote(&format!("w:{}", s)),
    }
}

/// Escape and wrap in double quotes.
fn quote(txt: &str) -> String {
    format!("\"{}\"", txt.replace("\"", "\\\""))
}

fn write_single_field(field: &str, text: &str, is_re: bool) -> String {
    let re = if is_re { "re:" } else { "" };
    let text = if !is_re && text.starts_with("re:") {
        text.replacen(":", "\\:", 1)
    } else {
        text.to_string()
    };
    quote(&format!("{}:{}{}", field.replace(":", "\\:"), re, &text))
}

fn write_template(template: &TemplateKind) -> String {
    match template {
        TemplateKind::Ordinal(u) => format!("\"card:{}\"", u + 1),
        TemplateKind::Name(s) => format!("\"card:{}\"", s),
    }
}

fn write_rated(days: &u32, ease: &RatingKind) -> String {
    use RatingKind::*;
    match ease {
        AnswerButton(n) => format!("\"rated:{}:{}\"", days, n),
        AnyAnswerButton => format!("\"rated:{}\"", days),
        ManualReschedule => format!("\"resched:{}\"", days),
    }
}

/// Escape double quotes and backslashes: \"
fn write_dupe(note_type_id: &NoteTypeIDType, text: &str) -> String {
    let esc = text.replace(r"\", r"\\").replace('"', r#"\""#);
    format!("\"dupe:{},{}\"", note_type_id, esc)
}

fn write_state(kind: &StateKind) -> String {
    use StateKind::*;
    format!(
        "\"is:{}\"",
        match kind {
            New => "new",
            Review => "review",
            Learning => "learn",
            Due => "due",
            Buried => "buried",
            UserBuried => "buried-manually",
            SchedBuried => "buried-sibling",
            Suspended => "suspended",
        }
    )
}

fn write_property(operator: &str, kind: &PropertyKind) -> String {
    use PropertyKind::*;
    match kind {
        Due(i) => format!("\"prop:due{}{}\"", operator, i),
        Interval(u) => format!("\"prop:ivl{}{}\"", operator, u),
        Reps(u) => format!("\"prop:reps{}{}\"", operator, u),
        Lapses(u) => format!("\"prop:lapses{}{}\"", operator, u),
        Ease(f) => format!("\"prop:ease{}{}\"", operator, f),
        Position(u) => format!("\"prop:pos{}{}\"", operator, u),
        Rated(u, ease) => match ease {
            RatingKind::AnswerButton(val) => format!("\"prop:rated{}{}:{}\"", operator, u, val),
            RatingKind::AnyAnswerButton => format!("\"prop:rated{}{}\"", operator, u),
            RatingKind::ManualReschedule => format!("\"prop:resched{}{}\"", operator, u),
        },
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::err::Result;
    use crate::search::parse_search as parse;

    /// Take an Anki-style search string and convert it into an equivalent
    /// search string with normalized syntax.
    fn normalize_search(input: &str) -> Result<String> {
        Ok(write_nodes(&parse(input)?))
    }

    #[test]
    fn normalizing() -> Result<()> {
        assert_eq!(r#""(" AND "-""#, normalize_search(r"\( \-").unwrap());
        assert_eq!(r#""deck::""#, normalize_search(r"deck:\:").unwrap());
        assert_eq!(r#""\*" OR "\:""#, normalize_search(r"\* or \:").unwrap());
        assert_eq!(
            r#""field:foo""#,
            normalize_search(r#"field:"foo""#).unwrap()
        );
        assert_eq!(
            r#""prop:ease>1""#,
            normalize_search("prop:ease>1.0").unwrap()
        );

        Ok(())
    }

    #[test]
    fn concatenating() -> Result<()> {
        assert_eq!(
            concatenate_searches(
                BoolSeparator::And,
                vec![Node::Search(SearchNode::UnqualifiedText("foo".to_string()))],
                Node::Search(SearchNode::UnqualifiedText("bar".to_string()))
            ),
            r#""foo" AND "bar""#,
        );
        assert_eq!(
            concatenate_searches(
                BoolSeparator::Or,
                vec![Node::Search(SearchNode::UnqualifiedText("foo".to_string()))],
                Node::Search(SearchNode::UnqualifiedText("bar".to_string()))
            ),
            r#""foo" OR "bar""#,
        );
        assert_eq!(
            concatenate_searches(
                BoolSeparator::Or,
                vec![Node::Search(SearchNode::WholeCollection)],
                Node::Search(SearchNode::UnqualifiedText("bar".to_string()))
            ),
            r#""deck:*" OR "bar""#,
        );
        assert_eq!(
            concatenate_searches(
                BoolSeparator::Or,
                vec![],
                Node::Search(SearchNode::UnqualifiedText("bar".to_string()))
            ),
            r#""bar""#,
        );

        Ok(())
    }

    #[test]
    fn replacing() -> Result<()> {
        assert_eq!(
            replace_search_node(parse("deck:baz bar")?, parse("deck:foo")?.pop().unwrap()),
            r#""deck:foo" AND "bar""#,
        );
        assert_eq!(
            replace_search_node(
                parse("tag:foo Or tag:bar")?,
                parse("tag:baz")?.pop().unwrap()
            ),
            r#""tag:baz" OR "tag:baz""#,
        );
        assert_eq!(
            replace_search_node(
                parse("foo or (-foo tag:baz)")?,
                parse("bar")?.pop().unwrap()
            ),
            r#""bar" OR (-"bar" AND "tag:baz")"#,
        );
        assert_eq!(
            replace_search_node(parse("is:due")?, parse("-is:new")?.pop().unwrap()),
            r#""is:due""#
        );
        assert_eq!(
            replace_search_node(parse("added:1")?, parse("is:due")?.pop().unwrap()),
            r#""added:1""#
        );

        Ok(())
    }
}
