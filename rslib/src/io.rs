// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::{Component, Path};

use tempfile::NamedTempFile;

use crate::prelude::*;

pub(crate) fn tempfile_in_parent_of(file: &Path) -> Result<NamedTempFile> {
    let dir = file
        .parent()
        .ok_or_else(|| AnkiError::invalid_input("not a file path"))?;
    NamedTempFile::new_in(dir).map_err(|err| AnkiError::file_io_error(err, dir))
}

/// Atomically replace the target path with the provided temp file.
///
/// If `fsync` is true, file data is synced to disk prior to renaming, and the
/// folder is synced on UNIX platforms after renaming. This minimizes the
/// chances of corruption if there is a crash or power loss directly after the
/// op, but it can be considerably slower.
pub(crate) fn atomic_rename(file: NamedTempFile, target: &Path, fsync: bool) -> Result<()> {
    if fsync {
        file.as_file().sync_all()?;
    }
    file.persist(&target)
        .map_err(|err| AnkiError::IoError(format!("write {target:?} failed: {err}")))?;
    #[cfg(not(windows))]
    if fsync {
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

/// True if name does not contain any path separators.
pub(crate) fn filename_is_safe(name: &str) -> bool {
    let mut components = Path::new(name).components();
    let first_element_normal = components
        .next()
        .map(|component| matches!(component, Component::Normal(_)))
        .unwrap_or_default();

    first_element_normal && components.next().is_none()
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

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn path_traversal() {
        assert!(filename_is_safe("foo"));

        assert!(!filename_is_safe(".."));
        assert!(!filename_is_safe("foo/bar"));
        assert!(!filename_is_safe("/foo"));
        assert!(!filename_is_safe("../foo"));

        if cfg!(windows) {
            assert!(!filename_is_safe("foo\\bar"));
            assert!(!filename_is_safe("c:\\foo"));
            assert!(!filename_is_safe("\\foo"));
        }
    }
}
