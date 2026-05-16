// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::PathBuf;

use anki_io::create_dir_all;
use anki_io::remove_file;
use futures::StreamExt;
use sha2::Digest;
use tokio::io::AsyncWriteExt;

use crate::error::AnkiError;
use crate::error::OrInvalid;
use crate::error::Result;
use crate::progress::ThrottlingProgressHandler;
use crate::version::version;

#[derive(Debug, Copy, Clone, Default)]
pub struct DownloadUpdateProgress {
    pub downloaded_bytes: usize,
    pub total_bytes: usize,
}

pub fn updates_dir() -> Result<PathBuf> {
    let dir = dirs::data_local_dir()
        .or_invalid("Unable to determine data_dir")?
        .join("AnkiProgramFiles")
        .join("updates");
    if !dir.exists() {
        create_dir_all(&dir)?;
    }

    Ok(dir)
}

pub fn release_path(filename: &str) -> Result<PathBuf> {
    let dir = updates_dir()?;

    Ok(dir.join(filename))
}

pub fn user_agent() -> String {
    format!("Anki {}", version())
}

pub async fn download_file(
    client: &reqwest::Client,
    progress: &mut ThrottlingProgressHandler<DownloadUpdateProgress>,
    filename: &str,
    url: &str,
    checksum: &str,
) -> Result<PathBuf> {
    let response = client
        .get(url)
        .header("User-Agent", user_agent())
        .send()
        .await?
        .error_for_status()?;
    let content_length = response.content_length().unwrap_or_default();
    let output_path = release_path(filename)?;
    let mut stream = response.bytes_stream();
    let mut file = tokio::fs::File::create(&output_path).await?;
    let mut downloaded_bytes: usize = 0;
    let mut digest = sha2::Sha256::new();
    progress.set(DownloadUpdateProgress {
        downloaded_bytes: 0,
        total_bytes: content_length as usize,
    })?;
    while let Some(chunk_result) = stream.next().await {
        let chunk = chunk_result?;
        digest.update(&chunk);
        file.write_all(&chunk).await?;
        downloaded_bytes += chunk.len();
        progress.set(DownloadUpdateProgress {
            downloaded_bytes,
            total_bytes: content_length as usize,
        })?;
    }
    file.flush().await?;
    let actual_checksum = hex::encode(digest.finalize());
    if actual_checksum != checksum {
        let _ = remove_file(output_path);
        return Err(AnkiError::InvalidChecksum {
            info: "Invalid checksum".into(),
        });
    }

    Ok(output_path)
}
