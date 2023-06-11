// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;

fn main() {
    println!("cargo:rerun-if-changed=../out/buildhash");
    let buildhash = fs::read_to_string("../out/buildhash").unwrap_or_default();
    println!("cargo:rustc-env=BUILDHASH={buildhash}")
}
