// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![allow(dead_code)]
#![allow(unused_imports)]

use std::env;

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::archives::download_and_extract;
use ninja_gen::archives::empty_manifest;
use ninja_gen::archives::with_exe;
use ninja_gen::archives::OnlineArchive;
use ninja_gen::archives::Platform;
use ninja_gen::build::BuildProfile;
use ninja_gen::cargo::CargoBuild;
use ninja_gen::cargo::RustOutput;
use ninja_gen::command::RunCommand;
use ninja_gen::git::SyncSubmodule;
use ninja_gen::glob;
use ninja_gen::hashmap;
use ninja_gen::input::BuildInput;
use ninja_gen::inputs;
use ninja_gen::python::PythonEnvironment;
use ninja_gen::Build;
use ninja_gen::Utf8Path;

use crate::anki_version;
use crate::platform::overriden_python_target_platform;
use crate::platform::overriden_rust_target_triple;

pub fn setup_uv_universal(build: &mut Build) -> Result<()> {
    build.add_action(
        "launcher:uv_universal",
        RunCommand {
            command: "/usr/bin/lipo",
            args: "-create -output $out $arm_bin $x86_bin",
            inputs: hashmap! {
                "arm_bin" => inputs![":extract:uv:bin"],
                "x86_bin" => inputs![":extract:uv_mac_x86:bin"],
            },
            outputs: hashmap! {
                "out" => vec!["launcher/uv"],
            },
        },
    )
}

pub fn build_launcher(build: &mut Build) -> Result<()> {
    setup_uv_universal(build)?;
    download_and_extract(build, "nsis_plugins", NSIS_PLUGINS, empty_manifest())?;

    Ok(())
}

const NSIS_PLUGINS: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2023-05-19/nsis.tar.zst",
    sha256: "6133f730ece699de19714d0479c73bc848647d277e9cc80dda9b9ebe532b40a8",
};
