// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

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
use crate::Result;

pub fn python_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20221106/cpython-3.9.15+20221106-x86_64_v2-unknown-linux-gnu-install_only.tar.gz",
                sha256: "436c35bd809abdd028f386cc623ae020c77e6b544eaaca405098387c4daa444c",
            }
        }
        Platform::LinuxArm => {
            OnlineArchive {
                url: "https://github.com/ankitects/python-build-standalone/releases/download/anki-2022-02-18/cpython-3.9.10-aarch64-unknown-linux-gnu-install_only-20220218T1329.tar.gz",
                sha256: "39070f9b9492dce3085c8c98916940434bb65663e6665b2c87bef86025532c1a",
            }
        }
        Platform::MacX64 => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20211012/cpython-3.9.7-x86_64-apple-darwin-install_only-20211011T1926.tar.gz",
                sha256: "43cb1a83919f49b1ce95e42f9c461d8a9fb00ff3957bebef9cffe61a5f362377",
            }
        }
        Platform::MacArm => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20221106/cpython-3.9.15+20221106-aarch64-apple-darwin-install_only.tar.gz",
                sha256: "64dc7e1013481c9864152c3dd806c41144c79d5e9cd3140e185c6a5060bdc9ab",
            }
        }
        Platform::WindowsX64 => {
            OnlineArchive {
                url: "https://github.com/indygreg/python-build-standalone/releases/download/20211012/cpython-3.9.7-x86_64-pc-windows-msvc-shared-install_only-20211011T1926.tar.gz",
                sha256: "80370f232fd63d5cb3ff9418121acb87276228b0dafbeee3c57af143aca11f89",
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
    let python_binary = build.expand_inputs(python_binary);
    build.variable("python_binary", &python_binary[0]);
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
        "$runner pyenv $python_binary $builddir/$pyenv_folder $system_pkgs $base_requirements $requirements"
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

        build.add_inputs("python_binary", inputs!["$python_binary"]);
        build.add_inputs("base_requirements", &self.base_requirements_txt);
        build.add_inputs("requirements", &self.requirements_txt);
        build.add_variable("pyenv_folder", self.folder);
        build.add_outputs_ext("bin", bin_path("python"), true);
        build.add_outputs_ext("pip", bin_path("pip"), true);
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
    build.add(
        &format!("check:format:python:{group}"),
        PythonFormat {
            inputs: &inputs,
            check_only: true,
            isort_ini,
        },
    )?;

    build.add(
        &format!("format:python:{group}"),
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
            &self.python_path.join(if cfg!(windows) { ";" } else { ":" }),
        );
        build.add_env_var("PYTHONPATH", "$pythonpath");
        build.add_env_var("ANKI_TEST_MODE", "1");
        let hash = simple_hash(self.folder);
        build.add_output_stamp(format!("tests/python_pytest.{hash}"));
    }
}
