// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io;
use std::io::Read;
use std::time::Duration;

use anki_io::open_file;
use anki_io::read_dir_files;
use anki_io::remove_file;
use anki_proto::github::GithubRelease;
use anki_proto::github::LatestReleaseRequest;
use serde_json::Value;
use sha2::Digest;

use super::Backend;
use crate::prelude::*;
use crate::services::BackendGithubService;
use crate::updates::download_file;
use crate::updates::release_path;
use crate::updates::updates_dir;
use crate::updates::user_agent;
use crate::updates::DownloadUpdateProgress;

const ALL_RELEASES_URL: &str = "https://api.github.com/repos/ankitects/anki/releases";
const LATEST_RELEASE_URL: &str = "https://api.github.com/repos/ankitects/anki/releases/latest";

// NOTE: must match platform suffixes in build_installer.py
fn get_platform_suffix() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => Some("-win-x64"),
        ("windows", "aarch64") => Some("-win-arm64"),
        ("macos", "x86_64") => Some("-mac-intel"),
        ("macos", "aarch64") => Some("-mac-apple"),
        ("linux", "x86_64") => Some("-linux-x86_64"),
        ("linux", "aarch64") => Some("-linux-aarch64"),
        _ => None,
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
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                result => result?,
            };
            digest.update(&buf[..count]);
        }
        let actual_checksum = hex::encode(digest.finalize());

        Ok(actual_checksum == checksum)
    } else {
        Ok(false)
    }
}

impl BackendGithubService for Backend {
    fn get_latest_release(&self, input: LatestReleaseRequest) -> Result<GithubRelease> {
        let no_updates_msg = self.tr.addons_no_updates_available();
        let platform_suffix = get_platform_suffix().or_invalid(no_updates_msg.clone())?;
        let url = if input.include_prerelease {
            ALL_RELEASES_URL
        } else {
            LATEST_RELEASE_URL
        };
        self.runtime_handle().block_on(async {
            let response = self
                .web_client()
                .get(url)
                .header("User-Agent", user_agent())
                .timeout(Duration::from_secs(60))
                .send()
                .await?
                .error_for_status()?;
            let release_info: Value;
            if input.include_prerelease {
                let json: Value = response.json().await?;
                let releases = json.as_array().or_invalid("expected an array")?;
                release_info = releases.first().or_invalid("no releases found")?.clone();
            } else {
                release_info = response.json().await?;
            }
            let tag_name = release_info["tag_name"]
                .as_str()
                .or_invalid("release tag not found")?;
            let assets = release_info["assets"]
                .as_array()
                .or_invalid("assets should be an array")?;
            let mut release: Option<GithubRelease> = None;
            for asset in assets {
                let filename = asset["name"]
                    .as_str()
                    .or_invalid("release name not found")?;
                let url = asset["browser_download_url"]
                    .as_str()
                    .or_invalid("download URL not found")?;
                let checksum = asset["digest"]
                    .as_str()
                    .or_invalid("release digest not found")?
                    .split_once("sha256:")
                    .or_invalid("sha256 suffix not found")?
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
            release.or_invalid(no_updates_msg)
        })
    }

    fn download_release(&self, release: GithubRelease) -> Result<anki_proto::generic::String> {
        let mut progress = self.new_progress_handler::<DownloadUpdateProgress>();
        let already_downloaded = release_is_downloaded(&release.filename, &release.checksum)?;
        if !already_downloaded {
            self.runtime_handle().block_on(async {
                download_file(
                    &self.web_client(),
                    &mut progress,
                    &release.filename,
                    &release.url,
                    &release.checksum,
                )
                .await
            })?;
        }

        // Remove old downloads
        let output_dir = updates_dir()?;
        let output_path = release_path(&release.filename)?;
        for file in read_dir_files(output_dir)? {
            let path = file?.path();
            if path != output_path {
                let _ = remove_file(path);
            }
        }

        let output_path = output_path
            .to_str()
            .or_invalid("non-unicode filename")?
            .to_string();
        Ok(output_path.into())
    }
}
