// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

fn main() {
    println!(
        "cargo:rustc-env=TARGET={}",
        if std::env::var("MAC_X86").is_ok() {
            "x86_64-apple-darwin".into()
        } else {
            std::env::var("TARGET").unwrap()
        }
    );
}
