// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::fs::File;
use std::io::Write;
use std::sync::Arc;
use std::sync::LazyLock;

use anki_proto::import_export::ExportNoteCsvRequest;
use itertools::Itertools;
use regex::Regex;

use super::metadata::Delimiter;
use crate::import_export::text::csv::metadata::DelimeterExt;
use crate::import_export::ExportProgress;
use crate::notetype::RenderCardOutput;
use crate::prelude::*;
use crate::search::SearchNode;
use crate::search::SortMode;
use crate::template::RenderedNode;
use crate::text::html_to_text_line;
use crate::text::CowMapping;

const DELIMITER: Delimiter = Delimiter::Tab;

impl Collection {
    pub fn export_card_csv(
        &mut self,
        path: &str,
        search: impl TryIntoSearch,
        with_html: bool,
    ) -> Result<usize> {
        let mut progress = self.new_progress_handler::<ExportProgress>();
        let mut incrementor = progress.incrementor(ExportProgress::Cards);

        let mut writer = file_writer_with_header(path, with_html)?;
        let mut cards = self.search_cards(search, SortMode::NoOrder)?;
        cards.sort_unstable();
        for &card in &cards {
            incrementor.increment()?;
            writer
                .write_record(self.card_record(card, with_html)?)
                .or_invalid("invalid csv")?;
        }
        writer.flush()?;

        Ok(cards.len())
    }

    pub fn export_note_csv(&mut self, mut request: ExportNoteCsvRequest) -> Result<usize> {
        let mut progress = self.new_progress_handler::<ExportProgress>();
        let mut incrementor = progress.incrementor(ExportProgress::Notes);

        let guard = self.search_notes_into_table(Into::<SearchNode>::into(&mut request))?;
        let ctx = NoteContext::new(&request, guard.col)?;
        let mut writer = note_file_writer_with_header(&request.out_path, &ctx)?;
        guard.col.storage.for_each_note_in_search(|note| {
            incrementor.increment()?;
            writer
                .write_record(ctx.record(&note))
                .or_invalid("invalid csv")?;
            Ok(())
        })?;
        writer.flush()?;

        Ok(incrementor.count())
    }

    fn card_record(&mut self, card: CardId, with_html: bool) -> Result<[String; 2]> {
        let RenderCardOutput { qnodes, anodes, .. } =
            self.render_existing_card(card, false, false)?;
        Ok([
            rendered_nodes_to_record_field(&qnodes, with_html, false),
            rendered_nodes_to_record_field(&anodes, with_html, true),
        ])
    }
}

fn file_writer_with_header(path: &str, with_html: bool) -> Result<csv::Writer<File>> {
    let mut file = File::create(path)?;
    write_file_header(&mut file, with_html)?;
    Ok(csv::WriterBuilder::new()
        .delimiter(DELIMITER.byte())
        .comment(Some(b'#'))
        .from_writer(file))
}

fn write_file_header(writer: &mut impl Write, with_html: bool) -> Result<()> {
    writeln!(writer, "#separator:{}", DELIMITER.name())?;
    writeln!(writer, "#html:{with_html}")?;
    Ok(())
}

fn note_file_writer_with_header(path: &str, ctx: &NoteContext) -> Result<csv::Writer<File>> {
    let mut file = File::create(path)?;
    write_note_file_header(&mut file, ctx)?;
    Ok(csv::WriterBuilder::new()
        .delimiter(DELIMITER.byte())
        .comment(Some(b'#'))
        .from_writer(file))
}

fn write_note_file_header(writer: &mut impl Write, ctx: &NoteContext) -> Result<()> {
    write_file_header(writer, ctx.with_html)?;
    write_column_header(ctx, writer)
}

fn write_column_header(ctx: &NoteContext, writer: &mut impl Write) -> Result<()> {
    for (name, column) in [
        ("guid", ctx.guid_column()),
        ("notetype", ctx.notetype_column()),
        ("deck", ctx.deck_column()),
        ("tags", ctx.tags_column()),
    ] {
        if let Some(index) = column {
            writeln!(writer, "#{name} column:{index}")?;
        }
    }
    Ok(())
}

fn rendered_nodes_to_record_field(
    nodes: &[RenderedNode],
    with_html: bool,
    answer_side: bool,
) -> String {
    let text = rendered_nodes_to_str(nodes);
    let mut text = strip_redundant_sections(&text);
    if answer_side {
        text = text.map_cow(strip_answer_side_question);
    }
    if !with_html {
        text = text.map_cow(|t| html_to_text_line(t, false));
    }
    text.into()
}

fn rendered_nodes_to_str(nodes: &[RenderedNode]) -> String {
    nodes
        .iter()
        .map(|node| match node {
            RenderedNode::Text { text } => text,
            RenderedNode::Replacement { current_text, .. } => current_text,
        })
        .join("")
}

fn field_to_record_field(field: &str, with_html: bool) -> Cow<'_, str> {
    let mut text = strip_redundant_sections(field);
    if !with_html {
        text = text.map_cow(|t| html_to_text_line(t, false));
    }
    text
}

fn strip_redundant_sections(text: &str) -> Cow<'_, str> {
    static RE: LazyLock<Regex> = LazyLock::new(|| {
        Regex::new(
            r"(?isx)
            <style>.*?</style>          # style elements
            |
            \[\[type:[^]]+\]\]          # type replacements
            ",
        )
        .unwrap()
    });
    RE.replace_all(text.as_ref(), "")
}

fn strip_answer_side_question(text: &str) -> Cow<'_, str> {
    static RE: LazyLock<Regex> =
        LazyLock::new(|| Regex::new(r"(?is)^.*<hr id=answer>\n*").unwrap());
    RE.replace_all(text.as_ref(), "")
}

struct NoteContext {
    with_html: bool,
    with_tags: bool,
    with_deck: bool,
    with_notetype: bool,
    with_guid: bool,
    notetypes: HashMap<NotetypeId, Arc<Notetype>>,
    deck_ids: HashMap<NoteId, DeckId>,
    deck_names: HashMap<DeckId, String>,
    field_columns: usize,
}

impl NoteContext {
    /// Caller must have searched notes into table.
    fn new(request: &ExportNoteCsvRequest, col: &mut Collection) -> Result<Self> {
        let notetypes = col.get_all_notetypes_of_search_notes()?;
        let field_columns = notetypes
            .values()
            .map(|nt| nt.fields.len())
            .max()
            .unwrap_or_default();
        let deck_ids = col.storage.all_decks_of_search_notes()?;
        let deck_names = HashMap::from_iter(col.storage.get_all_deck_names()?);

        Ok(Self {
            with_html: request.with_html,
            with_tags: request.with_tags,
            with_deck: request.with_deck,
            with_notetype: request.with_notetype,
            with_guid: request.with_guid,
            notetypes,
            field_columns,
            deck_ids,
            deck_names,
        })
    }

    fn guid_column(&self) -> Option<usize> {
        self.with_guid.then_some(1)
    }

    fn notetype_column(&self) -> Option<usize> {
        self.with_notetype
            .then(|| 1 + self.guid_column().unwrap_or_default())
    }

    fn deck_column(&self) -> Option<usize> {
        self.with_deck.then(|| {
            1 + self
                .notetype_column()
                .or_else(|| self.guid_column())
                .unwrap_or_default()
        })
    }

    fn tags_column(&self) -> Option<usize> {
        self.with_tags.then(|| {
            1 + self
                .deck_column()
                .or_else(|| self.notetype_column())
                .or_else(|| self.guid_column())
                .unwrap_or_default()
                + self.field_columns
        })
    }

    fn record<'c, 's: 'c, 'n: 'c>(&'s self, note: &'n Note) -> impl Iterator<Item = Cow<'c, [u8]>> {
        self.with_guid
            .then(|| Cow::from(note.guid.as_bytes()))
            .into_iter()
            .chain(self.notetype_name(note))
            .chain(self.deck_name(note))
            .chain(self.note_fields(note))
            .chain(self.tags(note))
    }

    fn notetype_name(&self, note: &Note) -> Option<Cow<'_, [u8]>> {
        self.with_notetype.then(|| {
            self.notetypes
                .get(&note.notetype_id)
                .map_or(Cow::from(vec![]), |nt| Cow::from(nt.name.as_bytes()))
        })
    }

    fn deck_name(&self, note: &Note) -> Option<Cow<'_, [u8]>> {
        self.with_deck.then(|| {
            self.deck_ids
                .get(&note.id)
                .and_then(|did| self.deck_names.get(did))
                .map_or(Cow::from(vec![]), |name| Cow::from(name.as_bytes()))
        })
    }

    fn tags(&self, note: &Note) -> Option<Cow<'_, [u8]>> {
        self.with_tags
            .then(|| Cow::from(note.tags.join(" ").into_bytes()))
    }

    fn note_fields<'n>(&self, note: &'n Note) -> impl Iterator<Item = Cow<'n, [u8]>> {
        let with_html = self.with_html;
        note.fields()
            .iter()
            .map(move |f| field_to_record_field(f, with_html))
            .pad_using(self.field_columns, |_| Cow::from(""))
            .map(|cow| match cow {
                Cow::Borrowed(s) => Cow::from(s.as_bytes()),
                Cow::Owned(s) => Cow::from(s.into_bytes()),
            })
    }
}

impl From<&mut ExportNoteCsvRequest> for SearchNode {
    fn from(req: &mut ExportNoteCsvRequest) -> Self {
        SearchNode::from(req.limit.take().unwrap_or_default())
    }
}

#[cfg(test)]
mod tests {
    use anki_proto::import_export::export_limit::Limit;
    use anki_proto::import_export::ExportLimit;
    use anki_proto::import_export::ExportNoteCsvRequest;

    use super::*;
    use crate::tests::open_fs_test_collection;
    use crate::tests::DeckAdder;
    use crate::tests::NoteAdder;

    fn note_csv_request(out_path: String) -> ExportNoteCsvRequest {
        ExportNoteCsvRequest {
            out_path,
            with_html: true,
            with_tags: true,
            with_deck: true,
            with_notetype: true,
            with_guid: true,
            limit: None,
        }
    }

    #[test]
    fn export_card_csv_writes_separator_and_html_header() {
        let (mut col, dir) = open_fs_test_collection("card_csv");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("cards.csv");
        col.export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, true)
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.starts_with("#separator:tab\n#html:true\n"),
            "expected CSV header, got: {content}"
        );
    }

    #[test]
    fn export_card_csv_html_false_writes_html_false_in_header() {
        let (mut col, dir) = open_fs_test_collection("card_csv_nohtml");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("cards.csv");
        col.export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, false)
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.starts_with("#separator:tab\n#html:false\n"),
            "expected CSV header, got: {content}"
        );
    }

    #[test]
    fn export_card_csv_empty_collection_still_writes_header() {
        let (mut col, dir) = open_fs_test_collection("card_csv_empty");
        let path = dir.path().join("cards.csv");
        let count = col
            .export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, true)
            .unwrap();
        assert_eq!(count, 0, "expected 0 cards exported");
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.starts_with("#separator:tab\n#html:true\n"),
            "expected header even with empty collection, got: {content}"
        );
    }

    #[test]
    fn export_card_csv_returns_card_count() {
        let (mut col, dir) = open_fs_test_collection("card_csv_count");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("cards.csv");
        let count = col
            .export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, true)
            .unwrap();
        assert_eq!(count, 1, "expected 1 card exported");
    }

    #[test]
    fn export_card_csv_returns_count_for_multiple_notes() {
        let (mut col, dir) = open_fs_test_collection("card_csv_multi");
        NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("cards.csv");
        let count = col
            .export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, true)
            .unwrap();
        assert_eq!(count, 3, "expected 3 cards exported");
    }

    #[test]
    fn export_card_csv_filters_by_deck() {
        let (mut col, dir) = open_fs_test_collection("card_csv_deck_filter");
        let target_deck = DeckAdder::new("target").add(&mut col);
        let other_deck = DeckAdder::new("other").add(&mut col);
        NoteAdder::basic(&mut col)
            .deck(target_deck.id)
            .add(&mut col);
        NoteAdder::basic(&mut col)
            .deck(target_deck.id)
            .add(&mut col);
        NoteAdder::basic(&mut col).deck(other_deck.id).add(&mut col);
        let path = dir.path().join("cards.csv");
        let count = col
            .export_card_csv(
                path.to_str().unwrap(),
                SearchNode::DeckIdWithChildren(target_deck.id),
                true,
            )
            .unwrap();
        assert_eq!(count, 2, "expected only cards from target deck");
    }

    #[test]
    fn export_card_csv_front_and_back_in_correct_columns() {
        let (mut col, dir) = open_fs_test_collection("card_csv_columns");
        NoteAdder::basic(&mut col)
            .fields(&["front text", "back text"])
            .add(&mut col);
        let path = dir.path().join("cards.csv");
        col.export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, false)
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        let first_record = content
            .lines()
            .find(|l| !l.starts_with('#'))
            .expect("no data record found");
        let cols: Vec<&str> = first_record.split('\t').collect();
        assert_eq!(cols[0], "front text", "expected front field in column 0");
        assert_eq!(cols[1], "back text", "expected back field in column 1");
    }

    #[test]
    fn export_card_csv_preserves_html_when_with_html_true() {
        let (mut col, dir) = open_fs_test_collection("card_csv_html_content");
        NoteAdder::basic(&mut col)
            .fields(&["<b>bold front</b>", "back text"])
            .add(&mut col);
        let path = dir.path().join("cards.csv");
        col.export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, true)
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.contains("<b>bold front</b>"),
            "expected HTML preserved in CSV, got: {content}"
        );
    }

    #[test]
    fn export_card_csv_strips_html_from_fields_when_with_html_false() {
        let (mut col, dir) = open_fs_test_collection("card_csv_html_stripped");
        NoteAdder::basic(&mut col)
            .fields(&["<b>bold front</b>", "back text"])
            .add(&mut col);
        let path = dir.path().join("cards.csv");
        col.export_card_csv(path.to_str().unwrap(), SearchNode::WholeCollection, false)
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.contains("bold front"),
            "expected stripped text in CSV, got: {content}"
        );
        assert!(
            !content.contains("<b>"),
            "expected no HTML tags in CSV, got: {content}"
        );
    }

    #[test]
    fn export_note_csv_returns_note_count() {
        let (mut col, dir) = open_fs_test_collection("note_csv_count");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("notes.csv");
        let count = col
            .export_note_csv(note_csv_request(path.to_str().unwrap().into()))
            .unwrap();
        assert_eq!(count, 1, "expected 1 note exported");
    }

    #[test]
    fn export_note_csv_returns_count_for_multiple_notes() {
        let (mut col, dir) = open_fs_test_collection("note_csv_multi");
        NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("notes.csv");
        let count = col
            .export_note_csv(note_csv_request(path.to_str().unwrap().into()))
            .unwrap();
        assert_eq!(count, 3, "expected 3 notes exported");
    }

    #[test]
    fn export_note_csv_filters_by_deck() {
        let (mut col, dir) = open_fs_test_collection("note_csv_deck_filter");
        let target_deck = DeckAdder::new("target").add(&mut col);
        let other_deck = DeckAdder::new("other").add(&mut col);
        NoteAdder::basic(&mut col)
            .deck(target_deck.id)
            .add(&mut col);
        NoteAdder::basic(&mut col)
            .deck(target_deck.id)
            .add(&mut col);
        NoteAdder::basic(&mut col).deck(other_deck.id).add(&mut col);
        let path = dir.path().join("notes.csv");
        let count = col
            .export_note_csv(ExportNoteCsvRequest {
                out_path: path.to_str().unwrap().into(),
                with_html: true,
                with_tags: false,
                with_deck: false,
                with_notetype: false,
                with_guid: false,
                limit: Some(ExportLimit {
                    limit: Some(Limit::DeckId(target_deck.id.0)),
                }),
            })
            .unwrap();
        assert_eq!(count, 2, "expected only notes from target deck");
    }

    #[test]
    fn export_note_csv_column_indices_match_actual_column_positions() {
        // basic note has 2 fields; with all metadata enabled:
        //   col 1: guid
        //   col 2: notetype
        //   col 3: deck
        //   col 4-5: note fields
        //   col 6: tags  (1 + deck(3) + 2 fields)
        let (mut col, dir) = open_fs_test_collection("note_csv_col_indices");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("notes.csv");
        col.export_note_csv(note_csv_request(path.to_str().unwrap().into()))
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.contains("#guid column:1\n"),
            "wrong guid column index"
        );
        assert!(
            content.contains("#notetype column:2\n"),
            "wrong notetype column index"
        );
        assert!(
            content.contains("#deck column:3\n"),
            "wrong deck column index"
        );
        assert!(
            content.contains("#tags column:6\n"),
            "wrong tags column index"
        );
    }

    #[test]
    fn export_note_csv_omits_column_headers_when_metadata_disabled() {
        let (mut col, dir) = open_fs_test_collection("note_csv_no_meta");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("notes.csv");
        col.export_note_csv(ExportNoteCsvRequest {
            out_path: path.to_str().unwrap().into(),
            with_html: true,
            with_guid: false,
            with_notetype: false,
            with_deck: false,
            with_tags: false,
            limit: None,
        })
        .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            !content.contains("#guid column:"),
            "guid column header should be absent"
        );
        assert!(
            !content.contains("#notetype column:"),
            "notetype column header should be absent"
        );
        assert!(
            !content.contains("#deck column:"),
            "deck column header should be absent"
        );
        assert!(
            !content.contains("#tags column:"),
            "tags column header should be absent"
        );
    }

    #[test]
    fn export_note_csv_fields_and_guid_in_correct_columns() {
        // with all metadata: guid(0) | notetype(1) | deck(2) | front(3) | back(4) |
        // tags(5)
        let (mut col, dir) = open_fs_test_collection("note_csv_col_positions");
        let note = NoteAdder::basic(&mut col)
            .fields(&["hello", "world"])
            .add(&mut col);
        let path = dir.path().join("notes.csv");
        col.export_note_csv(note_csv_request(path.to_str().unwrap().into()))
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        let first_record = content
            .lines()
            .find(|l| !l.starts_with('#'))
            .expect("no data record found");
        let cols: Vec<&str> = first_record.split('\t').collect();
        assert_eq!(cols[0], note.guid, "expected guid in column 0");
        assert_eq!(cols[3], "hello", "expected Front field in column 3");
        assert_eq!(cols[4], "world", "expected Back field in column 4");
    }

    #[test]
    fn export_note_csv_omits_guid_value_when_with_guid_false() {
        let (mut col, dir) = open_fs_test_collection("note_csv_no_guid");
        let note = NoteAdder::basic(&mut col)
            .fields(&["hello", "world"])
            .add(&mut col);
        let path = dir.path().join("notes.csv");
        col.export_note_csv(ExportNoteCsvRequest {
            out_path: path.to_str().unwrap().into(),
            with_html: true,
            with_guid: false,
            with_notetype: false,
            with_deck: false,
            with_tags: false,
            limit: None,
        })
        .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            !content.contains(&note.guid),
            "guid should be absent when with_guid is false, got: {content}"
        );
        assert!(content.contains("hello"), "fields should still be exported");
        assert!(content.contains("world"), "fields should still be exported");
    }

    #[test]
    fn export_note_csv_preserves_html_in_fields_when_with_html_true() {
        let (mut col, dir) = open_fs_test_collection("note_csv_html_content");
        NoteAdder::basic(&mut col)
            .fields(&["<i>italic</i>", "plain back"])
            .add(&mut col);
        let path = dir.path().join("notes.csv");
        col.export_note_csv(note_csv_request(path.to_str().unwrap().into()))
            .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.contains("<i>italic</i>"),
            "expected HTML preserved in note CSV, got: {content}"
        );
    }

    #[test]
    fn export_note_csv_with_html_false_strips_html_tags() {
        let (mut col, dir) = open_fs_test_collection("note_csv_nohtml");
        NoteAdder::basic(&mut col)
            .fields(&["<b>bold text</b>", "world"])
            .add(&mut col);
        let path = dir.path().join("notes.csv");
        col.export_note_csv(ExportNoteCsvRequest {
            out_path: path.to_str().unwrap().into(),
            with_html: false,
            with_tags: false,
            with_deck: false,
            with_notetype: false,
            with_guid: false,
            limit: None,
        })
        .unwrap();
        let content = std::fs::read_to_string(&path).unwrap();
        assert!(
            content.contains("bold text"),
            "expected stripped text in CSV, got: {content}"
        );
        assert!(
            !content.contains("<b>"),
            "expected no HTML tags in CSV, got: {content}"
        );
    }
}
