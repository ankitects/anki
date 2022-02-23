// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{fs, io::Write, path};

use chrono::prelude::*;
use zstd;

use crate::prelude::*;

const BACKUP_FORMAT_STRING: &str = "backup-%Y-%m-%d-%H.%M.%S.colpkg";

fn out_path(out_dir: &str) -> path::PathBuf {
    path::Path::new(out_dir).join(&format!("{}", Local::now().format(BACKUP_FORMAT_STRING)))
}

pub fn backup(col_path: &str, out_dir: &str) -> Result<()> {
    let out_file = fs::File::create(out_path(out_dir))?;
    let mut zip = zip::ZipWriter::new(out_file);
    let options =
        zip::write::FileOptions::default().compression_method(zip::CompressionMethod::Stored);

    let col_file = fs::File::open(col_path)?;
    let encoded_col = zstd::encode_all(col_file, 0)?;

    zip.start_file("collection.anki2", options)?;
    zip.write_all(&encoded_col)?;
    zip.start_file("media", options)?;
    zip.write_all(b"{}")?;
    zip.finish()?;

    Ok(())
}
