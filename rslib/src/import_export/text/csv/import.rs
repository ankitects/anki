// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::BufRead;
use std::io::BufReader;
use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;

use anki_io::open_file;

use crate::import_export::text::csv::metadata::CsvDeck;
use crate::import_export::text::csv::metadata::CsvMetadata;
use crate::import_export::text::csv::metadata::CsvMetadataHelpers;
use crate::import_export::text::csv::metadata::CsvNotetype;
use crate::import_export::text::csv::metadata::DelimeterExt;
use crate::import_export::text::csv::metadata::Delimiter;
use crate::import_export::text::ForeignData;
use crate::import_export::text::ForeignNote;
use crate::import_export::text::NameOrId;
use crate::import_export::NoteLog;
use crate::prelude::*;
use crate::text::strip_utf8_bom;

impl Collection {
    pub fn import_csv(&mut self, path: &str, metadata: CsvMetadata) -> Result<OpOutput<NoteLog>> {
        let progress = self.new_progress_handler();
        let file = open_file(path)?;
        let mut ctx = ColumnContext::new(&metadata)?;
        let notes = ctx.deserialize_csv(file, metadata.delimiter())?;
        let mut data = ForeignData::from(metadata);
        data.notes = notes;
        data.import(self, progress)
    }
}

impl From<CsvMetadata> for ForeignData {
    fn from(metadata: CsvMetadata) -> Self {
        ForeignData {
            dupe_resolution: metadata.dupe_resolution(),
            match_scope: metadata.match_scope(),
            default_deck: metadata.deck().map(|d| d.name_or_id()).unwrap_or_default(),
            default_notetype: metadata
                .notetype()
                .map(|nt| nt.name_or_id())
                .unwrap_or_default(),
            global_tags: metadata.global_tags,
            updated_tags: metadata.updated_tags,
            ..Default::default()
        }
    }
}

trait CsvDeckExt {
    fn name_or_id(&self) -> NameOrId;
    fn column(&self) -> Option<usize>;
}

impl CsvDeckExt for CsvDeck {
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

trait CsvNotetypeExt {
    fn name_or_id(&self) -> NameOrId;
    fn column(&self) -> Option<usize>;
}

impl CsvNotetypeExt for CsvNotetype {
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
pub(super) type FieldSourceColumns = Vec<Option<usize>>;

// Column indices are 1-based.
struct ColumnContext {
    tags_column: Option<usize>,
    guid_column: Option<usize>,
    deck_column: Option<usize>,
    notetype_column: Option<usize>,
    /// Source column indices for the fields of a notetype
    field_source_columns: FieldSourceColumns,
    /// How fields are converted to strings. Used for escaping HTML if
    /// appropriate.
    stringify: fn(&str) -> String,
}

impl ColumnContext {
    fn new(metadata: &CsvMetadata) -> Result<Self> {
        Ok(Self {
            tags_column: (metadata.tags_column > 0).then_some(metadata.tags_column as usize),
            guid_column: (metadata.guid_column > 0).then_some(metadata.guid_column as usize),
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
            .map(|res| {
                res.or_invalid("invalid csv")
                    .map(|record| self.foreign_note_from_record(&record))
            })
            .collect()
    }

    fn foreign_note_from_record(&self, record: &csv::StringRecord) -> ForeignNote {
        ForeignNote {
            notetype: name_or_id_from_record_column(self.notetype_column, record),
            fields: self.gather_note_fields(record),
            tags: self.gather_tags(record),
            deck: name_or_id_from_record_column(self.deck_column, record),
            guid: str_from_record_column(self.guid_column, record),
            ..Default::default()
        }
    }

    fn gather_tags(&self, record: &csv::StringRecord) -> Option<Vec<String>> {
        self.tags_column.and_then(|i| record.get(i - 1)).map(|s| {
            s.split_whitespace()
                .filter(|s| !s.is_empty())
                .map(ToString::to_string)
                .collect()
        })
    }

    fn gather_note_fields(&self, record: &csv::StringRecord) -> Vec<Option<String>> {
        let stringify = self.stringify;
        self.field_source_columns
            .iter()
            .map(|opt| opt.and_then(|idx| record.get(idx - 1)).map(stringify))
            .collect()
    }
}

fn str_from_record_column(column: Option<usize>, record: &csv::StringRecord) -> String {
    column
        .and_then(|i| record.get(i - 1))
        .unwrap_or_default()
        .to_string()
}

fn name_or_id_from_record_column(column: Option<usize>, record: &csv::StringRecord) -> NameOrId {
    NameOrId::parse(column.and_then(|i| record.get(i - 1)).unwrap_or_default())
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
        |s| htmlescape::encode_minimal(s).replace('\n', "<br>")
    }
}

/// If the reader's first line starts with "tags:", which is allowed for
/// historic reasons, seek to the second line.
fn remove_tags_line_from_reader(reader: &mut (impl Read + Seek)) -> Result<()> {
    let mut buf_reader = BufReader::new(reader);
    let mut first_line = String::new();
    buf_reader.read_line(&mut first_line)?;
    let offset = if strip_utf8_bom(&first_line).starts_with("tags:") {
        first_line.len()
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

    use anki_proto::import_export::csv_metadata::MappedNotetype;

    use super::super::metadata::test::CsvMetadataTestExt;
    use super::*;

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
            assert_eq!(fields.len(), $expected.len());
            for (note_fields, note_expected) in fields.iter().zip($expected.iter()) {
                assert_field_eq!(note_fields, note_expected);
            }
        };
    }

    macro_rules! assert_field_eq {
        ($fields:expr, $expected:expr) => {
            assert_eq!($fields.len(), $expected.len());
            for (field, expected) in $fields.iter().zip($expected.iter()) {
                assert_eq!(&field.as_ref().map(String::as_str), expected);
            }
        };
    }

    #[test]
    fn should_allow_missing_columns() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "foo\n", [[Some("foo"), None]]);
    }

    #[test]
    fn should_respect_custom_delimiter() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.set_delimiter(Delimiter::Pipe);
        assert_imported_fields!(
            metadata,
            "fr,ont|ba,ck\n",
            [[Some("fr,ont"), Some("ba,ck")]]
        );
    }

    #[test]
    fn should_ignore_first_line_starting_with_tags() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(
            metadata,
            "tags:foo\nfront,back\n",
            [[Some("front"), Some("back")]]
        );
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
        assert_imported_fields!(
            metadata,
            "front,foo,back\n",
            [[Some("back"), Some("front")]]
        );
    }

    #[test]
    fn should_ignore_lines_starting_with_number_sign() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(
            metadata,
            "#foo\nfront,back\n#bar\n",
            [[Some("front"), Some("back")]]
        );
    }

    #[test]
    fn should_escape_html_entities_if_csv_is_html() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "<hr>\n", [[Some("&lt;hr&gt;"), None]]);
        metadata.is_html = true;
        assert_imported_fields!(metadata, "<hr>\n", [[Some("<hr>"), None]]);
    }

    #[test]
    fn should_parse_tag_column() {
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.tags_column = 3;
        let notes = import!(metadata, "front,back,foo bar\n");
        assert_eq!(notes[0].tags.as_ref().unwrap(), &["foo", "bar"]);
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
        assert_field_eq!(notes[0].fields, [Some("front"), Some("back")]);
        assert_eq!(notes[0].notetype, NameOrId::Name(String::from("Basic")));
        assert_field_eq!(notes[1].fields, [Some("foo"), Some("bar")]);
        assert_eq!(notes[1].notetype, NameOrId::Name(String::from("Cloze")));
    }

    #[test]
    fn should_ignore_bom() {
        let metadata = CsvMetadata::defaults_for_testing();
        assert_imported_fields!(metadata, "\u{feff}foo,bar\n", [[Some("foo"), Some("bar")]]);
        assert!(import!(metadata, "\u{feff}#foo\n").is_empty());
        assert!(import!(metadata, "\u{feff}#html:true\n").is_empty());
        assert!(import!(metadata, "\u{feff}tags:foo\n").is_empty());
    }
}
