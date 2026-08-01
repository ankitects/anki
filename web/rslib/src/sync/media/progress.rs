// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[derive(Debug, Default, Clone, Copy)]
pub struct MediaSyncProgress {
    pub checked: usize,
    pub downloaded_files: usize,
    pub downloaded_deletions: usize,
    pub uploaded_files: usize,
    pub uploaded_deletions: usize,
}

#[derive(Debug, Default, Clone, Copy)]
#[repr(transparent)]
pub struct MediaCheckProgress {
    pub checked: usize,
}
