// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::action::BuildAction;
use ninja_gen::archives::Platform;
use ninja_gen::build::FilesHandle;
use ninja_gen::command::RunCommand;
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
use ninja_gen::Result;

pub fn setup_venv(build: &mut Build) -> Result<()> {
    let requirements_txt = if cfg!(windows) {
        inputs![
            "python/requirements.dev.txt",
            "python/requirements.qt6_4.txt",
            "python/requirements.win.txt",
        ]
    } else if cfg!(all(target_os = "linux", target_arch = "aarch64")) {
        inputs!["python/requirements.dev.txt"]
    } else {
        inputs![
            "python/requirements.dev.txt",
            if cfg!(target_os = "macos") {
                "python/requirements.qt6_3.txt"
            } else {
                "python/requirements.qt6_4.txt"
            }
        ]
    };
    build.add(
        "pyenv",
        PythonEnvironment {
            folder: "pyenv",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt,
            extra_binary_exports: &[
                "pip-compile",
                "pip-sync",
                "mypy",
                "black",
                "isort",
                "pylint",
                "pytest",
                "protoc-gen-mypy",
            ],
        },
    )?;

    // optional venvs for testing with Qt5
    let mut reqs_qt5 = inputs!["python/requirements.bundle.txt"];
    if cfg!(windows) {
        reqs_qt5 = inputs![reqs_qt5, "python/requirements.win.txt"];
    }

    build.add(
        "pyenv-qt5.15",
        PythonEnvironment {
            folder: "pyenv-qt5.15",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt: inputs![&reqs_qt5, "python/requirements.qt5_15.txt"],
            extra_binary_exports: &[],
        },
    )?;
    build.add(
        "pyenv-qt5.14",
        PythonEnvironment {
            folder: "pyenv-qt5.14",
            base_requirements_txt: inputs!["python/requirements.base.txt"],
            requirements_txt: inputs![reqs_qt5, "python/requirements.qt5_14.txt"],
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
        build.add_inputs("protoc", inputs!["$protoc_binary"]);
        build.add_inputs("protoc-gen-mypy", inputs![":pyenv:protoc-gen-mypy"]);
        build.add_outputs("", python_outputs);
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
                Platform::LinuxX64 => "manylinux_2_28_x86_64",
                Platform::LinuxArm => "manylinux_2_31_aarch64",
                Platform::MacX64 => "macosx_10_13_x86_64",
                Platform::MacArm => "macosx_11_0_arm64",
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

    build.add(
        "check:mypy",
        PythonTypecheck {
            folders: &[
                "pylib",
                "ts/lib",
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
                ":pylib/anki",
                ":qt/aqt"
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
    build.add(
        "pylint/anki",
        RsyncFiles {
            inputs: inputs![":pylib/anki"],
            target_folder: "pylint/anki",
            strip_prefix: "$builddir/pylib/anki",
            // avoid copying our large rsbridge binary
            extra_args: "--links",
        },
    )?;
    build.add(
        "pylint/anki",
        RsyncFiles {
            inputs: inputs![glob!["pylib/anki/**"]],
            target_folder: "pylint/anki",
            strip_prefix: "pylib/anki",
            extra_args: "",
        },
    )?;
    build.add(
        "pylint/anki",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $out",
            inputs: hashmap! { "script" => inputs!["python/mkempty.py"] },
            outputs: hashmap! { "out" => vec!["pylint/anki/__init__.py"] },
        },
    )?;
    build.add(
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
                ":pylint/anki",
                ":qt/aqt",
                glob!("{pylib/tools,ftl,qt,python,tools}/**/*.py")
            ],
        },
    )?;

    Ok(())
}

pub fn check_copyright(build: &mut Build) -> Result<()> {
    let script = inputs!["tools/copyright_headers.py"];
    let files = inputs![glob![
        "{build,rslib,pylib,qt,ftl,python,sass,tools,ts}/**/*.{py,rs,ts,svelte,mjs}",
        "qt/bundle/PyOxidizer/**"
    ]];
    build.add(
        "check:copyright",
        RunCommand {
            command: "$runner",
            args: "run --stamp=$out $pyenv_bin $script check",
            inputs: hashmap! {
                "pyenv_bin" => inputs![":pyenv:bin"],
                "script" => script.clone(),
                "script" => script.clone(),
                "" => files.clone(),
            },
            outputs: hashmap! {
                "out" => vec!["tests/copyright.check.marker"]
            },
        },
    )?;
    build.add(
        "fix:copyright",
        RunCommand {
            command: "$runner",
            args: "run --stamp=$out $pyenv_bin $script fix",
            inputs: hashmap! {
                "pyenv_bin" => inputs![":pyenv:bin"],
                "script" => script,
                "" => files,
            },
            outputs: hashmap! {
                "out" => vec!["tests/copyright.fix.marker"]
            },
        },
    )?;
    Ok(())
}
