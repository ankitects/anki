// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;
use std::io::BufRead;
use std::io::BufReader;
use std::io::Cursor;
use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;

use anki_io::read_to_string;
pub use anki_proto::import_export::csv_metadata::Deck as CsvDeck;
pub use anki_proto::import_export::csv_metadata::Delimiter;
pub use anki_proto::import_export::csv_metadata::DupeResolution;
pub use anki_proto::import_export::csv_metadata::MappedNotetype;
pub use anki_proto::import_export::csv_metadata::MatchScope;
pub use anki_proto::import_export::csv_metadata::Notetype as CsvNotetype;
pub use anki_proto::import_export::CsvMetadata;
use itertools::Itertools;
use strum::IntoEnumIterator;

use super::import::build_csv_reader;
use crate::config::I32ConfigKey;
use crate::import_export::text::csv::import::FieldSourceColumns;
use crate::import_export::text::NameOrId;
use crate::import_export::ImportError;
use crate::notetype::NoteField;
use crate::prelude::*;
use crate::text::html_to_text_line;
use crate::text::is_html;
use crate::text::strip_utf8_bom;

/// The maximum number of preview rows.
const PREVIEW_LENGTH: usize = 5;
/// The maximum number of characters per preview field.
const PREVIEW_FIELD_LENGTH: usize = 80;

impl Collection {
    pub fn get_csv_metadata(
        &mut self,
        path: &str,
        delimiter: Option<Delimiter>,
        notetype_id: Option<NotetypeId>,
        deck_id: Option<DeckId>,
        is_html: Option<bool>,
    ) -> Result<CsvMetadata> {
        let text = read_to_string(path)?;
        let mut reader = Cursor::new(text);
        let meta =
            self.get_reader_metadata(&mut reader, delimiter, notetype_id, deck_id, is_html)?;
        if meta.preview.is_empty() {
            return Err(ImportError::EmptyFile.into());
        }
        Ok(meta)
    }

    fn get_reader_metadata(
        &mut self,
        mut reader: impl Read + Seek,
        delimiter: Option<Delimiter>,
        notetype_id: Option<NotetypeId>,
        deck_id: Option<DeckId>,
        is_html: Option<bool>,
    ) -> Result<CsvMetadata> {
        let mut metadata = CsvMetadata::from_config(self);
        let meta_len = self.parse_meta_lines(&mut reader, &mut metadata)? as u64;
        maybe_set_fallback_delimiter(delimiter, &mut metadata, &mut reader, meta_len)?;
        let records = collect_preview_records(&mut metadata, reader)?;
        maybe_set_fallback_is_html(&mut metadata, &records, is_html)?;
        set_preview(&mut metadata, &records)?;
        maybe_set_fallback_columns(&mut metadata)?;
        self.maybe_set_notetype_and_deck(&mut metadata, notetype_id, deck_id)?;
        self.maybe_init_notetype_map(&mut metadata)?;

        Ok(metadata)
    }

    /// Parses the meta head of the file and returns the total of meta bytes.
    fn parse_meta_lines(&mut self, reader: impl Read, metadata: &mut CsvMetadata) -> Result<usize> {
        let mut meta_len = 0;
        let mut reader = BufReader::new(reader);
        let mut line = String::new();
        let mut line_len = reader.read_line(&mut line)?;
        if self.parse_first_line(&line, metadata) {
            meta_len += line_len;
            line.clear();
            line_len = reader.read_line(&mut line)?;
            while self.parse_line(&line, metadata) {
                meta_len += line_len;
                line.clear();
                line_len = reader.read_line(&mut line)?;
            }
        }
        Ok(meta_len)
    }

    /// True if the line is a meta line, i.e. a comment, or starting with
    /// 'tags:'.
    fn parse_first_line(&mut self, line: &str, metadata: &mut CsvMetadata) -> bool {
        let line = strip_utf8_bom(line);
        if let Some(tags) = line.strip_prefix("tags:") {
            metadata.global_tags = collect_tags(tags);
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
            "separator" => {
                if let Some(delimiter) = delimiter_from_value(value) {
                    metadata.delimiter = delimiter as i32;
                    metadata.force_delimiter = true;
                }
            }
            "html" => {
                if let Ok(is_html) = value.to_lowercase().parse() {
                    metadata.is_html = is_html;
                    metadata.force_is_html = true;
                }
            }
            "tags" => metadata.global_tags = collect_tags(value),
            "columns" => {
                if let Ok(columns) = parse_columns(value, metadata.delimiter()) {
                    metadata.column_labels = columns;
                }
            }
            "notetype" => {
                if let Ok(Some(nt)) = self.notetype_by_name_or_id(&NameOrId::parse(value)) {
                    metadata.notetype = Some(new_global_csv_notetype(nt.id));
                }
            }
            "deck" => {
                if let Ok(Some(did)) = self.deck_id_by_name_or_id(&NameOrId::parse(value)) {
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
            "tags column" => {
                if let Ok(n) = value.trim().parse() {
                    metadata.tags_column = n;
                }
            }
            "guid column" => {
                if let Ok(n) = value.trim().parse() {
                    metadata.guid_column = n;
                }
            }
            "match scope" => {
                if let Some(scope) = MatchScope::from_text(value) {
                    metadata.match_scope = scope as i32;
                }
            }
            "if matches" => {
                if let Some(resolution) = DupeResolution::from_text(value) {
                    metadata.dupe_resolution = resolution as i32;
                }
            }
            _ => (),
        }
    }

    /// Ensure notetype and deck are set.
    ///
    /// - When the UI is first loaded, both notetype and deck arguments will be
    ///   None.
    /// - When the UI refreshes due to user changes, the currently-selected deck
    ///   and notetype will be provided.
    /// - Metadata may already have deck and notetype set, if those directives
    ///   were present in the file to import. In the UI refresh case, we
    ///   override them with the current UI values, so that the user can adjust
    ///   the deck/notetype if they wish.
    /// - In the initial load case, if notetype/deck were not specified in file,
    ///   we apply the defaults from defaults_for_adding().
    pub(crate) fn maybe_set_notetype_and_deck(
        &mut self,
        metadata: &mut anki_proto::import_export::CsvMetadata,
        notetype_id: Option<NotetypeId>,
        deck_id: Option<DeckId>,
    ) -> Result<()> {
        let defaults = self.defaults_for_adding(DeckId(0))?;
        if metadata.notetype.is_none() || notetype_id.is_some() {
            metadata.notetype = Some(new_global_csv_notetype(
                notetype_id.unwrap_or(defaults.notetype_id),
            ));
        }
        if metadata.deck.is_none() || deck_id.is_some() {
            metadata.deck = Some(CsvDeck::DeckId(deck_id.unwrap_or(defaults.deck_id).0));
        }
        Ok(())
    }

    fn maybe_init_notetype_map(&mut self, metadata: &mut CsvMetadata) -> Result<()> {
        let meta_columns = metadata.meta_columns();
        if let Some(CsvNotetype::GlobalNotetype(ref mut global)) = metadata.notetype {
            let notetype = self
                .get_notetype(NotetypeId(global.id))?
                .or_not_found(NotetypeId(global.id))?;
            global.field_columns = vec![0; notetype.fields.len()];
            global.field_columns[0] = 1;
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
            maybe_set_tags_column(metadata, &meta_columns);
        }
        Ok(())
    }
}

pub(super) trait CsvMetadataHelpers {
    fn from_config(col: &Collection) -> Self;
    fn deck(&self) -> Result<&CsvDeck>;
    fn notetype(&self) -> Result<&CsvNotetype>;
    fn field_source_columns(&self) -> Result<FieldSourceColumns>;
    fn meta_columns(&self) -> HashSet<usize>;
}

impl CsvMetadataHelpers for CsvMetadata {
    /// Defaults with config values filled in.
    fn from_config(col: &Collection) -> Self {
        Self {
            dupe_resolution: DupeResolution::from_config(col) as i32,
            match_scope: MatchScope::from_config(col) as i32,
            ..Default::default()
        }
    }

    fn deck(&self) -> Result<&CsvDeck> {
        self.deck.as_ref().or_invalid("deck oneof not set")
    }

    fn notetype(&self) -> Result<&CsvNotetype> {
        self.notetype.as_ref().or_invalid("notetype oneof not set")
    }

    fn field_source_columns(&self) -> Result<FieldSourceColumns> {
        Ok(match self.notetype()? {
            CsvNotetype::GlobalNotetype(global) => global
                .field_columns
                .iter()
                .map(|&i| (i > 0).then_some(i as usize))
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

    fn meta_columns(&self) -> HashSet<usize> {
        let mut columns = HashSet::new();
        if let Some(CsvDeck::DeckColumn(deck_column)) = self.deck {
            columns.insert(deck_column as usize);
        }
        if let Some(CsvNotetype::NotetypeColumn(notetype_column)) = self.notetype {
            columns.insert(notetype_column as usize);
        }
        if self.tags_column > 0 {
            columns.insert(self.tags_column as usize);
        }
        if self.guid_column > 0 {
            columns.insert(self.guid_column as usize);
        }
        columns
    }
}

pub(super) trait DupeResolutionExt: Sized {
    fn from_config(col: &Collection) -> Self;
    fn from_text(text: &str) -> Option<Self>;
}

impl DupeResolutionExt for DupeResolution {
    fn from_config(col: &Collection) -> Self {
        Self::try_from(col.get_config_i32(I32ConfigKey::CsvDuplicateResolution)).unwrap_or_default()
    }

    fn from_text(text: &str) -> Option<Self> {
        match text {
            "update current" => Some(Self::Update),
            "keep current" => Some(Self::Preserve),
            "keep both" => Some(Self::Duplicate),
            _ => None,
        }
    }
}

pub(super) trait MatchScopeExt: Sized {
    fn from_config(col: &Collection) -> Self;
    fn from_text(text: &str) -> Option<Self>;
}

impl MatchScopeExt for MatchScope {
    fn from_config(col: &Collection) -> Self {
        Self::try_from(col.get_config_i32(I32ConfigKey::MatchScope)).unwrap_or_default()
    }

    fn from_text(text: &str) -> Option<Self> {
        match text {
            "notetype" => Some(Self::Notetype),
            "notetype + deck" => Some(Self::NotetypeAndDeck),
            _ => None,
        }
    }
}

fn parse_columns(line: &str, delimiter: Delimiter) -> Result<Vec<String>> {
    map_single_record(line, delimiter, |record| {
        record.iter().map(ToString::to_string).collect()
    })
}

fn collect_preview_records(
    metadata: &mut CsvMetadata,
    mut reader: impl Read + Seek,
) -> Result<Vec<csv::StringRecord>> {
    reader.rewind()?;
    let mut csv_reader = build_csv_reader(reader, metadata.delimiter())?;
    csv_reader
        .records()
        .take(PREVIEW_LENGTH)
        .collect::<csv::Result<_>>()
        .or_invalid("invalid csv")
}

fn set_preview(metadata: &mut CsvMetadata, records: &[csv::StringRecord]) -> Result<()> {
    let mut min_len = 1;
    metadata.preview = records
        .iter()
        .enumerate()
        .map(|(idx, record)| {
            let row = build_preview_row(min_len, record, metadata.is_html);
            if idx == 0 {
                min_len = row.vals.len();
            }
            row
        })
        .collect();
    Ok(())
}

fn build_preview_row(
    min_len: usize,
    record: &csv::StringRecord,
    strip_html: bool,
) -> anki_proto::generic::StringList {
    anki_proto::generic::StringList {
        vals: record
            .iter()
            .pad_using(min_len, |_| "")
            .map(|field| {
                if strip_html {
                    html_to_text_line(field, true)
                        .chars()
                        .take(PREVIEW_FIELD_LENGTH)
                        .collect()
                } else {
                    field.chars().take(PREVIEW_FIELD_LENGTH).collect()
                }
            })
            .collect(),
    }
}

pub(super) fn collect_tags(txt: &str) -> Vec<String> {
    txt.split_whitespace()
        .filter(|s| !s.is_empty())
        .map(ToString::to_string)
        .collect()
}

fn map_field_columns_by_index(
    field_columns: &mut [u32],
    column_len: usize,
    meta_columns: &HashSet<usize>,
) {
    let mut field_columns = field_columns.iter_mut();
    for index in 1..column_len + 1 {
        if !meta_columns.contains(&index) {
            if let Some(field_column) = field_columns.next() {
                *field_column = index as u32;
            } else {
                break;
            }
        }
    }
}

fn map_field_columns_by_name(
    field_columns: &mut [u32],
    column_labels: &[String],
    meta_columns: &HashSet<usize>,
    note_fields: &[NoteField],
) {
    let columns: HashMap<&str, usize> = HashMap::from_iter(
        column_labels
            .iter()
            .enumerate()
            .map(|(idx, s)| (s.as_str(), idx + 1))
            .filter(|(_, idx)| !meta_columns.contains(idx)),
    );
    for (column, field) in field_columns.iter_mut().zip(note_fields) {
        if let Some(index) = columns.get(field.name.as_str()) {
            *column = *index as u32;
        }
    }
}

fn ensure_first_field_is_mapped(
    field_columns: &mut [u32],
    column_len: usize,
    meta_columns: &HashSet<usize>,
) -> Result<()> {
    if field_columns[0] == 0 {
        field_columns[0] = (1..column_len + 1)
            .find(|i| !meta_columns.contains(i))
            .ok_or(AnkiError::ImportError {
                source: ImportError::NoFieldColumn,
            })? as u32;
    }
    Ok(())
}

fn maybe_set_fallback_columns(metadata: &mut CsvMetadata) -> Result<()> {
    if metadata.column_labels.is_empty() {
        metadata.column_labels =
            vec![String::new(); metadata.preview.first().map_or(0, |row| row.vals.len())];
    }
    Ok(())
}

fn maybe_set_fallback_is_html(
    metadata: &mut CsvMetadata,
    records: &[csv::StringRecord],
    is_html_option: Option<bool>,
) -> Result<()> {
    if let Some(is_html) = is_html_option {
        metadata.is_html = is_html;
    } else if !metadata.force_is_html {
        metadata.is_html = records.iter().flat_map(|record| record.iter()).any(is_html);
    }
    Ok(())
}

fn maybe_set_fallback_delimiter(
    delimiter: Option<Delimiter>,
    metadata: &mut CsvMetadata,
    mut reader: impl Read + Seek,
    meta_len: u64,
) -> Result<()> {
    if let Some(delim) = delimiter {
        metadata.set_delimiter(delim);
    } else if !metadata.force_delimiter {
        reader.seek(SeekFrom::Start(meta_len))?;
        metadata.set_delimiter(delimiter_from_reader(reader)?);
    }
    Ok(())
}

fn maybe_set_tags_column(metadata: &mut CsvMetadata, meta_columns: &HashSet<usize>) {
    if metadata.tags_column == 0 {
        if let Some(CsvNotetype::GlobalNotetype(ref global)) = metadata.notetype {
            let max_field = global.field_columns.iter().max().copied().unwrap_or(0);
            for idx in (max_field + 1) as usize..=metadata.column_labels.len() {
                if !meta_columns.contains(&idx) {
                    metadata.tags_column = idx as u32;
                    break;
                }
            }
        }
    }
}

fn delimiter_from_value(value: &str) -> Option<Delimiter> {
    let normed = value.to_ascii_lowercase();
    Delimiter::iter().find(|&delimiter| {
        normed.trim() == delimiter.name() || normed.as_bytes() == [delimiter.byte()]
    })
}

fn delimiter_from_reader(mut reader: impl Read) -> Result<Delimiter> {
    let mut buf = [0; 8 * 1024];
    let _ = reader.read(&mut buf)?;
    // TODO: use smarter heuristic
    for delimiter in Delimiter::iter() {
        if buf.contains(&delimiter.byte()) {
            return Ok(delimiter);
        }
    }
    Ok(Delimiter::Space)
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
        .map_err(|_| AnkiError::ImportError {
            source: ImportError::Corrupt,
        })
        .map(op)
}

fn strip_line_ending(line: &str) -> &str {
    line.strip_suffix("\r\n")
        .unwrap_or_else(|| line.strip_suffix('\n').unwrap_or(line))
}

pub(super) trait DelimeterExt {
    fn byte(self) -> u8;
    fn name(self) -> &'static str;
}

impl DelimeterExt for Delimiter {
    fn byte(self) -> u8 {
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

fn new_global_csv_notetype(id: NotetypeId) -> CsvNotetype {
    CsvNotetype::GlobalNotetype(MappedNotetype {
        id: id.0,
        field_columns: Vec::new(),
    })
}

impl NameOrId {
    pub fn parse(s: &str) -> Self {
        if let Ok(id) = s.parse() {
            Self::Id(id)
        } else {
            Self::Name(s.to_string())
        }
    }
}

#[cfg(test)]
pub(in crate::import_export) mod test {
    use std::io::Cursor;

    use super::*;

    macro_rules! metadata {
        ($col:expr,$csv:expr) => {
            metadata!($col, $csv, None)
        };
        ($col:expr,$csv:expr, $delim:expr) => {
            $col.get_reader_metadata(Cursor::new($csv.as_bytes()), $delim, None, None, None)
                .unwrap()
        };
    }

    pub trait CsvMetadataTestExt {
        fn defaults_for_testing() -> Self;
        fn unwrap_deck_id(&self) -> i64;
        fn unwrap_notetype_id(&self) -> i64;
        fn unwrap_notetype_map(&self) -> &[u32];
    }

    impl CsvMetadataTestExt for CsvMetadata {
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
                match_scope: 0,
            }
        }

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
        fn unwrap_notetype_map(&self) -> &[u32] {
            match &self.notetype {
                Some(CsvNotetype::GlobalNotetype(nt)) => &nt.field_columns,
                _ => panic!("no notetype map"),
            }
        }
    }

    #[test]
    fn should_detect_deck_by_name_or_id() {
        let mut col = Collection::new();
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
        let mut col = Collection::new();
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
        let mut col = Collection::new();
        assert_eq!(
            metadata!(col, "#separator:comma\n").delimiter(),
            Delimiter::Comma
        );
        assert_eq!(
            metadata!(col, "#separator:\t\n").delimiter(),
            Delimiter::Tab
        );
        // fallback
        assert_eq!(
            metadata!(col, "#separator:foo\n").delimiter(),
            Delimiter::Space
        );
        assert_eq!(
            metadata!(col, "#separator:♥\n").delimiter(),
            Delimiter::Space
        );
        // pick up from first line
        assert_eq!(metadata!(col, "foo\tbar\n").delimiter(), Delimiter::Tab);
        // override with provided
        assert_eq!(
            metadata!(col, "#separator: \nfoo\tbar\n", Some(Delimiter::Pipe)).delimiter(),
            Delimiter::Pipe
        );
    }

    #[test]
    fn should_enforce_valid_html_flag() {
        let mut col = Collection::new();

        let meta = metadata!(col, "#html:true\n");
        assert!(meta.is_html);
        assert!(meta.force_is_html);

        let meta = metadata!(col, "#html:FALSE\n");
        assert!(!meta.is_html);
        assert!(meta.force_is_html);

        assert!(!metadata!(col, "#html:maybe\n").force_is_html);
    }

    #[test]
    fn should_set_missing_html_flag_by_first_line() {
        let mut col = Collection::new();

        let meta = metadata!(col, "<br/>\n");
        assert!(meta.is_html);
        assert!(!meta.force_is_html);

        // HTML check is field-, not row-based
        assert!(!metadata!(col, "<br,/>\n").is_html);

        assert!(!metadata!(col, "#html:false\n<br>\n").is_html);
    }

    #[test]
    fn should_detect_old_and_new_style_tags() {
        let mut col = Collection::new();
        assert_eq!(metadata!(col, "tags:foo bar\n").global_tags, ["foo", "bar"]);
        assert_eq!(
            metadata!(col, "#tags:foo bar\n").global_tags,
            ["foo", "bar"]
        );
        // only in head
        assert_eq!(
            metadata!(col, "#\n#tags:foo bar\n").global_tags,
            ["foo", "bar"]
        );
        assert_eq!(metadata!(col, "\n#tags:foo bar\n").global_tags, [""; 0]);
        // only on very first line
        assert_eq!(metadata!(col, "#\ntags:foo bar\n").global_tags, [""; 0]);
    }

    #[test]
    fn should_detect_column_number_and_names() {
        let mut col = Collection::new();
        // detect from line
        assert_eq!(metadata!(col, "foo;bar\n").column_labels.len(), 2);
        // detect encoded
        assert_eq!(
            metadata!(col, "#separator:,\nfoo;bar\n")
                .column_labels
                .len(),
            1
        );
        assert_eq!(
            metadata!(col, "#separator:|\nfoo|bar\n")
                .column_labels
                .len(),
            2
        );
        // override
        assert_eq!(
            metadata!(col, "#separator:;\nfoo;bar\n", Some(Delimiter::Pipe))
                .column_labels
                .len(),
            1
        );

        // custom names
        assert_eq!(
            metadata!(col, "#columns:one\ttwo\n").column_labels,
            ["one", "two"]
        );
        assert_eq!(
            metadata!(col, "#separator:|\n#columns:one|two\n").column_labels,
            ["one", "two"]
        );
    }

    #[test]
    fn should_detect_column_number_despite_escaped_line_breaks() {
        let mut col = Collection::new();
        assert_eq!(
            metadata!(col, "\"foo|\nbar\"\tfoo\tbar\n")
                .column_labels
                .len(),
            3
        );
    }

    #[test]
    fn should_map_default_notetype_fields_by_index_if_no_column_names() {
        let mut col = Collection::new();
        let meta = metadata!(col, "#deck column:1\nfoo,bar,baz\n");
        assert_eq!(meta.unwrap_notetype_map(), &[2, 3]);
    }

    #[test]
    fn should_map_default_notetype_fields_by_given_column_names() {
        let mut col = Collection::new();
        let meta = metadata!(col, "#columns:Back\tFront\nfoo,bar,baz\n");
        assert_eq!(meta.unwrap_notetype_map(), &[2, 1]);
    }

    #[test]
    fn should_gather_first_lines_into_preview() {
        let mut col = Collection::new();
        let meta = metadata!(col, "#separator: \nfoo bar\nbaz<br>\n");
        assert_eq!(meta.preview[0].vals, ["foo", "bar"]);
        // html is stripped
        assert_eq!(meta.preview[1].vals, ["baz", ""]);
    }

    #[test]
    fn should_parse_first_first_line_despite_bom() {
        let mut col = Collection::new();
        assert_eq!(
            metadata!(col, "\u{feff}#separator:tab\n").delimiter(),
            Delimiter::Tab
        );
        assert_eq!(metadata!(col, "\u{feff}tags:foo\n").global_tags, ["foo"]);
    }

    #[test]
    fn should_not_set_tags_column_if_all_are_field_columns() {
        let meta_columns = Default::default();
        let mut metadata = CsvMetadata::defaults_for_testing();
        maybe_set_tags_column(&mut metadata, &meta_columns);
        assert_eq!(metadata.tags_column, 0);
    }

    #[test]
    fn should_set_tags_column_to_next_unused_column() {
        let mut meta_columns = HashSet::default();
        meta_columns.insert(3);
        let mut metadata = CsvMetadata::defaults_for_testing();
        metadata.column_labels.push(String::new());
        metadata.column_labels.push(String::new());
        maybe_set_tags_column(&mut metadata, &meta_columns);
        assert_eq!(metadata.tags_column, 4);
    }
}
