// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod aqt;
mod bundle;
mod platform;
mod proto;
mod pylib;
mod python;
mod rust;
mod web;

use aqt::build_and_check_aqt;
use bundle::build_bundle;
use ninja_gen::{Build, Result};
use pylib::{build_pylib, check_pylib};
use python::{check_copyright, check_python, setup_python};
use rust::{build_rust, check_rust};
use web::{build_and_check_web, check_sql};

use crate::proto::check_proto;

fn anki_version() -> &'static str {
    include_str!("../../../.version").trim()
}

fn main() -> Result<()> {
    let mut build = Build::new()?;
    let build = &mut build;

    let python_binary = setup_python(build)?;

    build_rust(build)?;
    build_pylib(build, &python_binary)?;
    build_and_check_web(build)?;
    build_and_check_aqt(build)?;
    build_bundle(build, &python_binary)?;

    check_rust(build)?;
    check_pylib(build)?;
    check_python(build)?;
    check_proto(build)?;
    check_sql(build)?;
    check_copyright(build)?;

    build.trailing_text = "default pylib/anki qt/aqt\n".into();

    build.write_build_file();

    Ok(())
}
