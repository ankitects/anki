// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use ninja_gen::archives::download_and_extract;
use ninja_gen::archives::empty_manifest;
use ninja_gen::archives::OnlineArchive;
use ninja_gen::command::RunCommand;
use ninja_gen::hashmap;
use ninja_gen::inputs;
use ninja_gen::Build;

pub fn setup_uv_universal(build: &mut Build) -> Result<()> {
    if !cfg!(target_arch = "aarch64") {
        return Ok(());
    }

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
