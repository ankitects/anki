// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use lazy_static::lazy_static;

pub fn version() -> &'static str {
    include_str!("../../meta/version").trim()
}

pub fn buildhash() -> &'static str {
    include_str!("../../meta/buildhash").trim()
}

pub(crate) fn sync_client_version() -> &'static str {
    lazy_static! {
        static ref VER: String = format!(
            "anki,{} ({}),{}",
            version(),
            buildhash(),
            std::env::consts::OS
        );
    }
    &VER
}
