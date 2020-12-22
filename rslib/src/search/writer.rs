// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    err::Result,
    decks::DeckID as DeckIDType,
    notetype::NoteTypeID as NoteTypeIDType,
    search::parser::{parse, Node, PropertyKind, SearchNode, StateKind, TemplateKind},
};

pub fn norm_search(input: &str) -> Result<String> {
    Ok(write_nodes(&parse(input)?))
}

fn write_nodes(nodes: &Vec<Node>) -> String {
    nodes.into_iter().map(|node| write_node(node)).collect()
}

fn write_node(node: &Node) -> String {
    use Node::*;
    match node {
        And => " ".to_string(),
        Or => " or ".to_string(),
        Not(n) => format!("-{}", write_node(n)),
        Group(ns) => format!("({})", write_nodes(ns)),
        Search(n) => write_search_node(n),
    }
}

fn write_search_node(node: &SearchNode) -> String {
    use SearchNode::*;
    match node {
        UnqualifiedText(s) => escape(s),
        SingleField { field, text, is_re } => write_single_field(field, text, *is_re),
        AddedInDays(u) => format!("added:{}", u),
        EditedInDays(u) => format!("edited:{}", u),
        CardTemplate(t) => write_template(t),
        Deck(s) => format!("deck:{}", &escape_suffix(s)),
        DeckID(DeckIDType(i)) => format!("did:{}", i),
        NoteTypeID(NoteTypeIDType(i)) => format!("mid:{}", i),
        NoteType(s) => format!("note:{}", &escape_suffix(s)),
        Rated { days, ease } => write_rated(days, ease),
        Tag(s) => format!("tag:{}", &escape_suffix(s)),
        Duplicates { note_type_id, text } => format!("dupes:{},{}", note_type_id, escape_suffix(text)),
        State(k) => write_state(k),
        Flag(u) => format!("flag:{}", u),
        NoteIDs(s) => format!("nid:{}", s),
        CardIDs(s) => format!("cid:{}", s),
        Property { operator, kind } => write_prop(operator, kind),
        WholeCollection => "".to_string(),
        Regex(s) => format!("re:{}", &escape_suffix(s)),
        NoCombining(s) => format!("re:{}", &escape_suffix(s)),
        WordBoundary(s) => format!("re:{}", &escape_suffix(s)),
    }
}

fn escape(txt: &str) -> String {
    let txt = txt.replace("\"", "\\\"").replace(":", "\\:");
    if txt.chars().any(|c| " \u{3000}()".contains(c)) {
        format!(r#""{}""#, txt)
    } else if txt.chars().next() == Some('-') {
        format!("\\{}", txt)
    } else {
        txt
    }
}

fn escape_suffix(txt: &str) -> String {
    let txt = txt.replace("\"", "\\\"");
    if txt.chars().any(|c| " \u{3000}()".contains(c)) {
        format!(r#""{}""#, txt)
    } else {
        txt
    }
}

fn write_single_field(field: &str, text: &str, is_re: bool) -> String {
    let field = field.replace(":", "\\:");
    let re = if is_re { "re:" } else { "" };
    let txt = format!("{}:{}{}", field, re, text).replace("\"", "\\\"");
    if txt.chars().any(|c| " \u{3000}()".contains(c)) {
        format!(r#""{}""#, txt)
    } else if txt.chars().next() == Some('-') {
        format!("\\{}", txt)
    } else {
        txt
    }
}

fn write_template(template: &TemplateKind) -> String {
    match template {
        TemplateKind::Ordinal(u) => format!("card:{}", u),
        TemplateKind::Name(s) => format!("card:{}", s),
    }
}

fn write_rated(days: &u32, ease: &Option<u8>) -> String {
    match ease {
        Some(u) => format!("rated:{}:{}", days, u),
        None => format!("rated:{}", days),
    }
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

fn write_prop(operator: &str, kind: &PropertyKind) -> String {
    use PropertyKind::*;
    match kind {
        Due(i) => format!("prop:due{}{}", operator, i),
        Interval(u) => format!("prop:ivl{}{}", operator, u),
        Reps(u) => format!("prop:reps{}{}", operator, u),
        Lapses(u) => format!("prop:lapses{}{}", operator, u),
        Ease(f) => format!("prop:ease{}{}", operator, f),
    }
}
