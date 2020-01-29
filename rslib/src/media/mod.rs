use crate::err::Result;
use crate::media::database::open_or_create;
use rusqlite::Connection;
use std::path::{Path, PathBuf};

pub mod database;
pub mod files;

pub struct MediaManager {
    db: Connection,
    media_folder: PathBuf,
}

impl MediaManager {
    pub fn new<P, P2>(media_folder: P, media_db: P2) -> Result<Self>
    where
        P: Into<PathBuf>,
        P2: AsRef<Path>,
    {
        let db = open_or_create(media_db.as_ref())?;
        Ok(MediaManager {
            db,
            media_folder: media_folder.into(),
        })
    }
}
