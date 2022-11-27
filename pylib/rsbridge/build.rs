// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

fn main() {
    // macOS needs special link flags for PyO3
    if cfg!(target_os = "macos") {
        println!("cargo:rustc-link-arg=-undefined");
        println!("cargo:rustc-link-arg=dynamic_lookup");
        println!("cargo:rustc-link-arg=-mmacosx-version-min=10.13");
    }

    // On Windows, we need to be able to link with python3.lib
    if cfg!(windows) {
        let lib_path = Path::new("../../out/extracted/python/libs")
            .canonicalize()
            .expect("libs");
        println!("cargo:rustc-link-search={}", lib_path.display());
    }
}
