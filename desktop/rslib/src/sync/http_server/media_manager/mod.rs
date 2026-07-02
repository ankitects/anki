// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod download;
pub mod upload;

use std::path::Path;
use std::path::PathBuf;

use anki_io::create_dir_all;

use crate::prelude::*;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::media::changes::MediaChange;
use crate::sync::media::database::server::ServerMediaDatabase;
use crate::sync::media::sanity::MediaSanityCheckResponse;

pub(crate) struct ServerMediaManager {
    pub media_folder: PathBuf,
    pub db: ServerMediaDatabase,
}

impl ServerMediaManager {
    pub(crate) fn new(user_folder: &Path) -> HttpResult<ServerMediaManager> {
        let media_folder = user_folder.join("media");
        create_dir_all(&media_folder).or_internal_err("media folder create")?;
        Ok(Self {
            media_folder,
            db: ServerMediaDatabase::new(&user_folder.join("media.db"))
                .or_internal_err("open media db")?,
        })
    }

    pub fn last_usn(&self) -> HttpResult<Usn> {
        self.db.last_usn().or_internal_err("get last usn")
    }

    pub fn media_changes_chunk(&self, after_usn: Usn) -> HttpResult<Vec<MediaChange>> {
        self.db
            .media_changes_chunk(after_usn)
            .or_internal_err("changes chunk")
    }

    pub fn sanity_check(&self, client_file_count: u32) -> HttpResult<MediaSanityCheckResponse> {
        let server = self
            .db
            .nonempty_file_count()
            .or_internal_err("get nonempty count")?;
        Ok(if server == client_file_count {
            MediaSanityCheckResponse::Ok
        } else {
            MediaSanityCheckResponse::SanityCheckFailed
        })
    }
}
