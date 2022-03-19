// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::HashMap,
    fs::{DirEntry, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
};

use prost::Message;
use sha1::Sha1;
use tempfile::NamedTempFile;
use zip::{write::FileOptions, CompressionMethod, ZipWriter};
use zstd::{
    stream::{raw::Encoder as RawEncoder, zio},
    Encoder,
};

use super::super::{MediaEntries, MediaEntry, Meta, Version};
use crate::{
    collection::CollectionBuilder,
    io::{atomic_rename, read_dir_files, tempfile_in_parent_of},
    media::files::filename_if_normalized,
    prelude::*,
    storage::SchemaVersion,
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
        let temp_colpkg = tempfile_in_parent_of(colpkg_name)?;
        let src_path = self.col_path.clone();
        let src_media_folder = if include_media {
            Some(self.media_folder.clone())
        } else {
            None
        };
        let tr = self.tr.clone();
        self.close(Some(if legacy {
            SchemaVersion::V11
        } else {
            SchemaVersion::V18
        }))?;

        export_collection_file(
            temp_colpkg.path(),
            &src_path,
            src_media_folder,
            legacy,
            &tr,
            progress_fn,
        )?;
        atomic_rename(temp_colpkg, colpkg_name, true)
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
    dummy_col.close(Some(SchemaVersion::V11))?;

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
    let mut copier = MediaCopier::new(meta);
    for (index, entry) in read_dir_files(dir)?.enumerate() {
        progress_fn(index);

        zip.start_file(index.to_string(), file_options_stored())?;

        let entry = entry?;
        let name = normalized_unicode_file_name(&entry)?;
        let mut file = File::open(entry.path())?;

        let (size, sha1) = copier.copy(&mut file, zip)?;
        media_entries.push(MediaEntry::new(name, size, sha1));
    }

    Ok(())
}

impl MediaEntry {
    fn new(name: impl Into<String>, size: impl TryInto<u32>, sha1: impl Into<Vec<u8>>) -> Self {
        MediaEntry {
            name: name.into(),
            size: size.try_into().unwrap_or_default(),
            sha1: sha1.into(),
        }
    }
}

fn normalized_unicode_file_name(entry: &DirEntry) -> Result<String> {
    let filename = entry.file_name();
    let filename = filename.to_str().ok_or_else(|| {
        AnkiError::IoError(format!(
            "non-unicode file name: {}",
            entry.file_name().to_string_lossy()
        ))
    })?;
    filename_if_normalized(filename)
        .map(Cow::into_owned)
        .ok_or(AnkiError::MediaCheckRequired)
}

/// Copies and hashes while encoding according to the targeted version.
/// If compressing, the encoder is reused to optimize for repeated calls.
struct MediaCopier {
    encoding: bool,
    encoder: Option<RawEncoder<'static>>,
}

impl MediaCopier {
    fn new(meta: &Meta) -> Self {
        Self {
            encoding: meta.zstd_compressed(),
            encoder: None,
        }
    }

    fn encoder(&mut self) -> Option<RawEncoder<'static>> {
        self.encoding.then(|| {
            self.encoder
                .take()
                .unwrap_or_else(|| RawEncoder::with_dictionary(0, &[]).unwrap())
        })
    }

    /// Returns size and sha1 hash of the copied data.
    fn copy(
        &mut self,
        reader: &mut impl Read,
        writer: &mut impl Write,
    ) -> Result<(usize, [u8; 20])> {
        let mut size = 0;
        let mut hasher = Sha1::new();
        let mut buf = [0; 64 * 1024];
        let mut wrapped_writer = MaybeEncodedWriter::new(writer, self.encoder());

        loop {
            let count = match reader.read(&mut buf) {
                Ok(0) => break,
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                result => result?,
            };
            size += count;
            hasher.update(&buf[..count]);
            wrapped_writer.write(&buf[..count])?;
        }

        self.encoder = wrapped_writer.finish()?;

        Ok((size, hasher.digest().bytes()))
    }
}

enum MaybeEncodedWriter<'a, W: Write> {
    Stored(&'a mut W),
    Encoded(zio::Writer<&'a mut W, RawEncoder<'static>>),
}

impl<'a, W: Write> MaybeEncodedWriter<'a, W> {
    fn new(writer: &'a mut W, encoder: Option<RawEncoder<'static>>) -> Self {
        if let Some(encoder) = encoder {
            Self::Encoded(zio::Writer::new(writer, encoder))
        } else {
            Self::Stored(writer)
        }
    }

    fn write(&mut self, buf: &[u8]) -> Result<()> {
        match self {
            Self::Stored(writer) => writer.write_all(buf)?,
            Self::Encoded(writer) => writer.write_all(buf)?,
        };
        Ok(())
    }

    fn finish(self) -> Result<Option<RawEncoder<'static>>> {
        Ok(match self {
            Self::Stored(_) => None,
            Self::Encoded(mut writer) => {
                writer.finish()?;
                Some(writer.into_inner().1)
            }
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::media::files::sha1_of_data;

    #[test]
    fn media_file_writing() {
        let bytes = b"foo";
        let bytes_hash = sha1_of_data(b"foo");

        for meta in [Meta::new_legacy(), Meta::new()] {
            let mut writer = MediaCopier::new(&meta);
            let mut buf = Vec::new();

            let (size, hash) = writer.copy(&mut bytes.as_slice(), &mut buf).unwrap();
            if meta.zstd_compressed() {
                buf = zstd::decode_all(buf.as_slice()).unwrap();
            }

            assert_eq!(buf, bytes);
            assert_eq!(size, bytes.len());
            assert_eq!(hash, bytes_hash);
        }
    }
}
