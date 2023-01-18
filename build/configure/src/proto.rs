// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::archives::download_and_extract;
use ninja_gen::archives::with_exe;
use ninja_gen::glob;
use ninja_gen::hashmap;
use ninja_gen::inputs;
use ninja_gen::protobuf::protoc_archive;
use ninja_gen::protobuf::ClangFormat;
use ninja_gen::Build;
use ninja_gen::Result;

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
