// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::PathBuf;

use anki_io::create_dir_all;
use anki_io::remove_file;
use futures::StreamExt;
use sha2::Digest;
use tokio::io::AsyncWriteExt;

use crate::error::AnkiError;
use crate::error::NetworkError;
use crate::error::NetworkErrorKind;
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
    let base = if let Some(root) = std::env::var_os("ANKI_PORTABLE_ROOT") {
        PathBuf::from(root).join("Anki Portable Data")
    } else {
        dirs::data_local_dir()
            .or_invalid("Unable to determine data_dir")?
            .join("AnkiProgramFiles")
    };
    let dir = base.join("updates");
    if !dir.exists() {
        create_dir_all(&dir)?;
    }

    Ok(dir)
}

pub fn release_path(filename: &str) -> Result<PathBuf> {
    let dir = updates_dir()?;

    Ok(dir.join(filename))
}

pub fn extract_portable_update(
    archive: &std::path::Path,
    destination: &std::path::Path,
    progress: &mut ThrottlingProgressHandler<DownloadUpdateProgress>,
) -> Result<()> {
    // A fresh staging directory keeps extraction separate from application/data
    // files.
    anki_io::create_dir(destination)?;
    progress.set(DownloadUpdateProgress::default())?;
    let file = anki_io::open_file(archive)?;
    if archive.to_string_lossy().ends_with(".zip") {
        let mut archive = zip::ZipArchive::new(file)?;
        for index in 0..archive.len() {
            archive
                .by_index(index)?
                .enclosed_name()
                .or_invalid("Unsafe update archive path")?;
            progress.check_cancelled()?;
        }
        archive.extract(destination)?;
    } else if archive.to_string_lossy().ends_with(".tar.zst") {
        let decoder = zstd::stream::read::Decoder::new(file)?;
        let mut archive = tar::Archive::new(decoder);
        for entry in archive.entries()? {
            let mut entry = entry?;
            if entry.path()?.components().any(|part| {
                !matches!(
                    part,
                    std::path::Component::Normal(_) | std::path::Component::CurDir
                )
            }) {
                crate::invalid_input!("Unsafe update archive path");
            }
            if !entry.unpack_in(destination)? {
                crate::invalid_input!("Unsafe update archive path");
            }
            progress.set(DownloadUpdateProgress::default())?;
        }
    } else {
        crate::invalid_input!("Unsupported portable update archive");
    }
    progress.set(DownloadUpdateProgress::default())?;
    Ok(())
}

pub fn user_agent() -> String {
    format!("Anki {}", version())
}

pub fn reqwest_error_to_anki_error(err: reqwest::Error) -> AnkiError {
    let info = err.to_string();
    if err.is_timeout() {
        AnkiError::NetworkError {
            source: NetworkError {
                info,
                kind: NetworkErrorKind::Timeout,
            },
        }
    } else {
        AnkiError::NetworkError {
            source: NetworkError {
                info,
                kind: NetworkErrorKind::Other,
            },
        }
    }
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
        .await
        .map_err(reqwest_error_to_anki_error)?
        .error_for_status()
        .map_err(reqwest_error_to_anki_error)?;
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

#[cfg(test)]
mod portable_tests {
    use std::io::Write;

    use super::*;

    #[test]
    fn portable_zip_extraction_preserves_executable_and_internal_symlinks() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("portable.zip");
        let mut zip = zip::ZipWriter::new(anki_io::create_file(&path).unwrap());
        let options = zip::write::SimpleFileOptions::default().unix_permissions(0o755);
        zip.start_file("Anki Portable/anki", options).unwrap();
        zip.write_all(b"launch").unwrap();
        #[cfg(unix)]
        zip.add_symlink("Anki Portable/alias", "anki", options)
            .unwrap();
        zip.finish().unwrap();
        let destination = temp.path().join("payload");

        extract_portable_update(&path, &destination, &mut Default::default()).unwrap();

        assert_eq!(
            std::fs::read(destination.join("Anki Portable/anki")).unwrap(),
            b"launch"
        );
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let app = destination.join("Anki Portable/anki");
            assert_ne!(
                std::fs::metadata(app).unwrap().permissions().mode() & 0o111,
                0
            );
            let link = destination.join("Anki Portable/alias");
            assert!(std::fs::symlink_metadata(&link)
                .unwrap()
                .file_type()
                .is_symlink());
            assert_eq!(std::fs::read(link).unwrap(), b"launch");
        }
    }

    #[test]
    fn portable_tar_zstd_extraction_preserves_application_content() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("portable.tar.zst");
        let encoder = zstd::Encoder::new(anki_io::create_file(&path).unwrap(), 1).unwrap();
        let mut archive = tar::Builder::new(encoder);
        let mut header = tar::Header::new_gnu();
        header.set_size(6);
        header.set_mode(0o755);
        header.set_cksum();
        archive
            .append_data(&mut header, "Anki Portable/anki", &b"launch"[..])
            .unwrap();
        archive.into_inner().unwrap().finish().unwrap();
        let destination = temp.path().join("payload");

        extract_portable_update(&path, &destination, &mut Default::default()).unwrap();

        assert_eq!(
            std::fs::read(destination.join("Anki Portable/anki")).unwrap(),
            b"launch"
        );
        assert!(extract_portable_update(&path, &destination, &mut Default::default()).is_err());
    }

    #[test]
    fn portable_extraction_rejects_parent_paths_without_writing_outside_staging() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("portable.zip");
        let mut zip = zip::ZipWriter::new(anki_io::create_file(&path).unwrap());
        zip.start_file("../outside", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(b"bad").unwrap();
        zip.finish().unwrap();

        assert!(extract_portable_update(
            &path,
            &temp.path().join("payload"),
            &mut Default::default()
        )
        .is_err());
        assert!(!temp.path().join("outside").exists());
    }
}
