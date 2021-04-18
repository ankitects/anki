// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use lazy_static::lazy_static;

fn buildinfo(key: &str) -> &'static str {
    let buildinfo = include_str!(env!("BUILDINFO"));
    for line in buildinfo.split('\n') {
        let mut it = line.split(' ');
        if it.next().unwrap() == key {
            return it.next().unwrap();
        }
    }
    unreachable!("{} not found", key);
}

pub fn version() -> &'static str {
    buildinfo("STABLE_VERSION")
}

pub fn buildhash() -> &'static str {
    buildinfo("STABLE_BUILDHASH")
}

pub(crate) fn sync_client_version() -> &'static str {
    lazy_static! {
        static ref VER: String = format!(
            "anki,{version} ({buildhash}),{platform}",
            version = version(),
            buildhash = buildhash(),
            platform = env::var("PLATFORM").unwrap_or_else(|_| env::consts::OS.to_string())
        );
    }
    &VER
}
