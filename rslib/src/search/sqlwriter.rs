// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::fmt::Write;
use std::ops::Range;

use itertools::Itertools;

use super::parser::FieldSearchMode;
use super::parser::Node;
use super::parser::PropertyKind;
use super::parser::RatingKind;
use super::parser::SearchNode;
use super::parser::StateKind;
use super::parser::TemplateKind;
use super::ReturnItemType;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::collection::Collection;
use crate::error::Result;
use crate::notes::field_checksum;
use crate::notetype::NotetypeId;
use crate::prelude::*;
use crate::storage::ids_to_string;
use crate::storage::ProcessTextFlags;
use crate::text::glob_matcher;
use crate::text::is_glob;
use crate::text::normalize_to_nfc;
use crate::text::strip_html_preserving_media_filenames;
use crate::text::to_custom_re;
use crate::text::to_re;
use crate::text::to_sql;
use crate::text::to_text;
use crate::text::without_combining;
use crate::timestamp::TimestampSecs;

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

    // NOTE: when adding any new nodes in the future, make sure that they are either
    // a single search term, or they wrap multiple terms in parentheses, as can
    // be seen in the sql() unit test at the bottom of the file.
    fn write_search_node_to_sql(&mut self, node: &SearchNode) -> Result<()> {
        use normalize_to_nfc as norm;
        match node {
            // note fields related
            SearchNode::UnqualifiedText(text) => {
                let text = &self.norm_note(text);
                self.write_unqualified(
                    text,
                    self.col.get_config_bool(BoolKey::IgnoreAccentsInSearch),
                    false,
                )?
            }
            SearchNode::SingleField { field, text, mode } => {
                self.write_field(&norm(field), &self.norm_note(text), *mode)?
            }
            SearchNode::Duplicates { notetype_id, text } => {
                self.write_dupe(*notetype_id, &self.norm_note(text))?
            }
            SearchNode::Regex(re) => self.write_regex(&self.norm_note(re), false)?,
            SearchNode::NoCombining(text) => {
                self.write_unqualified(&self.norm_note(text), true, false)?
            }
            SearchNode::StripClozes(text) => self.write_unqualified(
                &self.norm_note(text),
                self.col.get_config_bool(BoolKey::IgnoreAccentsInSearch),
                true,
            )?,
            SearchNode::WordBoundary(text) => self.write_word_boundary(&self.norm_note(text))?,

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
                write!(self.sql, "n.mid = {ntid}").unwrap();
            }
            SearchNode::DeckIdsWithoutChildren(dids) => {
                write!(
                    self.sql,
                    "c.did in ({dids}) or (c.odid != 0 and c.odid in ({dids}))"
                )
                .unwrap();
            }
            SearchNode::DeckIdWithChildren(did) => self.write_deck_id_with_children(*did)?,
            SearchNode::Notetype(notetype) => self.write_notetype(&norm(notetype)),
            SearchNode::Rated { days, ease } => self.write_rated(">", -i64::from(*days), ease)?,

            SearchNode::Tag { tag, mode } => self.write_tag(&norm(tag), *mode),
            SearchNode::State(state) => self.write_state(state)?,
            SearchNode::Flag(flag) => {
                write!(self.sql, "(c.flags & 7) == {flag}").unwrap();
            }
            SearchNode::NoteIds(nids) => {
                write!(self.sql, "{} in ({})", self.note_id_column(), nids).unwrap();
            }
            SearchNode::CardIds(cids) => {
                write!(self.sql, "c.id in ({cids})").unwrap();
            }
            SearchNode::Property { operator, kind } => self.write_prop(operator, kind)?,
            SearchNode::CustomData(key) => self.write_custom_data(key)?,
            SearchNode::WholeCollection => write!(self.sql, "true").unwrap(),
            SearchNode::Preset(name) => self.write_deck_preset(name)?,
        };
        Ok(())
    }

    fn write_unqualified(
        &mut self,
        text: &str,
        no_combining: bool,
        strip_clozes: bool,
    ) -> Result<()> {
        let text = to_sql(text);
        let text = if no_combining {
            without_combining(&text)
        } else {
            text
        };
        // implicitly wrap in %
        let text = format!("%{text}%");
        self.args.push(text);
        let arg_idx = self.args.len();

        let mut process_text_flags = ProcessTextFlags::empty();
        if no_combining {
            process_text_flags.insert(ProcessTextFlags::NoCombining);
        }
        if strip_clozes {
            process_text_flags.insert(ProcessTextFlags::StripClozes);
        }

        let (sfld_expr, flds_expr) = if !process_text_flags.is_empty() {
            let bits = process_text_flags.bits();
            (
                Cow::from(format!(
                    "coalesce(process_text(cast(n.sfld as text), {bits}), n.sfld)"
                )),
                Cow::from(format!("coalesce(process_text(n.flds, {bits}), n.flds)")),
            )
        } else {
            (Cow::from("n.sfld"), Cow::from("n.flds"))
        };

        if strip_clozes {
            let cloze_notetypes_only_clause = self
                .col
                .get_all_notetypes()?
                .iter()
                .filter(|nt| nt.is_cloze())
                .map(|nt| format!("n.mid = {}", nt.id))
                .join(" or ");
            write!(self.sql, "({cloze_notetypes_only_clause}) and ").unwrap();
        }

        if let Some(field_indicies_by_notetype) = self.included_fields_by_notetype()? {
            let field_idx_str = format!("' || ?{arg_idx} || '");
            let other_idx_str = "%".to_string();

            let notetype_clause = |ctx: &UnqualifiedSearchContext| -> String {
                let field_index_clause = |range: &Range<u32>| {
                    let f = (0..ctx.total_fields_in_note)
                        .filter_map(|i| {
                            if i as u32 == range.start {
                                Some(&field_idx_str)
                            } else if range.contains(&(i as u32)) {
                                None
                            } else {
                                Some(&other_idx_str)
                            }
                        })
                        .join("\x1f");
                    format!("{flds_expr} like '{f}' escape '\\'")
                };
                let mut all_field_clauses: Vec<String> = ctx
                    .field_ranges_to_search
                    .iter()
                    .map(field_index_clause)
                    .collect();
                if !ctx.sortf_excluded {
                    all_field_clauses.push(format!("{sfld_expr} like ?{arg_idx} escape '\\'"));
                }
                format!(
                    "(n.mid = {mid} and ({all_field_clauses}))",
                    mid = ctx.ntid,
                    all_field_clauses = all_field_clauses.join(" or ")
                )
            };
            let all_notetype_clauses = field_indicies_by_notetype
                .iter()
                .map(notetype_clause)
                .join(" or ");
            write!(self.sql, "({all_notetype_clauses})").unwrap();
        } else {
            write!(
                self.sql,
                "({sfld_expr} like ?{arg_idx} escape '\\' or {flds_expr} like ?{arg_idx} escape '\\')"
            )
            .unwrap();
        }

        Ok(())
    }

    fn write_tag(&mut self, tag: &str, mode: FieldSearchMode) {
        if mode == FieldSearchMode::Regex {
            self.args.push(format!("(?i){tag}"));
            write!(self.sql, "regexp_tags(?{}, n.tags)", self.args.len()).unwrap();
        } else {
            match tag {
                "none" => {
                    write!(self.sql, "n.tags = ''").unwrap();
                }
                "*" => {
                    write!(self.sql, "true").unwrap();
                }
                s if s.contains(' ') => write!(self.sql, "false").unwrap(),
                text => {
                    write!(self.sql, "n.tags regexp ?").unwrap();
                    let re = &to_custom_re(text, r"\S");
                    self.args.push(format!("(?i).* {re}(::| ).*"));
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
            ">" => write!(self.sql, " >= {target_cutoff_ms}"),
            ">=" => write!(self.sql, " >= {day_before_cutoff_ms}"),
            "<" => write!(self.sql, " < {day_before_cutoff_ms}"),
            "<=" => write!(self.sql, " < {target_cutoff_ms}"),
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
            RatingKind::AnswerButton(u) => write!(self.sql, " and ease = {u})"),
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
                    (c.queue in ({rev},{daylrn}) and 
                        (case when c.odue != 0 then c.odue else c.due end) {op} {day}) or \
                    (c.queue in ({lrn},{previewrepeat}) and 
                        (((case when c.odue != 0 then c.odue else c.due end) - {cutoff}) / 86400) {op} {days})\
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
                "(c.type = {t} and (case when c.odue != 0 then c.odue else c.due end) {op} {pos})",
                t = CardType::New as u8,
                op = op,
                pos = pos
            )
            .unwrap(),
            PropertyKind::Interval(ivl) => write!(self.sql, "ivl {op} {ivl}").unwrap(),
            PropertyKind::Reps(reps) => write!(self.sql, "reps {op} {reps}").unwrap(),
            PropertyKind::Lapses(days) => write!(self.sql, "lapses {op} {days}").unwrap(),
            PropertyKind::Ease(ease) => {
                write!(self.sql, "factor {} {}", op, (ease * 1000.0) as u32).unwrap()
            }
            PropertyKind::Rated(days, ease) => self.write_rated(op, i64::from(*days), ease)?,
            PropertyKind::CustomDataNumber { key, value } => {
                write!(
                    self.sql,
                    "cast(extract_custom_data(c.data, '{key}') as float) {op} {value}"
                )
                .unwrap();
            }
            PropertyKind::CustomDataString { key, value } => {
                write!(
                    self.sql,
                    "extract_custom_data(c.data, '{key}') {op} '{value}'"
                )
                .unwrap();
            }
            PropertyKind::Stability(s) => {
                write!(self.sql, "extract_fsrs_variable(c.data, 's') {op} {s}").unwrap()
            }
            PropertyKind::Difficulty(d) => {
                let d = d * 9.0 + 1.0;
                write!(self.sql, "extract_fsrs_variable(c.data, 'd') {op} {d}").unwrap()
            }
            PropertyKind::Retrievability(r) => {
                let (elap, next_day_at, now) = {
                    let timing = self.col.timing_today()?;
                    (timing.days_elapsed, timing.next_day_at, timing.now)
                };
                write!(
                    self.sql,
                    "extract_fsrs_retrievability(c.data, case when c.odue !=0 then c.odue else c.due end, c.ivl, {elap}, {next_day_at}, {now}) {op} {r}"
                )
                .unwrap()
            }
        }

        Ok(())
    }

    fn write_custom_data(&mut self, key: &str) -> Result<()> {
        write!(self.sql, "extract_custom_data(c.data, '{key}') is not null").unwrap();

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
                    NativeDeckName::from_human_name(to_re(deck))
                        .as_native_str()
                        .to_string()
                };

                // convert to a regex that includes child decks
                self.args.push(format!("(?i)^{native_deck}($|\x1f)"));
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
            write!(self.sql, "c.did in {buf}",).unwrap();
        } else {
            self.sql.push_str("false")
        }

        Ok(())
    }

    fn write_template(&mut self, template: &TemplateKind) {
        match template {
            TemplateKind::Ordinal(n) => {
                write!(self.sql, "c.ord = {n}").unwrap();
            }
            TemplateKind::Name(name) => {
                if is_glob(name) {
                    let re = format!("(?i)^{}$", to_re(name));
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
            let re = format!("(?i)^{}$", to_re(nt_name));
            self.sql
                .push_str("n.mid in (select id from notetypes where name regexp ?)");
            self.args.push(re);
        } else {
            self.sql
                .push_str("n.mid in (select id from notetypes where name = ?)");
            self.args.push(to_text(nt_name).into());
        }
    }

    fn write_field(&mut self, field_name: &str, val: &str, mode: FieldSearchMode) -> Result<()> {
        if matches!(field_name, "*" | "_*" | "*_") {
            if mode == FieldSearchMode::Regex {
                self.write_all_fields_regexp(val);
            } else {
                self.write_all_fields(val);
            }
            Ok(())
        } else if mode == FieldSearchMode::Regex {
            self.write_single_field_regexp(field_name, val)
        } else if mode == FieldSearchMode::NoCombining {
            self.write_single_field_nc(field_name, val)
        } else {
            self.write_single_field(field_name, val)
        }
    }

    fn write_all_fields_regexp(&mut self, val: &str) {
        self.args.push(format!("(?i){val}"));
        write!(self.sql, "regexp_fields(?{}, n.flds)", self.args.len()).unwrap();
    }

    fn write_all_fields(&mut self, val: &str) {
        self.args.push(format!("(?is)^{}$", to_re(val)));
        write!(self.sql, "regexp_fields(?{}, n.flds)", self.args.len()).unwrap();
    }

    fn write_single_field_nc(&mut self, field_name: &str, val: &str) -> Result<()> {
        let field_indicies_by_notetype = self.num_fields_and_fields_indices_by_notetype(
            field_name,
            matches!(val, "*" | "_*" | "*_"),
        )?;
        if field_indicies_by_notetype.is_empty() {
            write!(self.sql, "false").unwrap();
            return Ok(());
        }

        let val = to_sql(val);
        let val = without_combining(&val);
        self.args.push(val.into());
        let arg_idx = self.args.len();
        let field_idx_str = format!("' || ?{arg_idx} || '");
        let other_idx_str = "%".to_string();

        let notetype_clause = |ctx: &FieldQualifiedSearchContext| -> String {
            let field_index_clause = |range: &Range<u32>| {
                let f = (0..ctx.total_fields_in_note)
                    .filter_map(|i| {
                        if i as u32 == range.start {
                            Some(&field_idx_str)
                        } else if range.contains(&(i as u32)) {
                            None
                        } else {
                            Some(&other_idx_str)
                        }
                    })
                    .join("\x1f");
                format!(
                    "coalesce(process_text(n.flds, {}), n.flds) like '{f}' escape '\\'",
                    ProcessTextFlags::NoCombining.bits()
                )
            };

            let all_field_clauses = ctx
                .field_ranges_to_search
                .iter()
                .map(field_index_clause)
                .join(" or ");
            format!("(n.mid = {mid} and ({all_field_clauses}))", mid = ctx.ntid)
        };
        let all_notetype_clauses = field_indicies_by_notetype
            .iter()
            .map(notetype_clause)
            .join(" or ");
        write!(self.sql, "({all_notetype_clauses})").unwrap();

        Ok(())
    }

    fn write_single_field_regexp(&mut self, field_name: &str, val: &str) -> Result<()> {
        let field_indicies_by_notetype = self.fields_indices_by_notetype(field_name)?;
        if field_indicies_by_notetype.is_empty() {
            write!(self.sql, "false").unwrap();
            return Ok(());
        }

        self.args.push(format!("(?i){val}"));
        let arg_idx = self.args.len();

        let all_notetype_clauses = field_indicies_by_notetype
            .iter()
            .map(|(mid, field_indices)| {
                let field_index_list = field_indices.iter().join(", ");
                format!("(n.mid = {mid} and regexp_fields(?{arg_idx}, n.flds, {field_index_list}))")
            })
            .join(" or ");

        write!(self.sql, "({all_notetype_clauses})").unwrap();

        Ok(())
    }

    fn write_single_field(&mut self, field_name: &str, val: &str) -> Result<()> {
        let field_indicies_by_notetype = self.num_fields_and_fields_indices_by_notetype(
            field_name,
            matches!(val, "*" | "_*" | "*_"),
        )?;
        if field_indicies_by_notetype.is_empty() {
            write!(self.sql, "false").unwrap();
            return Ok(());
        }

        self.args.push(to_sql(val).into());
        let arg_idx = self.args.len();
        let field_idx_str = format!("' || ?{arg_idx} || '");
        let other_idx_str = "%".to_string();

        let notetype_clause = |ctx: &FieldQualifiedSearchContext| -> String {
            let field_index_clause = |range: &Range<u32>| {
                let f = (0..ctx.total_fields_in_note)
                    .filter_map(|i| {
                        if i as u32 == range.start {
                            Some(&field_idx_str)
                        } else if range.contains(&(i as u32)) {
                            None
                        } else {
                            Some(&other_idx_str)
                        }
                    })
                    .join("\x1f");
                format!("n.flds like '{f}' escape '\\'")
            };

            let all_field_clauses = ctx
                .field_ranges_to_search
                .iter()
                .map(field_index_clause)
                .join(" or ");
            format!("(n.mid = {mid} and ({all_field_clauses}))", mid = ctx.ntid)
        };
        let all_notetype_clauses = field_indicies_by_notetype
            .iter()
            .map(notetype_clause)
            .join(" or ");
        write!(self.sql, "({all_notetype_clauses})").unwrap();

        Ok(())
    }

    fn num_fields_and_fields_indices_by_notetype(
        &mut self,
        field_name: &str,
        test_for_nonempty: bool,
    ) -> Result<Vec<FieldQualifiedSearchContext>> {
        let matches_glob = glob_matcher(field_name);

        let mut field_map = vec![];
        for nt in self.col.get_all_notetypes()? {
            let matched_fields = nt
                .fields
                .iter()
                .filter(|&field| matches_glob(&field.name))
                .map(|field| field.ord.unwrap_or_default())
                .collect_ranges(!test_for_nonempty);
            if !matched_fields.is_empty() {
                field_map.push(FieldQualifiedSearchContext {
                    ntid: nt.id,
                    total_fields_in_note: nt.fields.len(),
                    field_ranges_to_search: matched_fields,
                });
            }
        }

        // for now, sort the map for the benefit of unit tests
        field_map.sort_by_key(|v| v.ntid);

        Ok(field_map)
    }

    fn fields_indices_by_notetype(
        &mut self,
        field_name: &str,
    ) -> Result<Vec<(NotetypeId, Vec<u32>)>> {
        let matches_glob = glob_matcher(field_name);

        let mut field_map = vec![];
        for nt in self.col.get_all_notetypes()? {
            let matched_fields: Vec<u32> = nt
                .fields
                .iter()
                .filter(|&field| matches_glob(&field.name))
                .map(|field| field.ord.unwrap_or_default())
                .collect();
            if !matched_fields.is_empty() {
                field_map.push((nt.id, matched_fields));
            }
        }

        // for now, sort the map for the benefit of unit tests
        field_map.sort();

        Ok(field_map)
    }

    fn included_fields_by_notetype(&mut self) -> Result<Option<Vec<UnqualifiedSearchContext>>> {
        let mut any_excluded = false;
        let mut field_map = vec![];
        for nt in self.col.get_all_notetypes()? {
            let mut sortf_excluded = false;
            let matched_fields = nt
                .fields
                .iter()
                .filter_map(|field| {
                    let ord = field.ord.unwrap_or_default();
                    if field.config.exclude_from_search {
                        any_excluded = true;
                        sortf_excluded |= ord == nt.config.sort_field_idx;
                    }
                    (!field.config.exclude_from_search).then_some(ord)
                })
                .collect_ranges(true);
            if !matched_fields.is_empty() {
                field_map.push(UnqualifiedSearchContext {
                    ntid: nt.id,
                    total_fields_in_note: nt.fields.len(),
                    sortf_excluded,
                    field_ranges_to_search: matched_fields,
                });
            }
        }
        if any_excluded {
            Ok(Some(field_map))
        } else {
            Ok(None)
        }
    }

    fn included_fields_for_unqualified_regex(
        &mut self,
    ) -> Result<Option<Vec<UnqualifiedRegexSearchContext>>> {
        let mut any_excluded = false;
        let mut field_map = vec![];
        for nt in self.col.get_all_notetypes()? {
            let matched_fields: Vec<u32> = nt
                .fields
                .iter()
                .filter_map(|field| {
                    any_excluded |= field.config.exclude_from_search;
                    (!field.config.exclude_from_search).then_some(field.ord.unwrap_or_default())
                })
                .collect();
            field_map.push(UnqualifiedRegexSearchContext {
                ntid: nt.id,
                total_fields_in_note: nt.fields.len(),
                fields_to_search: matched_fields,
            });
        }
        if any_excluded {
            Ok(Some(field_map))
        } else {
            Ok(None)
        }
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
        write!(self.sql, "c.id > {cutoff}").unwrap();
        Ok(())
    }

    fn write_edited(&mut self, days: u32) -> Result<()> {
        let cutoff = self.previous_day_cutoff(days)?;
        write!(self.sql, "n.mod > {cutoff}").unwrap();
        Ok(())
    }

    fn write_introduced(&mut self, days: u32) -> Result<()> {
        let cutoff = self.previous_day_cutoff(days)?.as_millis();
        write!(
            self.sql,
            concat!(
                "((SELECT coalesce(min(id) > {cutoff}, false) FROM revlog WHERE cid = c.id ",
                // Exclude manual reschedulings
                "AND ease != 0) ",
                // Logically redundant, speeds up query
                "AND c.id IN (SELECT cid FROM revlog WHERE id > {cutoff}))"
            ),
            cutoff = cutoff,
        )
        .unwrap();
        Ok(())
    }

    fn write_regex(&mut self, word: &str, no_combining: bool) -> Result<()> {
        let flds_expr = if no_combining {
            Cow::from(format!(
                "coalesce(process_text(n.flds, {}), n.flds)",
                ProcessTextFlags::NoCombining.bits()
            ))
        } else {
            Cow::from("n.flds")
        };
        let word = if no_combining {
            without_combining(word)
        } else {
            std::borrow::Cow::Borrowed(word)
        };
        self.args.push(format!(r"(?i){word}"));
        let arg_idx = self.args.len();
        if let Some(field_indices_by_notetype) = self.included_fields_for_unqualified_regex()? {
            let notetype_clause = |ctx: &UnqualifiedRegexSearchContext| -> String {
                let clause = if ctx.fields_to_search.len() == ctx.total_fields_in_note {
                    format!("{flds_expr} regexp ?{arg_idx}")
                } else {
                    let indices = ctx.fields_to_search.iter().join(",");
                    format!("regexp_fields(?{arg_idx}, {flds_expr}, {indices})")
                };

                format!("(n.mid = {mid} and {clause})", mid = ctx.ntid)
            };
            let all_notetype_clauses = field_indices_by_notetype
                .iter()
                .map(notetype_clause)
                .join(" or ");
            write!(self.sql, "({all_notetype_clauses})").unwrap();
        } else {
            write!(self.sql, "{flds_expr} regexp ?{arg_idx}").unwrap();
        }

        Ok(())
    }

    fn write_word_boundary(&mut self, word: &str) -> Result<()> {
        let re = format!(r"\b{}\b", to_re(word));
        self.write_regex(
            &re,
            self.col.get_config_bool(BoolKey::IgnoreAccentsInSearch),
        )
    }

    fn write_deck_preset(&mut self, name: &str) -> Result<()> {
        let dcid = self.col.storage.get_deck_config_id_by_name(name)?;
        if dcid.is_none() {
            write!(self.sql, "false").unwrap();
            return Ok(());
        };

        let mut str_ids = String::new();
        let deck_ids = self
            .col
            .storage
            .get_all_decks()?
            .into_iter()
            .filter_map(|d| {
                if d.config_id() == dcid {
                    Some(d.id)
                } else {
                    None
                }
            });
        ids_to_string(&mut str_ids, deck_ids);
        write!(self.sql, "(c.did in {str_ids} or c.odid in {str_ids})").unwrap();
        Ok(())
    }
}

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
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

/// Given a list of numbers, create one or more ranges, collapsing
/// contiguous numbers.
trait CollectRanges {
    type Item;
    fn collect_ranges(self, join: bool) -> Vec<Range<Self::Item>>;
}

impl<
        Idx: Copy + PartialOrd + std::ops::Add<Idx, Output = Idx> + From<u8>,
        I: IntoIterator<Item = Idx>,
    > CollectRanges for I
{
    type Item = Idx;

    fn collect_ranges(self, join: bool) -> Vec<Range<Self::Item>> {
        let mut result = Vec::new();
        let mut iter = self.into_iter();
        let next = iter.next();
        if next.is_none() {
            return result;
        }
        let mut start = next.unwrap();
        let mut end = next.unwrap();

        for i in iter {
            if join && i == end + 1.into() {
                end = end + 1.into();
            } else {
                result.push(start..end + 1.into());
                start = i;
                end = i;
            }
        }
        result.push(start..end + 1.into());

        result
    }
}

struct FieldQualifiedSearchContext {
    ntid: NotetypeId,
    total_fields_in_note: usize,
    /// This may include more than one field in the case the user
    /// has searched with a wildcard, eg f*:foo.
    field_ranges_to_search: Vec<Range<u32>>,
}

struct UnqualifiedSearchContext {
    ntid: NotetypeId,
    total_fields_in_note: usize,
    sortf_excluded: bool,
    field_ranges_to_search: Vec<Range<u32>>,
}

struct UnqualifiedRegexSearchContext {
    ntid: NotetypeId,
    total_fields_in_note: usize,
    /// Unlike the other contexts, this contains each individual index
    /// instead of a list of ranges.
    fields_to_search: Vec<u32>,
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
            SearchNode::DeckIdsWithoutChildren(_) => RequiredTable::Cards,
            SearchNode::DeckIdWithChildren(_) => RequiredTable::Cards,
            SearchNode::Rated { .. } => RequiredTable::Cards,
            SearchNode::State(_) => RequiredTable::Cards,
            SearchNode::Flag(_) => RequiredTable::Cards,
            SearchNode::CardIds(_) => RequiredTable::Cards,
            SearchNode::Property { .. } => RequiredTable::Cards,
            SearchNode::CustomData { .. } => RequiredTable::Cards,
            SearchNode::Preset(_) => RequiredTable::Cards,

            SearchNode::UnqualifiedText(_) => RequiredTable::Notes,
            SearchNode::SingleField { .. } => RequiredTable::Notes,
            SearchNode::Tag { .. } => RequiredTable::Notes,
            SearchNode::Duplicates { .. } => RequiredTable::Notes,
            SearchNode::Regex(_) => RequiredTable::Notes,
            SearchNode::NoCombining(_) => RequiredTable::Notes,
            SearchNode::StripClozes(_) => RequiredTable::Notes,
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
    use anki_io::write_file;
    use tempfile::tempdir;

    use super::super::parser::parse;
    use super::*;
    use crate::collection::Collection;
    use crate::collection::CollectionBuilder;

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
        write_file(&col_path, MEDIACHECK_ANKI2).unwrap();

        let mut col = CollectionBuilder::new(col_path).build().unwrap();
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
        assert_eq!(s(ctx, r"te\*s\_t").1, vec!["%te*s\\_t%".to_string()]);

        // field search
        assert_eq!(
            s(ctx, "front:te*st"),
            (
                concat!(
                    "(((n.mid = 1581236385344 and (n.flds like '' || ?1 || '\u{1f}%' escape '\\')) or ",
                    "(n.mid = 1581236385345 and (n.flds like '' || ?1 || '\u{1f}%\u{1f}%' escape '\\')) or ",
                    "(n.mid = 1581236385346 and (n.flds like '' || ?1 || '\u{1f}%' escape '\\')) or ",
                    "(n.mid = 1581236385347 and (n.flds like '' || ?1 || '\u{1f}%' escape '\\'))))"
                )
                .into(),
                vec!["te%st".into()]
            )
        );
        // field search with regex
        assert_eq!(
            s(ctx, "front:re:te.*st"),
            (
                concat!(
                    "(((n.mid = 1581236385344 and regexp_fields(?1, n.flds, 0)) or ",
                    "(n.mid = 1581236385345 and regexp_fields(?1, n.flds, 0)) or ",
                    "(n.mid = 1581236385346 and regexp_fields(?1, n.flds, 0)) or ",
                    "(n.mid = 1581236385347 and regexp_fields(?1, n.flds, 0))))"
                )
                .into(),
                vec!["(?i)te.*st".into()]
            )
        );
        // field search with no-combine
        assert_eq!(
            s(ctx, "front:nc:frânçais"),
            (
                concat!(
                    "(((n.mid = 1581236385344 and (coalesce(process_text(n.flds, 1), n.flds) like '' || ?1 || '\u{1f}%' escape '\\')) or ",
                    "(n.mid = 1581236385345 and (coalesce(process_text(n.flds, 1), n.flds) like '' || ?1 || '\u{1f}%\u{1f}%' escape '\\')) or ",
                    "(n.mid = 1581236385346 and (coalesce(process_text(n.flds, 1), n.flds) like '' || ?1 || '\u{1f}%' escape '\\')) or ",
                    "(n.mid = 1581236385347 and (coalesce(process_text(n.flds, 1), n.flds) like '' || ?1 || '\u{1f}%' escape '\\'))))"
                )
                .into(),
                vec!["francais".into()]
            )
        );
        // all field search
        assert_eq!(
            s(ctx, "*:te*st"),
            (
                "(regexp_fields(?1, n.flds))".into(),
                vec!["(?is)^te.*st$".into()]
            )
        );
        // all field search with regex
        assert_eq!(
            s(ctx, "*:re:te.*st"),
            (
                "(regexp_fields(?1, n.flds))".into(),
                vec!["(?i)te.*st".into()]
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
                    "(((SELECT coalesce(min(id) > {cutoff}, false) FROM revlog WHERE cid = c.id AND ease != 0) ",
                    "AND c.id IN (SELECT cid FROM revlog WHERE id > {cutoff})))"
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
        assert_eq!(
            s(ctx, "tag:re:.ne|tw."),
            (
                "(regexp_tags(?1, n.tags))".into(),
                vec!["(?i).ne|tw.".into()]
            )
        );

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
                "(((c.queue in (2,3) and \n                        (case when \
c.odue != 0 then c.odue else c.due end) != {days}) or (c.queue in (1,4) and 
                        (((case when c.odue != 0 then c.odue else c.due end) - {cutoff}) / 86400) != -1)))",
                days = timing.days_elapsed - 1,
                cutoff = timing.next_day_at
            )
        );
        assert_eq!(s(ctx, "prop:rated>-5:3").0, s(ctx, "rated:5:3").0);
        assert_eq!(
            &s(ctx, "prop:cdn:r=1").0,
            "(cast(extract_custom_data(c.data, 'r') as float) = 1)"
        );
        assert_eq!(
            &s(ctx, "prop:cds:r=s").0,
            "(extract_custom_data(c.data, 'r') = 's')"
        );

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
                vec!["(?i)^basic.*$".into()]
            )
        );

        // regex
        assert_eq!(
            s(ctx, r"re:\bone"),
            ("(n.flds regexp ?1)".into(), vec![r"(?i)\bone".into()])
        );

        // word boundary
        assert_eq!(
            s(ctx, r"w:foo"),
            ("(n.flds regexp ?1)".into(), vec![r"(?i)\bfoo\b".into()])
        );
        assert_eq!(
            s(ctx, r"w:*foo"),
            ("(n.flds regexp ?1)".into(), vec![r"(?i)\b.*foo\b".into()])
        );

        assert_eq!(
            s(ctx, r"w:*fo_o*"),
            (
                "(n.flds regexp ?1)".into(),
                vec![r"(?i)\b.*fo.o.*\b".into()]
            )
        );

        // has-cd
        assert_eq!(
            &s(ctx, "has-cd:r").0,
            "(extract_custom_data(c.data, 'r') is not null)"
        );

        // preset search
        assert_eq!(
            &s(ctx, "preset:default").0,
            "((c.did in (1) or c.odid in (1)))"
        );
        assert_eq!(&s(ctx, "preset:typo").0, "(false)");

        // strip clozes
        assert_eq!(&s(ctx, "sc:abcdef").0, "((n.mid = 1581236385343) and (coalesce(process_text(cast(n.sfld as text), 2), n.sfld) like ?1 escape '\\' or coalesce(process_text(n.flds, 2), n.flds) like ?1 escape '\\'))");
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

    #[allow(clippy::single_range_in_vec_init)]
    #[test]
    fn ranges() {
        assert_eq!([1, 2, 3].collect_ranges(true), [1..4]);
        assert_eq!([1, 3, 4].collect_ranges(true), [1..2, 3..5]);
        assert_eq!([1, 2, 5, 6].collect_ranges(false), [1..2, 2..3, 5..6, 6..7]);
    }
}
