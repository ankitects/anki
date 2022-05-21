// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fs::File,
    io::{BufRead, BufReader, Read, Seek, SeekFrom},
};

use crate::{
    import_export::{
        text::{
            csv::metadata::{CsvDeck, CsvMetadata, CsvNotetype, Delimiter},
            DupeResolution, ForeignData, ForeignNote,
        },
        NoteLog,
    },
    prelude::*,
};

impl Collection {
    pub fn import_csv(
        &mut self,
        path: &str,
        metadata: CsvMetadata,
        dupe_resolution: DupeResolution,
    ) -> Result<OpOutput<NoteLog>> {
        let file = File::open(path)?;
        let default_deck = metadata.deck()?.default_string();
        let default_notetype = metadata.notetype()?.default_string();
        let mut ctx = ColumnContext::new(&metadata)?;
        let notes = ctx.deserialize_csv(file, metadata.delimiter())?;

        ForeignData {
            dupe_resolution,
            // TODO: refactor to allow passing ids directly
            default_deck,
            default_notetype,
            notes,
            ..Default::default()
        }
        .import(self)
    }
}

impl CsvMetadata {
    fn deck(&self) -> Result<&CsvDeck> {
        self.deck
            .as_ref()
            .ok_or_else(|| AnkiError::invalid_input("deck oneof not set"))
    }

    fn notetype(&self) -> Result<&CsvNotetype> {
        self.notetype
            .as_ref()
            .ok_or_else(|| AnkiError::invalid_input("notetype oneof not set"))
    }

    fn field_source_columns(&self) -> Result<Vec<Option<usize>>> {
        Ok(match self.notetype()? {
            CsvNotetype::GlobalNotetype(global) => global
                .field_columns
                .iter()
                .map(|&i| i.try_into().ok())
                .collect(),
            CsvNotetype::NotetypeColumn(_) => {
                let meta_columns = self.meta_columns();
                (0..self.column_labels.len())
                    .filter(|idx| !meta_columns.contains(idx))
                    .map(Some)
                    .collect()
            }
        })
    }
}

impl CsvDeck {
    fn default_string(&self) -> String {
        match self {
            Self::DeckId(did) => did.to_string(),
            Self::DeckColumn(_) => String::new(),
        }
    }

    fn column(&self) -> Option<usize> {
        match self {
            Self::DeckId(_) => None,
            Self::DeckColumn(column) => Some(*column as usize),
        }
    }
}

impl CsvNotetype {
    fn default_string(&self) -> String {
        match self {
            Self::GlobalNotetype(nt) => nt.id.to_string(),
            Self::NotetypeColumn(_) => String::new(),
        }
    }

    fn column(&self) -> Option<usize> {
        match self {
            Self::GlobalNotetype(_) => None,
            Self::NotetypeColumn(column) => Some(*column as usize),
        }
    }
}

/// Column indices for the fields of a notetype.
type FieldSourceColumns = Vec<Option<usize>>;

struct ColumnContext {
    tags_column: Option<usize>,
    deck_column: Option<usize>,
    notetype_column: Option<usize>,
    /// Source column indices for the fields of a notetype, identified by its
    /// name or id as string. The empty string corresponds to the default notetype.
    field_source_columns: FieldSourceColumns,
    /// How fields are converted to strings. Used for escaping HTML if appropriate.
    stringify: fn(&str) -> String,
}

impl ColumnContext {
    fn new(metadata: &CsvMetadata) -> Result<Self> {
        Ok(Self {
            tags_column: metadata.tags_column.try_into().ok(),
            deck_column: metadata.deck()?.column(),
            notetype_column: metadata.notetype()?.column(),
            field_source_columns: metadata.field_source_columns()?,
            stringify: stringify_fn(metadata.is_html),
        })
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
                    .map(|record| self.foreign_note_from_record(&record))
            })
            .collect()
    }

    fn foreign_note_from_record(&mut self, record: &csv::StringRecord) -> ForeignNote {
        let notetype = self.gather_notetype(record);
        let deck = self.gather_deck(record);
        let tags = self.gather_tags(record);
        let fields = self.gather_note_fields(record);
        ForeignNote {
            notetype,
            fields,
            tags,
            deck,
            ..Default::default()
        }
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

    fn gather_note_fields(&mut self, record: &csv::StringRecord) -> Vec<String> {
        let stringify = self.stringify;
        self.field_source_columns
            .iter()
            .map(|opt| opt.and_then(|idx| record.get(idx)).unwrap_or_default())
            .map(stringify)
            .collect()
    }
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
    use std::io::Cursor;

    use super::*;
    use crate::backend_proto::import_export::csv_metadata::MappedNotetype;

    macro_rules! import {
        ($metadata:expr, $csv:expr) => {{
            let reader = Cursor::new($csv);
            let delimiter = $metadata.delimiter();
            let mut ctx = ColumnContext::new(&$metadata).unwrap();
            ctx.deserialize_csv(reader, delimiter).unwrap()
        }};
    }

    macro_rules! assert_imported_fields {
        ($metadata:expr, $csv:expr, $expected:expr) => {
            let notes = import!(&$metadata, $csv);
            let fields: Vec<_> = notes.into_iter().map(|note| note.fields).collect();
            assert_eq!(fields, $expected);
        };
    }

    impl CsvMetadata {
        fn defaults_for_testing() -> Self {
            Self {
                delimiter: Delimiter::Comma as i32,
                force_delimiter: false,
                is_html: false,
                force_is_html: false,
                tags_column: -1,
                tags: "".to_string(),
                column_labels: vec!["".to_string(); 2],
                deck: Some(CsvDeck::DeckId(1)),
                notetype: Some(CsvNotetype::GlobalNotetype(MappedNotetype {
                    id: 1,
                    field_columns: vec![0, 1],
                })),
            }
        }
    }

    #[test]
    fn should_allow_missing_columns() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "foo\n", &[&["foo", ""]]);
    }

    #[test]
    fn should_respect_custom_delimiter() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.set_delimiter(Delimiter::Pipe);
        assert_imported_fields!(metadata, "fr,ont|ba,ck\n", &[&["fr,ont", "ba,ck"]]);
    }

    #[test]
    fn should_ignore_first_line_starting_with_tags() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "tags:foo\nfront,back\n", &[&["front", "back"]]);
    }

    #[test]
    fn should_respect_column_remapping() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata
            .notetype
            .replace(CsvNotetype::GlobalNotetype(MappedNotetype {
                id: 1,
                field_columns: vec![2, 0],
            }));
        assert_imported_fields!(metadata, "front,foo,back\n", &[&["back", "front"]]);
    }

    #[test]
    fn should_ignore_lines_starting_with_number_sign() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "#foo\nfront,back\n#bar\n", &[&["front", "back"]]);
    }

    #[test]
    fn should_escape_html_entities_if_csv_is_html() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "<hr>\n", &[&["&lt;hr&gt;", ""]]);
        metadata.is_html = true;
        assert_imported_fields!(metadata, "<hr>\n", &[&["<hr>", ""]]);
    }

    #[test]
    fn should_parse_tag_column() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.tags_column = 2;
        let notes = import!(metadata, "front,back,foo bar\n");
        assert_eq!(notes[0].tags, &["foo", "bar"]);
    }

    #[test]
    fn should_parse_deck_column() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.deck.replace(CsvDeck::DeckColumn(0));
        let notes = import!(metadata, "front,back\n");
        assert_eq!(notes[0].deck, "front");
    }

    #[test]
    fn should_parse_notetype_column() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.notetype.replace(CsvNotetype::NotetypeColumn(0));
        metadata.column_labels.push("".to_string());
        let notes = import!(metadata, "Basic,front,back\nCloze,foo,bar\n");
        assert_eq!(notes[0].fields, &["front", "back"]);
        assert_eq!(notes[0].notetype, "Basic");
        assert_eq!(notes[1].fields, &["foo", "bar"]);
        assert_eq!(notes[1].notetype, "Cloze");
    }
}
