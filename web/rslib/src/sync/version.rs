// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;

use crate::storage::SchemaVersion;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;

pub const SYNC_VERSION_MIN: u8 = SYNC_VERSION_08_SESSIONKEY;
pub const SYNC_VERSION_MAX: u8 = SYNC_VERSION_11_DIRECT_POST;

/// Added in 2013. Introduced a session key to identify parallel attempts at
/// syncing. At the end of 2022, only used by 0.045% of syncers. Half are
/// AnkiUniversal users, as it never added support for the V2 scheduler.
pub const SYNC_VERSION_08_SESSIONKEY: u8 = 8;

/// Added Jan 2018. No functional changes to protocol, but marks that the client
/// supports the V2 scheduler.
///
/// In July 2018 a separate chunked graves method was added, but was optional.
/// At the end of 2022, AnkiDroid is still using the old approach of passing all
/// graves to the start method in the legacy schema path.
pub const SYNC_VERSION_09_V2_SCHEDULER: u8 = 9;

/// Added Mar 2020. No functional changes to protocol, but marks that the client
/// supports the V2 timezone changes.
pub const SYNC_VERSION_10_V2_TIMEZONE: u8 = 10;

/// Added Jan 2023. Switches from packaging messages in a multipart request with
/// gzip to using headers and zstd, and stops using a separate session key for
/// media syncs. Schema 18 uploads/downloads are now supported, and hostNum has
/// been deprecated in favour of a redirect.
pub const SYNC_VERSION_11_DIRECT_POST: u8 = 11;

#[derive(Debug, Serialize, Deserialize, Clone, Copy)]
#[repr(transparent)]
pub struct SyncVersion(pub u8);

impl SyncVersion {
    pub fn is_too_old(&self) -> bool {
        self.0 < SYNC_VERSION_MIN
    }

    pub fn is_too_new(&self) -> bool {
        self.0 > SYNC_VERSION_MAX
    }

    pub fn ensure_supported(&self) -> HttpResult<()> {
        if self.is_too_old() || self.is_too_new() {
            None.or_bad_request(format!("unsupported sync version: {}", self.0))?;
        }
        Ok(())
    }

    pub fn latest() -> Self {
        SyncVersion(SYNC_VERSION_MAX)
    }

    pub fn multipart() -> Self {
        Self(SYNC_VERSION_10_V2_TIMEZONE)
    }

    pub fn is_multipart(&self) -> bool {
        self.0 < SYNC_VERSION_11_DIRECT_POST
    }

    pub fn is_zstd(&self) -> bool {
        self.0 >= SYNC_VERSION_11_DIRECT_POST
    }

    pub fn collection_schema(&self) -> SchemaVersion {
        if self.is_multipart() {
            SchemaVersion::V11
        } else {
            SchemaVersion::V18
        }
    }
}
