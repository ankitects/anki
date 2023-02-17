// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod check;
mod extract;
mod gather;
mod write_strings;

use std::fs;

use check::check;
use extract::get_modules;
use gather::get_ftl_data;
use write_strings::write_strings;

// fixme: check all variables are present in translations as well?

fn main() {
    // generate our own requirements
    let map = get_ftl_data();
    check(&map);
    let modules = get_modules(&map);
    write_strings(&map, &modules);

    // write strings.json file to requested path
    println!("cargo:rerun-if-env-changed=STRINGS_JSON");
    if let Some(path) = option_env!("STRINGS_JSON") {
        let meta_json = serde_json::to_string_pretty(&modules).unwrap();
        fs::write(path, meta_json).unwrap();
    }
}
