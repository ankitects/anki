// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use camino::Utf8Path;

use super::artifacts::macos_deployment_target;
use crate::run::run_silent;

pub fn build_bundle_binary() {
    let mut features = String::from("build-mode-prebuilt-artifacts");
    if cfg!(target_os = "linux") || (cfg!(target_os = "macos") && cfg!(target_arch = "aarch64")) {
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
        .env("CARGO_BUILD_TARGET", env!("TARGET"));
    run_silent(&mut command);
}
