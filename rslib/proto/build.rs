// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod python;
pub mod rust;
pub mod ts;
pub mod utils;

use std::env;
use std::path::PathBuf;

use anyhow::Result;

fn main() -> Result<()> {
    let descriptors_path = env::var("DESCRIPTORS_BIN").ok().map(PathBuf::from);

    let pool = rust::write_backend_proto_rs(descriptors_path)?;
    python::write_python_interface(&pool)?;
    ts::write_ts_interface(&pool)?;

    Ok(())
}
