// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

use tempfile::NamedTempFile;

use crate::prelude::*;

pub(crate) fn tempfile_in_parent_of(file: &Path) -> Result<NamedTempFile> {
    let dir = file
        .parent()
        .ok_or_else(|| AnkiError::invalid_input("not a file path"))?;
    NamedTempFile::new_in(dir).map_err(|err| AnkiError::file_io_error(err, dir))
}

pub(crate) fn atomic_rename(file: NamedTempFile, target: &Path) -> Result<()> {
    file.as_file().sync_all()?;
    file.persist(&target)
        .map_err(|err| AnkiError::IoError(format!("write {target:?} failed: {err}")))?;
    if !cfg!(windows) {
        if let Some(parent) = target.parent() {
            std::fs::File::open(parent)
                .and_then(|file| file.sync_all())
                .map_err(|err| AnkiError::IoError(format!("sync {parent:?} failed: {err}")))?;
        }
    }
    Ok(())
}

/// Like [std::fs::read_dir], but only yielding files. [Err]s are not filtered.
pub(crate) fn read_dir_files(path: impl AsRef<Path>) -> std::io::Result<ReadDirFiles> {
    std::fs::read_dir(path).map(ReadDirFiles)
}

pub(crate) struct ReadDirFiles(std::fs::ReadDir);

impl Iterator for ReadDirFiles {
    type Item = std::io::Result<std::fs::DirEntry>;

    fn next(&mut self) -> Option<Self::Item> {
        let next = self.0.next();
        if let Some(Ok(entry)) = next.as_ref() {
            match entry.metadata().map(|metadata| metadata.is_file()) {
                Ok(true) => next,
                Ok(false) => self.next(),
                Err(error) => Some(Err(error)),
            }
        } else {
            next
        }
    }
}
