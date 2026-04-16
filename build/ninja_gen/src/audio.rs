// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;

use crate::archives::download_and_extract;
use crate::archives::empty_manifest;
use crate::archives::OnlineArchive;
use crate::archives::Platform;
use crate::Build;

fn mpv_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::MacX64 => OnlineArchive {
            url: "https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-macos-15-intel.zip",
            sha256: "41003617ab4f7784394b5ddea7ce51b3e0838e8cfc8166ad1a378b2eda3b583c",
        },
        Platform::MacArm => OnlineArchive {
            url: "https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-macos-14-arm.zip",
            sha256: "5c96f9b21355fc0a11d2e2161ad65f33031070e9fb3f6bd9865fb459b94587e6",
        },
        Platform::WindowsX64 => OnlineArchive {
            url: "https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-x86_64-pc-windows-msvc.zip",
            sha256: "4e197f729f5071c6772f35fffd96e0f36e3e8a044bd9479b136bb09b7c6a80ff",
        },
        Platform::WindowsArm => OnlineArchive {
            url: "https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-aarch64-pc-windows-msvc.zip",
            sha256: "a822abeffd0ac88951f4084f3425f949842aa17d616f880637ebe9041e482e97",
        },
        _ => panic!("audio wheel is not supported on {}", platform.as_rust_triple()),
    }
}

fn lame_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::MacX64 => todo!(),
        Platform::MacArm => todo!(),
        Platform::WindowsX64 => OnlineArchive {
            url: "https://www.rarewares.org/files/mp3/lame3.100-64-20200409.zip",
            sha256: "59ea16ac74afb04f8ed9f33f75618e4e7e5b3e1ea53f5d751e3834e99f58ba6d",
        },
        Platform::WindowsArm => OnlineArchive {
            url: "https://www.rarewares.org/files/mp3/lame-3.100-ARM64.zip",
            sha256: "b1868219b9c9f38f83834b2e6a69c616cabfe6658f5d05c26ac371f39afdac5d",
        },
        _ => panic!(
            "audio whee is not supported on {}",
            platform.as_rust_triple()
        ),
    }
}

pub fn extract_audio(build: &mut Build) -> Result<()> {
    download_and_extract(
        build,
        "mpv",
        mpv_archive(build.host_platform),
        empty_manifest(),
    )?;
    download_and_extract(
        build,
        "lame",
        lame_archive(build.host_platform),
        empty_manifest(),
    )?;

    Ok(())
}
