// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::PathBuf;

use snafu::Snafu;

/// Wrapper for [std::io::Error] with additional information on the attempted
/// operation.
#[derive(Debug, Snafu)]
#[snafu(visibility(pub), display("{op:?} {path:?}"))]
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
    Remove,
    CopyFrom(PathBuf),
    Persist,
    Sync,
    Metadata,
    DecodeUtf8Filename,
    /// For legacy errors without any context.
    Unknown,
}

impl FileOp {
    pub fn copy(from: impl Into<PathBuf>) -> Self {
        Self::CopyFrom(from.into())
    }
}

impl FileIoError {
    pub fn message(&self) -> String {
        format!(
            "Failed to {} '{}': {}",
            match &self.op {
                FileOp::Unknown => return format!("{}", self.source),
                FileOp::Open => "open".into(),
                FileOp::Read => "read".into(),
                FileOp::Create => "create file in".into(),
                FileOp::Write => "write".into(),
                FileOp::Remove => "remove".into(),
                FileOp::CopyFrom(p) => format!("copy from '{}' to", p.to_string_lossy()),
                FileOp::Persist => "persist".into(),
                FileOp::Sync => "sync".into(),
                FileOp::Metadata => "get metadata".into(),
                FileOp::DecodeUtf8Filename => "decode utf8 filename".into(),
            },
            self.path.to_string_lossy(),
            self.source
        )
    }

    pub fn is_not_found(&self) -> bool {
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
