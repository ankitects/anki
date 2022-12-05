// Based off PyOxidizer's 'init-rust-project'.
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

use std::path::{Path, PathBuf};

use embed_resource;

const DEFAULT_PYTHON_CONFIG_FILENAME: &str = "default_python_config.rs";
const DEFAULT_PYTHON_CONFIG: &str = "\
pub fn default_python_config<'a>() -> pyembed::OxidizedPythonInterpreterConfig<'a> {
    pyembed::OxidizedPythonInterpreterConfig::default()
}
";

/// Build by calling a `pyoxidizer` executable to generate build artifacts.
fn build_with_pyoxidizer_exe(exe: Option<String>, resolve_target: Option<&str>) {
    let pyoxidizer_exe = if let Some(path) = exe {
        path
    } else {
        "pyoxidizer".to_string()
    };

    let mut args = vec!["run-build-script", "build.rs"];
    if let Some(target) = resolve_target {
        args.push("--target");
        args.push(target);
    }

    match std::process::Command::new(pyoxidizer_exe)
        .args(args)
        .status()
    {
        Ok(status) => {
            if !status.success() {
                panic!("`pyoxidizer run-build-script` failed");
            }
        }
        Err(e) => panic!("`pyoxidizer run-build-script` failed: {}", e.to_string()),
    }
}

#[allow(clippy::if_same_then_else)]
fn main() {
    if std::env::var("CARGO_FEATURE_BUILD_MODE_STANDALONE").is_ok() {
        let path = PathBuf::from(std::env::var("OUT_DIR").expect("OUT_DIR not defined"));
        let path = path.join(DEFAULT_PYTHON_CONFIG_FILENAME);

        std::fs::write(&path, DEFAULT_PYTHON_CONFIG.as_bytes())
            .expect("failed to write default python config");
        println!(
            "cargo:rustc-env=DEFAULT_PYTHON_CONFIG_RS={}",
            path.display()
        );
    } else if std::env::var("CARGO_FEATURE_BUILD_MODE_PYOXIDIZER_EXE").is_ok() {
        let target = if let Ok(target) = std::env::var("PYOXIDIZER_BUILD_TARGET") {
            Some(target)
        } else {
            None
        };

        build_with_pyoxidizer_exe(
            std::env::var("PYOXIDIZER_EXE").ok(),
            target.as_ref().map(|target| target.as_ref()),
        );
    } else if std::env::var("CARGO_FEATURE_BUILD_MODE_PREBUILT_ARTIFACTS").is_ok() {
        // relative to src/
        let artifacts = Path::new("../../../out/bundle/artifacts/");
        let config_rs = artifacts.join("default_python_config.rs");
        println!(
            "cargo:rustc-env=DEFAULT_PYTHON_CONFIG_RS={}",
            config_rs.display()
        );
        let config_txt = artifacts.join("pyo3-build-config-file.txt");
        println!("cargo:rustc-env=PYO3_CONFIG_FILE={}", config_txt.display());

        let link_arg = if cfg!(target_os = "macos") {
            "-rdynamic"
        } else {
            "-Wl,-export-dynamic"
        };
        println!("cargo:rustc-link-arg={link_arg}");
    } else {
        panic!("build-mode-* feature not set");
    }

    let target_family =
        std::env::var("CARGO_CFG_TARGET_FAMILY").expect("CARGO_CFG_TARGET_FAMILY not defined");

    // embed manifest and icon
    if target_family == "windows" {
        embed_resource::compile("win/anki-manifest.rc");
    }
}
