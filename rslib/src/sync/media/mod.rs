// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod begin;
pub mod changes;
pub mod database;
pub mod download;
pub mod progress;
pub mod protocol;
pub mod sanity;
pub mod syncer;
mod tests;
pub mod upload;
pub mod zip;

/// The maximum length we allow a filename to be. When combined
/// with the rest of the path, the full path needs to be under ~240 chars
/// on some platforms, and some filesystems like eCryptFS will increase
/// the length of the filename.
pub static MAX_MEDIA_FILENAME_LENGTH: usize = 120;

// We can't enforce the 120 limit until all clients have shifted over to the
// Rust codebase.
pub const MAX_MEDIA_FILENAME_LENGTH_SERVER: usize = 255;

/// Media syncing does not support files over 100MiB.
pub static MAX_INDIVIDUAL_MEDIA_FILE_SIZE: usize = 100 * 1024 * 1024;

pub static MAX_MEDIA_FILES_IN_ZIP: usize = 25;

/// If reached, no further files are placed into the zip.
pub static MEDIA_SYNC_TARGET_ZIP_BYTES: usize = (2.5 * 1024.0 * 1024.0) as usize;
