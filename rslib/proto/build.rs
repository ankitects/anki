// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod python;
pub mod rust;
pub mod typescript;

use anki_proto_gen::descriptors_path;
use anki_proto_gen::get_services;
use anyhow::Result;

fn main() -> Result<()> {
    let descriptors_path = descriptors_path();

    let pool = rust::write_rust_protos(descriptors_path)?;
    let (_, services) = get_services(&pool);
    python::write_python_interface(&services)?;
    typescript::write_ts_interface(&services)?;

    Ok(())
}
