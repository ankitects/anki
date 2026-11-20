// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::path::Path;

use anki_io::create_dir_all;
use anki_io::read_file;
use tempfile::tempdir;

use crate::collection::CollectionBuilder;
use crate::import_export::package::import_colpkg;
use crate::import_export::package::validate_colpkg;
use crate::media::MediaManager;
use crate::prelude::*;

fn collection_with_media(dir: &Path, name: &str) -> Result<Collection> {
    let name = format!("{name}_src");
    // add collection with sentinel note
    let mut col = CollectionBuilder::new(dir.join(format!("{name}.anki2")))
        .with_desktop_media_paths()
        .build()?;
    let nt = col.get_notetype_by_name("Basic")?.unwrap();
    let mut note = nt.new_note();
    col.add_note(&mut note, DeckId(1))?;
    // add sample media
    let mgr = col.media()?;
    mgr.add_file("1", b"1")?;
    mgr.add_file("2", b"2")?;
    mgr.add_file("3", b"3")?;
    Ok(col)
}

#[test]
fn roundtrip() -> Result<()> {
    let _dir = tempdir()?;
    let dir = _dir.path();

    for (legacy, name) in [(true, "legacy"), (false, "v3")] {
        // export to a file
        let col = collection_with_media(dir, name)?;
        let colpkg_name = dir.join(format!("{name}.colpkg"));
        let progress = col.new_progress_handler();
        col.export_colpkg(&colpkg_name, true, legacy)?;
        let original = read_file(&colpkg_name)?;
        validate_colpkg(&colpkg_name)?;
        assert_eq!(read_file(&colpkg_name)?, original);

        // import into a new collection
        let anki2_name = dir
            .join(format!("{name}.anki2"))
            .to_string_lossy()
            .into_owned();
        let import_media_dir = dir.join(format!("{name}.media"));
        create_dir_all(&import_media_dir)?;
        let import_media_db = dir.join(format!("{name}.mdb"));
        MediaManager::new(&import_media_dir, &import_media_db)?;
        import_colpkg(
            &colpkg_name.to_string_lossy(),
            &anki2_name,
            &import_media_dir,
            &import_media_db,
            progress,
        )?;

        // confirm collection imported
        let col = CollectionBuilder::new(&anki2_name).build()?;
        assert_eq!(
            col.storage.db_scalar::<i32>("select count() from notes")?,
            1
        );
        // confirm media imported correctly
        assert_eq!(read_file(import_media_dir.join("1"))?, b"1");
        assert_eq!(read_file(import_media_dir.join("2"))?, b"2");
        assert_eq!(read_file(import_media_dir.join("3"))?, b"3");
    }

    Ok(())
}

#[test]
fn archive_validation_rejects_corrupt_members() -> Result<()> {
    use std::fs::OpenOptions;
    use std::io::Read;
    use std::io::Seek;
    use std::io::SeekFrom;
    use std::io::Write;

    for (legacy, member) in [
        (true, "collection.anki21"),
        (false, "collection.anki21b"),
        (false, "meta"),
        (false, "media"),
        (false, "0"),
    ] {
        let dir = tempdir()?;
        let path = dir.path().join("backup.colpkg");
        collection_with_media(dir.path(), "corrupt")?.export_colpkg(&path, true, legacy)?;
        validate_colpkg(&path)?;
        let offset = zip::ZipArchive::new(std::fs::File::open(&path)?)?
            .by_name(member)?
            .central_header_start()
            + 16;
        // Damage the declared CRC, leaving decompression and SQLite contents
        // valid. Every member must be read through its checksum verification.
        let mut file = OpenOptions::new().read(true).write(true).open(&path)?;
        file.seek(SeekFrom::Start(offset))?;
        let mut byte = [0];
        file.read_exact(&mut byte)?;
        byte[0] ^= 1;
        file.seek(SeekFrom::Start(offset))?;
        file.write_all(&byte)?;
        drop(file);
        assert!(validate_colpkg(&path).is_err(), "accepted corrupt {member}");
    }
    Ok(())
}

#[test]
fn archive_validation_rejects_invalid_collection_in_valid_zip() -> Result<()> {
    let dir = tempdir()?;
    let path = dir.path().join("backup.colpkg");
    archive_with_collection(&path, b"not a database")?;
    assert!(validate_colpkg(&path).is_err());
    Ok(())
}

#[test]
fn archive_validation_checks_sqlite_pages() -> Result<()> {
    let dir = tempdir()?;
    let collection = dir.path().join("collection.anki2");
    let col = CollectionBuilder::new(&collection).build()?;
    let root: usize = col
        .storage
        .db_scalar("select rootpage from sqlite_master where name = 'idx_decks_name'")?;
    col.close(None)?;
    let mut data = read_file(&collection)?;
    // The header, schema and core tables are intact. Only an index is damaged,
    // so opening the collection alone is insufficient to detect the corruption.
    let page_size = u16::from_be_bytes([data[16], data[17]]) as usize;
    data[(root - 1) * page_size] = 0;
    let path = dir.path().join("backup.colpkg");
    archive_with_collection(&path, &data)?;
    assert!(validate_colpkg(&path).is_err());
    Ok(())
}

fn archive_with_collection(path: &Path, data: &[u8]) -> Result<()> {
    use std::io::Write;

    let mut zip = zip::ZipWriter::new(std::fs::File::create(path)?);
    zip.start_file("collection.anki2", zip::write::SimpleFileOptions::default())?;
    zip.write_all(data)?;
    zip.start_file("media", zip::write::SimpleFileOptions::default())?;
    zip.write_all(b"{}")?;
    zip.finish()?;
    Ok(())
}

#[test]
fn modern_package_without_metadata_is_not_a_legacy_backup() -> Result<()> {
    let dir = tempdir()?;
    let source = dir.path().join("source.colpkg");
    collection_with_media(dir.path(), "metadata")?.export_colpkg(&source, false, false)?;
    let mut archive = zip::ZipArchive::new(std::fs::File::open(&source)?)?;
    let path = dir.path().join("missing-meta.colpkg");
    let mut writer = zip::ZipWriter::new(std::fs::File::create(&path)?);
    for index in 0..archive.len() {
        let file = archive.by_index(index)?;
        if file.name().unwrap() != "meta" {
            writer.raw_copy_file(file)?;
        }
    }
    writer.finish()?;
    assert!(validate_colpkg(&path).is_err());
    Ok(())
}

#[test]
fn legacy_backup_without_metadata_remains_usable() -> Result<()> {
    let dir = tempdir()?;
    let collection = dir.path().join("collection.anki2");
    CollectionBuilder::new(&collection)
        .build()?
        .close(Some(crate::storage::SchemaVersion::V11))?;
    let path = dir.path().join("legacy.colpkg");
    archive_with_collection(&path, &read_file(collection)?)?;
    validate_colpkg(&path)?;
    Ok(())
}

#[test]
fn unreadable_legacy_collection_does_not_fall_back_to_dummy() -> Result<()> {
    use std::io::Seek;
    use std::io::SeekFrom;
    use std::io::Write;

    let dir = tempdir()?;
    let source = dir.path().join("source.colpkg");
    collection_with_media(dir.path(), "metadata")?.export_colpkg(&source, false, true)?;
    let mut archive = zip::ZipArchive::new(std::fs::File::open(&source)?)?;
    let path = dir.path().join("legacy.colpkg");
    let mut writer = zip::ZipWriter::new(std::fs::File::create(&path)?);
    for index in 0..archive.len() {
        let file = archive.by_index(index)?;
        if file.name().unwrap() != "meta" {
            writer.raw_copy_file(file)?;
        }
    }
    writer.finish()?;
    validate_colpkg(&path)?;
    let offset = zip::ZipArchive::new(std::fs::File::open(&path)?)?
        .by_name("collection.anki21")?
        .header_start();
    let mut file = std::fs::OpenOptions::new().write(true).open(&path)?;
    file.seek(SeekFrom::Start(offset))?;
    file.write_all(b"bad!")?;
    drop(file);
    assert!(validate_colpkg(&path).is_err());
    Ok(())
}

#[test]
fn backup_validation_does_not_modify_extracted_collection() -> Result<()> {
    let dir = tempdir()?;
    let path = dir.path().join("collection with spaces.anki2");
    CollectionBuilder::new(&path).build()?.close(None)?;
    let original = read_file(&path)?;
    let files: Vec<_> = std::fs::read_dir(&dir)?
        .map(|entry| entry.unwrap().file_name())
        .collect();
    crate::storage::SqliteStorage::check_backup_file(&path)?;
    assert_eq!(read_file(&path)?, original);
    let after: Vec<_> = std::fs::read_dir(&dir)?
        .map(|entry| entry.unwrap().file_name())
        .collect();
    assert_eq!(after, files);
    Ok(())
}

/// Files with an invalid encoding should prevent export, except
/// on Apple platforms where the encoding is transparently changed.
#[test]
#[cfg(not(target_vendor = "apple"))]
fn normalization_check_on_export() -> Result<()> {
    use anki_io::write_file;

    let _dir = tempdir()?;
    let dir = _dir.path();

    let col = collection_with_media(dir, "normalize")?;
    let colpkg_name = dir.join("normalize.colpkg");
    // manually write a file in the wrong encoding.
    write_file(col.media_folder.join("ぱぱ.jpg"), "nfd encoding")?;
    assert_eq!(
        col.export_colpkg(&colpkg_name, true, false,).unwrap_err(),
        AnkiError::MediaCheckRequired
    );
    // file should have been cleaned up
    assert!(!colpkg_name.exists());

    Ok(())
}
