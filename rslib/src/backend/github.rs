// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Read;
use std::time::Duration;

use anki_io::open_file;
use anki_proto::github::GithubRelease;
use anki_proto::github::LatestReleaseRequest;
use serde_json::Value;
use sha2::Digest;

use super::Backend;
use crate::prelude::*;
use crate::services::BackendGithubService;
use crate::updates::download_file;
use crate::updates::release_path;
use crate::updates::DownloadUpdateProgress;
use crate::version::version;

const ALL_RELEASES_URL: &str = "https://api.github.com/repos/abdnh/anki/releases";
const LATEST_RELEASE_URL: &str = "https://api.github.com/repos/abdnh/anki/releases/latest";

fn get_platform_suffix() -> Option<&'static str> {
    if cfg!(target_os = "windows") {
        Some("-windows")
    } else if cfg!(target_os = "macos") {
        if cfg!(target_arch = "aarch64") {
            Some("-mac-apple")
        } else if cfg!(target_arch = "x86_64") {
            Some("-mac-intel")
        } else {
            None
        }
    } else if cfg!(target_os = "linux") {
        if cfg!(target_arch = "aarch64") {
            Some("-linux-aarch64")
        } else if cfg!(target_arch = "x86_64") {
            Some("-linux-x86_64")
        } else {
            None
        }
    } else {
        None
    }
}

fn release_is_downloaded(filename: &str, checksum: &str) -> Result<bool> {
    let output_path = release_path(filename)?;
    if output_path.exists() {
        let mut buf = [0; 64 * 1024];
        let mut file = open_file(&output_path)?;
        let mut digest = sha2::Sha256::new();
        loop {
            let count = match file.read(&mut buf) {
                Ok(0) => break,
                Err(e) if e.kind() == tokio::io::ErrorKind::Interrupted => continue,
                result => result?,
            };
            digest.update(&buf[..count]);
        }
        let result = digest.finalize();
        let actual_checksum = format!("{result:x}");

        Ok(actual_checksum == checksum)
    } else {
        Ok(false)
    }
}

impl BackendGithubService for Backend {
    fn get_latest_release(&self, input: LatestReleaseRequest) -> Result<GithubRelease> {
        let error_message = "No updates available for your platform";
        let platform_suffix = get_platform_suffix().or_invalid(error_message)?;
        let url = if input.include_prerelease {
            ALL_RELEASES_URL
        } else {
            LATEST_RELEASE_URL
        };
        self.runtime_handle().block_on(async move {
            let response = self
                .web_client()
                .get(url)
                .header("User-Agent", format!("Anki {}", version()))
                .timeout(Duration::from_secs(60))
                .send()
                .await?
                .error_for_status()?;
            let release_info: Value;
            if input.include_prerelease {
                let json: Value = response.json().await?;
                let releases = json.as_array().ok_or_else(|| AnkiError::JsonError {
                    info: "expected an array".into(),
                })?;
                release_info = releases
                    .first()
                    .ok_or_else(|| AnkiError::JsonError {
                        info: "no releases found".into(),
                    })?
                    .clone();
            } else {
                release_info = response.json().await?;
            }
            let tag_name = release_info["tag_name"].as_str().unwrap();
            let assets = release_info["assets"]
                .as_array()
                .ok_or_else(|| AnkiError::JsonError {
                    info: "assets should be an array".into(),
                })?;
            let mut release: Option<GithubRelease> = None;
            for asset in assets {
                let filename = asset["name"].as_str().unwrap_or("");
                let url = asset["browser_download_url"].as_str().unwrap();
                let checksum = asset["digest"]
                    .as_str()
                    .unwrap()
                    .split_once("sha256:")
                    .unwrap()
                    .1;
                if filename.contains(platform_suffix) {
                    release = Some(GithubRelease {
                        tag_name: tag_name.into(),
                        filename: filename.into(),
                        url: url.into(),
                        checksum: checksum.into(),
                    });
                    break;
                }
            }
            release.or_invalid(error_message)
        })
    }

    fn download_latest_release(
        &self,
        input: LatestReleaseRequest,
    ) -> Result<anki_proto::generic::String> {
        let mut progress = self.new_progress_handler::<DownloadUpdateProgress>();
        let release = self.get_latest_release(input)?;
        let already_downloaded = release_is_downloaded(&release.filename, &release.checksum)?;
        if !already_downloaded {
            self.runtime_handle().block_on(async {
                download_file(
                    &mut progress,
                    &release.filename,
                    &release.url,
                    &release.checksum,
                )
                .await
            })?;
        }

        let output_path = release_path(&release.filename)?
            .to_str()
            .or_invalid("non-unicode filename")?
            .to_string();

        Ok(output_path.into())
    }
}
