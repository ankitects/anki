// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::archives::Platform;
use ninja_gen::build::FilesHandle;
use ninja_gen::command::RunCommand;
use ninja_gen::copy::CopyFiles;
use ninja_gen::glob;
use ninja_gen::hashmap;
use ninja_gen::input::BuildInput;
use ninja_gen::inputs;
use ninja_gen::python::python_format;
use ninja_gen::python::PythonEnvironment;
use ninja_gen::python::PythonLint;
use ninja_gen::python::PythonTypecheck;
use ninja_gen::rsync::RsyncFiles;
use ninja_gen::Build;

pub fn setup_venv(build: &mut Build) -> Result<()> {
    let platform_deps = if cfg!(windows) {
        inputs![
            "python/requirements.qt6_6.txt",
            "python/requirements.win.txt",
        ]
    } else if cfg!(target_os = "macos") {
        inputs!["python/requirements.qt6_6.txt",]
    } else if std::env::var("PYTHONPATH").is_ok() {
        // assume we have a system-provided Qt
        inputs![]
    } else if cfg!(target_arch = "aarch64") {
        inputs!["python/requirements.qt6_8.txt"]
    } else {
        inputs!["python/requirements.qt6_6.txt"]
    };
    let requirements_txt = inputs!["python/requirements.dev.txt", platform_deps];
    build.add_action(
        "pyenv",
        PythonEnvironment {
            folder: "pyenv",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt,
            extra_binary_exports: &[
                "pip-compile",
                "pip-sync",
                "mypy",
                "black", // Required for offline build
                "isort",
                "pylint",
                "pytest",
                "protoc-gen-mypy", // ditto
            ],
        },
    )?;

    // optional venvs for testing other Qt versions
    let mut venv_reqs = inputs!["python/requirements.bundle.txt"];
    if cfg!(windows) {
        venv_reqs = inputs![venv_reqs, "python/requirements.win.txt"];
    }

    build.add_action(
        "pyenv-qt6.8",
        PythonEnvironment {
            folder: "pyenv-qt6.8",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt: inputs![&venv_reqs, "python/requirements.qt6_8.txt"],
            extra_binary_exports: &[],
        },
    )?;
    build.add_action(
        "pyenv-qt5.15",
        PythonEnvironment {
            folder: "pyenv-qt5.15",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt: inputs![&venv_reqs, "python/requirements.qt5_15.txt"],
            extra_binary_exports: &[],
        },
    )?;
    build.add_action(
        "pyenv-qt5.14",
        PythonEnvironment {
            folder: "pyenv-qt5.14",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt: inputs![venv_reqs, "python/requirements.qt5_14.txt"],
            extra_binary_exports: &[],
        },
    )?;

    Ok(())
}

pub struct GenPythonProto {
    pub proto_files: BuildInput,
}

impl BuildAction for GenPythonProto {
    fn command(&self) -> &str {
        "$protoc $
        --plugin=protoc-gen-mypy=$protoc-gen-mypy $
        --python_out=$builddir/pylib $
        --mypy_out=$builddir/pylib $
        -Iproto $in"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        let proto_inputs = build.expand_inputs(&self.proto_files);
        let python_outputs: Vec<_> = proto_inputs
            .iter()
            .flat_map(|path| {
                let path = path
                    .replace('\\', "/")
                    .replace("proto/", "pylib/")
                    .replace(".proto", "_pb2");
                [format!("{path}.py"), format!("{path}.pyi")]
            })
            .collect();
        build.add_inputs("in", &self.proto_files);
        build.add_inputs("protoc", inputs![":protoc_binary"]);
        build.add_inputs("protoc-gen-mypy", inputs![":pyenv:protoc-gen-mypy"]);
        build.add_outputs("", python_outputs);
    }

    fn hide_last_line(&self) -> bool {
        true
    }
}

pub struct BuildWheel {
    pub name: &'static str,
    pub version: String,
    pub src_folder: &'static str,
    pub gen_folder: &'static str,
    pub platform: Option<Platform>,
    pub deps: BuildInput,
}

impl BuildAction for BuildWheel {
    fn command(&self) -> &str {
        "$pyenv_bin $script $src $gen $out"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["python/write_wheel.py"]);
        build.add_inputs("", &self.deps);
        build.add_variable("src", self.src_folder);
        build.add_variable("gen", self.gen_folder);

        let tag = if let Some(platform) = self.platform {
            let platform = match platform {
                Platform::LinuxX64 => "manylinux_2_35_x86_64",
                Platform::LinuxArm => "manylinux_2_35_aarch64",
                Platform::MacX64 => "macosx_12_0_x86_64",
                Platform::MacArm => "macosx_12_0_arm64",
                Platform::WindowsX64 => "win_amd64",
            };
            format!("cp39-abi3-{platform}")
        } else {
            "py3-none-any".into()
        };
        let name = self.name;
        let version = &self.version;
        let wheel_path = format!("wheels/{name}-{version}-{tag}.whl");
        build.add_outputs("out", vec![wheel_path]);
    }
}

pub fn check_python(build: &mut Build) -> Result<()> {
    python_format(build, "ftl", inputs![glob!("ftl/**/*.py")])?;
    python_format(build, "tools", inputs![glob!("tools/**/*.py")])?;

    build.add_action(
        "check:mypy",
        PythonTypecheck {
            folders: &[
                "pylib",
                "qt/aqt",
                "qt/tools",
                "out/pylib/anki",
                "out/qt/_aqt",
                "ftl",
                "python",
                "tools",
            ],
            deps: inputs![
                glob!["{pylib,ftl,qt}/**/*.{py,pyi}"],
                ":pylib:anki",
                ":qt:aqt"
            ],
        },
    )?;

    add_pylint(build)?;

    Ok(())
}

fn add_pylint(build: &mut Build) -> Result<()> {
    // pylint does not support PEP420 implicit namespaces split across import paths,
    // so we need to merge our pylib sources and generated files before invoking it,
    // and add a top-level __init__.py
    build.add_action(
        "check:pylint:copy_pylib",
        RsyncFiles {
            inputs: inputs![":pylib:anki"],
            target_folder: "pylint/anki",
            strip_prefix: "$builddir/pylib/anki",
            // avoid copying our large rsbridge binary
            extra_args: "--links",
        },
    )?;
    build.add_action(
        "check:pylint:copy_pylib",
        RsyncFiles {
            inputs: inputs![glob!["pylib/anki/**"]],
            target_folder: "pylint/anki",
            strip_prefix: "pylib/anki",
            extra_args: "",
        },
    )?;
    build.add_action(
        "check:pylint:copy_pylib",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $out",
            inputs: hashmap! { "script" => inputs!["python/mkempty.py"] },
            outputs: hashmap! { "out" => vec!["pylint/anki/__init__.py"] },
        },
    )?;
    build.add_action(
        "check:pylint",
        PythonLint {
            folders: &[
                "$builddir/pylint/anki",
                "qt/aqt",
                "ftl",
                "pylib/tools",
                "tools",
                "python",
            ],
            pylint_ini: inputs![".pylintrc"],
            deps: inputs![
                ":check:pylint:copy_pylib",
                ":qt:aqt",
                glob!("{pylib/tools,ftl,qt,python,tools}/**/*.py")
            ],
        },
    )?;

    Ok(())
}

struct Sphinx {
    deps: BuildInput,
}

impl BuildAction for Sphinx {
    fn command(&self) -> &str {
        if env::var("OFFLINE_BUILD").is_err() {
            "$pip install sphinx sphinx_rtd_theme sphinx-autoapi \
             && $python python/sphinx/build.py"
        } else {
            "$python python/sphinx/build.py"
        }
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        if env::var("OFFLINE_BUILD").is_err() {
            build.add_inputs("pip", inputs![":pyenv:pip"]);
        }
        build.add_inputs("python", inputs![":pyenv:bin"]);
        build.add_inputs("", &self.deps);
        build.add_output_stamp("python/sphinx/stamp");
    }

    fn hide_success(&self) -> bool {
        false
    }
}

pub(crate) fn setup_sphinx(build: &mut Build) -> Result<()> {
    build.add_action(
        "python:sphinx:copy_conf",
        CopyFiles {
            inputs: inputs![glob!("python/sphinx/{conf.py,index.rst}")],
            output_folder: "python/sphinx",
        },
    )?;
    build.add_action(
        "python:sphinx",
        Sphinx {
            deps: inputs![":pylib", ":qt", ":python:sphinx:copy_conf"],
        },
    )?;
    Ok(())
}
