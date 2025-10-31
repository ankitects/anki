// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod rust_interface;

use anki_proto_gen::descriptors_path;
use anyhow::Result;
use prost_reflect::DescriptorPool;

fn main() -> Result<()> {
    let descriptors_path = descriptors_path();
    println!("cargo:rerun-if-changed={}", descriptors_path.display());
    let pool = DescriptorPool::decode(std::fs::read(descriptors_path)?.as_ref())?;
    rust_interface::write_rust_interface(&pool)?;

    println!("cargo:rerun-if-changed=../../../out/buildhash");
    let buildhash = std::fs::read_to_string("../../../out/buildhash").unwrap_or_default();
    println!("cargo:rustc-env=BUILDHASH={buildhash}");

    tauri_build::build();

    Ok(())
}
