// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod rust_interface;

use std::fs;
use std::path::Path;

use anki_proto_gen::descriptors_path;
use anyhow::Result;
use prost_reflect::DescriptorPool;

/// Where the RWKV model files go for the tests (not in the repository).
const RWKV_MODEL_DIR: &str = "../qt/aqt/rwkv_inference";
/// The model output vectors some RWKV tests compare against (not in the
/// repository either).
const RWKV_VECTOR_DIR: &str = "src/rwkv/testdata";

fn main() -> Result<()> {
    println!("cargo:rerun-if-changed=../out/buildhash");
    let buildhash = fs::read_to_string("../out/buildhash").unwrap_or_default();
    println!("cargo:rustc-env=BUILDHASH={buildhash}");

    let descriptors_path = descriptors_path();
    println!("cargo:rerun-if-changed={}", descriptors_path.display());
    let pool = DescriptorPool::decode(std::fs::read(descriptors_path)?.as_ref())?;
    rust_interface::write_rust_interface(&pool)?;

    // The RWKV weights have no public licence and are not committed. Tests
    // that read them run only when the files are placed locally; otherwise
    // they are ignored (rwkv_models) or not compiled (rwkv_vectors).
    println!("cargo::rustc-check-cfg=cfg(rwkv_models)");
    println!("cargo::rustc-check-cfg=cfg(rwkv_vectors)");
    println!("cargo:rerun-if-changed={RWKV_MODEL_DIR}");
    println!("cargo:rerun-if-changed={RWKV_VECTOR_DIR}");
    let models = Path::new(RWKV_MODEL_DIR)
        .join("sm56/weights.f32.bin")
        .exists();
    if models {
        println!("cargo:rustc-cfg=rwkv_models");
    }
    if models
        && Path::new(RWKV_VECTOR_DIR)
            .join("sm92-vectors.json.gz")
            .exists()
    {
        println!("cargo:rustc-cfg=rwkv_vectors");
    }
    Ok(())
}
