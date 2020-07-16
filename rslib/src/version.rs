// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use lazy_static::lazy_static;
use std::env;

pub fn version() -> &'static str {
    include_str!("../../meta/version").trim()
}

pub fn buildhash() -> &'static str {
    include_str!("../../meta/buildhash").trim()
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
