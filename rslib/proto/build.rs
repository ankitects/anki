// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod python;
pub mod rust;
pub mod ts;

use std::env;
use std::path::PathBuf;

use anki_proto_gen::get_services;
use anyhow::Result;

fn main() -> Result<()> {
    let descriptors_path = env::var("DESCRIPTORS_BIN").ok().map(PathBuf::from);

    let pool = rust::write_rust_protos(descriptors_path)?;
    let (_, services) = get_services(&pool);
    python::write_python_interface(&services)?;
    ts::write_ts_interface(&services)?;

    Ok(())
}
