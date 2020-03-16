// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::parser::{Node, PropertyKind, SearchNode, StateKind};
use crate::{collection::RequestContext, types::ObjID};
use rusqlite::types::ToSqlOutput;
use std::fmt::Write;

struct SearchContext<'a> {
    ctx: &'a mut RequestContext<'a>,
    sql: String,
    args: Vec<ToSqlOutput<'a>>,
}

#[allow(dead_code)]
fn node_to_sql<'a>(
    ctx: &'a mut RequestContext<'a>,
    node: &'a Node,
) -> (String, Vec<ToSqlOutput<'a>>) {
    let sql = String::new();
    let args = vec![];
    let mut sctx = SearchContext { ctx, sql, args };
    write_node_to_sql(&mut sctx, node);
    (sctx.sql, sctx.args)
}

fn write_node_to_sql(ctx: &mut SearchContext, node: &Node) {
    match node {
        Node::And => write!(ctx.sql, " and ").unwrap(),
        Node::Or => write!(ctx.sql, " or ").unwrap(),
        Node::Not(node) => {
            write!(ctx.sql, "not ").unwrap();
            write_node_to_sql(ctx, node);
        }
        Node::Group(nodes) => {
            write!(ctx.sql, "(").unwrap();
            for node in nodes {
                write_node_to_sql(ctx, node);
            }
            write!(ctx.sql, ")").unwrap();
        }
        Node::Search(search) => write_search_node_to_sql(ctx, search),
    }
}

fn write_search_node_to_sql(ctx: &mut SearchContext, node: &SearchNode) {
    match node {
        SearchNode::UnqualifiedText(text) => write_unqualified(ctx, text),
        SearchNode::SingleField { field, text } => {
            write_single_field(ctx, field.as_ref(), text.as_ref())
        }
        SearchNode::AddedInDays(days) => {
            write!(ctx.sql, "c.id > {}", days).unwrap();
        }
        SearchNode::CardTemplate(template) => write_template(ctx, template.as_ref()),
        SearchNode::Deck(deck) => write_deck(ctx, deck.as_ref()),
        SearchNode::NoteTypeID(ntid) => {
            write!(ctx.sql, "n.mid = {}", ntid).unwrap();
        }
        SearchNode::NoteType(notetype) => write_note_type(ctx, notetype.as_ref()),
        SearchNode::Rated { days, ease } => write_rated(ctx, days, ease),
        SearchNode::Tag(tag) => write_tag(ctx, tag),
        SearchNode::Duplicates { note_type_id, text } => write_dupes(ctx, note_type_id, text),
        SearchNode::State(state) => write_state(ctx, state),
        SearchNode::Flag(flag) => {
            write!(ctx.sql, "(c.flags & 7) == {}", flag).unwrap();
        }
        SearchNode::NoteIDs(nids) => {
            write!(ctx.sql, "n.id in ({})", nids).unwrap();
        }
        SearchNode::CardIDs(cids) => {
            write!(ctx.sql, "c.id in ({})", cids).unwrap();
        }
        SearchNode::Property { operator, kind } => write_prop(ctx, operator, kind),
    }
}

fn write_unqualified(ctx: &mut SearchContext, text: &str) {
    // implicitly wrap in %
    let text = format!("%{}%", text);
    write!(
        ctx.sql,
        "(n.sfld like ? escape '\\' or n.flds like ? escape '\\')"
    )
    .unwrap();
    ctx.args.push(text.clone().into());
    ctx.args.push(text.into());
}

fn write_tag(ctx: &mut SearchContext, text: &str) {
    if text == "none" {
        write!(ctx.sql, "n.tags = ''").unwrap();
        return;
    }

    let tag = format!(" %{}% ", text.replace('*', "%"));
    write!(ctx.sql, "n.tags like ?").unwrap();
    ctx.args.push(tag.into());
}

// fixme: need day cutoff
fn write_rated(ctx: &mut SearchContext, days: &u32, ease: &Option<u8>) {}

// fixme: need current day
fn write_prop(ctx: &mut SearchContext, op: &str, kind: &PropertyKind) {}

// fixme: need db
fn write_dupes(ctx: &mut SearchContext, ntid: &ObjID, text: &str) {}

// fixme: need cutoff & current day
fn write_state(ctx: &mut SearchContext, state: &StateKind) {}

// fixme: need deck manager
fn write_deck(ctx: &mut SearchContext, deck: &str) {}

// fixme: need note type manager
fn write_template(ctx: &mut SearchContext, template: &str) {}

// fixme: need note type manager
fn write_note_type(ctx: &mut SearchContext, notetype: &str) {}

// fixme: need note type manager
fn write_single_field(ctx: &mut SearchContext, field: &str, val: &str) {}

#[cfg(test)]
mod test {
    use super::super::parser::parse;
    use super::*;

    // parse
    fn p(search: &str) -> Node {
        Node::Group(parse(search).unwrap())
    }

    // get sql
    // fn s<'a>(n: &'a Node) -> (String, Vec<ToSqlOutput<'a>>) {
    //     node_to_sql(n)
    // }

    #[test]
    fn tosql() -> Result<(), String> {
        // assert_eq!(s(&p("added:1")), ("(c.id > 1)".into(), vec![]));

        Ok(())
    }
}
