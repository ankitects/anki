// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::archives::Platform;
use ninja_gen::build::FilesHandle;
use ninja_gen::copy::CopyFiles;
use ninja_gen::glob;
use ninja_gen::input::BuildInput;
use ninja_gen::inputs;
use ninja_gen::python::python_format;
use ninja_gen::python::PythonEnvironment;
use ninja_gen::python::RuffCheck;
use ninja_gen::python::PythonTypecheck;
use ninja_gen::Build;

/// Normalize version string by removing leading zeros from numeric parts
/// while preserving pre-release markers (b1, rc2, a3, etc.)
fn normalize_version(version: &str) -> String {
    version
        .split('.')
        .map(|part| {
            // Check if the part contains only digits
            if part.chars().all(|c| c.is_ascii_digit()) {
                // Numeric part: remove leading zeros
                part.parse::<u32>().unwrap_or(0).to_string()
            } else {
                // Mixed part (contains both numbers and pre-release markers)
                // Split on first non-digit character and normalize the numeric prefix
                let chars = part.chars();
                let mut numeric_prefix = String::new();
                let mut rest = String::new();
                let mut found_non_digit = false;

                for ch in chars {
                    if ch.is_ascii_digit() && !found_non_digit {
                        numeric_prefix.push(ch);
                    } else {
                        found_non_digit = true;
                        rest.push(ch);
                    }
                }

                if numeric_prefix.is_empty() {
                    part.to_string()
                } else {
                    let normalized_prefix = numeric_prefix.parse::<u32>().unwrap_or(0).to_string();
                    format!("{}{}", normalized_prefix, rest)
                }
            }
        })
        .collect::<Vec<_>>()
        .join(".")
}

pub fn setup_venv(build: &mut Build) -> Result<()> {
    let extra_binary_exports = &[
        "mypy",
        "black",
        "isort",
        "ruff",
        "pytest",
        "protoc-gen-mypy",
    ];
    build.add_action(
        "pyenv",
        PythonEnvironment {
            venv_folder: "pyenv",
            deps: inputs![
                "pyproject.toml",
                "pylib/pyproject.toml",
                "qt/pyproject.toml",
                "uv.lock"
            ],
            extra_args: "--all-packages --extra qt --extra audio",
            extra_binary_exports,
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

    fn hide_progress(&self) -> bool {
        true
    }
}

pub struct BuildWheel {
    pub name: &'static str,
    pub version: String,
    pub platform: Option<Platform>,
    pub deps: BuildInput,
}

impl BuildAction for BuildWheel {
    fn command(&self) -> &str {
        "$uv build --wheel --out-dir=$out_dir --project=$project_dir"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("uv", inputs![":uv_binary"]);
        build.add_inputs("", &self.deps);

        // Set the project directory based on which package we're building
        let project_dir = if self.name == "anki" { "pylib" } else { "qt" };
        build.add_variable("project_dir", project_dir);

        // Set environment variable for uv to use our pyenv
        build.add_variable("pyenv_path", "$builddir/pyenv");
        build.add_env_var("UV_PROJECT_ENVIRONMENT", "$pyenv_path");

        // Set output directory
        build.add_variable("out_dir", "$builddir/wheels/");

        // Calculate the wheel filename that uv will generate
        let tag = if let Some(platform) = self.platform {
            let platform_tag = match platform {
                Platform::LinuxX64 => "manylinux_2_36_x86_64",
                Platform::LinuxArm => "manylinux_2_36_aarch64",
                Platform::MacX64 => "macosx_12_0_x86_64",
                Platform::MacArm => "macosx_12_0_arm64",
                Platform::WindowsX64 => "win_amd64",
                Platform::WindowsArm => "win_arm64",
            };
            format!("cp39-abi3-{platform_tag}")
        } else {
            "py3-none-any".into()
        };

        // Set environment variable for hatch_build.py to use the correct platform tag
        build.add_variable("wheel_tag", &tag);
        build.add_env_var("ANKI_WHEEL_TAG", "$wheel_tag");

        let name = self.name;

        let normalized_version = normalize_version(&self.version);

        let wheel_path = format!("wheels/{name}-{normalized_version}-{tag}.whl");
        build.add_outputs("out", vec![wheel_path]);
    }
}

pub fn check_python(build: &mut Build) -> Result<()> {
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

    build.add_action("check:ruff", RuffCheck {
        folders: &[
            "qt/aqt",
            "ftl",
            "pylib/tools",
            "tools",
            "python",
        ],
        deps: inputs![
            glob!["{pylib,ftl,qt,python,tools}/**/*.py"],
            ":pylib:anki",
            ":qt:aqt"
        ],
    })?;


    Ok(())
}

struct Sphinx {
    deps: BuildInput,
}

impl BuildAction for Sphinx {
    fn command(&self) -> &str {
        if env::var("OFFLINE_BUILD").is_err() {
            "$uv sync --extra sphinx && $python python/sphinx/build.py"
        } else {
            "$python python/sphinx/build.py"
        }
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        if env::var("OFFLINE_BUILD").is_err() {
            build.add_inputs("uv", inputs![":uv_binary"]);
            // Set environment variable to use the existing pyenv
            build.add_variable("pyenv_path", "$builddir/pyenv");
            build.add_env_var("UV_PROJECT_ENVIRONMENT", "$pyenv_path");
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
            deps: inputs![
                ":pylib",
                ":qt",
                ":python:sphinx:copy_conf",
                "pyproject.toml"
            ],
        },
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normalize_version_basic() {
        assert_eq!(normalize_version("1.2.3"), "1.2.3");
        assert_eq!(normalize_version("01.02.03"), "1.2.3");
        assert_eq!(normalize_version("1.0.0"), "1.0.0");
    }

    #[test]
    fn test_normalize_version_with_prerelease() {
        assert_eq!(normalize_version("1.2.3b1"), "1.2.3b1");
        assert_eq!(normalize_version("01.02.03b1"), "1.2.3b1");
        assert_eq!(normalize_version("1.0.0rc2"), "1.0.0rc2");
        assert_eq!(normalize_version("2.1.0a3"), "2.1.0a3");
        assert_eq!(normalize_version("1.2.3beta1"), "1.2.3beta1");
        assert_eq!(normalize_version("1.2.3alpha1"), "1.2.3alpha1");
    }
}
