// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::path::Path;

use tempfile::tempdir;

use crate::{collection::CollectionBuilder, media::MediaManager, prelude::*, search::SearchNode};

fn collection_with_media(dir: &Path, name: &str) -> Result<Collection> {
    let name = format!("{name}_src");
    let media_folder = dir.join(format!("{name}.media"));
    std::fs::create_dir(&media_folder)?;
    // add collection with sentinel note
    let mut col = CollectionBuilder::new(dir.join(format!("{name}.anki2")))
        .set_media_paths(media_folder, dir.join(format!("{name}.mdb")))
        .build()?;
    let nt = col.get_notetype_by_name("Basic")?.unwrap();
    let mut note = nt.new_note();
    col.add_note(&mut note, DeckId(1))?;
    // add sample media
    let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
    let mut ctx = mgr.dbctx();
    mgr.add_file(&mut ctx, "1", b"1")?;
    mgr.add_file(&mut ctx, "2", b"2")?;
    mgr.add_file(&mut ctx, "3", b"3")?;
    Ok(col)
}

#[test]
fn roundtrip() -> Result<()> {
    let _dir = tempdir()?;
    let dir = _dir.path();
    const NAME: &str = "mycol";

    let mut col = collection_with_media(dir, NAME)?;
    let apkg_path = dir.join(format!("{NAME}.apkg"));

    col.export_apkg(
        &apkg_path,
        SearchNode::WholeCollection,
        true,
        true,
        None,
        |_| (),
    )?;
    col.import_apkg(&apkg_path)?;

    Ok(())
}
