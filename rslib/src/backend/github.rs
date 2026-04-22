// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time::Duration;

use anki_proto::github::GetLatestReleaseRequest;
use anki_proto::github::GithubRelease;
use serde_json::Value;

use super::Backend;
use crate::prelude::*;
use crate::services::BackendGithubService;
use crate::version::version;

const ALL_RELEASES_URL: &'static str = "https://api.github.com/repos/abdnh/anki/releases";
const LATEST_RELEASE_URL: &'static str = "https://api.github.com/repos/abdnh/anki/releases/latest";

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

impl BackendGithubService for Backend {
    fn get_latest_release(&self, input: GetLatestReleaseRequest) -> Result<GithubRelease> {
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
                    .get(0)
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
                if filename.contains(&platform_suffix) {
                    release = Some(GithubRelease {
                        tag_name: tag_name.into(),
                        filename: filename.into(),
                        url: url.into(),
                    });
                    break;
                }
            }
            release.or_invalid(error_message)
        })
    }
}
