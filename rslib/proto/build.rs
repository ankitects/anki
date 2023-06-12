// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod python;
pub mod rust;

use std::env;
use std::path::PathBuf;

use anyhow::Context;
use anyhow::Result;

fn main() -> Result<()> {
    let descriptors_path = PathBuf::from(env::var("DESCRIPTORS_BIN").context("DESCRIPTORS_BIN")?);

    let pool = rust::write_backend_proto_rs(&descriptors_path)?;
    python::write_python_interface(&pool)?;
    Ok(())
}
