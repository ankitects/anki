// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, fmt::Write};

use super::{
    parser::{Node, PropertyKind, RatingKind, SearchNode, StateKind, TemplateKind},
    ReturnItemType,
};
use crate::{
    card::{CardQueue, CardType},
    collection::Collection,
    error::Result,
    notes::field_checksum,
    notetype::NotetypeId,
    prelude::*,
    storage::ids_to_string,
    text::{
        is_glob, matches_glob, normalize_to_nfc, strip_html_preserving_media_filenames,
        to_custom_re, to_re, to_sql, to_text, without_combining,
    },
    timestamp::TimestampSecs,
};

pub(crate) struct SqlWriter<'a> {
    col: &'a mut Collection,
    sql: String,
    item_type: ReturnItemType,
    args: Vec<String>,
    normalize_note_text: bool,
    table: RequiredTable,
}

impl SqlWriter<'_> {
    pub(crate) fn new(col: &mut Collection, item_type: ReturnItemType) -> SqlWriter<'_> {
        let normalize_note_text = col.get_config_bool(BoolKey::NormalizeNoteText);
        let sql = String::new();
        let args = vec![];
        SqlWriter {
            col,
            sql,
            item_type,
            args,
            normalize_note_text,
            table: item_type.required_table(),
        }
    }

    pub(super) fn build_query(
        mut self,
        node: &Node,
        table: RequiredTable,
    ) -> Result<(String, Vec<String>)> {
        self.table = self.table.combine(table.combine(node.required_table()));
        self.write_table_sql();
        self.write_node_to_sql(node)?;
        Ok((self.sql, self.args))
    }

    fn write_table_sql(&mut self) {
        let sql = match self.table {
            RequiredTable::Cards => "select c.id from cards c where ",
            RequiredTable::Notes => "select n.id from notes n where ",
            _ => match self.item_type {
                ReturnItemType::Cards => "select c.id from cards c, notes n where c.nid=n.id and ",
                ReturnItemType::Notes => {
                    "select distinct n.id from cards c, notes n where c.nid=n.id and "
                }
            },
        };
        self.sql.push_str(sql);
    }

    /// As an optimization we can omit the cards or notes tables from
    /// certain queries. For code that specifies a note id, we need to
    /// choose the appropriate column name.
    fn note_id_column(&self) -> &'static str {
        match self.table {
            RequiredTable::Notes | RequiredTable::CardsAndNotes => "n.id",
            RequiredTable::Cards => "c.nid",
            RequiredTable::CardsOrNotes => unreachable!(),
        }
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

    /// Convert search text to NFC if note normalization is enabled.
    fn norm_note<'a>(&self, text: &'a str) -> Cow<'a, str> {
        if self.normalize_note_text {
            normalize_to_nfc(text)
        } else {
            text.into()
        }
    }

    fn write_search_node_to_sql(&mut self, node: &SearchNode) -> Result<()> {
        use normalize_to_nfc as norm;
        match node {
            // note fields related
            SearchNode::UnqualifiedText(text) => self.write_unqualified(&self.norm_note(text)),
            SearchNode::SingleField { field, text, is_re } => {
                self.write_single_field(&norm(field), &self.norm_note(text), *is_re)?
            }
            SearchNode::Duplicates { notetype_id, text } => {
                self.write_dupe(*notetype_id, &self.norm_note(text))?
            }
            SearchNode::Regex(re) => self.write_regex(&self.norm_note(re)),
            SearchNode::NoCombining(text) => self.write_no_combining(&self.norm_note(text)),
            SearchNode::WordBoundary(text) => self.write_word_boundary(&self.norm_note(text)),

            // other
            SearchNode::AddedInDays(days) => self.write_added(*days)?,
            SearchNode::EditedInDays(days) => self.write_edited(*days)?,
            SearchNode::IntroducedInDays(days) => self.write_introduced(*days)?,
            SearchNode::CardTemplate(template) => match template {
                TemplateKind::Ordinal(_) => self.write_template(template),
                TemplateKind::Name(name) => {
                    self.write_template(&TemplateKind::Name(norm(name).into()))
                }
            },
            SearchNode::Deck(deck) => self.write_deck(&norm(deck))?,
            SearchNode::NotetypeId(ntid) => {
                write!(self.sql, "n.mid = {}", ntid).unwrap();
            }
            SearchNode::DeckIdWithoutChildren(did) => {
                write!(self.sql, "c.did = {}", did).unwrap();
            }
            SearchNode::DeckIdWithChildren(did) => self.write_deck_id_with_children(*did)?,
            SearchNode::Notetype(notetype) => self.write_notetype(&norm(notetype)),
            SearchNode::Rated { days, ease } => self.write_rated(">", -i64::from(*days), ease)?,

            SearchNode::Tag(tag) => self.write_tag(&norm(tag)),
            SearchNode::State(state) => self.write_state(state)?,
            SearchNode::Flag(flag) => {
                write!(self.sql, "(c.flags & 7) == {}", flag).unwrap();
            }
            SearchNode::NoteIds(nids) => {
                write!(self.sql, "{} in ({})", self.note_id_column(), nids).unwrap();
            }
            SearchNode::CardIds(cids) => {
                write!(self.sql, "c.id in ({})", cids).unwrap();
            }
            SearchNode::Property { operator, kind } => self.write_prop(operator, kind)?,
            SearchNode::WholeCollection => write!(self.sql, "true").unwrap(),
        };
        Ok(())
    }

    fn write_unqualified(&mut self, text: &str) {
        // implicitly wrap in %
        let text = format!("%{}%", &to_sql(text));
        self.args.push(text);
        write!(
            self.sql,
            "(n.sfld like ?{n} escape '\\' or n.flds like ?{n} escape '\\')",
            n = self.args.len(),
        )
        .unwrap();
    }

    fn write_no_combining(&mut self, text: &str) {
        let text = format!("%{}%", without_combining(&to_sql(text)));
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

    fn write_tag(&mut self, text: &str) {
        if text.contains(' ') {
            write!(self.sql, "false").unwrap();
        } else {
            match text {
                "none" => {
                    write!(self.sql, "n.tags = ''").unwrap();
                }
                "*" => {
                    write!(self.sql, "true").unwrap();
                }
                text => {
                    write!(self.sql, "n.tags regexp ?").unwrap();
                    let re = &to_custom_re(text, r"\S");
                    self.args.push(format!("(?i).* {}(::| ).*", re));
                }
            }
        }
    }

    fn write_rated(&mut self, op: &str, days: i64, ease: &RatingKind) -> Result<()> {
        let today_cutoff = self.col.timing_today()?.next_day_at;
        let target_cutoff_ms = today_cutoff.adding_secs(86_400 * days).as_millis();
        let day_before_cutoff_ms = today_cutoff.adding_secs(86_400 * (days - 1)).as_millis();

        write!(self.sql, "c.id in (select cid from revlog where id").unwrap();

        match op {
            ">" => write!(self.sql, " >= {}", target_cutoff_ms),
            ">=" => write!(self.sql, " >= {}", day_before_cutoff_ms),
            "<" => write!(self.sql, " < {}", day_before_cutoff_ms),
            "<=" => write!(self.sql, " < {}", target_cutoff_ms),
            "=" => write!(
                self.sql,
                " between {} and {}",
                day_before_cutoff_ms,
                target_cutoff_ms.0 - 1
            ),
            "!=" => write!(
                self.sql,
                " not between {} and {}",
                day_before_cutoff_ms,
                target_cutoff_ms.0 - 1
            ),
            _ => unreachable!("unexpected op"),
        }
        .unwrap();

        match ease {
            RatingKind::AnswerButton(u) => write!(self.sql, " and ease = {})", u),
            RatingKind::AnyAnswerButton => write!(self.sql, " and ease > 0)"),
            RatingKind::ManualReschedule => write!(self.sql, " and ease = 0)"),
        }
        .unwrap();

        Ok(())
    }

    fn write_prop(&mut self, op: &str, kind: &PropertyKind) -> Result<()> {
        let timing = self.col.timing_today()?;

        match kind {
            PropertyKind::Due(days) => {
                let day = days + (timing.days_elapsed as i32);
                write!(
                    self.sql,
                    // SQL does integer division if both parameters are integers
                    "(\
                    (c.queue in ({rev},{daylrn}) and c.due {op} {day}) or \
                    (c.queue in ({lrn},{previewrepeat}) and ((c.due - {cutoff}) / 86400) {op} {days})\
                    )",
                    rev = CardQueue::Review as u8,
                    daylrn = CardQueue::DayLearn as u8,
                    op = op,
                    day = day,
                    lrn = CardQueue::Learn as i8,
                    previewrepeat = CardQueue::PreviewRepeat as i8,
                    cutoff = timing.next_day_at,
                    days = days
                ).unwrap()
            }
            PropertyKind::Position(pos) => write!(
                self.sql,
                "(c.type = {t} and due {op} {pos})",
                t = CardType::New as u8,
                op = op,
                pos = pos
            )
            .unwrap(),
            PropertyKind::Interval(ivl) => write!(self.sql, "ivl {} {}", op, ivl).unwrap(),
            PropertyKind::Reps(reps) => write!(self.sql, "reps {} {}", op, reps).unwrap(),
            PropertyKind::Lapses(days) => write!(self.sql, "lapses {} {}", op, days).unwrap(),
            PropertyKind::Ease(ease) => {
                write!(self.sql, "factor {} {}", op, (ease * 1000.0) as u32).unwrap()
            }
            PropertyKind::Rated(days, ease) => self.write_rated(op, i64::from(*days), ease)?,
        }

        Ok(())
    }

    fn write_state(&mut self, state: &StateKind) -> Result<()> {
        let timing = self.col.timing_today()?;
        match state {
            StateKind::New => write!(self.sql, "c.type = {}", CardType::New as i8),
            StateKind::Review => write!(
                self.sql,
                "c.type in ({}, {})",
                CardType::Review as i8,
                CardType::Relearn as i8,
            ),
            StateKind::Learning => write!(
                self.sql,
                "c.type in ({}, {})",
                CardType::Learn as i8,
                CardType::Relearn as i8,
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
                "(\
                (c.queue in ({rev},{daylrn}) and c.due <= {today}) or \
                (c.queue in ({lrn},{previewrepeat}) and c.due <= {learncutoff})\
                )",
                rev = CardQueue::Review as i8,
                daylrn = CardQueue::DayLearn as i8,
                today = timing.days_elapsed,
                lrn = CardQueue::Learn as i8,
                previewrepeat = CardQueue::PreviewRepeat as i8,
                learncutoff = TimestampSecs::now().0 + (self.col.learn_ahead_secs() as i64),
            ),
            StateKind::UserBuried => write!(self.sql, "c.queue = {}", CardQueue::UserBuried as i8),
            StateKind::SchedBuried => {
                write!(self.sql, "c.queue = {}", CardQueue::SchedBuried as i8)
            }
        }
        .unwrap();
        Ok(())
    }

    fn write_deck(&mut self, deck: &str) -> Result<()> {
        match deck {
            "*" => write!(self.sql, "true").unwrap(),
            "filtered" => write!(self.sql, "c.odid != 0").unwrap(),
            deck => {
                // rewrite "current" to the current deck name
                let native_deck = if deck == "current" {
                    let current_did = self.col.get_current_deck_id();
                    regex::escape(
                        self.col
                            .storage
                            .get_deck(current_did)?
                            .map(|d| d.name)
                            .unwrap_or_else(|| NativeDeckName::from_native_str("Default"))
                            .as_native_str(),
                    )
                } else {
                    NativeDeckName::from_human_name(&to_re(deck))
                        .as_native_str()
                        .to_string()
                };

                // convert to a regex that includes child decks
                self.args.push(format!("(?i)^{}($|\x1f)", native_deck));
                let arg_idx = self.args.len();
                self.sql.push_str(&format!(concat!(
                    "(c.did in (select id from decks where name regexp ?{n})",
                    " or (c.odid != 0 and c.odid in (select id from decks where name regexp ?{n})))"),
                    n=arg_idx
                ));
            }
        };
        Ok(())
    }

    fn write_deck_id_with_children(&mut self, deck_id: DeckId) -> Result<()> {
        if let Some(parent) = self.col.get_deck(deck_id)? {
            let ids = self.col.storage.deck_id_with_children(&parent)?;
            let mut buf = String::new();
            ids_to_string(&mut buf, &ids);
            write!(self.sql, "c.did in {}", buf,).unwrap();
        } else {
            self.sql.push_str("false")
        }

        Ok(())
    }

    fn write_template(&mut self, template: &TemplateKind) {
        match template {
            TemplateKind::Ordinal(n) => {
                write!(self.sql, "c.ord = {}", n).unwrap();
            }
            TemplateKind::Name(name) => {
                if is_glob(name) {
                    let re = format!("(?i){}", to_re(name));
                    self.sql.push_str(
                        "(n.mid,c.ord) in (select ntid,ord from templates where name regexp ?)",
                    );
                    self.args.push(re);
                } else {
                    self.sql.push_str(
                        "(n.mid,c.ord) in (select ntid,ord from templates where name = ?)",
                    );
                    self.args.push(to_text(name).into());
                }
            }
        };
    }

    fn write_notetype(&mut self, nt_name: &str) {
        if is_glob(nt_name) {
            let re = format!("(?i){}", to_re(nt_name));
            self.sql
                .push_str("n.mid in (select id from notetypes where name regexp ?)");
            self.args.push(re);
        } else {
            self.sql
                .push_str("n.mid in (select id from notetypes where name = ?)");
            self.args.push(to_text(nt_name).into());
        }
    }

    fn write_single_field(&mut self, field_name: &str, val: &str, is_re: bool) -> Result<()> {
        let notetypes = self.col.get_all_notetypes()?;

        let mut field_map = vec![];
        for nt in notetypes.values() {
            for field in &nt.fields {
                if matches_glob(&field.name, field_name) {
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
        let cmp_trailer;
        if is_re {
            cmp = "regexp";
            cmp_trailer = "";
            self.args.push(format!("(?i){}", val));
        } else {
            cmp = "like";
            cmp_trailer = "escape '\\'";
            self.args.push(to_sql(val).into())
        }

        let arg_idx = self.args.len();
        let searches: Vec<_> = field_map
            .iter()
            .map(|(ntid, ord)| {
                format!(
                    "(n.mid = {mid} and field_at_index(n.flds, {ord}) {cmp} ?{n} {cmp_trailer})",
                    mid = ntid,
                    ord = ord.unwrap_or_default(),
                    cmp = cmp,
                    cmp_trailer = cmp_trailer,
                    n = arg_idx
                )
            })
            .collect();
        write!(self.sql, "({})", searches.join(" or ")).unwrap();

        Ok(())
    }

    fn write_dupe(&mut self, ntid: NotetypeId, text: &str) -> Result<()> {
        let text_nohtml = strip_html_preserving_media_filenames(text);
        let csum = field_checksum(text_nohtml.as_ref());

        let nids: Vec<_> = self
            .col
            .storage
            .note_fields_by_checksum(ntid, csum)?
            .into_iter()
            .filter_map(|(nid, field)| {
                if strip_html_preserving_media_filenames(&field) == text_nohtml {
                    Some(nid)
                } else {
                    None
                }
            })
            .collect();

        self.sql += "n.id in ";
        ids_to_string(&mut self.sql, &nids);

        Ok(())
    }

    fn previous_day_cutoff(&mut self, days_back: u32) -> Result<TimestampSecs> {
        let timing = self.col.timing_today()?;
        Ok(timing.next_day_at.adding_secs(-86_400 * days_back as i64))
    }

    fn write_added(&mut self, days: u32) -> Result<()> {
        let cutoff = self.previous_day_cutoff(days)?.as_millis();
        write!(self.sql, "c.id > {}", cutoff).unwrap();
        Ok(())
    }

    fn write_edited(&mut self, days: u32) -> Result<()> {
        let cutoff = self.previous_day_cutoff(days)?;
        write!(self.sql, "n.mod > {}", cutoff).unwrap();
        Ok(())
    }

    fn write_introduced(&mut self, days: u32) -> Result<()> {
        let cutoff = self.previous_day_cutoff(days)?.as_millis();
        write!(
            self.sql,
            concat!(
                "(select min(id) > {cutoff} from revlog where cid = c.id)",
                "and c.id in (select cid from revlog where id > {cutoff})"
            ),
            cutoff = cutoff,
        )
        .unwrap();
        Ok(())
    }

    fn write_regex(&mut self, word: &str) {
        self.sql.push_str("n.flds regexp ?");
        self.args.push(format!(r"(?i){}", word));
    }

    fn write_word_boundary(&mut self, word: &str) {
        self.write_regex(&format!(r"\b{}\b", to_re(word)));
    }
}

#[derive(Debug, PartialEq, Clone, Copy)]
pub enum RequiredTable {
    Notes,
    Cards,
    CardsAndNotes,
    CardsOrNotes,
}

impl RequiredTable {
    fn combine(self, other: RequiredTable) -> RequiredTable {
        match (self, other) {
            (RequiredTable::CardsAndNotes, _) => RequiredTable::CardsAndNotes,
            (_, RequiredTable::CardsAndNotes) => RequiredTable::CardsAndNotes,
            (RequiredTable::CardsOrNotes, b) => b,
            (a, RequiredTable::CardsOrNotes) => a,
            (a, b) => {
                if a == b {
                    a
                } else {
                    RequiredTable::CardsAndNotes
                }
            }
        }
    }
}

impl Node {
    fn required_table(&self) -> RequiredTable {
        match self {
            Node::And => RequiredTable::CardsOrNotes,
            Node::Or => RequiredTable::CardsOrNotes,
            Node::Not(node) => node.required_table(),
            Node::Group(nodes) => nodes.iter().fold(RequiredTable::CardsOrNotes, |cur, node| {
                cur.combine(node.required_table())
            }),
            Node::Search(node) => node.required_table(),
        }
    }
}

impl SearchNode {
    fn required_table(&self) -> RequiredTable {
        match self {
            SearchNode::AddedInDays(_) => RequiredTable::Cards,
            SearchNode::IntroducedInDays(_) => RequiredTable::Cards,
            SearchNode::Deck(_) => RequiredTable::Cards,
            SearchNode::DeckIdWithoutChildren(_) => RequiredTable::Cards,
            SearchNode::DeckIdWithChildren(_) => RequiredTable::Cards,
            SearchNode::Rated { .. } => RequiredTable::Cards,
            SearchNode::State(_) => RequiredTable::Cards,
            SearchNode::Flag(_) => RequiredTable::Cards,
            SearchNode::CardIds(_) => RequiredTable::Cards,
            SearchNode::Property { .. } => RequiredTable::Cards,

            SearchNode::UnqualifiedText(_) => RequiredTable::Notes,
            SearchNode::SingleField { .. } => RequiredTable::Notes,
            SearchNode::Tag(_) => RequiredTable::Notes,
            SearchNode::Duplicates { .. } => RequiredTable::Notes,
            SearchNode::Regex(_) => RequiredTable::Notes,
            SearchNode::NoCombining(_) => RequiredTable::Notes,
            SearchNode::WordBoundary(_) => RequiredTable::Notes,
            SearchNode::NotetypeId(_) => RequiredTable::Notes,
            SearchNode::Notetype(_) => RequiredTable::Notes,
            SearchNode::EditedInDays(_) => RequiredTable::Notes,

            SearchNode::NoteIds(_) => RequiredTable::CardsOrNotes,
            SearchNode::WholeCollection => RequiredTable::CardsOrNotes,

            SearchNode::CardTemplate(_) => RequiredTable::CardsAndNotes,
        }
    }
}

#[cfg(test)]
mod test {
    use std::{fs, path::PathBuf};

    use tempfile::tempdir;

    use super::{super::parser::parse, *};
    use crate::{
        collection::{open_collection, Collection},
        i18n::I18n,
        log,
    };

    // shortcut
    fn s(req: &mut Collection, search: &str) -> (String, Vec<String>) {
        let node = Node::Group(parse(search).unwrap());
        let mut writer = SqlWriter::new(req, ReturnItemType::Cards);
        writer.table = RequiredTable::Notes.combine(node.required_table());
        writer.write_node_to_sql(&node).unwrap();
        (writer.sql, writer.args)
    }

    #[test]
    fn sql() {
        // re-use the mediacheck .anki2 file for now
        use crate::media::check::test::MEDIACHECK_ANKI2;
        let dir = tempdir().unwrap();
        let col_path = dir.path().join("col.anki2");
        fs::write(&col_path, MEDIACHECK_ANKI2).unwrap();

        let tr = I18n::template_only();
        let mut col = open_collection(
            &col_path,
            &PathBuf::new(),
            &PathBuf::new(),
            false,
            tr,
            log::terminal(),
        )
        .unwrap();

        let ctx = &mut col;

        // unqualified search
        assert_eq!(
            s(ctx, "te*st"),
            (
                "((n.sfld like ?1 escape '\\' or n.flds like ?1 escape '\\'))".into(),
                vec!["%te%st%".into()]
            )
        );
        assert_eq!(s(ctx, "te%st").1, vec![r"%te\%st%".to_string()]);
        // user should be able to escape wildcards
        assert_eq!(s(ctx, r#"te\*s\_t"#).1, vec!["%te*s\\_t%".to_string()]);

        // qualified search
        assert_eq!(
            s(ctx, "front:te*st"),
            (
                concat!(
                    "(((n.mid = 1581236385344 and field_at_index(n.flds, 0) like ?1 escape '\\') or ",
                    "(n.mid = 1581236385345 and field_at_index(n.flds, 0) like ?1 escape '\\') or ",
                    "(n.mid = 1581236385346 and field_at_index(n.flds, 0) like ?1 escape '\\') or ",
                    "(n.mid = 1581236385347 and field_at_index(n.flds, 0) like ?1 escape '\\')))"
                )
                .into(),
                vec!["te%st".into()]
            )
        );

        // added
        let timing = ctx.timing_today().unwrap();
        assert_eq!(
            s(ctx, "added:3").0,
            format!("(c.id > {})", (timing.next_day_at.0 - (86_400 * 3)) * 1_000)
        );
        assert_eq!(s(ctx, "added:0").0, s(ctx, "added:1").0,);

        // introduced
        assert_eq!(
            s(ctx, "introduced:3").0,
            format!(
                concat!(
                    "((select min(id) > {cutoff} from revlog where cid = c.id)",
                    "and c.id in (select cid from revlog where id > {cutoff}))"
                ),
                cutoff = (timing.next_day_at.0 - (86_400 * 3)) * 1_000,
            )
        );
        assert_eq!(s(ctx, "introduced:0").0, s(ctx, "introduced:1").0,);

        // deck
        assert_eq!(
            s(ctx, "deck:default"),
            (
                "((c.did in (select id from decks where name regexp ?1) or (c.odid != 0 and \
                c.odid in (select id from decks where name regexp ?1))))"
                    .into(),
                vec!["(?i)^default($|\u{1f})".into()]
            )
        );
        assert_eq!(
            s(ctx, "deck:current").1,
            vec!["(?i)^Default($|\u{1f})".to_string()]
        );
        assert_eq!(s(ctx, "deck:d*").1, vec!["(?i)^d.*($|\u{1f})".to_string()]);
        assert_eq!(s(ctx, "deck:filtered"), ("(c.odid != 0)".into(), vec![],));

        // card
        assert_eq!(
            s(ctx, r#""card:card 1""#),
            (
                "((n.mid,c.ord) in (select ntid,ord from templates where name = ?))".into(),
                vec!["card 1".into()]
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
        assert_eq!(s(ctx, "dupe:123,test"), ("(n.id in ())".into(), vec![]));

        // tags
        assert_eq!(
            s(ctx, r"tag:one"),
            (
                "(n.tags regexp ?)".into(),
                vec!["(?i).* one(::| ).*".into()]
            )
        );
        assert_eq!(
            s(ctx, r"tag:foo::bar"),
            (
                "(n.tags regexp ?)".into(),
                vec!["(?i).* foo::bar(::| ).*".into()]
            )
        );

        assert_eq!(
            s(ctx, r"tag:o*n\*et%w%oth_re\_e"),
            (
                "(n.tags regexp ?)".into(),
                vec![r"(?i).* o\S*n\*et%w%oth\Sre_e(::| ).*".into()]
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
            format!("(c.type = {})", CardType::New as i8)
        );

        // rated
        assert_eq!(
            s(ctx, "rated:2").0,
            format!(
                "(c.id in (select cid from revlog where id >= {} and ease > 0))",
                (timing.next_day_at.0 - (86_400 * 2)) * 1_000
            )
        );
        assert_eq!(
            s(ctx, "rated:400:1").0,
            format!(
                "(c.id in (select cid from revlog where id >= {} and ease = 1))",
                (timing.next_day_at.0 - (86_400 * 400)) * 1_000
            )
        );
        assert_eq!(s(ctx, "rated:0").0, s(ctx, "rated:1").0);

        // resched
        assert_eq!(
            s(ctx, "resched:400").0,
            format!(
                "(c.id in (select cid from revlog where id >= {} and ease = 0))",
                (timing.next_day_at.0 - (86_400 * 400)) * 1_000
            )
        );

        // props
        assert_eq!(s(ctx, "prop:lapses=3").0, "(lapses = 3)".to_string());
        assert_eq!(s(ctx, "prop:ease>=2.5").0, "(factor >= 2500)".to_string());
        assert_eq!(
            s(ctx, "prop:due!=-1").0,
            format!(
                "(((c.queue in (2,3) and c.due != {days}) or (c.queue in (1,4) and ((c.due - {cutoff}) / 86400) != -1)))",
                days = timing.days_elapsed - 1,
                cutoff = timing.next_day_at
            )
        );
        assert_eq!(s(ctx, "prop:rated>-5:3").0, s(ctx, "rated:5:3").0);

        // note types by name
        assert_eq!(
            s(ctx, "note:basic"),
            (
                "(n.mid in (select id from notetypes where name = ?))".into(),
                vec!["basic".into()]
            )
        );
        assert_eq!(
            s(ctx, "note:basic*"),
            (
                "(n.mid in (select id from notetypes where name regexp ?))".into(),
                vec!["(?i)basic.*".into()]
            )
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
    }

    #[test]
    fn required_table() {
        assert_eq!(
            Node::Group(parse("").unwrap()).required_table(),
            RequiredTable::CardsOrNotes
        );
        assert_eq!(
            Node::Group(parse("test").unwrap()).required_table(),
            RequiredTable::Notes
        );
        assert_eq!(
            Node::Group(parse("cid:1").unwrap()).required_table(),
            RequiredTable::Cards
        );
        assert_eq!(
            Node::Group(parse("cid:1 test").unwrap()).required_table(),
            RequiredTable::CardsAndNotes
        );
        assert_eq!(
            Node::Group(parse("nid:1").unwrap()).required_table(),
            RequiredTable::CardsOrNotes
        );
        assert_eq!(
            Node::Group(parse("cid:1 nid:1").unwrap()).required_table(),
            RequiredTable::Cards
        );
        assert_eq!(
            Node::Group(parse("test nid:1").unwrap()).required_table(),
            RequiredTable::Notes
        );
    }
}
