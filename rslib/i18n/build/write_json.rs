// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{env, fs, path::Path};

pub fn main() {
    let args: Vec<_> = env::args().collect();
    let target_file = Path::new(args.get(1).expect("output path not provided"));
    let strings_json = include_str!(concat!(env!("OUT_DIR"), "/strings.json"));

    fs::write(target_file, strings_json).unwrap();
}
