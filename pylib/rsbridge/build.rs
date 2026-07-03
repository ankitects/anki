// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

fn main() {
    // macOS needs special link flags for PyO3
    if cfg!(target_os = "macos") {
        println!("cargo:rustc-link-arg=-undefined");
        println!("cargo:rustc-link-arg=dynamic_lookup");
        println!("cargo:rustc-link-arg=-mmacosx-version-min=11");
    }

    // On Windows, we need to be able to link with python3.lib
    if cfg!(windows) {
        use std::process::Command;

        // Run Python to get sysconfig paths
        let output = Command::new("../../out/pyenv/scripts/python")
            .args([
                "-c",
                "import sysconfig; print(sysconfig.get_paths()['stdlib'])",
            ])
            .output()
            .expect("Failed to execute Python");

        let stdlib_path = String::from_utf8(output.stdout)
            .expect("Failed to parse Python output")
            .trim()
            .to_string();

        let libs_path = stdlib_path + "s";
        println!("cargo:rustc-link-search={libs_path}");
    }
}
