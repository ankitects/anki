// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::mem;
use std::sync::LazyLock;

use regex::Regex;

use crate::notetype::NotetypeId as NotetypeIdType;
use crate::prelude::*;
use crate::search::parser::parse;
use crate::search::parser::Node;
use crate::search::parser::PropertyKind;
use crate::search::parser::RatingKind;
use crate::search::parser::SearchNode;
use crate::search::parser::StateKind;
use crate::search::parser::TemplateKind;
use crate::text::escape_anki_wildcards;

/// Given an existing parsed search, if the provided `replacement` is a single
/// search node such as a deck:xxx search, replace any instances of that search
/// in `existing` with the new value. Then return the possibly modified first
/// search as a string.
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

pub(super) fn write_nodes(nodes: &[Node]) -> String {
    nodes.iter().map(write_node).collect()
}

#[allow(clippy::to_string_trait_impl)]
impl ToString for Node {
    fn to_string(&self) -> String {
        write_node(self)
    }
}

fn write_node(node: &Node) -> String {
    use Node::*;
    match node {
        And => " ".to_string(),
        Or => " OR ".to_string(),
        Not(n) => format!("-{}", write_node(n)),
        Group(ns) => format!("({})", write_nodes(ns)),
        Search(n) => write_search_node(n),
    }
}

fn write_search_node(node: &SearchNode) -> String {
    use SearchNode::*;
    match node {
        UnqualifiedText(s) => maybe_quote(&s.replace(':', "\\:")),
        SingleField {
            field,
            text,
            is_re,
            is_nc,
        } => write_single_field(field, text, *is_re, *is_nc),
        AddedInDays(u) => format!("added:{u}"),
        EditedInDays(u) => format!("edited:{u}"),
        IntroducedInDays(u) => format!("introduced:{u}"),
        CardTemplate(t) => write_template(t),
        Deck(s) => maybe_quote(&format!("deck:{s}")),
        DeckIdsWithoutChildren(s) => format!("did:{s}"),
        // not exposed on the GUI end
        DeckIdWithChildren(_) => "".to_string(),
        NotetypeId(NotetypeIdType(i)) => format!("mid:{i}"),
        Notetype(s) => maybe_quote(&format!("note:{s}")),
        Rated { days, ease } => write_rated(days, ease),
        Tag { tag, is_re, is_nc } => write_single_field("tag", tag, *is_re, *is_nc),
        Duplicates { notetype_id, text } => write_dupe(notetype_id, text),
        State(k) => write_state(k),
        Flag(u) => format!("flag:{u}"),
        NoteIds(s) => format!("nid:{s}"),
        CardIds(s) => format!("cid:{s}"),
        Property { operator, kind } => write_property(operator, kind),
        WholeCollection => "deck:*".to_string(),
        Regex(s) => maybe_quote(&format!("re:{s}")),
        NoCombining(s) => maybe_quote(&format!("nc:{s}")),
        StripClozes(s) => maybe_quote(&format!("sc:{s}")),
        WordBoundary(s) => maybe_quote(&format!("w:{s}")),
        CustomData(k) => maybe_quote(&format!("has-cd:{k}")),
        Preset(s) => maybe_quote(&format!("preset:{s}")),
    }
}

/// Escape double quotes and wrap in double quotes if necessary.
fn maybe_quote(txt: &str) -> String {
    if needs_quotation(txt) {
        format!("\"{}\"", txt.replace('\"', "\\\""))
    } else {
        txt.replace('\"', "\\\"")
    }
}

/// Checks for the reserved keywords "and" and "or", a prepended hyphen,
/// whitespace and brackets.
fn needs_quotation(txt: &str) -> bool {
    static RE: LazyLock<Regex> =
        LazyLock::new(|| Regex::new("(?i)^and$|^or$|^-.| |\u{3000}|\\(|\\)").unwrap());
    RE.is_match(txt)
}

/// Also used by tag search, which has the same syntax.
fn write_single_field(field: &str, text: &str, is_re: bool, is_nc: bool) -> String {
    let prefix = if is_re {
        "re:"
    } else if is_nc {
        "nc:"
    } else {
        ""
    };
    let text = if !is_re && !is_nc && (text.starts_with("re:") || text.starts_with("ne:")) {
        text.replacen(':', "\\:", 1)
    } else {
        text.to_string()
    };
    maybe_quote(&format!(
        "{}:{}{}",
        field.replace(':', "\\:"),
        prefix,
        &text
    ))
}

fn write_template(template: &TemplateKind) -> String {
    match template {
        TemplateKind::Ordinal(u) => format!("card:{}", u + 1),
        TemplateKind::Name(s) => maybe_quote(&format!("card:{s}")),
    }
}

fn write_rated(days: &u32, ease: &RatingKind) -> String {
    use RatingKind::*;
    match ease {
        AnswerButton(n) => format!("rated:{days}:{n}"),
        AnyAnswerButton => format!("rated:{days}"),
        ManualReschedule => format!("resched:{days}"),
    }
}

/// Escape double quotes and backslashes: \"
fn write_dupe(notetype_id: &NotetypeId, text: &str) -> String {
    let esc = text.replace('\\', r"\\");
    maybe_quote(&format!("dupe:{notetype_id},{esc}"))
}

fn write_state(kind: &StateKind) -> String {
    use StateKind::*;
    format!(
        "is:{}",
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
        Due(i) => format!("prop:due{operator}{i}"),
        Interval(u) => format!("prop:ivl{operator}{u}"),
        Reps(u) => format!("prop:reps{operator}{u}"),
        Lapses(u) => format!("prop:lapses{operator}{u}"),
        Ease(f) => format!("prop:ease{operator}{f}"),
        Position(u) => format!("prop:pos{operator}{u}"),
        Stability(u) => format!("prop:s{operator}{u}"),
        Difficulty(u) => format!("prop:d{operator}{u}"),
        Retrievability(u) => format!("prop:r{operator}{u}"),
        Rated(u, ease) => match ease {
            RatingKind::AnswerButton(val) => format!("prop:rated{operator}{u}:{val}"),
            RatingKind::AnyAnswerButton => format!("prop:rated{operator}{u}"),
            RatingKind::ManualReschedule => format!("prop:resched{operator}{u}"),
        },
        CustomDataNumber { key, value } => format!("prop:cdn:{key}{operator}{value}"),
        CustomDataString { key, value } => {
            maybe_quote(&format!("prop:cds:{key}{operator}{value}",))
        }
    }
}

pub(crate) fn deck_search(name: &str) -> String {
    write_nodes(&[Node::Search(SearchNode::Deck(escape_anki_wildcards(name)))])
}

/// Take an Anki-style search string and convert it into an equivalent
/// search string with normalized syntax.
pub(crate) fn normalize_search(input: &str) -> Result<String> {
    Ok(write_nodes(&parse(input)?))
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::error::Result;
    use crate::search::parse_search as parse;

    #[test]
    fn normalizing() {
        // remove redundant quotes
        assert_eq!(
            r#"foo "b a r""#,
            normalize_search(r#""foo" "b a r""#).unwrap()
        );
        assert_eq!("field:foo", normalize_search(r#"field:"foo""#).unwrap());
        // escape by quoting where possible
        assert_eq!(r#""(" ")""#, normalize_search(r"\( \)").unwrap());
        assert_eq!(r#""-foo""#, normalize_search(r"\-foo").unwrap());
        assert_eq!(r"\*\:\_", normalize_search(r"\*\:\_").unwrap());
        // remove redundant escapes
        assert_eq!("deck::", normalize_search(r"deck:\:").unwrap());
        assert_eq!("-", normalize_search(r"\-").unwrap());
        assert_eq!("--", normalize_search(r"-\-").unwrap());
        // ANDs are implicit, ORs in upper case
        assert_eq!("1 2 OR 3", normalize_search(r"1 and 2 or 3").unwrap());
        assert_eq!(r#""f o o" bar"#, normalize_search(r#""f o o"bar"#).unwrap());
        // AND and OR must be escaped regardless of case
        assert_eq!(r#""aNd" "oR""#, normalize_search(r#""aNd" "oR""#).unwrap());
        // normalize numbers
        assert_eq!("prop:ease>1", normalize_search("prop:ease>1.0").unwrap());
    }

    #[test]
    fn replacing() -> Result<()> {
        assert_eq!(
            replace_search_node(parse("deck:baz bar")?, parse("deck:foo")?.pop().unwrap()),
            "deck:foo bar",
        );
        assert_eq!(
            replace_search_node(
                parse("tag:foo Or tag:bar")?,
                parse("tag:baz")?.pop().unwrap()
            ),
            "tag:baz OR tag:baz",
        );
        assert_eq!(
            replace_search_node(
                parse("foo or (-foo tag:baz)")?,
                parse("bar")?.pop().unwrap()
            ),
            "bar OR (-bar tag:baz)",
        );
        assert_eq!(
            replace_search_node(parse("is:due")?, parse("-is:new")?.pop().unwrap()),
            "is:due"
        );
        assert_eq!(
            replace_search_node(parse("added:1")?, parse("is:due")?.pop().unwrap()),
            "added:1"
        );

        Ok(())
    }
}
