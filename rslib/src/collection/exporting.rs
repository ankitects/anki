// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashMap,
    fs::{read_dir, DirEntry, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
};

use serde_derive::{Deserialize, Serialize};
use zip::{write::FileOptions, CompressionMethod, ZipWriter};
use zstd::{
    stream::{raw::Encoder as RawEncoder, zio::Writer},
    Encoder,
};

use crate::{prelude::*, text::normalize_to_nfc};

/// Bump if making changes that break restoring on older releases.
pub const COLLECTION_VERSION: u8 = 3;
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
            1 => "collection.anki2",
            2 => "collection.anki21",
            _ => "collection.anki21b",
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
) -> Result<()> {
    let meta = if legacy { Meta::new_v2() } else { Meta::new() };
    let mut col_file = File::open(col_path)?;
    let col_size = col_file.metadata()?.len() as usize;
    export_collection(meta, out_path, &mut col_file, col_size, media_dir)
}

pub(crate) fn export_collection_data(
    out_path: impl AsRef<Path>,
    mut col_data: &[u8],
) -> Result<()> {
    let col_size = col_data.len();
    export_collection(Meta::new(), out_path, &mut col_data, col_size, None)
}

fn export_collection(
    meta: Meta,
    out_path: impl AsRef<Path>,
    col: &mut impl Read,
    col_size: usize,
    media_dir: Option<PathBuf>,
) -> Result<()> {
    let out_file = File::create(out_path)?;
    let mut zip = ZipWriter::new(out_file);
    let options = FileOptions::default().compression_method(CompressionMethod::Stored);

    zip.start_file("meta", options)?;
    zip.write_all(serde_json::to_string(&meta).unwrap().as_bytes())?;
    write_collection(meta, &mut zip, col, col_size)?;
    write_media(meta, &mut zip, media_dir)?;
    zip.finish()?;

    Ok(())
}

fn write_collection(
    meta: Meta,
    zip: &mut ZipWriter<File>,
    col: &mut impl Read,
    size: usize,
) -> Result<()> {
    if meta.zstd_compressed() {
        let options = FileOptions::default().compression_method(CompressionMethod::Stored);
        zip.start_file(meta.collection_name(), options)?;
        zstd_copy(col, zip, size)?;
    } else {
        zip.start_file(meta.collection_name(), FileOptions::default())?;
        io::copy(col, zip)?;
    }
    Ok(())
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

fn write_media(meta: Meta, zip: &mut ZipWriter<File>, media_dir: Option<PathBuf>) -> Result<()> {
    let options = FileOptions::default().compression_method(CompressionMethod::Stored);
    let mut media_names: HashMap<String, String> = HashMap::new();
    let mut file_writer = MediaFileWriter::new(meta);

    if let Some(media_dir) = media_dir {
        for (i, res) in read_dir(media_dir)?.enumerate() {
            let entry = res?;
            if entry.metadata()?.is_file() {
                media_names.insert(i.to_string(), normalized_unicode_file_name(&entry)?);
                zip.start_file(i.to_string(), options)?;
                file_writer = file_writer.write(&mut File::open(entry.path())?, zip)?;
            }
        }
    }

    zip.start_file("media", options)?;
    zip.write_all(serde_json::to_string(&media_names).unwrap().as_bytes())?;

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
