// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use anki_process::CommandExt;
use camino::Utf8Path;
use camino::Utf8PathBuf;

use super::artifacts::macos_deployment_target;
use crate::run::run_command;

pub fn build_bundle_binary() {
    let mut features = String::from("build-mode-prebuilt-artifacts");
    if cfg!(target_os = "linux") || cfg!(target_os = "macos") {
        features.push_str(",global-allocator-jemalloc,allocator-jemalloc");
    }

    let mut command = Command::new("cargo");
    command
        .args([
            "build",
            "--manifest-path=qt/bundle/Cargo.toml",
            "--target-dir=out/bundle/rust",
            "--release",
            "--no-default-features",
        ])
        .arg(format!("--features={features}"))
        .env(
            "DEFAULT_PYTHON_CONFIG_RS",
            // included in main.rs, so relative to qt/bundle/src
            "../../../out/bundle/artifacts/",
        )
        .env(
            "PYO3_CONFIG_FILE",
            Utf8Path::new("out/bundle/artifacts/pyo3-build-config-file.txt")
                .canonicalize_utf8()
                .unwrap(),
        )
        .env("MACOSX_DEPLOYMENT_TARGET", macos_deployment_target())
        .env("SDKROOT", "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk")
        .env("CARGO_BUILD_TARGET", env!("TARGET"));
    if env!("TARGET") == "x86_64-apple-darwin" {
        let xcode_path = Command::run_with_output(["xcode-select", "-p"]).unwrap();
        let ld_classic = Utf8PathBuf::from(xcode_path.stdout.trim())
            .join("Toolchains/XcodeDefault.xctoolchain/usr/bin/ld-classic");
        if ld_classic.exists() {
            // work around XCode 15's default linker not supporting macOS 10.15-12.
            command.env("RUSTFLAGS", format!("-Clink-arg=-fuse-ld={ld_classic}"));
        }
    }
    run_command(&mut command);
}
