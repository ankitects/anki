// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::parser::{Node, PropertyKind, SearchNode, StateKind, TemplateKind};
use crate::card::CardQueue;
use crate::decks::child_ids;
use crate::decks::get_deck;
use crate::err::{AnkiError, Result};
use crate::notes::field_checksum;
use crate::notetype::NoteTypeID;
use crate::text::matches_wildcard;
use crate::text::without_combining;
use crate::{collection::Collection, text::strip_html_preserving_image_filenames};
use lazy_static::lazy_static;
use regex::{Captures, Regex};
use std::fmt::Write;

struct SqlWriter<'a> {
    col: &'a mut Collection,
    sql: String,
    args: Vec<String>,
}

pub(super) fn node_to_sql(req: &mut Collection, node: &Node) -> Result<(String, Vec<String>)> {
    let mut sctx = SqlWriter::new(req);
    sctx.write_node_to_sql(&node)?;
    Ok((sctx.sql, sctx.args))
}

impl SqlWriter<'_> {
    fn new(col: &mut Collection) -> SqlWriter<'_> {
        let sql = String::new();
        let args = vec![];
        SqlWriter { col, sql, args }
    }

    fn write_node_to_sql(&mut self, node: &Node) -> Result<()> {
        match node {
            Node::And => write!(self.sql, " and ").unwrap(),
            Node::Or => write!(self.sql, " or ").unwrap(),
            Node::Not(node) => {
                write!(self.sql, "not ").unwrap();
                self.write_node_to_sql(node)?;
            }
            Node::Group(nodes) => {
                write!(self.sql, "(").unwrap();
                for node in nodes {
                    self.write_node_to_sql(node)?;
                }
                write!(self.sql, ")").unwrap();
            }
            Node::Search(search) => self.write_search_node_to_sql(search)?,
        };
        Ok(())
    }

    fn write_search_node_to_sql(&mut self, node: &SearchNode) -> Result<()> {
        match node {
            SearchNode::UnqualifiedText(text) => self.write_unqualified(text),
            SearchNode::SingleField { field, text, is_re } => {
                self.write_single_field(field.as_ref(), text.as_ref(), *is_re)?
            }
            SearchNode::AddedInDays(days) => self.write_added(*days)?,
            SearchNode::CardTemplate(template) => self.write_template(template)?,
            SearchNode::Deck(deck) => self.write_deck(deck.as_ref())?,
            SearchNode::NoteTypeID(ntid) => {
                write!(self.sql, "n.mid = {}", ntid).unwrap();
            }
            SearchNode::NoteType(notetype) => self.write_note_type(notetype.as_ref())?,
            SearchNode::Rated { days, ease } => self.write_rated(*days, *ease)?,
            SearchNode::Tag(tag) => self.write_tag(tag)?,
            SearchNode::Duplicates { note_type_id, text } => self.write_dupes(*note_type_id, text),
            SearchNode::State(state) => self.write_state(state)?,
            SearchNode::Flag(flag) => {
                write!(self.sql, "(c.flags & 7) == {}", flag).unwrap();
            }
            SearchNode::NoteIDs(nids) => {
                write!(self.sql, "n.id in ({})", nids).unwrap();
            }
            SearchNode::CardIDs(cids) => {
                write!(self.sql, "c.id in ({})", cids).unwrap();
            }
            SearchNode::Property { operator, kind } => self.write_prop(operator, kind)?,
            SearchNode::WholeCollection => write!(self.sql, "true").unwrap(),
            SearchNode::Regex(re) => self.write_regex(re.as_ref()),
            SearchNode::NoCombining(text) => self.write_no_combining(text.as_ref()),
            SearchNode::WordBoundary(text) => self.write_word_boundary(text.as_ref()),
        };
        Ok(())
    }

    fn write_unqualified(&mut self, text: &str) {
        // implicitly wrap in %
        let text = format!("%{}%", text);
        self.args.push(text);
        write!(
            self.sql,
            "(n.sfld like ?{n} escape '\\' or n.flds like ?{n} escape '\\')",
            n = self.args.len(),
        )
        .unwrap();
    }

    fn write_no_combining(&mut self, text: &str) {
        let text = format!("%{}%", without_combining(text));
        self.args.push(text);
        write!(
            self.sql,
            concat!(
                "(coalesce(without_combining(cast(n.sfld as text)), n.sfld) like ?{n} escape '\\' ",
                "or coalesce(without_combining(n.flds), n.flds) like ?{n} escape '\\')"
            ),
            n = self.args.len(),
        )
        .unwrap();
    }

    fn write_tag(&mut self, text: &str) -> Result<()> {
        match text {
            "none" => {
                write!(self.sql, "n.tags = ''").unwrap();
            }
            "*" | "%" => {
                write!(self.sql, "true").unwrap();
            }
            text => {
                if let Some(re_glob) = glob_to_re(text) {
                    // text contains a wildcard
                    let re_glob = format!("(?i).* {} .*", re_glob);
                    write!(self.sql, "n.tags regexp ?").unwrap();
                    self.args.push(re_glob);
                } else if let Some(tag) = self.col.storage.preferred_tag_case(&text)? {
                    write!(self.sql, "n.tags like ?").unwrap();
                    self.args.push(format!("% {} %", tag));
                } else {
                    write!(self.sql, "false").unwrap();
                }
            }
        }
        Ok(())
    }

    fn write_rated(&mut self, days: u32, ease: Option<u8>) -> Result<()> {
        let today_cutoff = self.col.timing_today()?.next_day_at;
        let days = days.min(365) as i64;
        let target_cutoff_ms = (today_cutoff - 86_400 * days) * 1_000;
        write!(
            self.sql,
            "c.id in (select cid from revlog where id>{}",
            target_cutoff_ms
        )
        .unwrap();
        if let Some(ease) = ease {
            write!(self.sql, " and ease={})", ease).unwrap();
        } else {
            write!(self.sql, ")").unwrap();
        }

        Ok(())
    }

    fn write_prop(&mut self, op: &str, kind: &PropertyKind) -> Result<()> {
        let timing = self.col.timing_today()?;
        match kind {
            PropertyKind::Due(days) => {
                let day = days + (timing.days_elapsed as i32);
                write!(
                    self.sql,
                    "(c.queue in ({rev},{daylrn}) and due {op} {day})",
                    rev = CardQueue::Review as u8,
                    daylrn = CardQueue::DayLearn as u8,
                    op = op,
                    day = day
                )
            }
            PropertyKind::Interval(ivl) => write!(self.sql, "ivl {} {}", op, ivl),
            PropertyKind::Reps(reps) => write!(self.sql, "reps {} {}", op, reps),
            PropertyKind::Lapses(days) => write!(self.sql, "lapses {} {}", op, days),
            PropertyKind::Ease(ease) => {
                write!(self.sql, "factor {} {}", op, (ease * 1000.0) as u32)
            }
        }
        .unwrap();
        Ok(())
    }

    fn write_state(&mut self, state: &StateKind) -> Result<()> {
        let timing = self.col.timing_today()?;
        match state {
            StateKind::New => write!(self.sql, "c.type = {}", CardQueue::New as i8),
            StateKind::Review => write!(self.sql, "c.type = {}", CardQueue::Review as i8),
            StateKind::Learning => write!(
                self.sql,
                "c.queue in ({},{})",
                CardQueue::Learn as i8,
                CardQueue::DayLearn as i8
            ),
            StateKind::Buried => write!(
                self.sql,
                "c.queue in ({},{})",
                CardQueue::SchedBuried as i8,
                CardQueue::UserBuried as i8
            ),
            StateKind::Suspended => write!(self.sql, "c.queue = {}", CardQueue::Suspended as i8),
            StateKind::Due => write!(
                self.sql,
                "(
    (c.queue in ({rev},{daylrn}) and c.due <= {today}) or
    (c.queue = {lrn} and c.due <= {daycutoff})
    )",
                rev = CardQueue::Review as i8,
                daylrn = CardQueue::DayLearn as i8,
                today = timing.days_elapsed,
                lrn = CardQueue::Learn as i8,
                daycutoff = timing.next_day_at,
            ),
        }
        .unwrap();
        Ok(())
    }

    fn write_deck(&mut self, deck: &str) -> Result<()> {
        match deck {
            "*" => write!(self.sql, "true").unwrap(),
            "filtered" => write!(self.sql, "c.odid > 0").unwrap(),
            deck => {
                let all_decks: Vec<_> = self
                    .col
                    .storage
                    .get_all_decks()?
                    .into_iter()
                    .map(|(_, v)| v)
                    .collect();
                let dids_with_children = if deck == "current" {
                    let current_id = self.col.get_current_deck_id();
                    let mut dids_with_children = vec![current_id];
                    let current = get_deck(&all_decks, current_id)
                        .ok_or_else(|| AnkiError::invalid_input("invalid current deck"))?;
                    for child_did in child_ids(&all_decks, &current.name()) {
                        dids_with_children.push(child_did);
                    }
                    dids_with_children
                } else {
                    let mut dids_with_children = vec![];
                    for deck in all_decks
                        .iter()
                        .filter(|d| matches_wildcard(&d.name(), deck))
                    {
                        dids_with_children.push(deck.id());
                        for child_id in child_ids(&all_decks, &deck.name()) {
                            dids_with_children.push(child_id);
                        }
                    }
                    dids_with_children
                };

                self.sql.push_str("c.did in ");
                ids_to_string(&mut self.sql, &dids_with_children);
            }
        };
        Ok(())
    }

    fn write_template(&mut self, template: &TemplateKind) -> Result<()> {
        match template {
            TemplateKind::Ordinal(n) => {
                write!(self.sql, "c.ord = {}", n).unwrap();
            }
            TemplateKind::Name(name) => {
                let note_types = self.col.storage.get_all_notetypes()?;
                let mut id_ords = vec![];
                for nt in note_types.values() {
                    for tmpl in &nt.templates {
                        if matches_wildcard(&tmpl.name, name) {
                            id_ords.push((nt.id, tmpl.ord));
                        }
                    }
                }

                // sort for the benefit of unit tests
                id_ords.sort();

                if id_ords.is_empty() {
                    self.sql.push_str("false");
                } else {
                    let v: Vec<_> = id_ords
                        .iter()
                        .map(|(ntid, ord)| format!("(n.mid = {} and c.ord = {})", ntid, ord))
                        .collect();
                    write!(self.sql, "({})", v.join(" or ")).unwrap();
                }
            }
        };
        Ok(())
    }

    fn write_note_type(&mut self, nt_name: &str) -> Result<()> {
        let mut ntids: Vec<_> = self
            .col
            .storage
            .get_all_notetypes()?
            .values()
            .filter(|nt| matches_wildcard(&nt.name, nt_name))
            .map(|nt| nt.id)
            .collect();
        self.sql.push_str("n.mid in ");
        // sort for the benefit of unit tests
        ntids.sort();
        ids_to_string(&mut self.sql, &ntids);
        Ok(())
    }

    fn write_single_field(&mut self, field_name: &str, val: &str, is_re: bool) -> Result<()> {
        let note_types = self.col.storage.get_all_notetypes()?;

        let mut field_map = vec![];
        for nt in note_types.values() {
            for field in &nt.fields {
                if matches_wildcard(&field.name, field_name) {
                    field_map.push((nt.id, field.ord));
                }
            }
        }

        // for now, sort the map for the benefit of unit tests
        field_map.sort();

        if field_map.is_empty() {
            write!(self.sql, "false").unwrap();
            return Ok(());
        }

        let cmp;
        if is_re {
            cmp = "regexp";
            self.args.push(format!("(?i){}", val));
        } else {
            cmp = "like";
            self.args.push(val.replace('*', "%"));
        }

        let arg_idx = self.args.len();
        let searches: Vec<_> = field_map
            .iter()
            .map(|(ntid, ord)| {
                format!(
                    "(n.mid = {mid} and field_at_index(n.flds, {ord}) {cmp} ?{n})",
                    mid = ntid,
                    ord = ord,
                    cmp = cmp,
                    n = arg_idx
                )
            })
            .collect();
        write!(self.sql, "({})", searches.join(" or ")).unwrap();

        Ok(())
    }

    fn write_dupes(&mut self, ntid: NoteTypeID, text: &str) {
        let text_nohtml = strip_html_preserving_image_filenames(text);
        let csum = field_checksum(text_nohtml.as_ref());
        write!(
            self.sql,
            "(n.mid = {} and n.csum = {} and n.sfld = ?)",
            ntid, csum
        )
        .unwrap();
        self.args.push(text.to_string());
    }

    fn write_added(&mut self, days: u32) -> Result<()> {
        let timing = self.col.timing_today()?;
        let cutoff = (timing.next_day_at - (86_400 * (days as i64))) * 1_000;
        write!(self.sql, "c.id > {}", cutoff).unwrap();
        Ok(())
    }

    fn write_regex(&mut self, word: &str) {
        self.sql.push_str("n.flds regexp ?");
        self.args.push(format!(r"(?i){}", word));
    }

    fn write_word_boundary(&mut self, word: &str) {
        let re = glob_to_re(word).unwrap_or_else(|| word.to_string());
        self.write_regex(&format!(r"\b{}\b", re))
    }
}

// Write a list of IDs as '(x,y,...)' into the provided string.
fn ids_to_string<T>(buf: &mut String, ids: &[T])
where
    T: std::fmt::Display,
{
    buf.push('(');
    if !ids.is_empty() {
        for id in ids.iter().skip(1) {
            write!(buf, "{},", id).unwrap();
        }
        write!(buf, "{}", ids[0]).unwrap();
    }
    buf.push(')');
}

/// Convert a string with _, % or * characters into a regex.
fn glob_to_re(glob: &str) -> Option<String> {
    if !glob.contains(|c| c == '_' || c == '*' || c == '%') {
        return None;
    }

    lazy_static! {
        static ref ESCAPED: Regex = Regex::new(r"(\\\\)?\\\*").unwrap();
        static ref GLOB: Regex = Regex::new(r"(\\\\)?[_%]").unwrap();
    }

    let escaped = regex::escape(glob);

    let text = ESCAPED.replace_all(&escaped, |caps: &Captures| {
        if caps.get(0).unwrap().as_str().len() == 2 {
            ".*"
        } else {
            r"\*"
        }
    });

    let text2 = GLOB.replace_all(&text, |caps: &Captures| {
        match caps.get(0).unwrap().as_str() {
            "_" => ".",
            "%" => ".*",
            other => {
                // strip off the escaping char
                &other[2..]
            }
        }
        .to_string()
    });

    text2.into_owned().into()
}

#[cfg(test)]
mod test {
    use super::ids_to_string;
    use crate::{
        collection::{open_collection, Collection},
        i18n::I18n,
        log,
        types::Usn,
    };
    use std::{fs, path::PathBuf};
    use tempfile::tempdir;

    #[test]
    fn ids_string() {
        let mut s = String::new();
        ids_to_string::<u8>(&mut s, &[]);
        assert_eq!(s, "()");
        s.clear();
        ids_to_string(&mut s, &[7]);
        assert_eq!(s, "(7)");
        s.clear();
        ids_to_string(&mut s, &[7, 6]);
        assert_eq!(s, "(6,7)");
        s.clear();
        ids_to_string(&mut s, &[7, 6, 5]);
        assert_eq!(s, "(6,5,7)");
        s.clear();
    }

    use super::super::parser::parse;
    use super::*;

    // shortcut
    fn s(req: &mut Collection, search: &str) -> (String, Vec<String>) {
        let node = Node::Group(parse(search).unwrap());
        node_to_sql(req, &node).unwrap()
    }

    #[test]
    fn sql() -> Result<()> {
        // re-use the mediacheck .anki2 file for now
        use crate::media::check::test::MEDIACHECK_ANKI2;
        let dir = tempdir().unwrap();
        let col_path = dir.path().join("col.anki2");
        fs::write(&col_path, MEDIACHECK_ANKI2).unwrap();

        let i18n = I18n::new(&[""], "", log::terminal());
        let mut col = open_collection(
            &col_path,
            &PathBuf::new(),
            &PathBuf::new(),
            false,
            i18n,
            log::terminal(),
        )
        .unwrap();

        let ctx = &mut col;

        // unqualified search
        assert_eq!(
            s(ctx, "test"),
            (
                "((n.sfld like ?1 escape '\\' or n.flds like ?1 escape '\\'))".into(),
                vec!["%test%".into()]
            )
        );
        assert_eq!(s(ctx, "te%st").1, vec!["%te%st%".to_string()]);
        // user should be able to escape sql wildcards
        assert_eq!(s(ctx, r#"te\%s\_t"#).1, vec!["%te\\%s\\_t%".to_string()]);

        // qualified search
        assert_eq!(
            s(ctx, "front:te*st"),
            (
                concat!(
                    "(((n.mid = 1581236385344 and field_at_index(n.flds, 0) like ?1) or ",
                    "(n.mid = 1581236385345 and field_at_index(n.flds, 0) like ?1) or ",
                    "(n.mid = 1581236385346 and field_at_index(n.flds, 0) like ?1) or ",
                    "(n.mid = 1581236385347 and field_at_index(n.flds, 0) like ?1)))"
                )
                .into(),
                vec!["te%st".into()]
            )
        );

        // added
        let timing = ctx.timing_today().unwrap();
        assert_eq!(
            s(ctx, "added:3").0,
            format!("(c.id > {})", (timing.next_day_at - (86_400 * 3)) * 1_000)
        );

        // deck
        assert_eq!(s(ctx, "deck:default"), ("(c.did in (1))".into(), vec![],));
        assert_eq!(s(ctx, "deck:current"), ("(c.did in (1))".into(), vec![],));
        assert_eq!(s(ctx, "deck:missing"), ("(c.did in ())".into(), vec![],));
        assert_eq!(s(ctx, "deck:d*"), ("(c.did in (1))".into(), vec![],));
        assert_eq!(s(ctx, "deck:filtered"), ("(c.odid > 0)".into(), vec![],));

        // card
        assert_eq!(s(ctx, "card:front"), ("(false)".into(), vec![],));
        assert_eq!(
            s(ctx, r#""card:card 1""#),
            (
                concat!(
                    "(((n.mid = 1581236385344 and c.ord = 0) or ",
                    "(n.mid = 1581236385345 and c.ord = 0) or ",
                    "(n.mid = 1581236385346 and c.ord = 0) or ",
                    "(n.mid = 1581236385347 and c.ord = 0)))"
                )
                .into(),
                vec![],
            )
        );

        // IDs
        assert_eq!(s(ctx, "mid:3"), ("(n.mid = 3)".into(), vec![]));
        assert_eq!(s(ctx, "nid:3"), ("(n.id in (3))".into(), vec![]));
        assert_eq!(s(ctx, "nid:3,4"), ("(n.id in (3,4))".into(), vec![]));
        assert_eq!(s(ctx, "cid:3,4"), ("(c.id in (3,4))".into(), vec![]));

        // flags
        assert_eq!(s(ctx, "flag:2"), ("((c.flags & 7) == 2)".into(), vec![]));
        assert_eq!(s(ctx, "flag:0"), ("((c.flags & 7) == 0)".into(), vec![]));

        // dupes
        assert_eq!(
            s(ctx, "dupe:123,test"),
            (
                "((n.mid = 123 and n.csum = 2840236005 and n.sfld = ?))".into(),
                vec!["test".into()]
            )
        );

        // unregistered tag short circuits
        assert_eq!(s(ctx, r"tag:one"), ("(false)".into(), vec![]));

        // if registered, searches with canonical
        ctx.transact(None, |col| col.register_tag("One", Usn(-1)))
            .unwrap();
        assert_eq!(
            s(ctx, r"tag:one"),
            ("(n.tags like ?)".into(), vec![r"% One %".into()])
        );

        // wildcards force a regexp search
        assert_eq!(
            s(ctx, r"tag:o*n\*et%w\%oth_re\_e"),
            (
                "(n.tags regexp ?)".into(),
                vec![r"(?i).* o.*n\*et.*w%oth.re_e .*".into()]
            )
        );
        assert_eq!(s(ctx, "tag:none"), ("(n.tags = '')".into(), vec![]));
        assert_eq!(s(ctx, "tag:*"), ("(true)".into(), vec![]));

        // state
        assert_eq!(
            s(ctx, "is:suspended").0,
            format!("(c.queue = {})", CardQueue::Suspended as i8)
        );
        assert_eq!(
            s(ctx, "is:new").0,
            format!("(c.type = {})", CardQueue::New as i8)
        );

        // rated
        assert_eq!(
            s(ctx, "rated:2").0,
            format!(
                "(c.id in (select cid from revlog where id>{}))",
                (timing.next_day_at - (86_400 * 2)) * 1_000
            )
        );
        assert_eq!(
            s(ctx, "rated:400:1").0,
            format!(
                "(c.id in (select cid from revlog where id>{} and ease=1))",
                (timing.next_day_at - (86_400 * 365)) * 1_000
            )
        );

        // props
        assert_eq!(s(ctx, "prop:lapses=3").0, "(lapses = 3)".to_string());
        assert_eq!(s(ctx, "prop:ease>=2.5").0, "(factor >= 2500)".to_string());
        assert_eq!(
            s(ctx, "prop:due!=-1").0,
            format!(
                "((c.queue in (2,3) and due != {}))",
                timing.days_elapsed - 1
            )
        );

        // note types by name
        assert_eq!(&s(ctx, "note:basic").0, "(n.mid in (1581236385347))");
        assert_eq!(
            &s(ctx, "note:basic*").0,
            "(n.mid in (1581236385345,1581236385346,1581236385347,1581236385344))"
        );

        // regex
        assert_eq!(
            s(ctx, r"re:\bone"),
            ("(n.flds regexp ?)".into(), vec![r"(?i)\bone".into()])
        );

        // word boundary
        assert_eq!(
            s(ctx, r"w:foo"),
            ("(n.flds regexp ?)".into(), vec![r"(?i)\bfoo\b".into()])
        );
        assert_eq!(
            s(ctx, r"w:*foo"),
            ("(n.flds regexp ?)".into(), vec![r"(?i)\b.*foo\b".into()])
        );

        assert_eq!(
            s(ctx, r"w:*fo_o*"),
            ("(n.flds regexp ?)".into(), vec![r"(?i)\b.*fo.o.*\b".into()])
        );

        Ok(())
    }
}
