// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashMap,
    fs::{read_dir, DirEntry, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
};

use prost::Message;
use tempfile::NamedTempFile;
use zip::{write::FileOptions, CompressionMethod, ZipWriter};
use zstd::{
    stream::{raw::Encoder as RawEncoder, zio::Writer},
    Encoder,
};

use super::super::{MediaEntries, MediaEntry, Meta, Version};
use crate::{
    collection::CollectionBuilder, media::files::sha1_of_data, prelude::*, text::normalize_to_nfc,
};

/// Enable multithreaded compression if over this size. For smaller files,
/// multithreading makes things slower, and in initial tests, the crossover
/// point was somewhere between 1MB and 10MB on a many-core system.
const MULTITHREAD_MIN_BYTES: usize = 10 * 1024 * 1024;

impl Collection {
    pub fn export_colpkg(
        self,
        out_path: impl AsRef<Path>,
        include_media: bool,
        legacy: bool,
        progress_fn: impl FnMut(usize),
    ) -> Result<()> {
        let colpkg_name = out_path.as_ref();
        let src_path = self.col_path.clone();
        let src_media_folder = if include_media {
            Some(self.media_folder.clone())
        } else {
            None
        };
        let tr = self.tr.clone();
        // FIXME: downgrade on v3 export is superfluous at current schema version. We don't
        // want things to break when the schema is bumped in the future, so perhaps the
        // exporting code should be downgrading to 18 instead of 11 (which will probably require
        // changing the boolean to an enum).
        self.close(true)?;
        export_collection_file(
            &colpkg_name,
            &src_path,
            src_media_folder,
            legacy,
            &tr,
            progress_fn,
        )
    }
}

fn export_collection_file(
    out_path: impl AsRef<Path>,
    col_path: impl AsRef<Path>,
    media_dir: Option<PathBuf>,
    legacy: bool,
    tr: &I18n,
    progress_fn: impl FnMut(usize),
) -> Result<()> {
    let meta = if legacy {
        Meta::new_legacy()
    } else {
        Meta::new()
    };
    let mut col_file = File::open(col_path)?;
    let col_size = col_file.metadata()?.len() as usize;
    export_collection(
        meta,
        out_path,
        &mut col_file,
        col_size,
        media_dir,
        tr,
        progress_fn,
    )
}

/// Write copied collection data without any media.
pub(crate) fn export_colpkg_from_data(
    out_path: impl AsRef<Path>,
    mut col_data: &[u8],
    tr: &I18n,
) -> Result<()> {
    let col_size = col_data.len();
    export_collection(
        Meta::new(),
        out_path,
        &mut col_data,
        col_size,
        None,
        tr,
        |_| (),
    )
}

fn export_collection(
    meta: Meta,
    out_path: impl AsRef<Path>,
    col: &mut impl Read,
    col_size: usize,
    media_dir: Option<PathBuf>,
    tr: &I18n,
    progress_fn: impl FnMut(usize),
) -> Result<()> {
    let out_file = File::create(&out_path)?;
    let mut zip = ZipWriter::new(out_file);

    zip.start_file("meta", file_options_stored())?;
    let mut meta_bytes = vec![];
    meta.encode(&mut meta_bytes)?;
    zip.write_all(&meta_bytes)?;
    write_collection(&meta, &mut zip, col, col_size)?;
    write_dummy_collection(&mut zip, tr)?;
    write_media(&meta, &mut zip, media_dir, progress_fn)?;
    zip.finish()?;

    Ok(())
}

fn file_options_stored() -> FileOptions {
    FileOptions::default().compression_method(CompressionMethod::Stored)
}

fn write_collection(
    meta: &Meta,
    zip: &mut ZipWriter<File>,
    col: &mut impl Read,
    size: usize,
) -> Result<()> {
    if meta.zstd_compressed() {
        zip.start_file(meta.collection_filename(), file_options_stored())?;
        zstd_copy(col, zip, size)?;
    } else {
        zip.start_file(meta.collection_filename(), FileOptions::default())?;
        io::copy(col, zip)?;
    }
    Ok(())
}

fn write_dummy_collection(zip: &mut ZipWriter<File>, tr: &I18n) -> Result<()> {
    let mut tempfile = create_dummy_collection_file(tr)?;
    zip.start_file(
        Version::Legacy1.collection_filename(),
        file_options_stored(),
    )?;
    io::copy(&mut tempfile, zip)?;

    Ok(())
}

fn create_dummy_collection_file(tr: &I18n) -> Result<NamedTempFile> {
    let tempfile = NamedTempFile::new()?;
    let mut dummy_col = CollectionBuilder::new(tempfile.path()).build()?;
    dummy_col.add_dummy_note(tr)?;
    dummy_col
        .storage
        .db
        .execute_batch("pragma page_size=512; pragma journal_mode=delete; vacuum;")?;
    dummy_col.close(true)?;

    Ok(tempfile)
}

impl Collection {
    fn add_dummy_note(&mut self, tr: &I18n) -> Result<()> {
        let notetype = self.get_notetype_by_name("basic")?.unwrap();
        let mut note = notetype.new_note();
        note.set_field(0, tr.exporting_colpkg_too_new())?;
        self.add_note(&mut note, DeckId(1))?;
        Ok(())
    }
}

/// Copy contents of reader into writer, compressing as we copy.
fn zstd_copy(reader: &mut impl Read, writer: &mut impl Write, size: usize) -> Result<()> {
    let mut encoder = Encoder::new(writer, 0)?;
    if size > MULTITHREAD_MIN_BYTES {
        encoder.multithread(num_cpus::get() as u32)?;
    }
    io::copy(reader, &mut encoder)?;
    encoder.finish()?;
    Ok(())
}

fn write_media(
    meta: &Meta,
    zip: &mut ZipWriter<File>,
    media_dir: Option<PathBuf>,
    progress_fn: impl FnMut(usize),
) -> Result<()> {
    let mut media_entries = vec![];

    if let Some(media_dir) = media_dir {
        write_media_files(meta, zip, &media_dir, &mut media_entries, progress_fn)?;
    }

    write_media_map(meta, media_entries, zip)?;

    Ok(())
}

fn write_media_map(
    meta: &Meta,
    media_entries: Vec<MediaEntry>,
    zip: &mut ZipWriter<File>,
) -> Result<()> {
    zip.start_file("media", file_options_stored())?;
    let encoded_bytes = if meta.media_list_is_hashmap() {
        let map: HashMap<String, &str> = media_entries
            .iter()
            .enumerate()
            .map(|(k, entry)| (k.to_string(), entry.name.as_str()))
            .collect();
        serde_json::to_vec(&map)?
    } else {
        let mut buf = vec![];
        MediaEntries {
            entries: media_entries,
        }
        .encode(&mut buf)?;
        buf
    };
    let size = encoded_bytes.len();
    let mut cursor = std::io::Cursor::new(encoded_bytes);
    if meta.zstd_compressed() {
        zstd_copy(&mut cursor, zip, size)?;
    } else {
        io::copy(&mut cursor, zip)?;
    }
    Ok(())
}

fn write_media_files(
    meta: &Meta,
    zip: &mut ZipWriter<File>,
    dir: &Path,
    media_entries: &mut Vec<MediaEntry>,
    mut progress_fn: impl FnMut(usize),
) -> Result<()> {
    let mut writer = MediaFileWriter::new(meta);
    let mut index = 0;
    for entry in read_dir(dir)? {
        let entry = entry?;
        if !entry.metadata()?.is_file() {
            continue;
        }
        progress_fn(index);

        zip.start_file(index.to_string(), file_options_stored())?;

        let name = normalized_unicode_file_name(&entry)?;
        // FIXME: we should chunk this
        let data = std::fs::read(entry.path())?;
        let media_entry = make_media_entry(&data, name);
        writer = writer.write(&mut std::io::Cursor::new(data), zip)?;
        media_entries.push(media_entry);
        // can't enumerate(), as we skip folders
        index += 1;
    }

    Ok(())
}

fn make_media_entry(data: &[u8], name: String) -> MediaEntry {
    MediaEntry {
        name,
        size: data.len() as u32,
        sha1: sha1_of_data(data).to_vec(),
    }
}

fn normalized_unicode_file_name(entry: &DirEntry) -> Result<String> {
    entry
        .file_name()
        .to_str()
        .map(|name| normalize_to_nfc(name).into())
        .ok_or_else(|| {
            AnkiError::IoError(format!(
                "non-unicode file name: {}",
                entry.file_name().to_string_lossy()
            ))
        })
}

/// Writes media files while compressing according to the targeted version.
/// If compressing, the encoder is reused to optimize for repeated calls.
struct MediaFileWriter(Option<RawEncoder<'static>>);

impl MediaFileWriter {
    fn new(meta: &Meta) -> Self {
        Self(
            meta.zstd_compressed()
                .then(|| RawEncoder::with_dictionary(0, &[]).unwrap()),
        )
    }

    fn write(mut self, reader: &mut impl Read, writer: &mut impl Write) -> Result<Self> {
        // take [self] by value to prevent it from being reused after an error
        if let Some(encoder) = self.0.take() {
            let mut encoder_writer = Writer::new(writer, encoder);
            io::copy(reader, &mut encoder_writer)?;
            encoder_writer.finish()?;
            self.0 = Some(encoder_writer.into_inner().1);
        } else {
            io::copy(reader, writer)?;
        }

        Ok(self)
    }
}
