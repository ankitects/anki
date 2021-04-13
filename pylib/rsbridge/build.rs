// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

fn main() {
    // this build script simply exists so we can extend the link path
    // inside Bazel based on an env var, as we need to provide a custom
    // path to Python on Windows.
    if let Ok(path) = std::env::var("PYTHON_SYS_EXECUTABLE") {
        let path = std::path::Path::new(&path).with_file_name("libs");
        println!("cargo:rustc-link-search={}", path.to_str().unwrap());
    }
}
