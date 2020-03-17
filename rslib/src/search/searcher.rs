// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::parser::{Node, PropertyKind, SearchNode, StateKind, TemplateKind};
use crate::card::CardQueue;
use crate::decks::child_ids;
use crate::decks::get_deck;
use crate::err::{AnkiError, Result};
use crate::notes::field_checksum;
use crate::text::matches_wildcard;
use crate::{
    collection::RequestContext, text::strip_html_preserving_image_filenames, types::ObjID,
};
use rusqlite::types::ToSqlOutput;
use std::fmt::Write;

struct SearchContext<'a> {
    #[allow(dead_code)]
    ctx: &'a mut RequestContext<'a>,
    sql: String,
    args: Vec<ToSqlOutput<'a>>,
}

#[allow(dead_code)]
fn node_to_sql<'a>(
    ctx: &'a mut RequestContext<'a>,
    node: &'a Node,
) -> Result<(String, Vec<ToSqlOutput<'a>>)> {
    let sql = String::new();
    let args = vec![];
    let mut sctx = SearchContext { ctx, sql, args };
    write_node_to_sql(&mut sctx, node)?;
    Ok((sctx.sql, sctx.args))
}

fn write_node_to_sql(ctx: &mut SearchContext, node: &Node) -> Result<()> {
    match node {
        Node::And => write!(ctx.sql, " and ").unwrap(),
        Node::Or => write!(ctx.sql, " or ").unwrap(),
        Node::Not(node) => {
            write!(ctx.sql, "not ").unwrap();
            write_node_to_sql(ctx, node)?;
        }
        Node::Group(nodes) => {
            write!(ctx.sql, "(").unwrap();
            for node in nodes {
                write_node_to_sql(ctx, node)?;
            }
            write!(ctx.sql, ")").unwrap();
        }
        Node::Search(search) => write_search_node_to_sql(ctx, search)?,
    };
    Ok(())
}

fn write_search_node_to_sql(ctx: &mut SearchContext, node: &SearchNode) -> Result<()> {
    match node {
        SearchNode::UnqualifiedText(text) => write_unqualified(ctx, text),
        SearchNode::SingleField { field, text } => {
            write_single_field(ctx, field.as_ref(), text.as_ref())
        }
        SearchNode::AddedInDays(days) => {
            write!(ctx.sql, "c.id > {}", days).unwrap();
        }
        SearchNode::CardTemplate(template) => write_template(ctx, template),
        SearchNode::Deck(deck) => write_deck(ctx, deck.as_ref())?,
        SearchNode::NoteTypeID(ntid) => {
            write!(ctx.sql, "n.mid = {}", ntid).unwrap();
        }
        SearchNode::NoteType(notetype) => write_note_type(ctx, notetype.as_ref()),
        SearchNode::Rated { days, ease } => write_rated(ctx, *days, *ease),
        SearchNode::Tag(tag) => write_tag(ctx, tag),
        SearchNode::Duplicates { note_type_id, text } => write_dupes(ctx, *note_type_id, text),
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
    };
    Ok(())
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
fn write_rated(ctx: &mut SearchContext, days: u32, ease: Option<u8>) {
    let today_cutoff = 0; // fixme
    let days = days.min(31);
    let target_cutoff = today_cutoff - 86_400 * days;
    write!(
        ctx.sql,
        "c.id in (select cid from revlog where id>{}",
        target_cutoff
    )
    .unwrap();
    if let Some(ease) = ease {
        write!(ctx.sql, "and ease={})", ease).unwrap();
    } else {
        write!(ctx.sql, ")").unwrap();
    }
}

// fixme: need current day
fn write_prop(ctx: &mut SearchContext, op: &str, kind: &PropertyKind) {
    match kind {
        PropertyKind::Due(days) => {
            let day = days; // fixme: + sched_today
            write!(
                ctx.sql,
                "(c.queue in ({rev},{daylrn}) and due {op} {day})",
                rev = CardQueue::Review as u8,
                daylrn = CardQueue::DayLearn as u8,
                op = op,
                day = day
            )
        }
        PropertyKind::Interval(ivl) => write!(ctx.sql, "ivl {} {}", op, ivl),
        PropertyKind::Reps(reps) => write!(ctx.sql, "reps {} {}", op, reps),
        PropertyKind::Lapses(days) => write!(ctx.sql, "lapses {} {}", op, days),
        PropertyKind::Ease(ease) => write!(ctx.sql, "ease {} {}", op, (ease * 1000.0) as u32),
    }
    .unwrap();
}

// fixme: need cutoff & current day
fn write_state(ctx: &mut SearchContext, state: &StateKind) {
    match state {
        StateKind::New => write!(ctx.sql, "c.queue = {}", CardQueue::New as u8),
        StateKind::Review => write!(ctx.sql, "c.queue = {}", CardQueue::Review as u8),
        StateKind::Learning => write!(
            ctx.sql,
            "c.queue in ({},{})",
            CardQueue::Learn as u8,
            CardQueue::DayLearn as u8
        ),
        StateKind::Buried => write!(
            ctx.sql,
            "c.queue in ({},{})",
            CardQueue::SchedBuried as u8,
            CardQueue::UserBuried as u8
        ),
        StateKind::Suspended => write!(ctx.sql, "c.queue = {}", CardQueue::Suspended as u8),
        StateKind::Due => {
            let today = 0; // fixme: today
            let day_cutoff = 0; // fixme: day_cutoff
            write!(
                ctx.sql,
                "
(c.queue in ({rev},{daylrn}) and c.due <= {today}) or
(c.queue = {lrn} and c.due <= {daycutoff})",
                rev = CardQueue::Review as u8,
                daylrn = CardQueue::DayLearn as u8,
                today = today,
                lrn = CardQueue::Learn as u8,
                daycutoff = day_cutoff,
            )
        }
    }
    .unwrap()
}

fn write_deck(ctx: &mut SearchContext, deck: &str) -> Result<()> {
    match deck {
        "*" => write!(ctx.sql, "true").unwrap(),
        "filtered" => write!(ctx.sql, "c.odid > 0").unwrap(),
        deck => {
            let all_decks = ctx.ctx.storage.all_decks()?;
            let dids_with_children = if deck == "current" {
                let config = ctx.ctx.storage.all_config()?;
                let mut dids_with_children = vec![config.current_deck_id];
                let current = get_deck(&all_decks, config.current_deck_id)
                    .ok_or_else(|| AnkiError::invalid_input("invalid current deck"))?;
                for child_did in child_ids(&all_decks, &current.name) {
                    dids_with_children.push(child_did);
                }
                dids_with_children
            } else {
                let mut dids_with_children = vec![];
                for deck in all_decks.iter().filter(|d| matches_wildcard(&d.name, deck)) {
                    dids_with_children.push(deck.id);
                    for child_id in child_ids(&all_decks, &deck.name) {
                        dids_with_children.push(child_id);
                    }
                }
                dids_with_children
            };

            if dids_with_children.is_empty() {
                write!(ctx.sql, "false")
            } else {
                let did_strings: Vec<String> =
                    dids_with_children.iter().map(ToString::to_string).collect();
                write!(ctx.sql, "c.did in ({})", did_strings.join(","))
            }
            .unwrap();
        }
    };
    Ok(())
}

// fixme: need note type manager
fn write_template(ctx: &mut SearchContext, template: &TemplateKind) {
    match template {
        TemplateKind::Ordinal(n) => {
            write!(ctx.sql, "c.ord = {}", n).unwrap();
        }
        TemplateKind::Name(_name) => {
            // fixme: search through note types loooking for template name
        }
    }
}

// fixme: need note type manager
fn write_note_type(ctx: &mut SearchContext, _notetype: &str) {
    let ntid: Option<ObjID> = None; // fixme: get id via name search
    if let Some(ntid) = ntid {
        write!(ctx.sql, "n.mid = {}", ntid).unwrap();
    } else {
        write!(ctx.sql, "false").unwrap();
    }
}

// fixme: need note type manager
fn write_single_field(ctx: &mut SearchContext, field: &str, val: &str) {
    let _ = field;
    let fields = vec![(0, 0)]; // fixme: get list of (ntid, ordinal)

    if fields.is_empty() {
        write!(ctx.sql, "false").unwrap();
        return;
    }

    write!(ctx.sql, "(").unwrap();
    ctx.args.push(val.to_string().into());
    let arg_idx = ctx.args.len();
    for (ntid, ord) in fields {
        write!(
            ctx.sql,
            "(n.mid = {} and field_at_index(n.flds, {}) like ?{})",
            ntid, ord, arg_idx
        )
        .unwrap();
    }
    write!(ctx.sql, ")").unwrap();
}

fn write_dupes(ctx: &mut SearchContext, ntid: ObjID, text: &str) {
    let text_nohtml = strip_html_preserving_image_filenames(text);
    let csum = field_checksum(text_nohtml.as_ref());
    write!(
        ctx.sql,
        "(n.mid = {} and n.csum = {} and field_at_index(n.flds, 0) = ?",
        ntid, csum
    )
    .unwrap();
    ctx.args.push(text.to_string().into())
}

#[cfg(test)]
mod test {
    // use super::super::parser::parse;
    // use super::*;

    // parse
    // fn p(search: &str) -> Node {
    //     Node::Group(parse(search).unwrap())
    // }

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
