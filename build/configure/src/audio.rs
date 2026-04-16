// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use ninja_gen::audio::extract_audio;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::Build;

use crate::platform::overriden_python_wheel_platform;
use crate::python::BuildWheel;

fn read_version() -> String {
    std::fs::read_to_string("qt/audio/.version")
        .unwrap()
        .trim()
        .to_string()
}

pub fn build_audio(build: &mut Build) -> Result<()> {
    extract_audio(build)?;
    build.add_action(
        "wheels:audio",
        BuildWheel {
            name: "anki-audio",
            version: read_version(),
            platform: overriden_python_wheel_platform().or(Some(build.host_platform)),
            deps: inputs![glob!("qt/audio/**"), ":extract:mpv", ":extract:lame"],
            project_dir: "qt/audio",
        },
    )?;

    Ok(())
}
