// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

fn main() {
    // macOS needs special link flags for PyO3
    if cfg!(target_os = "macos") {
        println!("cargo:rustc-link-arg=-undefined");
        println!("cargo:rustc-link-arg=dynamic_lookup");
        println!("cargo:rustc-link-arg=-mmacosx-version-min=10.13");
    }

    // add a custom link path if required
    if let Ok(path) = std::env::var("PYO3_PYTHON") {
        println!("cargo:rerun-if-env-changed=PYO3_PYTHON");
        let path = std::path::Path::new(&path).with_file_name("libs");
        println!("cargo:rustc-link-search={}", path.to_str().unwrap());
    }
}
