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
            ForeignData, ForeignNote, NameOrId,
        },
        ImportProgress, NoteLog,
    },
    prelude::*,
};

impl Collection {
    pub fn import_csv(
        &mut self,
        path: &str,
        metadata: CsvMetadata,
        progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
    ) -> Result<OpOutput<NoteLog>> {
        let file = File::open(path)?;
        let default_deck = metadata.deck()?.name_or_id();
        let default_notetype = metadata.notetype()?.name_or_id();
        let mut ctx = ColumnContext::new(&metadata)?;
        let notes = ctx.deserialize_csv(file, metadata.delimiter())?;

        ForeignData {
            dupe_resolution: metadata.dupe_resolution(),
            default_deck,
            default_notetype,
            notes,
            global_tags: metadata.global_tags,
            updated_tags: metadata.updated_tags,
            ..Default::default()
        }
        .import(self, progress_fn)
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
                .map(|&i| (i > 0).then(|| i as usize))
                .collect(),
            CsvNotetype::NotetypeColumn(_) => {
                let meta_columns = self.meta_columns();
                (1..self.column_labels.len() + 1)
                    .filter(|idx| !meta_columns.contains(idx))
                    .map(Some)
                    .collect()
            }
        })
    }
}

impl CsvDeck {
    fn name_or_id(&self) -> NameOrId {
        match self {
            Self::DeckId(did) => NameOrId::Id(*did),
            Self::DeckColumn(_) => NameOrId::default(),
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
    fn name_or_id(&self) -> NameOrId {
        match self {
            Self::GlobalNotetype(nt) => NameOrId::Id(nt.id),
            Self::NotetypeColumn(_) => NameOrId::default(),
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

// Column indices are 1-based.
struct ColumnContext {
    tags_column: Option<usize>,
    guid_column: Option<usize>,
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
            tags_column: (metadata.tags_column > 0).then(|| metadata.tags_column as usize),
            guid_column: (metadata.guid_column > 0).then(|| metadata.guid_column as usize),
            deck_column: metadata.deck()?.column(),
            notetype_column: metadata.notetype()?.column(),
            field_source_columns: metadata.field_source_columns()?,
            stringify: stringify_fn(metadata.is_html),
        })
    }

    fn deserialize_csv(
        &mut self,
        reader: impl Read + Seek,
        delimiter: Delimiter,
    ) -> Result<Vec<ForeignNote>> {
        let mut csv_reader = build_csv_reader(reader, delimiter)?;
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

    fn foreign_note_from_record(&self, record: &csv::StringRecord) -> ForeignNote {
        ForeignNote {
            notetype: str_from_record_column(self.notetype_column, record).into(),
            fields: self.gather_note_fields(record),
            tags: self.gather_tags(record),
            deck: str_from_record_column(self.deck_column, record).into(),
            guid: str_from_record_column(self.guid_column, record),
            ..Default::default()
        }
    }

    fn gather_tags(&self, record: &csv::StringRecord) -> Vec<String> {
        self.tags_column
            .and_then(|i| record.get(i - 1))
            .unwrap_or_default()
            .split_whitespace()
            .filter(|s| !s.is_empty())
            .map(ToString::to_string)
            .collect()
    }

    fn gather_note_fields(&self, record: &csv::StringRecord) -> Vec<String> {
        let stringify = self.stringify;
        self.field_source_columns
            .iter()
            .map(|opt| opt.and_then(|idx| record.get(idx - 1)).unwrap_or_default())
            .map(stringify)
            .collect()
    }
}

fn str_from_record_column(column: Option<usize>, record: &csv::StringRecord) -> String {
    column
        .and_then(|i| record.get(i - 1))
        .unwrap_or_default()
        .to_string()
}

pub(super) fn build_csv_reader(
    mut reader: impl Read + Seek,
    delimiter: Delimiter,
) -> Result<csv::Reader<impl Read + Seek>> {
    remove_tags_line_from_reader(&mut reader)?;
    Ok(csv::ReaderBuilder::new()
        .has_headers(false)
        .flexible(true)
        .comment(Some(b'#'))
        .delimiter(delimiter.byte())
        .from_reader(reader))
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
    use crate::pb::import_export::csv_metadata::MappedNotetype;

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
                tags_column: 0,
                guid_column: 0,
                global_tags: Vec::new(),
                updated_tags: Vec::new(),
                column_labels: vec!["".to_string(); 2],
                deck: Some(CsvDeck::DeckId(1)),
                notetype: Some(CsvNotetype::GlobalNotetype(MappedNotetype {
                    id: 1,
                    field_columns: vec![1, 2],
                })),
                preview: Vec::new(),
                dupe_resolution: 0,
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
                field_columns: vec![3, 1],
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
        metadata.tags_column = 3;
        let notes = import!(metadata, "front,back,foo bar\n");
        assert_eq!(notes[0].tags, &["foo", "bar"]);
    }

    #[test]
    fn should_parse_deck_column() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.deck.replace(CsvDeck::DeckColumn(1));
        let notes = import!(metadata, "front,back\n");
        assert_eq!(notes[0].deck, NameOrId::Name(String::from("front")));
    }

    #[test]
    fn should_parse_notetype_column() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.notetype.replace(CsvNotetype::NotetypeColumn(1));
        metadata.column_labels.push("".to_string());
        let notes = import!(metadata, "Basic,front,back\nCloze,foo,bar\n");
        assert_eq!(notes[0].fields, &["front", "back"]);
        assert_eq!(notes[0].notetype, NameOrId::Name(String::from("Basic")));
        assert_eq!(notes[1].fields, &["foo", "bar"]);
        assert_eq!(notes[1].notetype, NameOrId::Name(String::from("Cloze")));
    }
}
