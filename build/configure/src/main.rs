// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod aqt;
mod launcher;
mod platform;
mod pylib;
mod python;
mod rust;
mod web;

use std::env;

use anyhow::Result;
use aqt::build_and_check_aqt;
use launcher::build_launcher;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::protobuf::check_proto;
use ninja_gen::protobuf::setup_protoc;
use ninja_gen::python::setup_uv;
use ninja_gen::Build;
use platform::overriden_python_target_platform;
use pylib::build_pylib;
use pylib::check_pylib;
use python::check_python;
use python::setup_venv;
use rust::build_rust;
use rust::check_minilints;
use rust::check_rust;
use web::build_and_check_web;
use web::check_sql;

use crate::python::setup_sphinx;

fn anki_version() -> String {
    std::fs::read_to_string(".version")
        .unwrap()
        .trim()
        .to_string()
}

fn main() -> Result<()> {
    let mut build = Build::new()?;
    let build = &mut build;

    setup_protoc(build)?;
    check_proto(build, inputs![glob!["proto/**/*.proto"]])?;

    if env::var("OFFLINE_BUILD").is_err() {
        setup_uv(
            build,
            overriden_python_target_platform().unwrap_or(build.host_platform),
        )?;
    }
    setup_venv(build)?;

    build_rust(build)?;
    build_pylib(build)?;
    build_and_check_web(build)?;
    build_and_check_aqt(build)?;

    if env::var("OFFLINE_BUILD").is_err() {
        build_launcher(build)?;
    }

    setup_sphinx(build)?;

    check_rust(build)?;
    check_pylib(build)?;
    check_python(build)?;

    check_sql(build)?;
    check_minilints(build)?;

    build.trailing_text = "default pylib qt\n".into();

    build.write_build_file()?;

    Ok(())
}
