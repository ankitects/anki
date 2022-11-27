// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::{
    archives::{download_and_extract, with_exe},
    glob, hashmap, inputs,
    protobuf::{protoc_archive, ClangFormat},
    Build, Result,
};

pub fn download_protoc(build: &mut Build) -> Result<()> {
    download_and_extract(
        build,
        "protoc",
        protoc_archive(build.host_platform),
        hashmap! {
            "bin" => [with_exe("bin/protoc")]
        },
    )?;
    Ok(())
}

pub fn check_proto(build: &mut Build) -> Result<()> {
    build.add(
        "check:format:proto",
        ClangFormat {
            inputs: inputs![glob!["proto/**/*.proto"]],
            check_only: true,
        },
    )?;
    build.add(
        "format:proto",
        ClangFormat {
            inputs: inputs![glob!["proto/**/*.proto"]],
            check_only: false,
        },
    )?;
    Ok(())
}
