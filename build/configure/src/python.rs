// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use ninja_gen::{
    action::BuildAction,
    archives::{download_and_extract, OnlineArchive, Platform},
    build::FilesHandle,
    command::RunCommand,
    glob, hashmap,
    input::BuildInput,
    inputs,
    python::{python_format, PythonLint, PythonTypecheck},
    rsync::RsyncFiles,
    Build, Result, Utf8Path,
};

fn python_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => {
            // pending https://github.com/indygreg/python-build-standalone/issues/95
            OnlineArchive {
                url: "https://github.com/ankitects/python-build-standalone/releases/download/anki-2022-02-18/cpython-3.9.10-x86_64-unknown-linux-gnu-install_only-20220218T1329.tar.gz",
                sha256: "1f5d63c9099da6e51d121ca0eee8d32fb4f084bfc51643fad39be0c14dfbf530",
            }
        }
        Platform::LinuxArm => {
            // pending https://github.com/indygreg/python-build-standalone/issues/95
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
pub fn setup_python(build: &mut Build) -> Result<BuildInput> {
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
    Ok(python_binary)
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

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
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
        build.add_inputs("protoc", inputs![":extract:protoc:bin"]);
        build.add_inputs("protoc-gen-mypy", inputs![":pyenv:protoc-gen-mypy"]);
        build.add_outputs("", python_outputs);
    }
}

pub struct BuildWheel {
    pub name: &'static str,
    pub version: &'static str,
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
        let version = self.version;
        let wheel_path = format!("wheels/{name}-{version}-{tag}.whl");
        build.add_outputs("out", vec![wheel_path]);
    }
}

pub fn check_python(build: &mut Build) -> Result<()> {
    let format_inputs = inputs![glob![
        "{pylib,qt,ftl,tools}/**/*.py",
        "qt/bundle/PyOxidizer/**"
    ]];
    let isort_ini = inputs![".isort.cfg"];
    python_format(build, format_inputs, isort_ini)?;
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
            deps: inputs![glob!["{pylib,ftl,qt}/**/*.{py,pyi}"], ":pylib/anki"],
        },
    )?;

    add_pylint(build)?;

    Ok(())
}

fn add_pylint(build: &mut Build) -> Result<()> {
    // pylint does not support PEP420 implicit namespaces split across import paths,
    // so we need to merge our pylib sources and generated files before invoking it, and
    // add a top-level __init__.py
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
