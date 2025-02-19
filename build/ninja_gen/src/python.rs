// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anyhow::Result;
use camino::Utf8Path;
use maplit::hashmap;

use crate::action::BuildAction;
use crate::archives::download_and_extract;
use crate::archives::OnlineArchive;
use crate::archives::Platform;
use crate::hash::simple_hash;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;

/// When updating this, pyoxidizer.bzl needs updating too, but it uses different
/// files.
pub fn python_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.9.18+20240107-x86_64_v2-unknown-linux-gnu-install_only.tar.gz",
                sha256: "9426bca501ae0a257392b10719e2e20ff5fa5e22a3ce4599d6ad0b3139f86417",
            }
        }
        Platform::LinuxArm => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.9.18+20240107-aarch64-unknown-linux-gnu-install_only.tar.gz",
                sha256: "7d19e1ecd6e582423f7c74a0c67491eaa982ce9d5c5f35f0e4289f83127abcb8",
            }
        }
        Platform::MacX64 => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.9.18+20240107-x86_64-apple-darwin-install_only.tar.gz",
                sha256: "5a0bf895a5cb08d6d008140abb41bb2c8cd638a665273f7d8eb258bc89de439b",
            }
        }
        Platform::MacArm => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.9.18+20240107-aarch64-apple-darwin-install_only.tar.gz",
                sha256: "bf0cd90204a2cc6da48cae1e4b32f48c9f7031fbe1238c5972104ccb0155d368",
            }
        }
        Platform::WindowsX64 => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20240107/cpython-3.9.18+20240107-x86_64-pc-windows-msvc-shared-install_only.tar.gz",
                sha256: "8f0544cd593984f7ecb90c685931249c579302124b9821064873f3a14ed07005",
            }
        }
    }
}

/// Returns the Python binary, which can be used to create venvs.
/// Downloads if missing.
pub fn setup_python(build: &mut Build) -> Result<()> {
    // if changing this, make sure you remove out/pyenv
    let python_binary = match env::var("PYTHON_BINARY") {
        Ok(path) => {
            assert!(
                Utf8Path::new(&path).is_absolute(),
                "PYTHON_BINARY must be absolute"
            );
            path.into()
        }
        Err(_) => {
            download_and_extract(
                build,
                "python",
                python_archive(build.host_platform),
                hashmap! { "bin" => [
                    if cfg!(windows) { "python.exe" } else { "bin/python3"}
                ] },
            )?;
            inputs![":extract:python:bin"]
        }
    };
    build.add_dependency("python_binary", python_binary);
    Ok(())
}

pub struct PythonEnvironment {
    pub folder: &'static str,
    pub base_requirements_txt: BuildInput,
    pub requirements_txt: BuildInput,
    pub extra_binary_exports: &'static [&'static str],
}

impl BuildAction for PythonEnvironment {
    fn command(&self) -> &str {
        if env::var("OFFLINE_BUILD").is_err() {
            "$runner pyenv $python_binary $builddir/$pyenv_folder $system_pkgs $base_requirements $requirements"
        } else {
            "echo 'OFFLINE_BUILD is set. Using the existing PythonEnvironment.'"
        }
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        let bin_path = |binary: &str| -> Vec<String> {
            let folder = self.folder;
            let path = if cfg!(windows) {
                format!("{folder}/scripts/{binary}.exe")
            } else {
                format!("{folder}/bin/{binary}")
            };
            vec![path]
        };

        if env::var("OFFLINE_BUILD").is_err() {
            build.add_inputs("python_binary", inputs![":python_binary"]);
            build.add_variable("pyenv_folder", self.folder);
            build.add_inputs("base_requirements", &self.base_requirements_txt);
            build.add_inputs("requirements", &self.requirements_txt);
            build.add_outputs_ext("pip", bin_path("pip"), true);
        }
        build.add_outputs_ext("bin", bin_path("python"), true);
        for binary in self.extra_binary_exports {
            build.add_outputs_ext(*binary, bin_path(binary), true);
        }
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
    pub isort_ini: &'a BuildInput,
}

impl BuildAction for PythonFormat<'_> {
    fn command(&self) -> &str {
        "$black -t py39 -q $check --color $in && $
         $isort --color --settings-path $isort_ini $check $in"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("in", self.inputs);
        build.add_inputs("black", inputs![":pyenv:black"]);
        build.add_inputs("isort", inputs![":pyenv:isort"]);

        let hash = simple_hash(self.inputs);
        build.add_env_var("BLACK_CACHE_DIR", "out/python/black.cache.{hash}");
        build.add_inputs("isort_ini", self.isort_ini);
        build.add_variable(
            "check",
            if self.check_only {
                "--diff --check"
            } else {
                ""
            },
        );

        build.add_output_stamp(format!(
            "tests/python_format.{}.{hash}",
            if self.check_only { "check" } else { "fix" }
        ));
    }
}

pub fn python_format(build: &mut Build, group: &str, inputs: BuildInput) -> Result<()> {
    let isort_ini = &inputs![".isort.cfg"];
    build.add_action(
        format!("check:format:python:{group}"),
        PythonFormat {
            inputs: &inputs,
            check_only: true,
            isort_ini,
        },
    )?;

    build.add_action(
        format!("format:python:{group}"),
        PythonFormat {
            inputs: &inputs,
            check_only: false,
            isort_ini,
        },
    )?;
    Ok(())
}

pub struct PythonLint {
    pub folders: &'static [&'static str],
    pub pylint_ini: BuildInput,
    pub deps: BuildInput,
}

impl BuildAction for PythonLint {
    fn command(&self) -> &str {
        "$pylint --rcfile $pylint_ini -sn -j $cpus $folders"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_inputs("pylint", inputs![":pyenv:pylint"]);
        build.add_inputs("pylint_ini", &self.pylint_ini);
        build.add_variable("folders", self.folders.join(" "));
        // On a 16 core system, values above 10 do not improve wall clock time,
        // but waste extra cores that could be working on other tests.
        build.add_variable("cpus", num_cpus::get().min(10).to_string());

        let hash = simple_hash(&self.deps);
        build.add_output_stamp(format!("tests/python_lint.{hash}"));
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
