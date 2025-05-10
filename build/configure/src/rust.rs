// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::build::BuildProfile;
use ninja_gen::build::FilesHandle;
use ninja_gen::cargo::CargoBuild;
use ninja_gen::cargo::CargoClippy;
use ninja_gen::cargo::CargoFormat;
use ninja_gen::cargo::CargoTest;
use ninja_gen::cargo::RustOutput;
use ninja_gen::git::SyncSubmodule;
use ninja_gen::glob;
use ninja_gen::hash::simple_hash;
use ninja_gen::input::BuildInput;
use ninja_gen::inputs;
use ninja_gen::Build;

use crate::platform::overriden_rust_target_triple;

pub fn build_rust(build: &mut Build) -> Result<()> {
    prepare_translations(build)?;
    build_proto_descriptors_and_interfaces(build)?;
    build_rsbridge(build)
}

fn prepare_translations(build: &mut Build) -> Result<()> {
    let offline_build = env::var("OFFLINE_BUILD").is_ok();

    // ensure repos are checked out
    build.add_action(
        "ftl:repo:core",
        SyncSubmodule {
            path: "ftl/core-repo",
            offline_build,
        },
    )?;
    build.add_action(
        "ftl:repo:qt",
        SyncSubmodule {
            path: "ftl/qt-repo",
            offline_build,
        },
    )?;
    // build anki_i18n and spit out strings.json
    build.add_action(
        "rslib:i18n",
        CargoBuild {
            inputs: inputs![
                glob!["rslib/i18n/**"],
                glob!["ftl/{core,core-repo,qt,qt-repo}/**"],
                ":ftl:repo",
            ],
            outputs: &[
                RustOutput::Data("py", "pylib/anki/_fluent.py"),
                RustOutput::Data("ts", "ts/lib/generated/ftl.ts"),
            ],
            target: None,
            extra_args: "-p anki_i18n",
            release_override: None,
        },
    )?;

    build.add_action(
        "ftl:bin",
        CargoBuild {
            inputs: inputs![glob!["ftl/**"],],
            outputs: &[RustOutput::Binary("ftl")],
            target: None,
            extra_args: "-p ftl",
            release_override: None,
        },
    )?;

    // These don't use :group notation, as it doesn't make sense to invoke multiple
    // commands as a group.
    build.add_action(
        "ftl-sync",
        FtlCommand {
            args: "sync",
            deps: inputs![":ftl:repo", glob!["ftl/**"]],
        },
    )?;

    build.add_action(
        "ftl-deprecate",
        FtlCommand {
            args: "deprecate --ftl-roots ftl/core ftl/qt --source-roots pylib qt rslib ts --json-roots ftl/usage",
            deps: inputs!["ftl/core", "ftl/qt", "pylib", "qt", "rslib", "ts"],
        },
    )?;

    Ok(())
}

struct FtlCommand {
    args: &'static str,
    deps: BuildInput,
}

impl BuildAction for FtlCommand {
    fn command(&self) -> &str {
        "$ftl_bin $args"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("ftl_bin", inputs![":ftl:bin"]);
        build.add_variable("args", self.args);
        build.add_output_stamp(format!("ftl/stamp.{}", simple_hash(self.args)));
    }
}

fn build_proto_descriptors_and_interfaces(build: &mut Build) -> Result<()> {
    let outputs = vec![
        RustOutput::Data("descriptors.bin", "rslib/proto/descriptors.bin"),
        RustOutput::Data("py", "pylib/anki/_backend_generated.py"),
        RustOutput::Data("ts", "ts/lib/generated/backend.ts"),
    ];
    build.add_action(
        "rslib:proto",
        CargoBuild {
            inputs: inputs![glob!["{proto,rslib/proto}/**"], ":protoc_binary",],
            outputs: &outputs,
            target: None,
            extra_args: "-p anki_proto",
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
    build.add_action(
        "pylib:rsbridge",
        CargoBuild {
            inputs: inputs![
                glob!["{pylib/rsbridge/**,rslib/**}"],
                // declare a dependency on i18n/proto so they get built first, allowing
                // things depending on them to build faster, and ensuring
                // changes to the ftl files trigger a rebuild
                ":rslib:i18n",
                ":rslib:proto",
                // when env vars change the build hash gets updated
                "$builddir/env",
                "$builddir/buildhash",
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
        glob!("{rslib/**,pylib/rsbridge/**,ftl/**,build/**,tools/workspace-hack/**}"),
        "Cargo.lock",
        "Cargo.toml",
        "rust-toolchain.toml",
    ];
    build.add_action(
        "check:format:rust",
        CargoFormat {
            inputs: inputs.clone(),
            check_only: true,
            working_dir: Some("cargo/format"),
        },
    )?;
    build.add_action(
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
        ":pylib:rsbridge"
    ];

    build.add_action(
        "check:clippy",
        CargoClippy {
            inputs: inputs.clone(),
        },
    )?;
    build.add_action("check:rust_test", CargoTest { inputs })?;

    Ok(())
}

pub fn check_minilints(build: &mut Build) -> Result<()> {
    struct RunMinilints {
        pub deps: BuildInput,
        pub fix: bool,
    }

    impl BuildAction for RunMinilints {
        fn command(&self) -> &str {
            "$minilints_bin $fix $stamp"
        }

        fn bypass_runner(&self) -> bool {
            true
        }

        fn files(&mut self, build: &mut impl FilesHandle) {
            build.add_inputs("minilints_bin", inputs![":build:minilints"]);
            build.add_inputs("", &self.deps);
            build.add_variable("fix", if self.fix { "fix" } else { "check" });
            build.add_output_stamp(format!("tests/minilints.{}", self.fix));
        }

        fn on_first_instance(&self, build: &mut Build) -> Result<()> {
            build.add_action(
                "build:minilints",
                CargoBuild {
                    inputs: inputs![glob!("tools/minilints/**/*")],
                    outputs: &[RustOutput::Binary("minilints")],
                    target: None,
                    extra_args: "-p minilints",
                    release_override: Some(BuildProfile::Debug),
                },
            )
        }
    }

    let files = inputs![
        glob![
            "**/*.{py,rs,ts,svelte,mjs,md}",
            "{node_modules,qt/bundle/PyOxidizer,ts/.svelte-kit}/**"
        ],
        "Cargo.lock"
    ];

    build.add_action(
        "check:minilints",
        RunMinilints {
            deps: files.clone(),
            fix: false,
        },
    )?;
    build.add_action(
        "fix:minilints",
        RunMinilints {
            deps: files,
            fix: true,
        },
    )?;
    Ok(())
}
