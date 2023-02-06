// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::cargo::CargoBuild;
use ninja_gen::cargo::CargoClippy;
use ninja_gen::cargo::CargoFormat;
use ninja_gen::cargo::CargoRun;
use ninja_gen::cargo::CargoTest;
use ninja_gen::cargo::RustOutput;
use ninja_gen::git::SyncSubmodule;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::Build;
use ninja_gen::Result;

use crate::platform::overriden_rust_target_triple;
use crate::proto::setup_protoc;

pub fn build_rust(build: &mut Build) -> Result<()> {
    prepare_translations(build)?;
    setup_protoc(build)?;
    build_rsbridge(build)
}

fn prepare_translations(build: &mut Build) -> Result<()> {
    // ensure repos are checked out
    build.add(
        "ftl:repo:core",
        SyncSubmodule {
            path: "ftl/core-repo",
        },
    )?;
    build.add(
        "ftl:repo:qt",
        SyncSubmodule {
            path: "ftl/qt-repo",
        },
    )?;
    // build anki_i18n and spit out strings.json
    build.add(
        "rslib/i18n",
        CargoBuild {
            inputs: inputs![
                glob!["rslib/i18n/**"],
                glob!["ftl/{core,core-repo,qt,qt-repo}/**"],
                ":ftl:repo",
            ],
            outputs: &[RustOutput::Data(
                "strings.json",
                "$builddir/rslib/i18n/strings.json",
            )],
            target: None,
            extra_args: "-p anki_i18n",
            release_override: None,
        },
    )?;

    build.add(
        "ftl:sync",
        CargoRun {
            binary_name: "ftl-sync",
            cargo_args: "-p ftl",
            bin_args: "",
            deps: inputs![":ftl:repo", glob!["ftl/{core,core-repo,qt,qt-repo}/**"]],
        },
    )?;

    build.add(
        "ftl:deprecate",
        CargoRun {
            binary_name: "deprecate_ftl_entries",
            cargo_args: "-p anki_i18n_helpers",
            bin_args: "ftl/core ftl/qt -- pylib qt rslib ts --keep ftl/usage",
            deps: inputs!["ftl/core", "ftl/qt", "pylib", "qt", "rslib", "ts"],
        },
    )?;

    Ok(())
}

fn build_rsbridge(build: &mut Build) -> Result<()> {
    let features = if cfg!(target_os = "linux") {
        "rustls"
    } else {
        "native-tls"
    };
    build.add(
        "pylib/rsbridge",
        CargoBuild {
            inputs: inputs![
                glob!["{pylib/rsbridge/**,rslib/**,proto/**}"],
                "$protoc_binary",
                // declare a dependency on i18n so it gets built first, allowing
                // things depending on strings.json to build faster, and ensuring
                // changes to the ftl files trigger a rebuild
                ":rslib/i18n",
                // when env vars change the build hash gets updated
                "$builddir/build.ninja",
                // building on Windows requires python3.lib
                if cfg!(windows) {
                    inputs![":extract:python"]
                } else {
                    inputs![]
                }
            ],
            outputs: &[RustOutput::DynamicLib("rsbridge")],
            target: overriden_rust_target_triple(),
            extra_args: &format!("-p rsbridge --features {features}"),
            release_override: None,
        },
    )
}

pub fn check_rust(build: &mut Build) -> Result<()> {
    let inputs = inputs![
        glob!("{rslib/**,pylib/rsbridge/**,build/**,tools/workspace-hack/**}"),
        "Cargo.lock",
        "Cargo.toml",
        "rust-toolchain.toml",
    ];
    build.add(
        "check:format:rust",
        CargoFormat {
            inputs: inputs.clone(),
            check_only: true,
            working_dir: Some("cargo/format"),
        },
    )?;
    build.add(
        "format:rust",
        CargoFormat {
            inputs: inputs.clone(),
            check_only: false,
            working_dir: Some("cargo/format"),
        },
    )?;

    let inputs = inputs![
        inputs,
        // defer tests until build has completed; ensure re-run on changes
        ":pylib/rsbridge"
    ];

    build.add(
        "check:clippy",
        CargoClippy {
            inputs: inputs.clone(),
        },
    )?;
    build.add("check:rust_test", CargoTest { inputs })?;

    Ok(())
}
