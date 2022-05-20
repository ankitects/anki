// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    fs::File,
    io::{BufRead, BufReader},
};

use strum::IntoEnumIterator;

pub use crate::backend_proto::import_export::{
    csv_metadata::{Deck as CsvDeck, Delimiter, MappedNotetype, Notetype as CsvNotetype},
    CsvMetadata,
};
use crate::{error::ImportError, notetype::NoteField, prelude::*};

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
        let mut metadata = CsvMetadata::new();
        let line = self.parse_meta_lines(reader, &mut metadata)?;
        maybe_set_fallback_delimiter(delimiter, &mut metadata, &line);
        maybe_set_fallback_columns(&mut metadata, &line)?;
        self.maybe_set_fallback_notetype(&mut metadata)?;
        self.maybe_init_notetype_map(&mut metadata)?;
        self.maybe_set_fallback_deck(&mut metadata)?;
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
            "html" => metadata.is_html = value.to_lowercase().parse::<bool>().ok(),
            "tags" => metadata.tags = value.trim().to_owned(),
            "columns" => {
                if let Ok(columns) = self.parse_columns(value, metadata) {
                    metadata.column_labels = columns;
                }
            }
            "notetype" => {
                if let Ok(Some(nt)) = self.notetype_for_string(value) {
                    metadata.notetype = Some(CsvNotetype::new_global(nt.id));
                }
            }
            "deck" => {
                if let Ok(Some(did)) = self.deck_id_for_string(value) {
                    metadata.deck = Some(CsvDeck::DeckId(did.0));
                }
            }
            "notetype column" => {
                if let Ok(n) = value.trim().parse() {
                    metadata.notetype = Some(CsvNotetype::NotetypeColumn(n));
                }
            }
            "deck column" => {
                if let Ok(n) = value.trim().parse() {
                    metadata.deck = Some(CsvDeck::DeckColumn(n));
                }
            }
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
            record.iter().map(ToString::to_string).collect()
        })
    }

    fn maybe_set_fallback_notetype(&mut self, metadata: &mut CsvMetadata) -> Result<()> {
        if metadata.notetype.is_none() {
            metadata.notetype = Some(CsvNotetype::new_global(self.fallback_notetype_id()?));
        }
        Ok(())
    }

    fn maybe_set_fallback_deck(&mut self, metadata: &mut CsvMetadata) -> Result<()> {
        if metadata.deck.is_none() {
            metadata.deck = Some(CsvDeck::DeckId(
                metadata
                    .notetype_id()
                    .and_then(|ntid| self.default_deck_for_notetype(ntid).transpose())
                    .unwrap_or_else(|| self.get_current_deck().map(|d| d.id))?
                    .0,
            ));
        }
        Ok(())
    }

    fn maybe_init_notetype_map(&mut self, metadata: &mut CsvMetadata) -> Result<()> {
        let meta_columns = metadata.meta_columns();
        if let Some(CsvNotetype::GlobalNotetype(ref mut global)) = metadata.notetype {
            let notetype = self
                .get_notetype(NotetypeId(global.id))?
                .ok_or(AnkiError::NotFound)?;
            global.field_columns = vec![-1; notetype.fields.len()];
            global.field_columns[0] = 0;
            let column_len = metadata.column_labels.len();
            if metadata.column_labels.iter().all(String::is_empty) {
                map_field_columns_by_index(&mut global.field_columns, column_len, &meta_columns);
            } else {
                map_field_columns_by_name(
                    &mut global.field_columns,
                    &metadata.column_labels,
                    &meta_columns,
                    &notetype.fields,
                );
            }
            ensure_first_field_is_mapped(&mut global.field_columns, column_len, &meta_columns)?;
        }
        Ok(())
    }

    fn fallback_notetype_id(&mut self) -> Result<NotetypeId> {
        Ok(if let Some(notetype_id) = self.get_current_notetype_id() {
            notetype_id
        } else {
            self.storage
                .get_all_notetype_names()?
                .first()
                .ok_or(AnkiError::NotFound)?
                .0
        })
    }
}

fn map_field_columns_by_index(
    field_columns: &mut [i32],
    column_len: usize,
    meta_columns: &HashSet<usize>,
) {
    let mut field_columns = field_columns.iter_mut();
    for index in 0..column_len {
        if !meta_columns.contains(&index) {
            if let Some(field_column) = field_columns.next() {
                *field_column = index as i32;
            } else {
                break;
            }
        }
    }
}

fn map_field_columns_by_name(
    field_columns: &mut [i32],
    column_labels: &[String],
    meta_columns: &HashSet<usize>,
    note_fields: &[NoteField],
) {
    let columns: HashMap<&str, usize> = HashMap::from_iter(
        column_labels
            .iter()
            .enumerate()
            .filter(|(idx, _)| !meta_columns.contains(idx))
            .map(|(idx, s)| (s.as_str(), idx)),
    );
    for (column, field) in field_columns.iter_mut().zip(note_fields) {
        if let Some(index) = columns.get(field.name.as_str()) {
            *column = *index as i32;
        }
    }
}

fn ensure_first_field_is_mapped(
    field_columns: &mut [i32],
    column_len: usize,
    meta_columns: &HashSet<usize>,
) -> Result<()> {
    if field_columns[0] == -1 {
        field_columns[0] = (0..column_len)
            .find(|i| !meta_columns.contains(i))
            .ok_or(AnkiError::ImportError(ImportError::NoFieldColumn))?
            as i32;
    }
    Ok(())
}

fn maybe_set_fallback_columns(metadata: &mut CsvMetadata, line: &str) -> Result<()> {
    if metadata.column_labels.is_empty() {
        let columns = map_single_record(line, metadata.delimiter(), |r| r.len())?;
        metadata.column_labels = vec![String::new(); columns];
    }
    Ok(())
}

fn maybe_set_fallback_delimiter(
    delimiter: Option<Delimiter>,
    metadata: &mut CsvMetadata,
    line: &str,
) {
    if let Some(delim) = delimiter {
        metadata.set_delimiter(delim);
    } else if metadata.delimiter_is_unset() {
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

impl CsvNotetype {
    fn new_global(id: NotetypeId) -> Self {
        Self::GlobalNotetype(MappedNotetype {
            id: id.0,
            field_columns: Vec::new(),
        })
    }
}

impl CsvMetadata {
    fn new() -> Self {
        Self {
            delimiter: -1,
            // FIXME: use optional, so we can use default()?
            tags_column: -1,
            ..Self::default()
        }
    }

    fn delimiter_is_unset(&self) -> bool {
        self.delimiter == -1
    }

    fn notetype_id(&self) -> Option<NotetypeId> {
        if let Some(CsvNotetype::GlobalNotetype(ref global)) = self.notetype {
            Some(NotetypeId(global.id))
        } else {
            None
        }
    }

    pub(super) fn meta_columns(&self) -> HashSet<usize> {
        let mut columns = HashSet::new();
        if let Some(CsvDeck::DeckColumn(deck_column)) = self.deck {
            columns.insert(deck_column as usize);
        }
        if let Some(CsvNotetype::NotetypeColumn(notetype_column)) = self.notetype {
            columns.insert(notetype_column as usize);
        }
        if let Ok(tags_column) = self.tags_column.try_into() {
            columns.insert(tags_column);
        }
        columns
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

    impl CsvMetadata {
        fn unwrap_deck_id(&self) -> i64 {
            match self.deck {
                Some(CsvDeck::DeckId(did)) => did,
                _ => panic!("no deck id"),
            }
        }

        fn unwrap_notetype_id(&self) -> i64 {
            match self.notetype {
                Some(CsvNotetype::GlobalNotetype(ref nt)) => nt.id,
                _ => panic!("no notetype id"),
            }
        }
    }

    #[test]
    fn should_detect_deck_by_name_or_id() {
        let mut col = open_test_collection();
        let deck_id = col.get_or_create_normal_deck("my deck").unwrap().id.0;
        assert_eq!(metadata!(col, "#deck:my deck\n").unwrap_deck_id(), deck_id);
        assert_eq!(
            metadata!(col, format!("#deck:{deck_id}\n")).unwrap_deck_id(),
            deck_id
        );
        // fallback
        assert_eq!(metadata!(col, "#deck:foo\n").unwrap_deck_id(), 1);
        assert_eq!(metadata!(col, "\n").unwrap_deck_id(), 1);
    }

    #[test]
    fn should_detect_notetype_by_name_or_id() {
        let mut col = open_test_collection();
        let basic_id = col.get_notetype_by_name("Basic").unwrap().unwrap().id.0;
        assert_eq!(
            metadata!(col, "#notetype:Basic\n").unwrap_notetype_id(),
            basic_id
        );
        assert_eq!(
            metadata!(col, &format!("#notetype:{basic_id}\n")).unwrap_notetype_id(),
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
        assert_eq!(metadata!(col, "#html:true\n").is_html, Some(true));
        assert_eq!(metadata!(col, "#html:FALSE\n").is_html, Some(false));
        assert_eq!(metadata!(col, "#html:maybe\n").is_html, None);
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
        assert_eq!(metadata!(col, "foo;bar\n").column_labels.len(), 2);
        // detect encoded
        assert_eq!(
            metadata!(col, "#delimiter:,\nfoo;bar\n")
                .column_labels
                .len(),
            1
        );
        assert_eq!(
            metadata!(col, "#delimiter:|\nfoo|bar\n")
                .column_labels
                .len(),
            2
        );
        // override
        assert_eq!(
            metadata!(col, "#delimiter:;\nfoo;bar\n", Some(Delimiter::Pipe))
                .column_labels
                .len(),
            1
        );

        // custom names
        assert_eq!(
            metadata!(col, "#columns:one,two\n").column_labels,
            ["one", "two"]
        );
        assert_eq!(
            metadata!(col, "#delimiter:|\n#columns:one|two\n").column_labels,
            ["one", "two"]
        );
    }

    impl CsvMetadata {
        fn unwrap_notetype_map(&self) -> &[i32] {
            match &self.notetype {
                Some(CsvNotetype::GlobalNotetype(nt)) => &nt.field_columns,
                _ => panic!("no notetype map"),
            }
        }
    }

    #[test]
    fn should_map_default_notetype_fields_by_index_if_no_column_names() {
        let mut col = open_test_collection();
        let meta = metadata!(col, "#deck column:0\nfoo,bar,baz\n");
        assert_eq!(meta.unwrap_notetype_map(), &[1, 2]);
    }

    #[test]
    fn should_map_default_notetype_fields_by_given_column_names() {
        let mut col = open_test_collection();
        let meta = metadata!(col, "#columns:Back,Front\nfoo,bar,baz\n");
        assert_eq!(meta.unwrap_notetype_map(), &[1, 0]);
    }
}
