// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::io;
use std::io::Read;
use std::path::Path;

use serde::Deserialize;
use serde::Serialize;

use crate::error;
use crate::error::AnkiError;
use crate::error::SyncErrorKind;
use crate::media::files::add_file_from_ankiweb;
use crate::media::files::AddedFile;

#[derive(Debug, Serialize, Deserialize)]
pub struct DownloadFilesRequest {
    pub files: Vec<String>,
}

pub(crate) fn extract_into_media_folder(
    media_folder: &Path,
    zip: Vec<u8>,
) -> error::Result<Vec<AddedFile>> {
    let reader = io::Cursor::new(zip);
    let mut zip = zip::ZipArchive::new(reader)?;

    let meta_file = zip.by_name("_meta")?;
    let fmap: HashMap<String, String> = serde_json::from_reader(meta_file)?;
    let mut output = Vec::with_capacity(fmap.len());

    for i in 0..zip.len() {
        let mut file = zip.by_index(i)?;
        let name = file.name();
        if name == "_meta" {
            continue;
        }

        let real_name = fmap
            .get(name)
            .ok_or_else(|| AnkiError::sync_error("malformed zip", SyncErrorKind::Other))?;

        let mut data = Vec::with_capacity(file.size() as usize);
        file.read_to_end(&mut data)?;

        let added = add_file_from_ankiweb(media_folder, real_name, &data)?;

        output.push(added);
    }

    Ok(output)
}
