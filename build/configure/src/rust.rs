// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::{
    cargo::{CargoBuild, CargoClippy, CargoFormat, CargoTest, RustOutput},
    git::SyncSubmodule,
    glob, inputs, Build, Result,
};

use crate::{platform::overriden_rust_target_triple, proto::download_protoc};

pub fn build_rust(build: &mut Build) -> Result<()> {
    prepare_translations(build)?;
    download_protoc(build)?;
    build_rsbridge(build)
}

fn prepare_translations(build: &mut Build) -> Result<()> {
    // ensure repos are checked out
    build.add(
        "ftl/core-repo",
        SyncSubmodule {
            path: "ftl/core-repo",
        },
    )?;
    build.add(
        "ftl/qt-repo",
        SyncSubmodule {
            path: "ftl/qt-repo",
        },
    )?;
    build.add_inputs_to_group("ftl/core-repo", inputs![glob!["ftl/{core,core-repo}/**"]]);
    // build anki_i18n and spit out strings.json
    build.add(
        "rslib/i18n",
        CargoBuild {
            inputs: inputs![glob!["rslib/i18n/**"], ":ftl/core-repo", ":ftl/qt-repo"],
            outputs: &[RustOutput::Data(
                "strings.json",
                "$builddir/rslib/i18n/strings.json",
            )],
            target: None,
            extra_args: "-p anki_i18n",
            release_override: None,
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
                ":extract:protoc:bin",
                // declare a dependency on i18n so it gets built first, allowing
                // things depending on strings.json to build faster, and ensuring
                // changes to the ftl files trigger a rebuild
                ":rslib/i18n",
                // when env vars change the build hash gets updated
                "$builddir/build.ninja"
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
        },
    )?;
    build.add(
        "format:rust",
        CargoFormat {
            inputs: inputs.clone(),
            check_only: false,
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
