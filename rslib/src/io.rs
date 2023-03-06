// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs::File;
use std::io::Read;
use std::io::Seek;
use std::path::Component;
use std::path::Path;

use tempfile::NamedTempFile;

use crate::error::FileIoError;
use crate::error::FileIoSnafu;
use crate::error::FileOp;
use crate::prelude::*;

pub(crate) type Result<T, E = FileIoError> = std::result::Result<T, E>;

/// See [File::open].
pub(crate) fn open_file(path: impl AsRef<Path>) -> Result<File> {
    File::open(&path).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Open,
    })
}

/// See [std::fs::write].
pub(crate) fn write_file(path: impl AsRef<Path>, contents: impl AsRef<[u8]>) -> Result<()> {
    std::fs::write(&path, contents).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Write,
    })
}

/// See [std::fs::remove_file].
#[allow(dead_code)]
pub(crate) fn remove_file(path: impl AsRef<Path>) -> Result<()> {
    std::fs::remove_file(&path).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Remove,
    })
}

/// See [std::fs::create_dir].
pub(crate) fn create_dir(path: impl AsRef<Path>) -> Result<()> {
    std::fs::create_dir(&path).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Create,
    })
}

/// See [std::fs::create_dir_all].
pub(crate) fn create_dir_all(path: impl AsRef<Path>) -> Result<()> {
    std::fs::create_dir_all(&path).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Create,
    })
}

/// See [std::fs::read].
pub(crate) fn read_file(path: impl AsRef<Path>) -> Result<Vec<u8>> {
    std::fs::read(&path).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Read,
    })
}

/// Like [read_file], but skips the section that is potentially locked by
/// SQLite.
pub(crate) fn read_locked_db_file(path: impl AsRef<Path>) -> Result<Vec<u8>> {
    read_locked_db_file_inner(&path).context(FileIoSnafu {
        path: path.as_ref(),
        op: FileOp::Read,
    })
}

const LOCKED_SECTION_START_BYTE: usize = 1024 * 1024 * 1024;
const LOCKED_SECTION_LEN_BYTES: usize = 512;
const LOCKED_SECTION_END_BYTE: usize = LOCKED_SECTION_START_BYTE + LOCKED_SECTION_LEN_BYTES;

fn read_locked_db_file_inner(path: impl AsRef<Path>) -> std::io::Result<Vec<u8>> {
    let size = std::fs::metadata(&path)?.len() as usize;
    if size < LOCKED_SECTION_END_BYTE {
        return std::fs::read(path);
    }

    let mut file = File::open(&path)?;
    let mut buf = vec![0; size];
    file.read_exact(&mut buf[..LOCKED_SECTION_START_BYTE])?;
    file.seek(std::io::SeekFrom::Current(LOCKED_SECTION_LEN_BYTES as i64))?;
    file.read_exact(&mut buf[LOCKED_SECTION_END_BYTE..])?;

    Ok(buf)
}

pub(crate) fn new_tempfile() -> Result<NamedTempFile> {
    NamedTempFile::new().context(FileIoSnafu {
        path: std::env::temp_dir(),
        op: FileOp::Create,
    })
}

pub(crate) fn new_tempfile_in(dir: impl AsRef<Path>) -> Result<NamedTempFile> {
    NamedTempFile::new_in(&dir).context(FileIoSnafu {
        path: dir.as_ref(),
        op: FileOp::Create,
    })
}

pub(crate) fn new_tempfile_in_parent_of(file: &Path) -> Result<NamedTempFile> {
    let dir = file.parent().unwrap_or(file);
    NamedTempFile::new_in(dir).context(FileIoSnafu {
        path: dir,
        op: FileOp::Create,
    })
}

/// Atomically replace the target path with the provided temp file.
///
/// If `fsync` is true, file data is synced to disk prior to renaming, and the
/// folder is synced on UNIX platforms after renaming. This minimizes the
/// chances of corruption if there is a crash or power loss directly after the
/// op, but it can be considerably slower.
pub(crate) fn atomic_rename(file: NamedTempFile, target: &Path, fsync: bool) -> Result<()> {
    if fsync {
        file.as_file().sync_all().context(FileIoSnafu {
            path: file.path(),
            op: FileOp::Sync,
        })?;
    }
    file.persist(target)?;
    #[cfg(not(windows))]
    if fsync {
        if let Some(parent) = target.parent() {
            open_file(parent)?.sync_all().context(FileIoSnafu {
                path: parent,
                op: FileOp::Sync,
            })?;
        }
    }
    Ok(())
}

/// Like [std::fs::read_dir], but only yielding files. [Err]s are not filtered.
pub(crate) fn read_dir_files(path: impl AsRef<Path>) -> Result<ReadDirFiles> {
    std::fs::read_dir(&path)
        .map(ReadDirFiles)
        .context(FileIoSnafu {
            path: path.as_ref(),
            op: FileOp::Read,
        })
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
