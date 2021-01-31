// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    decks::DeckID as DeckIDType,
    err::Result,
    notetype::NoteTypeID as NoteTypeIDType,
    search::parser::{parse, Node, PropertyKind, RatingKind, SearchNode, StateKind, TemplateKind},
};
use itertools::Itertools;
use std::mem;

#[derive(Debug, PartialEq)]
pub enum BoolSeparator {
    And,
    Or,
}

/// Take an Anki-style search string and convert it into an equivalent
/// search string with normalized syntax.
pub fn normalize_search(input: &str) -> Result<String> {
    Ok(write_nodes(&parse(input)?))
}

/// Take an Anki-style search string and return the negated counterpart.
/// Empty searches (whole collection) remain unchanged.
pub fn negate_search(input: &str) -> Result<String> {
    let mut nodes = parse(input)?;
    use Node::*;
    Ok(if nodes.len() == 1 {
        let node = nodes.remove(0);
        match node {
            Not(n) => write_node(&n),
            Search(SearchNode::WholeCollection) => "".to_string(),
            Group(_) | Search(_) => write_node(&Not(Box::new(node))),
            _ => unreachable!(),
        }
    } else {
        write_node(&Not(Box::new(Group(nodes))))
    })
}

/// Take arbitrary Anki-style search strings and return their concatenation where they
/// are separated by the provided boolean operator.
/// Empty searches (whole collection) are left out.
pub fn concatenate_searches(sep: BoolSeparator, searches: &[String]) -> Result<String> {
    let bool_node = vec![match sep {
        BoolSeparator::And => Node::And,
        BoolSeparator::Or => Node::Or,
    }];
    Ok(write_nodes(
        searches
            .iter()
            .map(|s| parse(s))
            .collect::<Result<Vec<Vec<Node>>>>()?
            .iter()
            .filter(|v| v[0] != Node::Search(SearchNode::WholeCollection))
            .intersperse(&&bool_node)
            .flat_map(|v| v.iter()),
    ))
}

/// Take two Anki-style search strings. If the second one evaluates to a single search
/// node, replace with it all search terms of the same kind in the first search.
/// Then return the possibly modified first search.
pub fn replace_search_term(search: &str, replacement: &str) -> Result<String> {
    let mut nodes = parse(search)?;
    let new = parse(replacement)?;
    if let [Node::Search(search_node)] = &new[..] {
        fn update_node_vec<'a>(old_nodes: &mut [Node<'a>], new_node: &SearchNode<'a>) {
            fn update_node<'a>(old_node: &mut Node<'a>, new_node: &SearchNode<'a>) {
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
        update_node_vec(&mut nodes, search_node);
    }
    Ok(write_nodes(&nodes))
}

pub fn write_nodes<'a, I>(nodes: I) -> String
where
    I: IntoIterator<Item = &'a Node<'a>>,
{
    nodes.into_iter().map(|node| write_node(node)).collect()
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
        WholeCollection => "".to_string(),
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
    fn negating() -> Result<()> {
        assert_eq!(r#"-("foo" AND "bar")"#, negate_search("foo bar").unwrap());
        assert_eq!(r#""foo""#, negate_search("-foo").unwrap());
        assert_eq!(r#"("foo")"#, negate_search("-(foo)").unwrap());
        assert_eq!("", negate_search("").unwrap());

        Ok(())
    }

    #[test]
    fn concatenating() -> Result<()> {
        assert_eq!(
            r#""foo" AND "bar""#,
            concatenate_searches(BoolSeparator::And, &["foo".to_string(), "bar".to_string()])
                .unwrap()
        );
        assert_eq!(
            r#""foo" OR "bar""#,
            concatenate_searches(
                BoolSeparator::Or,
                &["foo".to_string(), "".to_string(), "bar".to_string()]
            )
            .unwrap()
        );
        assert_eq!(
            "",
            concatenate_searches(BoolSeparator::Or, &["".to_string()]).unwrap()
        );
        assert_eq!("", concatenate_searches(BoolSeparator::Or, &[]).unwrap());

        Ok(())
    }

    #[test]
    fn replacing() -> Result<()> {
        assert_eq!(
            r#""deck:foo" AND "bar""#,
            replace_search_term("deck:baz bar", "deck:foo").unwrap()
        );
        assert_eq!(
            r#""tag:baz" OR "tag:baz""#,
            replace_search_term("tag:foo Or tag:bar", "tag:baz").unwrap()
        );
        assert_eq!(
            r#""bar" OR (-"bar" AND "tag:baz")"#,
            replace_search_term("foo or (-foo tag:baz)", "bar").unwrap()
        );
        assert_eq!(
            r#""is:due""#,
            replace_search_term("is:due", "-is:new").unwrap()
        );
        assert_eq!(
            r#""added:1""#,
            replace_search_term("added:1", "is:due").unwrap()
        );

        Ok(())
    }
}
