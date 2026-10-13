// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io;
use std::io::Read;
use std::time::Duration;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

use anki_io::open_file;
use anki_io::read_dir_files;
use anki_io::remove_file;
use anki_proto::github::GithubRelease;
use anki_proto::github::LatestReleaseRequest;
use reqwest::header::HeaderMap;
use reqwest::StatusCode;
use serde_json::Value;
use sha2::Digest;

use super::Backend;
use crate::prelude::*;
use crate::services::BackendGithubService;
use crate::updates::download_file;
use crate::updates::release_path;
use crate::updates::reqwest_error_to_anki_error;
use crate::updates::updates_dir;
use crate::updates::user_agent;
use crate::updates::DownloadUpdateProgress;

const ALL_RELEASES_URL: &str = "https://api.github.com/repos/ankitects/anki/releases";
const LATEST_RELEASE_URL: &str = "https://api.github.com/repos/ankitects/anki/releases/latest";

/// GitHub answers an exhausted rate limit with 403 or 429 and no useful body,
/// which `error_for_status` turns into a bare "403 Forbidden". Repeating the
/// request immediately is also what gets a user agent blocked outright, so the
/// retry headers are read here and reported instead.
///
/// <https://docs.github.com/en/rest/using-the-rest-api/troubleshooting-the-rest-api#rate-limit-errors>
fn rate_limit_error(status: StatusCode, headers: &HeaderMap, now_secs: u64) -> Option<AnkiError> {
    if status != StatusCode::FORBIDDEN && status != StatusCode::TOO_MANY_REQUESTS {
        return None;
    }
    let header = |name| {
        headers
            .get(name)
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.trim().parse::<u64>().ok())
    };
    // `retry-after` is in seconds; `x-ratelimit-reset` is a unix timestamp, and
    // only means we are rate limited when no requests remain.
    let wait_secs = match header("retry-after") {
        Some(secs) => secs,
        None => match (header("x-ratelimit-remaining"), header("x-ratelimit-reset")) {
            (Some(0), Some(reset)) => reset.saturating_sub(now_secs),
            _ => return None,
        },
    };
    let wait = match wait_secs {
        0 => "a moment".to_string(),
        1 => "1 second".to_string(),
        secs if secs < 120 => format!("{secs} seconds"),
        secs => format!("{} minutes", secs.div_ceil(60)),
    };
    Some(AnkiError::NetworkError {
        source: crate::error::NetworkError {
            info: format!("GitHub rate limit reached; please try again in {wait}."),
            kind: crate::error::NetworkErrorKind::Other,
        },
    })
}

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or_default()
}

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
        let no_updates_msg = self.tr.errors_no_updates_available();
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
                .await
                .map_err(reqwest_error_to_anki_error)?;
            if let Some(err) = rate_limit_error(response.status(), response.headers(), now_secs()) {
                return Err(err);
            }
            let response = response
                .error_for_status()
                .map_err(reqwest_error_to_anki_error)?;
            let release_info: Value = if input.include_prerelease {
                let json: Value = response.json().await?;
                let releases = json.as_array().or_invalid("expected an array")?;
                releases.first().or_invalid("no releases found")?.clone()
            } else {
                response.json().await?
            };
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

#[cfg(test)]
mod test {
    use super::*;

    fn headers(pairs: &[(&'static str, &str)]) -> HeaderMap {
        let mut map = HeaderMap::new();
        for (name, value) in pairs {
            map.insert(*name, value.parse().unwrap());
        }
        map
    }

    fn info(err: Option<AnkiError>) -> String {
        match err {
            Some(AnkiError::NetworkError { source }) => source.info,
            other => panic!("expected a network error, got {other:?}"),
        }
    }

    #[test]
    fn retry_after_is_reported() {
        let err = rate_limit_error(
            StatusCode::TOO_MANY_REQUESTS,
            &headers(&[("retry-after", "90")]),
            0,
        );
        assert_eq!(
            info(err),
            "GitHub rate limit reached; please try again in 90 seconds."
        );
    }

    #[test]
    fn a_long_wait_is_reported_in_minutes() {
        let err = rate_limit_error(
            StatusCode::FORBIDDEN,
            &headers(&[("retry-after", "3601")]),
            0,
        );
        assert_eq!(
            info(err),
            "GitHub rate limit reached; please try again in 61 minutes."
        );
    }

    /// The primary rate limit comes back as 403 with no `retry-after`, and is
    /// only a rate limit when the remaining count is zero.
    #[test]
    fn reset_time_is_used_when_no_requests_remain() {
        let hdrs = headers(&[("x-ratelimit-remaining", "0"), ("x-ratelimit-reset", "600")]);
        let err = rate_limit_error(StatusCode::FORBIDDEN, &hdrs, 300);
        assert_eq!(
            info(err),
            "GitHub rate limit reached; please try again in 5 minutes."
        );
    }

    #[test]
    fn a_reset_in_the_past_does_not_underflow() {
        let hdrs = headers(&[("x-ratelimit-remaining", "0"), ("x-ratelimit-reset", "100")]);
        let err = rate_limit_error(StatusCode::FORBIDDEN, &hdrs, 500);
        assert_eq!(
            info(err),
            "GitHub rate limit reached; please try again in a moment."
        );
    }

    #[test]
    fn other_failures_are_left_alone() {
        // a 403 with requests still remaining is a permissions problem
        let remaining = headers(&[
            ("x-ratelimit-remaining", "12"),
            ("x-ratelimit-reset", "600"),
        ]);
        assert!(rate_limit_error(StatusCode::FORBIDDEN, &remaining, 0).is_none());
        // and so is one with no rate limit headers at all
        assert!(rate_limit_error(StatusCode::FORBIDDEN, &HeaderMap::new(), 0).is_none());
        // a different status is never a rate limit
        let hdrs = headers(&[("retry-after", "30")]);
        assert!(rate_limit_error(StatusCode::NOT_FOUND, &hdrs, 0).is_none());
        assert!(rate_limit_error(StatusCode::OK, &hdrs, 0).is_none());
    }
}
