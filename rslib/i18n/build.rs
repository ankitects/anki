// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod check;
mod extract;
mod gather;
mod python;
mod typescript;
mod write_strings;

use std::env;
use std::path::PathBuf;

use anki_io::create_dir_all;
use anki_io::write_file_if_changed;
use anyhow::Result;
use check::check;
use extract::get_modules;
use gather::get_ftl_data;
use write_strings::write_strings;

// fixme: check all variables are present in translations as well?

fn main() -> Result<()> {
    // generate our own requirements
    let map = get_ftl_data();
    check(&map);
    let modules = get_modules(&map);
    write_strings(&map, &modules);

    typescript::write_ts_interface(&modules)?;
    python::write_py_interface(&modules)?;

    // write strings.json file to requested path
    println!("cargo:rerun-if-env-changed=STRINGS_JSON");
    if let Ok(path) = env::var("STRINGS_JSON") {
        if !path.is_empty() {
            let path = PathBuf::from(path);
            let meta_json = serde_json::to_string_pretty(&modules).unwrap();
            create_dir_all(path.parent().unwrap())?;
            write_file_if_changed(path, meta_json)?;
        }
    }
    Ok(())
}
