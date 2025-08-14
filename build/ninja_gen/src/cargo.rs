// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use camino::Utf8Path;
use camino::Utf8PathBuf;

use crate::action::BuildAction;
use crate::archives::with_exe;
use crate::build::BuildProfile;
use crate::build::FilesHandle;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;

#[derive(Debug, PartialEq, Eq)]
pub enum RustOutput<'a> {
    Binary(&'a str),
    StaticLib(&'a str),
    DynamicLib(&'a str),
    /// (group_name, fully qualified path)
    Data(&'a str, &'a str),
}

impl RustOutput<'_> {
    pub fn name(&self) -> &str {
        match self {
            RustOutput::Binary(pkg) => pkg,
            RustOutput::StaticLib(pkg) => pkg,
            RustOutput::DynamicLib(pkg) => pkg,
            RustOutput::Data(name, _) => name,
        }
    }

    pub fn path(
        &self,
        rust_base: &Utf8Path,
        target: Option<&str>,
        build_profile: BuildProfile,
    ) -> String {
        let filename = match *self {
            RustOutput::Binary(package) => {
                if cfg!(windows) {
                    format!("{package}.exe")
                } else {
                    package.into()
                }
            }
            RustOutput::StaticLib(package) => format!("lib{package}.a"),
            RustOutput::DynamicLib(package) => {
                if cfg!(windows) {
                    format!("{package}.dll")
                } else if cfg!(target_os = "macos") {
                    format!("lib{package}.dylib")
                } else {
                    format!("lib{package}.so")
                }
            }
            RustOutput::Data(_, path) => return path.to_string(),
        };
        let mut path: Utf8PathBuf = rust_base.into();
        if let Some(target) = target {
            path = path.join(target);
        }
        path = path.join(profile_output_dir(build_profile)).join(filename);
        path.to_string()
    }
}

fn profile_output_dir(profile: BuildProfile) -> &'static str {
    match profile {
        BuildProfile::Debug => "debug",
        BuildProfile::Release => "release",
        BuildProfile::ReleaseWithLto => "release-lto",
    }
}

#[derive(Debug, Default)]
pub struct CargoBuild<'a> {
    pub inputs: BuildInput,
    pub outputs: &'a [RustOutput<'a>],
    pub target: Option<&'static str>,
    pub extra_args: &'a str,
    pub release_override: Option<BuildProfile>,
}

impl BuildAction for CargoBuild<'_> {
    fn command(&self) -> &str {
        "cargo build $release_arg $target_arg $cargo_flags $extra_args"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        let release_build = self
            .release_override
            .unwrap_or_else(|| build.build_profile());
        let release_arg = profile_arg_for_cargo(release_build).unwrap_or_default();
        let target_arg = if let Some(target) = self.target {
            format!("--target {target}")
        } else {
            "".into()
        };

        build.add_inputs("", &self.inputs);
        build.add_inputs(
            "",
            inputs![".cargo/config.toml", "rust-toolchain.toml", "Cargo.lock"],
        );
        build.add_variable("release_arg", release_arg);
        build.add_variable("target_arg", target_arg);
        build.add_variable("extra_args", self.extra_args);

        let output_root = Utf8Path::new("$builddir/rust");
        for output in self.outputs {
            let name = output.name();
            let path = output.path(output_root, self.target, release_build);
            build.add_outputs_ext(name, vec![path], true);
        }
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        setup_flags(build)
    }
}

fn profile_arg_for_cargo(profile: BuildProfile) -> Option<&'static str> {
    match profile {
        BuildProfile::Debug => None,
        BuildProfile::Release => Some("--release"),
        BuildProfile::ReleaseWithLto => Some("--profile release-lto"),
    }
}

fn setup_flags(build: &mut Build) -> Result<()> {
    build.once_only("cargo_flags_and_pool", |build| {
        build.variable("cargo_flags", "--locked");
        Ok(())
    })
}

pub struct CargoTest {
    pub inputs: BuildInput,
}

impl BuildAction for CargoTest {
    fn command(&self) -> &str {
        "cargo nextest run --color=always --failure-output=final --status-level=none $cargo_flags"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("", &self.inputs);
        build.add_inputs("", inputs![":cargo-nextest"]);
        build.add_env_var("ANKI_TEST_MODE", "1");
        build.add_output_stamp("tests/cargo_test");
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        build.add_action(
            "cargo-nextest",
            CargoInstall {
                binary_name: "cargo-nextest",
                args: "cargo-nextest --version 0.9.99 --locked --no-default-features --features default-no-update",
            },
        )?;
        setup_flags(build)
    }
}

pub struct CargoClippy {
    pub inputs: BuildInput,
}

impl BuildAction for CargoClippy {
    fn command(&self) -> &str {
        "cargo clippy $cargo_flags --tests -- -Dclippy::dbg_macro -Dwarnings"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs(
            "",
            inputs![&self.inputs, "Cargo.lock", "rust-toolchain.toml"],
        );
        build.add_output_stamp("tests/cargo_clippy");
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        setup_flags(build)
    }
}

pub struct CargoFormat {
    pub inputs: BuildInput,
    pub check_only: bool,
    pub working_dir: Option<&'static str>,
}

impl BuildAction for CargoFormat {
    fn command(&self) -> &str {
        "cargo fmt $mode --all"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("", &self.inputs);
        build.add_variable("mode", if self.check_only { "--check" } else { "" });
        if let Some(working_dir) = self.working_dir {
            build.set_working_dir("$working_dir");
            build.add_variable("working_dir", working_dir);
        }
        build.add_output_stamp(format!(
            "tests/cargo_format.{}",
            if self.check_only { "check" } else { "fmt" }
        ));
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        setup_flags(build)
    }
}

/// Use Cargo to download and build a Rust binary. If `binary_name` is `foo`, a
/// `$foo` variable will be defined with the path to the binary.
pub struct CargoInstall {
    pub binary_name: &'static str,
    /// eg 'foo --version 1.3' or '--git git://...'
    pub args: &'static str,
}

impl BuildAction for CargoInstall {
    fn command(&self) -> &str {
        "cargo install --color always $args --root $builddir"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_variable("args", self.args);
        build.add_outputs("", vec![with_exe(&format!("bin/{}", self.binary_name))])
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}

pub struct CargoRun {
    pub binary_name: &'static str,
    pub cargo_args: &'static str,
    pub bin_args: &'static str,
    pub deps: BuildInput,
}

impl BuildAction for CargoRun {
    fn command(&self) -> &str {
        "cargo run --bin $binary $cargo_args -- $bin_args"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_variable("binary", self.binary_name);
        build.add_variable("cargo_args", self.cargo_args);
        build.add_variable("bin_args", self.bin_args);
        build.add_outputs("", vec![format!("phony-{}", self.binary_name)]);
    }
}
