// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use ninja_gen::archives::download_and_extract;
use ninja_gen::archives::empty_manifest;
use ninja_gen::archives::OnlineArchive;
use ninja_gen::archives::Platform;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::Build;

use crate::platform::overriden_python_wheel_platform;
use crate::python::BuildWheel;

fn mpv_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::WindowsX64 => OnlineArchive {
            url: "https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-x86_64-pc-windows-msvc.zip",
            sha256: "4e197f729f5071c6772f35fffd96e0f36e3e8a044bd9479b136bb09b7c6a80ff",
        },
        Platform::WindowsArm => OnlineArchive {
            url: "https://github.com/mpv-player/mpv/releases/download/v0.41.0/mpv-v0.41.0-aarch64-pc-windows-msvc.zip",
            sha256: "a822abeffd0ac88951f4084f3425f949842aa17d616f880637ebe9041e482e97",
        },
        _ => unreachable!(),
    }
}

fn lame_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::WindowsX64 => OnlineArchive {
            url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2026-05-08/lame3.100-64-20200409.zip",
            sha256: "59ea16ac74afb04f8ed9f33f75618e4e7e5b3e1ea53f5d751e3834e99f58ba6d",
        },
        Platform::WindowsArm => OnlineArchive {
            url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2026-05-08/lame-3.100-ARM64.zip",
            sha256: "03ecceab5d4f408b0f919b4618947a7731b5bd1f297fba52914bfb6eeae187fb",
        },
        _ => unreachable!(),
    }
}

fn read_version() -> String {
    std::fs::read_to_string("qt/audio/.version")
        .unwrap()
        .trim()
        .to_string()
}

pub fn build_audio(build: &mut Build) -> Result<()> {
    if cfg!(target_os = "linux") {
        return Ok(());
    }

    let deps = if cfg!(target_os = "macos") {
        inputs![glob!("qt/audio/**")]
    } else {
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
        inputs![glob!("qt/audio/**"), ":extract:mpv", ":extract:lame"]
    };

    build.add_action(
        "audio_wheel",
        BuildWheel {
            name: "anki-audio",
            version: read_version(),
            platform: overriden_python_wheel_platform().or(Some(build.host_platform)),
            deps,
            project_dir: "qt/audio",
        },
    )?;

    Ok(())
}
