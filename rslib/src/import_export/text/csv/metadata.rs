// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fs::File,
    io::{BufRead, BufReader},
};

use strum::IntoEnumIterator;

pub use crate::backend_proto::csv_metadata::Delimiter;
use crate::{
    backend_proto::CsvMetadata, error::ImportError, import_export::text::import::NotetypeForString,
    prelude::*,
};

impl Collection {
    pub fn get_csv_metadata(
        &mut self,
        path: &str,
        delimiter: Option<Delimiter>,
    ) -> Result<CsvMetadata> {
        let reader = BufReader::new(File::open(path)?);
        self.get_reader_metadata(reader, delimiter)
    }

    fn get_reader_metadata(
        &mut self,
        reader: impl BufRead,
        delimiter: Option<Delimiter>,
    ) -> Result<CsvMetadata> {
        let mut metadata = CsvMetadata {
            // mark as unset
            delimiter: -1,
            ..Default::default()
        };
        let line = self.parse_meta_lines(reader, &mut metadata)?;
        set_delimiter(delimiter, &mut metadata, &line);
        set_columns(&mut metadata, &line, &self.tr)?;
        Ok(metadata)
    }

    /// Parses the meta head of the file, and returns the first content line.
    fn parse_meta_lines(
        &mut self,
        mut reader: impl BufRead,
        metadata: &mut CsvMetadata,
    ) -> Result<String> {
        let mut line = String::new();
        reader.read_line(&mut line)?;
        if self.parse_first_line(&line, metadata) {
            line.clear();
            reader.read_line(&mut line)?;
            while self.parse_line(&line, metadata) {
                line.clear();
                reader.read_line(&mut line)?;
            }
        }
        Ok(line)
    }

    /// True if the line is a meta line, i.e. a comment, or starting with 'tags:'.
    fn parse_first_line(&mut self, line: &str, metadata: &mut CsvMetadata) -> bool {
        if let Some(tags) = line.strip_prefix("tags:") {
            metadata.tags = tags.trim().to_owned();
            true
        } else {
            self.parse_line(line, metadata)
        }
    }

    /// True if the line is a comment.
    fn parse_line(&mut self, line: &str, metadata: &mut CsvMetadata) -> bool {
        if let Some(l) = line.strip_prefix('#') {
            if let Some((key, value)) = l.split_once(':') {
                self.parse_meta_value(key, strip_line_ending(value), metadata);
            }
            true
        } else {
            false
        }
    }

    fn parse_meta_value(&mut self, key: &str, value: &str, metadata: &mut CsvMetadata) {
        match key.trim().to_ascii_lowercase().as_str() {
            "delimiter" => {
                if let Some(delimiter) = delimiter_from_value(value) {
                    metadata.delimiter = delimiter as i32;
                }
            }
            "tags" => metadata.tags = value.trim().to_owned(),
            "columns" => {
                if let Ok(columns) = self.parse_columns(value, metadata) {
                    metadata.columns = columns;
                }
            }
            "deck" => {
                if let Ok(Some(did)) = self.deck_id_for_string(value) {
                    metadata.deck_id = did.0;
                }
            }
            "notetype" => {
                if let Ok(Some(nt)) = self.notetype_for_string(value) {
                    metadata.notetype_id = nt.id.0;
                }
            }
            "is_html" => metadata.is_html = value.to_lowercase().parse::<bool>().ok(),

            _ => (),
        }
    }

    fn parse_columns(&mut self, line: &str, metadata: &mut CsvMetadata) -> Result<Vec<String>> {
        let delimiter = if metadata.delimiter != -1 {
            metadata.delimiter()
        } else {
            delimiter_from_line(line)
        };
        map_single_record(line, delimiter, |record| {
            record
                .iter()
                .enumerate()
                .map(|(idx, s)| self.column_label(idx, s))
                .collect()
        })
    }

    fn column_label(&self, idx: usize, column: &str) -> String {
        match column.trim() {
            "" => self.tr.importing_column(idx + 1).to_string(),
            "tags" => self.tr.editing_tags().to_string(),
            s => s.to_string(),
        }
    }
}

fn set_columns(metadata: &mut CsvMetadata, line: &str, tr: &I18n) -> Result<()> {
    if metadata.columns.is_empty() {
        let columns = map_single_record(line, metadata.delimiter(), |r| r.len())?;
        metadata.columns = (0..columns)
            .map(|i| tr.importing_column(i + 1).to_string())
            .collect();
    }
    Ok(())
}

fn set_delimiter(delimiter: Option<Delimiter>, metadata: &mut CsvMetadata, line: &str) {
    if let Some(delim) = delimiter {
        metadata.set_delimiter(delim);
    } else if metadata.delimiter == -1 {
        metadata.set_delimiter(delimiter_from_line(line));
    }
}

fn delimiter_from_value(value: &str) -> Option<Delimiter> {
    let normed = value.to_ascii_lowercase();
    for delimiter in Delimiter::iter() {
        if normed.trim() == delimiter.name() || normed.as_bytes() == [delimiter.byte()] {
            return Some(delimiter);
        }
    }
    None
}

fn delimiter_from_line(line: &str) -> Delimiter {
    // TODO: use smarter heuristic
    for delimiter in Delimiter::iter() {
        if line.contains(delimiter.byte() as char) {
            return delimiter;
        }
    }
    Delimiter::Space
}

fn map_single_record<T>(
    line: &str,
    delimiter: Delimiter,
    op: impl FnOnce(&csv::StringRecord) -> T,
) -> Result<T> {
    csv::ReaderBuilder::new()
        .delimiter(delimiter.byte())
        .from_reader(line.as_bytes())
        .headers()
        .map_err(|_| AnkiError::ImportError(ImportError::Corrupt))
        .map(op)
}

fn strip_line_ending(line: &str) -> &str {
    line.strip_suffix("\r\n")
        .unwrap_or_else(|| line.strip_suffix('\n').unwrap_or(line))
}

impl Delimiter {
    pub fn byte(self) -> u8 {
        match self {
            Delimiter::Comma => b',',
            Delimiter::Semicolon => b';',
            Delimiter::Tab => b'\t',
            Delimiter::Space => b' ',
            Delimiter::Pipe => b'|',
            Delimiter::Colon => b':',
        }
    }

    fn name(self) -> &'static str {
        match self {
            Delimiter::Comma => "comma",
            Delimiter::Semicolon => "semicolon",
            Delimiter::Tab => "tab",
            Delimiter::Space => "space",
            Delimiter::Pipe => "pipe",
            Delimiter::Colon => "colon",
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;

    macro_rules! metadata {
        ($col:expr,$csv:expr) => {
            metadata!($col, $csv, None)
        };
        ($col:expr,$csv:expr, $delim:expr) => {
            $col.get_reader_metadata(BufReader::new($csv.as_bytes()), $delim)
                .unwrap()
        };
    }

    #[test]
    fn should_detect_deck_by_name_or_id() {
        let mut col = open_test_collection();
        assert_eq!(metadata!(col, "#deck:Default\n").deck_id, 1);
        assert_eq!(metadata!(col, "#deck:1\n").deck_id, 1);
    }

    #[test]
    fn should_detect_notetype_by_name_or_id() {
        let mut col = open_test_collection();
        let basic_id = col.get_notetype_by_name("Basic").unwrap().unwrap().id.0;
        assert_eq!(metadata!(col, "#notetype:Basic\n").notetype_id, basic_id);
        assert_eq!(
            metadata!(col, &format!("#notetype:{basic_id}\n")).notetype_id,
            basic_id
        );
    }

    #[test]
    fn should_detect_valid_delimiters() {
        let mut col = open_test_collection();
        assert_eq!(
            metadata!(col, "#delimiter:comma\n").delimiter(),
            Delimiter::Comma
        );
        assert_eq!(
            metadata!(col, "#delimiter:\t\n").delimiter(),
            Delimiter::Tab
        );
        // fallback
        assert_eq!(
            metadata!(col, "#delimiter:foo\n").delimiter(),
            Delimiter::Space
        );
        assert_eq!(
            metadata!(col, "#delimiter:♥\n").delimiter(),
            Delimiter::Space
        );
        // pick up from first line
        assert_eq!(metadata!(col, "foo\tbar\n").delimiter(), Delimiter::Tab);
        // override with provided
        assert_eq!(
            metadata!(col, "#delimiter: \nfoo\tbar\n", Some(Delimiter::Pipe)).delimiter(),
            Delimiter::Pipe
        );
    }

    #[test]
    fn should_detect_valid_html_toggle() {
        let mut col = open_test_collection();
        assert_eq!(metadata!(col, "#is_html:true\n").is_html, Some(true));
        assert_eq!(metadata!(col, "#is_html:FALSE\n").is_html, Some(false));
        assert_eq!(metadata!(col, "#is_html:maybe\n").is_html, None);
    }

    #[test]
    fn should_detect_old_and_new_style_tags() {
        let mut col = open_test_collection();
        assert_eq!(&metadata!(col, "tags:foo bar\n").tags, "foo bar");
        assert_eq!(&metadata!(col, "#tags:foo bar\n").tags, "foo bar");
        // only in head
        assert_eq!(&metadata!(col, "#\n#tags:foo bar\n").tags, "foo bar");
        assert_eq!(&metadata!(col, "\n#tags:foo bar\n").tags, "");
        // only on very first line
        assert_eq!(&metadata!(col, "#\ntags:foo bar\n").tags, "");
    }

    #[test]
    fn should_detect_column_number_and_names() {
        let mut col = open_test_collection();
        // detect from line
        assert_eq!(
            metadata!(col, "foo;bar\n").columns,
            ["Column 1", "Column 2"]
        );
        // detect encoded
        assert_eq!(
            metadata!(col, "#delimiter:,\nfoo;bar\n").columns,
            ["Column 1"]
        );
        assert_eq!(
            metadata!(col, "#delimiter:|\nfoo|bar\n").columns,
            ["Column 1", "Column 2"]
        );
        // override
        assert_eq!(
            metadata!(col, "#delimiter:;\nfoo;bar\n", Some(Delimiter::Pipe)).columns,
            ["Column 1"]
        );

        // custom names
        assert_eq!(metadata!(col, "#columns:one,two\n").columns, ["one", "two"]);
        assert_eq!(
            metadata!(col, "#delimiter:|\n#columns:one|two\n").columns,
            ["one", "two"]
        );
        // fill in missing
        assert_eq!(
            metadata!(col, "#columns:one, ,two,\n").columns,
            ["one", "Column 2", "two", "Column 4"]
        );
        // fill in special names
        assert_eq!(metadata!(col, "#columns:tags\n").columns, ["Tags"]);
    }
}
