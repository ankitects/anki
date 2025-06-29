// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anyhow::Result;
use camino::Utf8Path;
use maplit::hashmap;

use crate::action::BuildAction;
use crate::archives::download_and_extract;
use crate::archives::with_exe;
use crate::archives::OnlineArchive;
use crate::archives::Platform;
use crate::hash::simple_hash;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;

// To update, run 'cargo run --bin update_uv'.
// You'll need to do this when bumping Python versions, as uv bakes in
// the latest known version.
// When updating Python version, make sure to update version tag in BuildWheel
// too.
pub fn uv_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.7.13/uv-x86_64-unknown-linux-gnu.tar.gz",
                sha256: "909278eb197c5ed0e9b5f16317d1255270d1f9ea4196e7179ce934d48c4c2545",
            }
        },
        Platform::LinuxArm => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.7.13/uv-aarch64-unknown-linux-gnu.tar.gz",
                sha256: "0b2ad9fe4295881615295add8cc5daa02549d29cc9a61f0578e397efcf12f08f",
            }
        },
        Platform::MacX64 => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.7.13/uv-x86_64-apple-darwin.tar.gz",
                sha256: "d785753ac092e25316180626aa691c5dfe1fb075290457ba4fdb72c7c5661321",
            }
        },
        Platform::MacArm => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.7.13/uv-aarch64-apple-darwin.tar.gz",
                sha256: "721f532b73171586574298d4311a91d5ea2c802ef4db3ebafc434239330090c6",
            }
        },
        Platform::WindowsX64 => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.7.13/uv-x86_64-pc-windows-msvc.zip",
                sha256: "e199b10bef1a7cc540014483e7f60f825a174988f41020e9d2a6b01bd60f0669",
            }
        },
        Platform::WindowsArm => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.7.13/uv-aarch64-pc-windows-msvc.zip",
                sha256: "bb40708ad549ad6a12209cb139dd751bf0ede41deb679ce7513ce197bd9ef234",
            }
        }
    }
}

pub fn setup_uv(build: &mut Build, platform: Platform) -> Result<()> {
    let uv_binary = match env::var("UV_BINARY") {
        Ok(path) => {
            assert!(
                Utf8Path::new(&path).is_absolute(),
                "UV_BINARY must be absolute"
            );
            path.into()
        }
        Err(_) => {
            download_and_extract(
                build,
                "uv",
                uv_archive(platform),
                hashmap! { "bin" => [
                with_exe("uv")
                                ] },
            )?;
            inputs![":extract:uv:bin"]
        }
    };
    build.add_dependency("uv_binary", uv_binary);

    // Our macOS packaging needs access to the x86 binary on ARM.
    if cfg!(target_arch = "aarch64") {
        download_and_extract(
            build,
            "uv_mac_x86",
            uv_archive(Platform::MacX64),
            hashmap! { "bin" => [
                with_exe("uv")
            ] },
        )?;
    }
    // Our Linux packaging needs access to the ARM binary on x86
    if cfg!(target_arch = "x86_64") {
        download_and_extract(
            build,
            "uv_lin_arm",
            uv_archive(Platform::LinuxArm),
            hashmap! { "bin" => [
                with_exe("uv")
            ] },
        )?;
    }

    Ok(())
}

pub struct PythonEnvironment {
    pub deps: BuildInput,
    // todo: rename
    pub venv_folder: &'static str,
    pub extra_args: &'static str,
    pub extra_binary_exports: &'static [&'static str],
}

impl BuildAction for PythonEnvironment {
    fn command(&self) -> &str {
        if env::var("OFFLINE_BUILD").is_err() {
            "$runner pyenv $uv_binary $builddir/$pyenv_folder -- $extra_args"
        } else {
            "echo 'OFFLINE_BUILD is set. Using the existing PythonEnvironment.'"
        }
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        let bin_path = |binary: &str| -> Vec<String> {
            let folder = self.venv_folder;
            let path = if cfg!(windows) {
                format!("{folder}/scripts/{binary}.exe")
            } else {
                format!("{folder}/bin/{binary}")
            };
            vec![path]
        };

        build.add_inputs("", &self.deps);
        build.add_variable("pyenv_folder", self.venv_folder);
        if env::var("OFFLINE_BUILD").is_err() {
            build.add_inputs("uv_binary", inputs![":uv_binary"]);

            // Add --python flag to extra_args if PYTHON_BINARY is set
            let mut args = self.extra_args.to_string();
            if let Ok(python_binary) = env::var("PYTHON_BINARY") {
                args = format!("--python {python_binary} {args}");
            }
            build.add_variable("extra_args", args);
        }

        build.add_outputs_ext("bin", bin_path("python"), true);
        for binary in self.extra_binary_exports {
            build.add_outputs_ext(*binary, bin_path(binary), true);
        }
        build.add_output_stamp(format!("{}/.stamp", self.venv_folder));
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}

pub struct PythonTypecheck {
    pub folders: &'static [&'static str],
    pub deps: BuildInput,
}

impl BuildAction for PythonTypecheck {
    fn command(&self) -> &str {
        "$mypy $folders"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("mypy", inputs![":pyenv:mypy"]);
        build.add_inputs("", inputs![".mypy.ini"]);
        build.add_variable("folders", self.folders.join(" "));

        let hash = simple_hash(self.folders);
        build.add_output_stamp(format!("tests/python_typecheck.{hash}"));
    }

    fn hide_progress(&self) -> bool {
        true
    }
}

struct PythonFormat<'a> {
    pub inputs: &'a BuildInput,
    pub check_only: bool,
}

impl BuildAction for PythonFormat<'_> {
    fn command(&self) -> &str {
        "$ruff format $mode $in && $ruff check --select I --fix $in"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("in", self.inputs);
        build.add_inputs("ruff", inputs![":pyenv:ruff"]);

        let hash = simple_hash(self.inputs);
        build.add_variable("mode", if self.check_only { "--check" } else { "" });

        build.add_output_stamp(format!(
            "tests/python_format.{}.{hash}",
            if self.check_only { "check" } else { "fix" }
        ));
    }
}

pub fn python_format(build: &mut Build, group: &str, inputs: BuildInput) -> Result<()> {
    build.add_action(
        format!("check:format:python:{group}"),
        PythonFormat {
            inputs: &inputs,
            check_only: true,
        },
    )?;

    build.add_action(
        format!("format:python:{group}"),
        PythonFormat {
            inputs: &inputs,
            check_only: false,
        },
    )?;
    Ok(())
}

pub struct RuffCheck {
    pub folders: &'static [&'static str],
    pub deps: BuildInput,
    pub check_only: bool,
}

impl BuildAction for RuffCheck {
    fn command(&self) -> &str {
        "$ruff check $folders $mode"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("ruff", inputs![":pyenv:ruff"]);
        build.add_variable("folders", self.folders.join(" "));
        build.add_variable(
            "mode",
            if self.check_only {
                ""
            } else {
                "--fix --unsafe-fixes"
            },
        );

        let hash = simple_hash(&self.deps);
        let kind = if self.check_only { "check" } else { "fix" };
        build.add_output_stamp(format!("tests/python_ruff.{kind}.{hash}"));
    }
}

pub struct PythonTest {
    pub folder: &'static str,
    pub python_path: &'static [&'static str],
    pub deps: BuildInput,
}

impl BuildAction for PythonTest {
    fn command(&self) -> &str {
        "$pytest -p no:cacheprovider $folder"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("pytest", inputs![":pyenv:pytest"]);
        build.add_variable("folder", self.folder);
        build.add_variable(
            "pythonpath",
            self.python_path.join(if cfg!(windows) { ";" } else { ":" }),
        );
        build.add_env_var("PYTHONPATH", "$pythonpath");
        build.add_env_var("ANKI_TEST_MODE", "1");
        let hash = simple_hash(self.folder);
        build.add_output_stamp(format!("tests/python_pytest.{hash}"));
    }

    fn hide_progress(&self) -> bool {
        true
    }
}
