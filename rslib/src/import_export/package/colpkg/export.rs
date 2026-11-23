// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::fs::File;
use std::io;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

use anki_io::atomic_rename;
use anki_io::atomic_rename_noclobber;
use anki_io::new_tempfile;
use anki_io::new_tempfile_in_parent_of;
use anki_io::open_file;
use prost::Message;
use tempfile::NamedTempFile;
use zip::write::SimpleFileOptions;
use zip::CompressionMethod;
use zip::ZipWriter;
use zstd::stream::raw::Encoder as RawEncoder;
use zstd::stream::zio;
use zstd::Encoder;

use super::super::meta::MetaExt;
use super::super::meta::VersionExt;
use super::super::MediaEntries;
use super::super::MediaEntry;
use super::super::Meta;
use super::super::Version;
use crate::collection::CollectionBuilder;
use crate::import_export::package::media::new_media_entry;
use crate::import_export::package::media::MediaCopier;
use crate::import_export::package::media::MediaIter;
use crate::import_export::package::validate_colpkg;
use crate::import_export::ExportProgress;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::storage::SchemaVersion;

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
    ) -> Result<()> {
        let mut progress = self.new_progress_handler();
        let colpkg_name = out_path.as_ref();
        let temp_colpkg = new_tempfile_in_parent_of(colpkg_name)?;
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
            &mut progress,
        )?;
        atomic_rename(temp_colpkg, colpkg_name, true)?;

        Ok(())
    }
}

fn export_collection_file(
    out_path: impl AsRef<Path>,
    col_path: impl AsRef<Path>,
    media_dir: Option<PathBuf>,
    legacy: bool,
    tr: &I18n,
    progress: &mut ThrottlingProgressHandler<ExportProgress>,
) -> Result<()> {
    let meta = if legacy {
        Meta::new_legacy()
    } else {
        Meta::new()
    };
    let mut col_file = open_file(col_path)?;
    let col_size = col_file.metadata()?.len() as usize;
    let media = if let Some(path) = media_dir {
        MediaIter::from_folder(&path)?
    } else {
        MediaIter::empty()
    };

    export_collection(meta, out_path, &mut col_file, col_size, media, tr, progress)
}

/// Publish copied collection data without media, preserving an existing target.
pub(crate) fn export_colpkg_from_data(
    out_path: impl AsRef<Path>,
    mut col_data: &[u8],
    tr: &I18n,
) -> Result<()> {
    let out_path = out_path.as_ref();
    let tempfile = new_tempfile_in_parent_of(out_path)?;
    let col_size = col_data.len();
    let mut progress = ThrottlingProgressHandler::new(Default::default());
    export_collection(
        Meta::new(),
        tempfile.path(),
        &mut col_data,
        col_size,
        MediaIter::empty(),
        tr,
        &mut progress,
    )?;
    validate_colpkg(tempfile.path())?;
    atomic_rename_noclobber(tempfile, out_path, true)?;
    Ok(())
}

pub(crate) fn export_collection(
    meta: Meta,
    out_path: impl AsRef<Path>,
    col: &mut impl Read,
    col_size: usize,
    media: MediaIter,
    tr: &I18n,
    progress: &mut ThrottlingProgressHandler<ExportProgress>,
) -> Result<()> {
    let out_file = File::create(&out_path)?;
    let mut zip = ZipWriter::new(out_file);

    zip.start_file("meta", file_options_stored())?;
    let mut meta_bytes = vec![];
    meta.encode(&mut meta_bytes)?;
    zip.write_all(&meta_bytes)?;
    write_collection(&meta, &mut zip, col, col_size)?;
    write_dummy_collection(&mut zip, tr)?;
    write_media(&meta, &mut zip, media, progress)?;
    zip.finish()?;

    Ok(())
}

fn file_options_stored() -> SimpleFileOptions {
    SimpleFileOptions::default().compression_method(CompressionMethod::Stored)
}

fn file_options_default() -> SimpleFileOptions {
    SimpleFileOptions::default()
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
        zip.start_file(meta.collection_filename(), file_options_default())?;
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
    let tempfile = new_tempfile()?;
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
    media: MediaIter,
    progress: &mut ThrottlingProgressHandler<ExportProgress>,
) -> Result<()> {
    let mut media_entries = vec![];
    write_media_files(meta, zip, media, &mut media_entries, progress)?;
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
    let mut cursor = io::Cursor::new(encoded_bytes);
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
    media: MediaIter,
    media_entries: &mut Vec<MediaEntry>,
    progress: &mut ThrottlingProgressHandler<ExportProgress>,
) -> Result<()> {
    let mut copier = MediaCopier::new(meta.zstd_compressed());
    let mut incrementor = progress.incrementor(ExportProgress::Media);
    for (index, res) in media.0.enumerate() {
        incrementor.increment()?;
        let mut entry = res?;

        zip.start_file(index.to_string(), file_options_stored())?;

        let (size, sha1) = copier.copy(&mut entry.data, zip)?;
        media_entries.push(new_media_entry(entry.nfc_filename, size, sha1));
    }

    Ok(())
}

pub(crate) enum MaybeEncodedWriter<'a, W: Write> {
    Stored(&'a mut W),
    Encoded(zio::Writer<&'a mut W, RawEncoder<'static>>),
}

impl<'a, W: Write> MaybeEncodedWriter<'a, W> {
    pub fn new(writer: &'a mut W, encoder: Option<RawEncoder<'static>>) -> Self {
        if let Some(encoder) = encoder {
            Self::Encoded(zio::Writer::new(writer, encoder))
        } else {
            Self::Stored(writer)
        }
    }

    pub fn write(&mut self, buf: &[u8]) -> Result<()> {
        match self {
            Self::Stored(writer) => writer.write_all(buf)?,
            Self::Encoded(writer) => writer.write_all(buf)?,
        };
        Ok(())
    }

    pub fn finish(self) -> Result<Option<RawEncoder<'static>>> {
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
    fn backup_filename_collision_preserves_completed_archive() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("backup.colpkg");
        CollectionBuilder::new(dir.path().join("collection.anki2"))
            .build()
            .unwrap()
            .export_colpkg(&path, false, false)
            .unwrap();
        let original = std::fs::read(&path).unwrap();
        let data = std::fs::read(dir.path().join("collection.anki2")).unwrap();
        let error = export_colpkg_from_data(&path, &data, &I18n::template_only()).unwrap_err();
        assert!(
            matches!(error, AnkiError::FileIoError { source } if source.source.kind() == io::ErrorKind::AlreadyExists)
        );
        assert_eq!(std::fs::read(&path).unwrap(), original);
        crate::import_export::package::validate_colpkg(&path).unwrap();
    }

    #[test]
    fn invalid_snapshot_is_not_published() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("backup.colpkg");
        assert!(export_colpkg_from_data(&path, b"not a database", &I18n::template_only()).is_err());
        assert!(!path.exists());
        assert_eq!(std::fs::read_dir(&dir).unwrap().count(), 0);
    }

    #[cfg(unix)]
    #[test]
    fn interrupted_backup_preserves_destination() {
        const NAME: &str = "backup.colpkg";
        const ENV: &str = "ANKI_TEST_BACKUP_WRITE_FAILURE";
        if let Some(backup_dir) = std::env::var_os(ENV) {
            use rand::Rng;
            use rand::SeedableRng;

            let mut data = vec![0; 64 * 1024];
            rand::rngs::StdRng::seed_from_u64(5728).fill_bytes(&mut data);
            let mut original_limit = libc::rlimit {
                rlim_cur: 0,
                rlim_max: 0,
            };
            assert_eq!(
                unsafe { libc::getrlimit(libc::RLIMIT_FSIZE, &mut original_limit) },
                0
            );
            // Restore the soft limit before the process exits so coverage output
            // is not truncated by the backup write-failure simulation.
            let restore_limit = scopeguard::guard(original_limit, |limit| {
                assert_eq!(unsafe { libc::setrlimit(libc::RLIMIT_FSIZE, &limit) }, 0);
            });
            let write_limit = libc::rlimit {
                rlim_cur: 512,
                rlim_max: restore_limit.rlim_max,
            };
            assert_eq!(
                unsafe { libc::setrlimit(libc::RLIMIT_FSIZE, &write_limit) },
                0
            );
            let error = export_colpkg_from_data(
                Path::new(&backup_dir).join(NAME),
                &data,
                &I18n::template_only(),
            )
            .unwrap_err();
            assert!(
                matches!(error, AnkiError::FileIoError { source } if source.source.kind() == io::ErrorKind::FileTooLarge)
            );
            return;
        }
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join(NAME);
        for existing in [false, true] {
            if existing {
                std::fs::write(&path, b"previous archive").unwrap();
            }
            // Isolate RLIMIT_FSIZE in a subprocess. Ignoring SIGXFSZ makes the
            // write return an error, exercising cleanup rather than killing the test.
            let output = std::process::Command::new("sh")
                .args(["-c", "trap '' XFSZ && exec \"$@\"", "--"])
                .arg(std::env::current_exe().unwrap())
                .args(["--exact", "import_export::package::colpkg::export::test::interrupted_backup_preserves_destination", "--nocapture"])
                .env(ENV, dir.path())
                .output().unwrap();
            assert!(
                output.status.success(),
                "{} {}",
                String::from_utf8_lossy(&output.stdout),
                String::from_utf8_lossy(&output.stderr)
            );
            if existing {
                assert_eq!(std::fs::read(&path).unwrap(), b"previous archive");
            } else {
                assert!(!path.exists());
            }
            assert_eq!(
                std::fs::read_dir(&dir).unwrap().count(),
                usize::from(existing)
            );
        }
    }

    #[test]
    fn media_file_writing() {
        let bytes = b"foo";
        let bytes_hash = sha1_of_data(b"foo");

        for meta in [Meta::new_legacy(), Meta::new()] {
            let mut writer = MediaCopier::new(meta.zstd_compressed());
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
