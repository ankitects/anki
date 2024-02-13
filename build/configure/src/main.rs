// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod aqt;
mod bundle;
mod platform;
mod pylib;
mod python;
mod rust;
mod web;

use std::env;

use anyhow::Result;
use aqt::build_and_check_aqt;
use bundle::build_bundle;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::protobuf::check_proto;
use ninja_gen::protobuf::setup_protoc;
use ninja_gen::python::setup_python;
use ninja_gen::Build;
use pylib::build_pylib;
use pylib::check_pylib;
use python::check_python;
use python::setup_venv;
use python::setup_venv_stub;
use rust::build_rust;
use rust::check_minilints;
use rust::check_rust;
use web::build_and_check_web;
use web::check_sql;

use crate::python::setup_sphix;

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

    setup_python(build)?;

    if env::var("NO_VENV").is_ok() {
        println!("NO_VENV is set, using Python system environment.");
        setup_venv_stub(build)?;
    } else {
        setup_venv(build)?;
    }

    build_rust(build)?;
    build_pylib(build)?;
    build_and_check_web(build)?;
    build_and_check_aqt(build)?;
    build_bundle(build)?;

    if env::var("OFFLINE_BUILD").is_err() {
        setup_sphix(build)?;
    }

    check_rust(build)?;
    check_pylib(build)?;
    check_python(build)?;

    check_sql(build)?;
    check_minilints(build)?;

    build.trailing_text = "default pylib qt\n".into();

    build.write_build_file()?;

    Ok(())
}
