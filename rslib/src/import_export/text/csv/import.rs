// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fs::File,
    io::{BufRead, BufReader, Read, Seek, SeekFrom},
};

use crate::{
    import_export::{
        text::{
            csv::{metadata::Delimiter, Column},
            ForeignData, ForeignNote,
        },
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
        delimiter: Delimiter,
        is_html: bool,
    ) -> Result<OpOutput<NoteLog>> {
        let notetype = self.get_notetype(notetype_id)?.ok_or(AnkiError::NotFound)?;
        let fields_len = notetype.fields.len();
        let file = File::open(path)?;
        let notes = deserialize_csv(file, &columns, fields_len, delimiter, is_html)?;

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
    mut reader: impl Read + Seek,
    columns: &[Column],
    fields_len: usize,
    delimiter: Delimiter,
    is_html: bool,
) -> Result<Vec<ForeignNote>> {
    remove_tags_line_from_reader(&mut reader)?;
    let mut csv_reader = csv::ReaderBuilder::new()
        .has_headers(false)
        .flexible(true)
        .comment(Some(b'#'))
        .delimiter(delimiter.byte())
        .from_reader(reader);
    deserialize_csv_reader(&mut csv_reader, columns, fields_len, is_html)
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

fn deserialize_csv_reader(
    reader: &mut csv::Reader<impl Read>,
    columns: &[Column],
    fields_len: usize,
    is_html: bool,
) -> Result<Vec<ForeignNote>> {
    reader
        .records()
        .into_iter()
        .map(|res| {
            res.map(|record| ForeignNote::from_record(record, columns, fields_len, is_html))
                .map_err(Into::into)
        })
        .collect()
}

impl ForeignNote {
    fn from_record(
        record: csv::StringRecord,
        columns: &[Column],
        fields_len: usize,
        is_html: bool,
    ) -> Self {
        let mut note = Self {
            fields: vec!["".to_string(); fields_len],
            ..Default::default()
        };
        for (&column, value) in columns.iter().zip(record.iter()) {
            note.add_column_value(column, value, is_html);
        }
        note
    }

    fn add_column_value(&mut self, column: Column, value: &str, is_html: bool) {
        match column {
            Column::Ignore => (),
            Column::Field(idx) => {
                if let Some(field) = self.fields.get_mut(idx) {
                    *field = if is_html {
                        value.to_string()
                    } else {
                        htmlescape::encode_minimal(value)
                    };
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
                $options.is_html,
            )
            .unwrap();
            let fields: Vec<_> = notes.into_iter().map(|note| note.fields).collect();
            assert_eq!(fields, $expected);
        };
    }

    struct CsvOptions {
        columns: Vec<Column>,
        fields_len: usize,
        delimiter: Delimiter,
        is_html: bool,
    }

    impl CsvOptions {
        fn new() -> Self {
            Self {
                columns: vec![Column::Field(0), Column::Field(1)],
                fields_len: 2,
                delimiter: Delimiter::Comma,
                is_html: false,
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

        fn delimiter(mut self, delimiter: Delimiter) -> Self {
            self.delimiter = delimiter;
            self
        }

        #[allow(clippy::wrong_self_convention)]
        fn is_html(mut self, is_html: bool) -> Self {
            self.is_html = is_html;
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
        let options = CsvOptions::new().delimiter(Delimiter::Pipe);
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

    #[test]
    fn should_escape_html_entities_if_csv_is_html() {
        assert_imported_fields!(CsvOptions::new(), "<hr>\n", &[&["&lt;hr&gt;", ""]]);
        let with_html = CsvOptions::new().is_html(true);
        assert_imported_fields!(with_html, "<hr>\n", &[&["<hr>", ""]]);
    }
}
