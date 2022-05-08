// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fs::File,
    io::{BufRead, BufReader, Read, Seek},
};

use crate::{
    import_export::{
        text::{csv::Column, ForeignData, ForeignNote},
        NoteLog,
    },
    prelude::*,
};

impl Collection {
    pub fn import_csv(
        &mut self,
        path: &str,
        deck_id: DeckId,
        notetype_id: NotetypeId,
        columns: Vec<Column>,
        delimiter: u8,
        //allow_html: bool,
    ) -> Result<OpOutput<NoteLog>> {
        let notetype = self.get_notetype(notetype_id)?.ok_or(AnkiError::NotFound)?;
        let fields_len = notetype.fields.len();
        let file = File::open(path)?;
        let notes = deserialize_csv(file, &columns, fields_len, delimiter)?;

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

fn deserialize_csv(
    reader: impl Read + Seek,
    columns: &[Column],
    fields_len: usize,
    delimiter: u8,
) -> Result<Vec<ForeignNote>> {
    let mut reader = csv::ReaderBuilder::new()
        .has_headers(false)
        .flexible(true)
        .comment(Some(b'#'))
        .delimiter(delimiter)
        .trim(csv::Trim::All)
        .from_reader(reader_without_tags_line(reader)?);
    deserialize_csv_reader(&mut reader, columns, fields_len)
}

/// Returns a reader with the first line stripped if it starts with "tags:",
/// which is allowed for historic reasons.
fn reader_without_tags_line(reader: impl Read + Seek) -> Result<impl Read> {
    // FIXME: shouldn't pass a buffered reader to csv
    // https://docs.rs/csv/latest/csv/struct.ReaderBuilder.html#method.from_reader
    let mut buf_reader = BufReader::new(reader);
    let mut first_line = String::new();
    buf_reader.read_line(&mut first_line)?;
    if !first_line.starts_with("tags:") {
        buf_reader.rewind()?;
    }
    Ok(buf_reader)
}

fn deserialize_csv_reader(
    reader: &mut csv::Reader<impl Read>,
    columns: &[Column],
    fields_len: usize,
) -> Result<Vec<ForeignNote>> {
    reader
        .records()
        .into_iter()
        .map(|res| {
            res.map(|record| ForeignNote::from_record(record, columns, fields_len))
                .map_err(Into::into)
        })
        .collect()
}

impl ForeignNote {
    fn from_record(record: csv::StringRecord, columns: &[Column], fields_len: usize) -> Self {
        let mut note = Self {
            fields: vec!["".to_string(); fields_len],
            ..Default::default()
        };
        for (&column, value) in columns.iter().zip(record.iter()) {
            note.add_column_value(column, value);
        }
        note
    }

    fn add_column_value(&mut self, column: Column, value: &str) {
        match column {
            Column::Ignore => (),
            Column::Field(idx) => {
                if let Some(field) = self.fields.get_mut(idx) {
                    field.push_str(value)
                }
            }
            Column::Tags => self.tags.extend(value.split(' ').map(ToString::to_string)),
        }
    }
}

#[cfg(test)]
mod test {
    use std::io::Cursor;

    use super::*;

    macro_rules! assert_imported_fields {
        ($options:expr,$csv:expr, $expected:expr) => {
            let reader = Cursor::new($csv);
            let notes = deserialize_csv(
                reader,
                &$options.columns,
                $options.fields_len,
                $options.delimiter,
            )
            .unwrap();
            assert_eq!(notes.len(), $expected.len());
            for (note, fields) in notes.iter().zip($expected.iter()) {
                assert_eq!(note.fields.len(), fields.len());
                for (note_field, field) in note.fields.iter().zip(fields.iter()) {
                    assert_eq!(note_field, field);
                }
            }
        };
    }

    struct CsvOptions {
        columns: Vec<Column>,
        fields_len: usize,
        delimiter: u8,
    }

    impl CsvOptions {
        fn new() -> Self {
            Self {
                columns: vec![Column::Field(0), Column::Field(1)],
                fields_len: 2,
                delimiter: b',',
            }
        }

        fn add_column(mut self, column: Column) -> Self {
            self.columns.push(column);
            self
        }

        fn columns(mut self, columns: Vec<Column>) -> Self {
            self.columns = columns;
            self
        }

        fn fields_len(mut self, fields_len: usize) -> Self {
            self.fields_len = fields_len;
            self
        }

        fn delimiter(mut self, delimiter: u8) -> Self {
            self.delimiter = delimiter;
            self
        }
    }

    #[test]
    fn should_allow_missing_columns() {
        let options = CsvOptions::new().add_column(Column::Field(2)).fields_len(4);
        assert_imported_fields!(
            options,
            "front,back\nfoo\n",
            &[&["front", "back", "", ""], &["foo", "", "", ""]]
        );
    }

    #[test]
    fn should_respect_custom_delimiter() {
        let options = CsvOptions::new().delimiter(b'|');
        assert_imported_fields!(options, "fr,ont|ba,ck\n", &[&["fr,ont", "ba,ck"]]);
    }

    #[test]
    fn should_ignore_first_line_starting_with_tags() {
        let options = CsvOptions::new();
        assert_imported_fields!(options, "tags:foo\nfront,back\n", &[&["front", "back"]]);
    }

    #[test]
    fn should_respect_column_remapping() {
        let options =
            CsvOptions::new().columns(vec![Column::Field(1), Column::Ignore, Column::Field(0)]);
        assert_imported_fields!(options, "front,foo,back\n", &[&["back", "front"]]);
    }

    #[test]
    fn should_ignore_lines_starting_with_number_sign() {
        let options = CsvOptions::new();
        assert_imported_fields!(options, "#foo\nfront,back\n#bar\n", &[&["front", "back"]]);
    }
}
