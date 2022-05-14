// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashMap,
    fs::File,
    io::{BufRead, BufReader, Read, Seek, SeekFrom},
};

use crate::{
    backend_proto::import_export::import_csv_request::Columns as ProtoColumns,
    import_export::{
        text::{csv::metadata::Delimiter, import::NotetypeForString, ForeignData, ForeignNote},
        NoteLog,
    },
    prelude::*,
};

impl Collection {
    #[allow(clippy::too_many_arguments)]
    pub fn import_csv(
        &mut self,
        path: &str,
        deck_id: DeckId,
        notetype_id: NotetypeId,
        delimiter: Delimiter,
        is_html: bool,
        columns: ProtoColumns,
        column_names: Vec<String>,
    ) -> Result<OpOutput<NoteLog>> {
        let file = File::open(path)?;
        let mut ctx = ColumnContext::new(columns, column_names, is_html, self);
        let notes = ctx.deserialize_csv(file, delimiter)?;

        ForeignData {
            // TODO: refactor to allow passing ids directly
            default_deck: deck_id.to_string(),
            default_notetype: notetype_id.to_string(),
            notes,
            ..Default::default()
        }
        .import(self)
    }
}

/// Column indices for the fields of a notetype.
type FieldSourceColumns = Vec<Option<usize>>;

struct ColumnContext<'a, C: NotetypeForString> {
    tags_column: Option<usize>,
    deck_column: Option<usize>,
    notetype_column: Option<usize>,
    /// Source column indices for the fields of a notetype, identified by its
    /// name or id as string. The empty string corresponds to the default notetype.
    notetype_fields: HashMap<String, FieldSourceColumns>,
    /// CSV column labels. Used to identify the source columns for the fields
    /// of a notetype.
    column_names: HashMap<String, usize>,
    /// How fields are converted to strings. Used for escaping HTML if appropriate.
    stringify: fn(&str) -> String,
    col: &'a mut C,
}

impl<'a, C: NotetypeForString> ColumnContext<'a, C> {
    fn new(
        columns: ProtoColumns,
        column_names: Vec<String>,
        is_html: bool,
        col: &'a mut C,
    ) -> Self {
        Self {
            tags_column: columns.tags.try_into().ok(),
            deck_column: columns.deck.try_into().ok(),
            notetype_column: columns.notetype.try_into().ok(),
            notetype_fields: notetype_fields_map(&columns.fields),
            column_names: column_names_map(column_names),
            stringify: stringify_fn(is_html),
            col,
        }
    }

    fn deserialize_csv(
        &mut self,
        mut reader: impl Read + Seek,
        delimiter: Delimiter,
    ) -> Result<Vec<ForeignNote>> {
        remove_tags_line_from_reader(&mut reader)?;
        let mut csv_reader = csv::ReaderBuilder::new()
            .has_headers(false)
            .flexible(true)
            .comment(Some(b'#'))
            .delimiter(delimiter.byte())
            .from_reader(reader);
        self.deserialize_csv_reader(&mut csv_reader)
    }

    fn deserialize_csv_reader(
        &mut self,
        reader: &mut csv::Reader<impl Read>,
    ) -> Result<Vec<ForeignNote>> {
        reader
            .records()
            .into_iter()
            .map(|res| {
                res.map_err(Into::into)
                    .and_then(|record| self.foreign_note_from_record(&record))
            })
            .collect()
    }

    fn foreign_note_from_record(&mut self, record: &csv::StringRecord) -> Result<ForeignNote> {
        let notetype = self.gather_notetype(record);
        let deck = self.gather_deck(record);
        let tags = self.gather_tags(record);
        let fields = self.gather_note_fields(record, &notetype)?;
        Ok(ForeignNote {
            notetype,
            fields,
            tags,
            deck,
            ..Default::default()
        })
    }

    fn gather_notetype(&self, record: &csv::StringRecord) -> String {
        self.notetype_column
            .and_then(|i| record.get(i))
            .unwrap_or_default()
            .to_string()
    }

    fn gather_deck(&self, record: &csv::StringRecord) -> String {
        self.deck_column
            .and_then(|i| record.get(i))
            .unwrap_or_default()
            .to_string()
    }

    fn gather_tags(&self, record: &csv::StringRecord) -> Vec<String> {
        self.tags_column
            .and_then(|i| record.get(i))
            .unwrap_or_default()
            .split_whitespace()
            .filter(|s| !s.is_empty())
            .map(ToString::to_string)
            .collect()
    }

    fn gather_note_fields(
        &mut self,
        record: &csv::StringRecord,
        notetype: &str,
    ) -> Result<Vec<String>> {
        let stringify = self.stringify;
        Ok(self
            .get_notetype_fields(notetype)?
            .iter()
            .map(|opt| opt.and_then(|idx| record.get(idx)).unwrap_or_default())
            .map(stringify)
            .collect())
    }

    fn get_notetype_fields(&mut self, notetype: &str) -> Result<&FieldSourceColumns> {
        Ok(if self.notetype_fields.contains_key(notetype) {
            // borrow checker doesn't allow to use `if let` here
            // https://users.rust-lang.org/t/solved-borrow-doesnt-drop-returning-this-value-requires-that/24182
            self.notetype_fields.get(notetype).unwrap()
        } else {
            // TODO: more specific errors
            let nt = self
                .col
                .notetype_for_string(notetype)?
                .ok_or(AnkiError::NotFound)?;
            let map = self.build_notetype_fields_map(&nt);
            if map[0].is_none() {
                return Err(AnkiError::NotFound);
            }
            self.notetype_fields
                .entry(notetype.to_string())
                .or_insert(map)
        })
    }

    fn build_notetype_fields_map(&mut self, notetype: &Notetype) -> FieldSourceColumns {
        notetype
            .fields
            .iter()
            .map(|f| self.column_names.get(&f.name).copied())
            .collect()
    }
}

fn column_names_map(column_names: Vec<String>) -> HashMap<String, usize> {
    HashMap::from_iter(column_names.into_iter().enumerate().map(|(i, s)| (s, i)))
}

fn notetype_fields_map(default_fields: &[i32]) -> HashMap<String, FieldSourceColumns> {
    let default_fields = default_fields.iter().map(|&i| i.try_into().ok()).collect();
    let mut notetype_fields = HashMap::new();
    notetype_fields.insert(String::new(), default_fields);
    notetype_fields
}

fn stringify_fn(is_html: bool) -> fn(&str) -> String {
    if is_html {
        ToString::to_string
    } else {
        htmlescape::encode_minimal
    }
}

/// If the reader's first line starts with "tags:", which is allowed for historic
/// reasons, seek to the second line.
fn remove_tags_line_from_reader(reader: &mut (impl Read + Seek)) -> Result<()> {
    let mut buf_reader = BufReader::new(reader);
    let mut first_line = String::new();
    buf_reader.read_line(&mut first_line)?;
    let offset = if first_line.starts_with("tags:") {
        first_line.as_bytes().len()
    } else {
        0
    };
    buf_reader
        .into_inner()
        .seek(SeekFrom::Start(offset as u64))?;
    Ok(())
}

#[cfg(test)]
mod test {
    use std::{io::Cursor, sync::Arc};

    use super::*;
    use crate::notetype::all_stock_notetypes;

    macro_rules! import {
        ($options:expr, $csv:expr) => {{
            let reader = Cursor::new($csv);
            let delimiter = $options.delimiter;
            let mut ctx = $options.ctx();
            ctx.deserialize_csv(reader, delimiter).unwrap()
        }};
    }

    macro_rules! assert_imported_fields {
        ($options:expr, $csv:expr, $expected:expr) => {
            let notes = import!($options, $csv);
            let fields: Vec<_> = notes.into_iter().map(|note| note.fields).collect();
            assert_eq!(fields, $expected);
        };
    }

    struct MockNotetypeForString(HashMap<String, Arc<Notetype>>);

    impl NotetypeForString for MockNotetypeForString {
        fn notetype_for_string(&mut self, name_or_id: &str) -> Result<Option<Arc<Notetype>>> {
            Ok(self.0.get(name_or_id).cloned())
        }
    }

    impl MockNotetypeForString {
        fn new() -> Self {
            Self(HashMap::from_iter(
                all_stock_notetypes(&I18n::template_only())
                    .into_iter()
                    .map(|nt| (nt.name.clone(), Arc::new(nt))),
            ))
        }
    }

    struct CsvOptions {
        delimiter: Delimiter,
        is_html: bool,
        columns: ProtoColumns,
        column_names: Vec<String>,
        mock_col: MockNotetypeForString,
    }

    impl CsvOptions {
        fn new() -> Self {
            Self {
                delimiter: Delimiter::Comma,
                is_html: false,
                columns: ProtoColumns {
                    tags: -1,
                    deck: -1,
                    notetype: -1,
                    fields: vec![0, 1],
                },
                column_names: vec![],
                mock_col: MockNotetypeForString::new(),
            }
        }

        fn ctx(&mut self) -> ColumnContext<MockNotetypeForString> {
            ColumnContext::new(
                self.columns.clone(),
                std::mem::take(&mut self.column_names),
                self.is_html,
                &mut self.mock_col,
            )
        }
    }

    #[test]
    fn should_allow_missing_columns() {
        let mut options = CsvOptions::new();
        assert_imported_fields!(options, "foo\n", &[&["foo", ""]]);
    }

    #[test]
    fn should_respect_custom_delimiter() {
        let mut options = CsvOptions::new();
        options.delimiter = Delimiter::Pipe;
        assert_imported_fields!(options, "fr,ont|ba,ck\n", &[&["fr,ont", "ba,ck"]]);
    }

    #[test]
    fn should_ignore_first_line_starting_with_tags() {
        let mut options = CsvOptions::new();
        assert_imported_fields!(options, "tags:foo\nfront,back\n", &[&["front", "back"]]);
    }

    #[test]
    fn should_respect_column_remapping() {
        let mut options = CsvOptions::new();
        options.columns.fields = vec![2, 0];
        assert_imported_fields!(options, "front,foo,back\n", &[&["back", "front"]]);
    }

    #[test]
    fn should_ignore_lines_starting_with_number_sign() {
        let mut options = CsvOptions::new();
        assert_imported_fields!(options, "#foo\nfront,back\n#bar\n", &[&["front", "back"]]);
    }

    #[test]
    fn should_escape_html_entities_if_csv_is_html() {
        let mut options = CsvOptions::new();
        assert_imported_fields!(options, "<hr>\n", &[&["&lt;hr&gt;", ""]]);
        options.is_html = true;
        assert_imported_fields!(options, "<hr>\n", &[&["<hr>", ""]]);
    }

    #[test]
    fn should_parse_tag_column() {
        let mut options = CsvOptions::new();
        options.columns.tags = 2;
        let notes = import!(options, "front,back,foo bar\n");
        assert_eq!(notes[0].tags, &["foo", "bar"]);
    }

    #[test]
    fn should_parse_deck_column() {
        let mut options = CsvOptions::new();
        options.columns.deck = 2;
        let notes = import!(options, "front,back,foo bar\n");
        assert_eq!(notes[0].deck, "foo bar");
    }

    #[test]
    fn should_parse_notes_according_to_their_respective_notetypes() {
        let mut options = CsvOptions::new();
        options.columns.notetype = 3;
        options.column_names = ["Front", "Back", "Text", "notetype"]
            .iter()
            .map(ToString::to_string)
            .collect();
        assert_imported_fields!(
            options,
            "front,back,Basic (and reversed card)\n,,foo,Cloze\n",
            &[&["front", "back"], &["foo", ""]]
        );
    }
}
