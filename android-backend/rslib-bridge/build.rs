// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod fluent;
pub mod proto;

use std::{
    env::{self, consts::OS},
    path::PathBuf,
    process::Command,
};

use anyhow::Result;
use prost_reflect::DescriptorPool;

fn main() -> Result<()> {
    let descriptors_path = env::var("DESCRIPTORS_BIN").ok().map(PathBuf::from).unwrap();
    println!("cargo:rerun-if-changed={}", descriptors_path.display());
    let pool = DescriptorPool::decode(std::fs::read(descriptors_path)?.as_ref())?;
    let (_, services) = anki_proto_gen::get_services(&pool);
    proto::write_kotlin_interface(&services)?;
    fluent::write_translations();

    // Work around issue with recent NDKs. Based on
    // https://github.com/bendk/application-services/commit/0443ba6f42d1964c214ff44c1f2a61689bf1dbf2
    let target_os = env::var("CARGO_CFG_TARGET_OS").unwrap();
    let target_arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap();
    if target_arch == "x86_64" && target_os == "android" {
        let android_ndk_home = env::var("ANDROID_NDK_HOME").expect("ANDROID_NDK_HOME not set");
        let platform = if OS == "linux" {
            "linux-x86_64"
        } else if OS == "macos" {
            "darwin-x86_64"
        } else {
            "windows-x86_64"
        };

        // cargo-ndk sets CC_x86_64-linux-android to the path to `clang`, within the
        // Android NDK.
        let clang_path = PathBuf::from(
            env::var("CC_x86_64-linux-android").expect("CC_x86_64-linux-android not set"),
        );
        let clang_version = get_clang_major_version(&clang_path);

        let lib_dir =
            format!("/toolchains/llvm/prebuilt/{platform}/lib/clang/{clang_version}/lib/linux/");
        println!("cargo:rustc-link-search={android_ndk_home}/{lib_dir}");
        println!("cargo:rustc-link-lib=static=clang_rt.builtins-x86_64-android");
    }

    Ok(())
}

/// Run the clang binary at `clang_path`, and return its major version number
fn get_clang_major_version(clang_path: &PathBuf) -> String {
    let clang_output = Command::new(clang_path)
        .arg("-dumpversion")
        .output()
        .expect("failed to start clang");

    if !clang_output.status.success() {
        panic!(
            "failed to run clang: {}",
            String::from_utf8_lossy(&clang_output.stderr)
        );
    }

    let clang_version = String::from_utf8(clang_output.stdout).expect("clang output is not utf8");
    clang_version
        .split('.')
        .next()
        .expect("could not parse clang output")
        .to_owned()
}
