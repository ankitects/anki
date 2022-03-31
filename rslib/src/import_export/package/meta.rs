// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(super) use crate::backend_proto::{package_metadata::Version, PackageMetadata as Meta};

impl Version {
    pub(super) fn collection_filename(&self) -> &'static str {
        match self {
            Version::Unknown => unreachable!(),
            Version::Legacy1 => "collection.anki2",
            Version::Legacy2 => "collection.anki21",
            Version::Latest => "collection.anki21b",
        }
    }
}

impl Meta {
    pub(super) fn new() -> Self {
        Self {
            version: Version::Latest as i32,
        }
    }

    pub(super) fn new_legacy() -> Self {
        Self {
            version: Version::Legacy2 as i32,
        }
    }

    pub(super) fn collection_filename(&self) -> &'static str {
        self.version().collection_filename()
    }

    pub(super) fn zstd_compressed(&self) -> bool {
        !self.is_legacy()
    }

    pub(super) fn media_list_is_hashmap(&self) -> bool {
        self.is_legacy()
    }

    pub(super) fn strict_media_checks(&self) -> bool {
        !self.is_legacy()
    }

    fn is_legacy(&self) -> bool {
        matches!(self.version(), Version::Legacy1 | Version::Legacy2)
    }
}
