// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anki_io::read_file;
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
                url: "https://github.com/astral-sh/uv/releases/download/0.10.11/uv-x86_64-unknown-linux-gnu.tar.gz",
                sha256: "5a360b0de092ddf4131f5313d0411b48c4e95e8107e40c3f8f2e9fcb636b3583",
            }
        },
        Platform::LinuxArm => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.10.11/uv-aarch64-unknown-linux-gnu.tar.gz",
                sha256: "23003df007937dd607409c8ddf010baa82bad2673e60e254632ca5b04edcce13",
            }
        },
        Platform::MacX64 => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.10.11/uv-x86_64-apple-darwin.tar.gz",
                sha256: "ff90020b554cf02ef8008535c9aab6ef27bb7be6b075359300dec79c361df897",
            }
        },
        Platform::MacArm => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.10.11/uv-aarch64-apple-darwin.tar.gz",
                sha256: "437a7d498dd6564d5bf986074249ba1fc600e73da55ae04d7bd4c24d5f149b95",
            }
        },
        Platform::WindowsX64 => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.10.11/uv-x86_64-pc-windows-msvc.zip",
                sha256: "9ee74df98582f37fdd6069e1caac80d2616f9a489f5dbb2b1c152f30be69c58e",
            }
        },
        Platform::WindowsArm => {
            OnlineArchive {
                url: "https://github.com/astral-sh/uv/releases/download/0.10.11/uv-aarch64-pc-windows-msvc.zip",
                sha256: "6a3eec4105c775dd87c11ef8ec41564648273751ff807c8955c24ddbcc636d03",
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
            "$runner pyenv $uv_binary $builddir/$pyenv_folder $python -- $extra_args"
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

            // Set --python flag to .python-version (--no-config ignores it)
            // override if PYTHON_BINARY is set
            let python = env::var("PYTHON_BINARY").unwrap_or_else(|_| {
                let python_version =
                    read_file(".python-version").expect("No .python-version in cwd");
                let python_version_str =
                    String::from_utf8(python_version).expect("Invalid UTF-8 in .python-version");
                python_version_str.trim().to_string()
            });
            build.add_variable("python", python);
            build.add_variable("extra_args", self.extra_args);
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
        build.add_inputs("", inputs![".ruff.toml"]);
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
