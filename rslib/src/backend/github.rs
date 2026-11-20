// Copyright: Anki Software, LLC and contributors
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
use crate::updates::reqwest_error_to_anki_error;
use crate::updates::updates_dir;
use crate::updates::user_agent;
use crate::updates::DownloadUpdateProgress;

const ALL_RELEASES_URL: &str = "https://api.github.com/repos/JSchoreels/anki/releases";
const LATEST_RELEASE_URL: &str = "https://api.github.com/repos/JSchoreels/anki/releases/latest";

// NOTE: must match filenames produced by build_installer.py
fn platform_release_suffix(os: &str, arch: &str, portable: bool) -> Option<&'static str> {
    match (os, arch, portable) {
        ("windows", "x86_64", false) => Some("-win-x64.msi"),
        ("windows", "aarch64", false) => Some("-win-arm64.msi"),
        ("macos", "x86_64", false) => Some("-mac-intel.dmg"),
        ("macos", "aarch64", false) => Some("-mac-apple.dmg"),
        ("linux", "x86_64", false) => Some("-linux-x86_64.tar.zst"),
        ("linux", "aarch64", false) => Some("-linux-aarch64.tar.zst"),
        ("windows", "x86_64", true) => Some("-portable-win-x64.zip"),
        ("windows", "aarch64", true) => Some("-portable-win-arm64.zip"),
        ("macos", "x86_64", true) => Some("-portable-mac-intel.zip"),
        ("macos", "aarch64", true) => Some("-portable-mac-apple.zip"),
        ("linux", "x86_64", true) => Some("-portable-linux-x86_64.tar.zst"),
        ("linux", "aarch64", true) => Some("-portable-linux-aarch64.tar.zst"),
        _ => None,
    }
}

fn find_platform_release_asset<'a>(
    assets: &'a [Value],
    suffix: &str,
    portable: bool,
) -> Option<&'a Value> {
    assets.iter().find(|asset| {
        asset["name"].as_str().is_some_and(|filename| {
            filename.contains("-portable-") == portable && filename.ends_with(suffix)
        })
    })
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
    fn extract_portable_update(
        &self,
        input: anki_proto::github::ExtractPortableUpdateRequest,
    ) -> Result<()> {
        crate::updates::extract_portable_update(
            std::path::Path::new(&input.archive_path),
            std::path::Path::new(&input.destination),
            &mut self.new_progress_handler::<DownloadUpdateProgress>(),
        )
    }

    fn get_latest_release(&self, input: LatestReleaseRequest) -> Result<GithubRelease> {
        let no_updates_msg = self.tr.errors_no_updates_available();
        let suffix =
            platform_release_suffix(std::env::consts::OS, std::env::consts::ARCH, input.portable)
                .or_invalid(no_updates_msg.clone())?;
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
                .map_err(reqwest_error_to_anki_error)?
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
            let target_commitish = release_info["target_commitish"]
                .as_str()
                .or_invalid("release target commit not found")?;
            let assets = release_info["assets"]
                .as_array()
                .or_invalid("assets should be an array")?;
            let asset = find_platform_release_asset(assets, suffix, input.portable)
                .or_invalid(no_updates_msg)?;
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
            Ok(GithubRelease {
                tag_name: tag_name.into(),
                filename: filename.into(),
                url: url.into(),
                checksum: checksum.into(),
                target_commitish: target_commitish.into(),
            })
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
mod tests {
    use serde_json::json;

    use super::*;

    #[test]
    fn update_asset_selection_matches_edition_platform_and_architecture() {
        for (os, arch, installer, portable) in [
            (
                "windows",
                "x86_64",
                "anki-1.2.3-win-x64.msi",
                "anki-1.2.3-portable-win-x64.zip",
            ),
            (
                "windows",
                "aarch64",
                "anki-1.2.3-win-arm64.msi",
                "anki-1.2.3-portable-win-arm64.zip",
            ),
            (
                "macos",
                "aarch64",
                "anki-1.2.3-mac-apple.dmg",
                "anki-1.2.3-portable-mac-apple.zip",
            ),
            (
                "macos",
                "x86_64",
                "anki-1.2.3-mac-intel.dmg",
                "anki-1.2.3-portable-mac-intel.zip",
            ),
            (
                "linux",
                "x86_64",
                "anki-1.2.3-linux-x86_64.tar.zst",
                "anki-1.2.3-portable-linux-x86_64.tar.zst",
            ),
            (
                "linux",
                "aarch64",
                "anki-1.2.3-linux-aarch64.tar.zst",
                "anki-1.2.3-portable-linux-aarch64.tar.zst",
            ),
        ] {
            let release_assets = json!([
                { "name": "anki-1.2.3-portable-win-x86.zip" },
                { "name": portable },
                { "name": installer },
            ]);
            for (is_portable, expected) in [(false, installer), (true, portable)] {
                let suffix = platform_release_suffix(os, arch, is_portable).unwrap();
                let selected = find_platform_release_asset(
                    release_assets.as_array().unwrap(),
                    suffix,
                    is_portable,
                )
                .unwrap();

                assert_eq!(selected["name"].as_str(), Some(expected));
                let wrong_edition =
                    json!([{ "name": if is_portable { installer } else { portable } }]);
                assert!(find_platform_release_asset(
                    wrong_edition.as_array().unwrap(),
                    suffix,
                    is_portable,
                )
                .is_none());
            }
        }
    }

    #[test]
    fn portable_download_is_not_an_installer_fallback() {
        let release_assets = json!([
            { "name": "anki-1.2.3-portable-win-x64.msi" },
        ]);

        assert!(find_platform_release_asset(
            release_assets.as_array().unwrap(),
            "-win-x64.msi",
            false
        )
        .is_none());
    }

    #[test]
    fn unsupported_update_platform_has_no_download() {
        for portable in [false, true] {
            assert!(platform_release_suffix("freebsd", "x86_64", portable).is_none());
            assert!(platform_release_suffix("windows", "x86", portable).is_none());
        }
    }
}
