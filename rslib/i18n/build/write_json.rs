// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    env, fs,
    path::{Path, PathBuf},
};

pub fn main() {
    let args: Vec<_> = env::args().collect();
    let target_file = Path::new(args.get(1).expect("output path not provided"));

    let dir = PathBuf::from(env!("OUT_DIR"));
    let path = dir.join("strings.json");
    fs::copy(path, target_file).unwrap();
}
