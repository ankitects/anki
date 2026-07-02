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

pub fn protoc_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => {
            OnlineArchive {
                url: "https://github.com/protocolbuffers/protobuf/releases/download/v31.1/protoc-31.1-linux-x86_64.zip",
                sha256: "96553041f1a91ea0efee963cb16f462f5985b4d65365f3907414c360044d8065",
            }
        },
        Platform::LinuxArm => {
            OnlineArchive {
                url: "https://github.com/protocolbuffers/protobuf/releases/download/v31.1/protoc-31.1-linux-aarch_64.zip",
                sha256: "6c554de11cea04c56ebf8e45b54434019b1cd85223d4bbd25c282425e306ecc2",
            }
        },
        Platform::MacX64 | Platform::MacArm => {
            OnlineArchive {
                url: "https://github.com/protocolbuffers/protobuf/releases/download/v31.1/protoc-31.1-osx-universal_binary.zip",
                sha256: "99ea004549c139f46da5638187a85bbe422d78939be0fa01af1aa8ab672e395f",
            }
        },
        Platform::WindowsX64 | Platform::WindowsArm => {
            OnlineArchive {
                url: "https://github.com/protocolbuffers/protobuf/releases/download/v31.1/protoc-31.1-win64.zip",
                sha256: "70381b116ab0d71cb6a5177d9b17c7c13415866603a0fd40d513dafe32d56c35",
            }
        }
    }
}

fn clang_format_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => {
            OnlineArchive {
                url: "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_linux_x86_64.zip",
                sha256: "64060bc4dbca30d0d96aab9344e2783008b16e1cae019a2532f1126ca5ec5449",
            }
        }
        Platform::LinuxArm => {
            // todo: replace with arm64 binary
            OnlineArchive {
                url: "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_linux_x86_64.zip",
                sha256: "64060bc4dbca30d0d96aab9344e2783008b16e1cae019a2532f1126ca5ec5449",
            }
        }
        Platform::MacX64 | Platform::MacArm => {
            OnlineArchive {
                url: "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_macos_x86_64.zip",
                sha256: "238be68d9478163a945754f06a213483473044f5a004c4125d3d9d8d3556466e",
            }
        }
        Platform::WindowsX64 | Platform::WindowsArm=> {
            OnlineArchive {
                url: "https://github.com/ankitects/clang-format-binaries/releases/download/anki-2021-01-09/clang-format_windows_x86_64.zip",
                sha256: "7d9f6915e3f0fb72407830f0fc37141308d2e6915daba72987a52f309fbeaccc",
            }
        }
    }
}
pub struct ClangFormat {
    pub inputs: BuildInput,
    pub check_only: bool,
}

impl BuildAction for ClangFormat {
    fn command(&self) -> &str {
        "$clang-format --style=google $args $in"
    }
    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("clang-format", inputs![":extract:clang-format:bin"]);
        build.add_inputs("in", &self.inputs);
        let (args, mode) = if self.check_only {
            ("--dry-run -ferror-limit=1 -Werror", "check")
        } else {
            ("-i", "fix")
        };
        build.add_variable("args", args);
        let hash = simple_hash(&self.inputs);
        build.add_output_stamp(format!("tests/clang-format.{mode}.{hash}"));
    }
    fn on_first_instance(&self, build: &mut crate::Build) -> anyhow::Result<()> {
        let binary = with_exe("clang-format");
        download_and_extract(
            build,
            "clang-format",
            clang_format_archive(build.host_platform),
            hashmap! {
                "bin" => [binary]
            },
        )
    }
}

pub fn setup_protoc(build: &mut Build) -> Result<()> {
    let protoc_binary = match env::var("PROTOC_BINARY") {
        Ok(path) => {
            assert!(
                Utf8Path::new(&path).is_absolute(),
                "PROTOC_BINARY must be absolute"
            );
            path.into()
        }
        Err(_) => {
            download_and_extract(
                build,
                "protoc",
                protoc_archive(build.host_platform),
                hashmap! {
                    "bin" => [with_exe("bin/protoc")]
                },
            )?;
            inputs![":extract:protoc:bin"]
        }
    };
    build.add_dependency("protoc_binary", protoc_binary);
    Ok(())
}

pub fn check_proto(build: &mut Build, inputs: BuildInput) -> Result<()> {
    build.add_action(
        "check:format:proto",
        ClangFormat {
            inputs: inputs.clone(),
            check_only: true,
        },
    )?;
    build.add_action(
        "format:proto",
        ClangFormat {
            inputs,
            check_only: false,
        },
    )?;
    Ok(())
}
