// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::path::Path;

use anki_io::create_dir_all;
use anki_io::read_file;
use tempfile::tempdir;

use crate::collection::CollectionBuilder;
use crate::import_export::package::import_colpkg;
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
