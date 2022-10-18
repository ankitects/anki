// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::PathBuf;

use snafu::Snafu;

#[derive(Debug, Snafu)]
#[snafu(visibility(pub))]
pub struct FileIoError {
    pub path: PathBuf,
    pub op: FileOp,
    pub source: std::io::Error,
}

impl PartialEq for FileIoError {
    fn eq(&self, other: &Self) -> bool {
        self.path == other.path && self.op == other.op
    }
}

impl Eq for FileIoError {}

#[derive(Debug, PartialEq, Clone, Eq)]
pub enum FileOp {
    Read,
    Open,
    Create,
    Write,
    CopyFrom(PathBuf),
    Persist,
    Sync,
}

impl FileOp {
    pub fn copy(from: impl Into<PathBuf>) -> Self {
        Self::CopyFrom(from.into())
    }

    fn verb(&self) -> String {
        match self {
            Self::Open => "open".into(),
            Self::Read => "read".into(),
            Self::Create => "create file in".into(),
            Self::Write => "write".into(),
            Self::CopyFrom(p) => format!("copy from {} to", p.to_string_lossy()),
            Self::Persist => "persist".into(),
            Self::Sync => "sync".into(),
        }
    }
}

impl FileIoError {
    pub fn message(&self) -> String {
        format!(
            "failed to {} {}",
            self.op.verb(),
            self.path.to_string_lossy(),
        )
    }

    pub(crate) fn is_not_found(&self) -> bool {
        self.source.kind() == std::io::ErrorKind::NotFound
    }
}

impl From<tempfile::PathPersistError> for FileIoError {
    fn from(err: tempfile::PathPersistError) -> Self {
        FileIoError {
            path: err.path.to_path_buf(),
            op: FileOp::Persist,
            source: err.error,
        }
    }
}

impl From<tempfile::PersistError> for FileIoError {
    fn from(err: tempfile::PersistError) -> Self {
        FileIoError {
            path: err.file.path().into(),
            op: FileOp::Persist,
            source: err.error,
        }
    }
}
