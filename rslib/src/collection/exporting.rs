// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashMap,
    fs::{read_dir, DirEntry, File},
    io::{self, Read, Seek, Write},
    path::{Path, PathBuf},
};

use serde_derive::{Deserialize, Serialize};
use tempfile::NamedTempFile;
use zip::{write::FileOptions, CompressionMethod, ZipWriter};
use zstd::{
    stream::{raw::Encoder as RawEncoder, zio::Writer},
    Encoder,
};

use crate::{collection::CollectionBuilder, prelude::*, text::normalize_to_nfc};

/// Bump if making changes that break restoring on older releases.
pub const COLLECTION_VERSION: u8 = 3;
const COLLECTION_NAME: &str = "collection.anki21b";
const COLLECTION_NAME_V1: &str = "collection.anki2";
const COLLECTION_NAME_V2: &str = "collection.anki21";
/// Enable multithreaded compression if over this size. For smaller files,
/// multithreading makes things slower, and in initial tests, the crossover
/// point was somewhere between 1MB and 10MB on a many-core system.
const MULTITHREAD_MIN_BYTES: usize = 10 * 1024 * 1024;

#[derive(Debug, Default, Serialize, Deserialize, Clone, Copy)]
#[serde(default)]
pub(super) struct Meta {
    #[serde(rename = "ver")]
    pub(super) version: u8,
}

impl Meta {
    pub(super) fn new() -> Self {
        Self {
            version: COLLECTION_VERSION,
        }
    }

    pub(super) fn new_v2() -> Self {
        Self { version: 2 }
    }

    pub(super) fn collection_name(&self) -> &'static str {
        match self.version {
            1 => COLLECTION_NAME_V1,
            2 => COLLECTION_NAME_V2,
            _ => COLLECTION_NAME,
        }
    }

    pub(super) fn zstd_compressed(&self) -> bool {
        self.version >= 3
    }
}

pub fn export_collection_file(
    out_path: impl AsRef<Path>,
    col_path: impl AsRef<Path>,
    media_dir: Option<PathBuf>,
    legacy: bool,
    tr: &I18n,
    progress_fn: impl FnMut(usize),
) -> Result<()> {
    let meta = if legacy { Meta::new_v2() } else { Meta::new() };
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

pub(crate) fn export_collection_data(
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
    let out_dir = out_path
        .as_ref()
        .parent()
        .ok_or_else(|| AnkiError::invalid_input("bad out path"))?;

    zip.start_file("meta", file_options_stored())?;
    zip.write_all(serde_json::to_string(&meta).unwrap().as_bytes())?;
    write_collection(meta, &mut zip, col, col_size)?;
    write_dummy_collections(meta, &mut zip, out_dir, tr)?;
    write_media(meta, &mut zip, media_dir, progress_fn)?;
    zip.finish()?;

    Ok(())
}

fn file_options_stored() -> FileOptions {
    FileOptions::default().compression_method(CompressionMethod::Stored)
}

fn write_collection(
    meta: Meta,
    zip: &mut ZipWriter<File>,
    col: &mut impl Read,
    size: usize,
) -> Result<()> {
    if meta.zstd_compressed() {
        zip.start_file(meta.collection_name(), file_options_stored())?;
        zstd_copy(col, zip, size)?;
    } else {
        zip.start_file(meta.collection_name(), FileOptions::default())?;
        io::copy(col, zip)?;
    }
    Ok(())
}

fn write_dummy_collections(
    meta: Meta,
    zip: &mut ZipWriter<File>,
    temp_dir: &Path,
    tr: &I18n,
) -> Result<()> {
    let mut tempfile = create_dummy_collection_file(temp_dir, tr)?;

    for (version, name) in [(1, COLLECTION_NAME_V1), (2, COLLECTION_NAME_V2)] {
        if meta.version > version {
            tempfile.seek(io::SeekFrom::Start(0))?;
            zip.start_file(name, file_options_stored())?;
            io::copy(&mut tempfile, zip)?;
        }
    }

    Ok(())
}

fn create_dummy_collection_file(temp_dir: &Path, tr: &I18n) -> Result<NamedTempFile> {
    let tempfile = NamedTempFile::new_in(temp_dir)?;
    let mut dummy_col = CollectionBuilder::new(tempfile.path()).build()?;
    dummy_col.add_dummy_note(tr)?;
    dummy_col.close(true)?;

    Ok(tempfile)
}

impl Collection {
    fn add_dummy_note(&mut self, tr: &I18n) -> Result<()> {
        let notetype = self.get_notetype_by_name("basic")?.unwrap();
        let mut note = notetype.new_note();
        note.set_field(0, tr.errors_collection_too_new())?;
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
    meta: Meta,
    zip: &mut ZipWriter<File>,
    media_dir: Option<PathBuf>,
    progress_fn: impl FnMut(usize),
) -> Result<()> {
    let mut media_names = HashMap::new();

    if let Some(media_dir) = media_dir {
        write_media_files(meta, zip, &media_dir, &mut media_names, progress_fn)?;
    }

    zip.start_file("media", file_options_stored())?;
    zip.write_all(serde_json::to_string(&media_names).unwrap().as_bytes())?;

    Ok(())
}

fn write_media_files(
    meta: Meta,
    zip: &mut ZipWriter<File>,
    dir: &Path,
    names: &mut HashMap<String, String>,
    mut progress_fn: impl FnMut(usize),
) -> Result<()> {
    let mut writer = MediaFileWriter::new(meta);

    for (i, res) in read_dir(dir)?.enumerate() {
        let entry = res?;
        progress_fn(i);

        if entry.metadata()?.is_file() {
            names.insert(i.to_string(), normalized_unicode_file_name(&entry)?);
            zip.start_file(i.to_string(), file_options_stored())?;
            writer = writer.write(&mut File::open(entry.path())?, zip)?;
        }
    }

    Ok(())
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
    fn new(meta: Meta) -> Self {
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
