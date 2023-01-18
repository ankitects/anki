// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use lazy_static::lazy_static;

pub fn version() -> &'static str {
    include_str!("../../.version").trim()
}

pub fn buildhash() -> &'static str {
    option_env!("BUILDHASH").unwrap_or("dev").trim()
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

pub(crate) fn sync_client_version_short() -> &'static str {
    lazy_static! {
        static ref VER: String = format!(
            "{version},{buildhash},{platform}",
            version = version(),
            buildhash = buildhash(),
            platform = env::consts::OS
        );
    }
    &VER
}
