// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::action::BuildAction;
use ninja_gen::archives::Platform;
use ninja_gen::command::RunCommand;
use ninja_gen::copy::LinkFile;
use ninja_gen::glob;
use ninja_gen::hashmap;
use ninja_gen::inputs;
use ninja_gen::python::python_format;
use ninja_gen::python::PythonTest;
use ninja_gen::Build;
use ninja_gen::Result;

use crate::anki_version;
use crate::platform::overriden_python_target_platform;
use crate::python::BuildWheel;
use crate::python::GenPythonProto;

pub fn build_pylib(build: &mut Build) -> Result<()> {
    // generated files
    build.add(
        "pylib/anki:proto",
        GenPythonProto {
            proto_files: inputs![glob!["proto/anki/*.proto"]],
        },
    )?;
    build.add(
        "pylib/anki:_backend_generated.py",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $out",
            inputs: hashmap! {
                "script" => inputs!["pylib/tools/genbackend.py"],
                "" => inputs!["pylib/anki/_vendor/stringcase.py", ":pylib/anki:proto"]
            },
            outputs: hashmap! {
                "out" => vec!["pylib/anki/_backend_generated.py"]
            },
        },
    )?;
    build.add(
        "pylib/anki:_fluent.py",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $strings $out",
            inputs: hashmap! {
                "script" => inputs!["pylib/tools/genfluent.py"],
                "strings" => inputs![":rslib/i18n:strings.json"],
                "" => inputs!["pylib/anki/_vendor/stringcase.py"]
            },
            outputs: hashmap! {
                "out" => vec!["pylib/anki/_fluent.py"]
            },
        },
    )?;
    build.add(
        "pylib/anki:hooks_gen.py",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $out",
            inputs: hashmap! {
                "script" => inputs!["pylib/tools/genhooks.py"],
                "" => inputs!["pylib/anki/_vendor/stringcase.py", "pylib/tools/hookslib.py"]
            },
            outputs: hashmap! {
                "out" => vec!["pylib/anki/hooks_gen.py"]
            },
        },
    )?;
    build.add(
        "pylib/anki:_rsbridge",
        LinkFile {
            input: inputs![":pylib/rsbridge"],
            output: &format!(
                "pylib/anki/_rsbridge.{}",
                match build.host_platform {
                    Platform::WindowsX64 => "pyd",
                    _ => "so",
                }
            ),
        },
    )?;
    build.add("pylib/anki:buildinfo.py", GenBuildInfo {})?;

    // wheel
    build.add(
        "wheels:anki",
        BuildWheel {
            name: "anki",
            version: anki_version(),
            src_folder: "pylib/anki",
            gen_folder: "$builddir/pylib/anki",
            platform: overriden_python_target_platform().or(Some(build.host_platform)),
            deps: inputs![
                ":pylib/anki",
                glob!("pylib/anki/**"),
                "python/requirements.anki.in",
            ],
        },
    )?;
    Ok(())
}

pub fn check_pylib(build: &mut Build) -> Result<()> {
    python_format(build, "pylib", inputs![glob!("pylib/**/*.py")])?;

    build.add(
        "check:pytest:pylib",
        PythonTest {
            folder: "pylib/tests",
            python_path: &["$builddir/pylib"],
            deps: inputs![":pylib/anki", glob!["pylib/{anki,tests}/**"]],
        },
    )
}

pub struct GenBuildInfo {}

impl BuildAction for GenBuildInfo {
    fn command(&self) -> &str {
        "$pyenv_bin $script $version_file $buildhash_file $out"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["pylib/tools/genbuildinfo.py"]);
        build.add_inputs("version_file", inputs![".version"]);
        build.add_inputs("buildhash_file", inputs!["$builddir/buildhash"]);
        build.add_outputs("out", vec!["pylib/anki/buildinfo.py"]);
    }
}
