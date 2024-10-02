// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fs;
use std::process::Command;

use camino::Utf8PathBuf;
use clap::Args;

use crate::run::run_command;

#[derive(Args, Debug)]
pub struct BuildArtifactsArgs {
    bundle_root: Utf8PathBuf,
    pyoxidizer_bin: String,
}

pub fn build_artifacts(args: BuildArtifactsArgs) {
    // build.rs doesn't declare inputs from venv, so we need to force a rebuild to
    // ensure changes to our libs/the venv get included
    let artifacts = args.bundle_root.join("artifacts");
    if artifacts.exists() {
        fs::remove_dir_all(&artifacts).unwrap();
    }
    let bundle_root = args.bundle_root.canonicalize_utf8().unwrap();
    let build_folder = bundle_root.join("build");
    if build_folder.exists() {
        fs::remove_dir_all(&build_folder).unwrap();
    }

    run_command(
        Command::new(&args.pyoxidizer_bin)
            .args([
                "--system-rust",
                "run-build-script",
                "qt/bundle/build.rs",
                "--var",
                "venv",
                "out/bundle/pyenv",
                "--var",
                "build",
                build_folder.as_str(),
            ])
            .env("CARGO_MANIFEST_DIR", "qt/bundle")
            .env("CARGO_TARGET_DIR", "out/bundle/rust")
            .env("PROFILE", "release")
            .env("OUT_DIR", &artifacts)
            .env("TARGET", env!("TARGET"))
            .env("SDKROOT", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk")
            .env("MACOSX_DEPLOYMENT_TARGET", macos_deployment_target())
            .env("CARGO_BUILD_TARGET", env!("TARGET")),
    );
}

pub fn macos_deployment_target() -> &'static str {
    if env!("TARGET") == "x86_64-apple-darwin" {
        "10.13.4"
    } else {
        "11"
    }
}
