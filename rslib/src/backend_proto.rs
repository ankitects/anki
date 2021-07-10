// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod backend {
    include!(concat!(env!("OUT_DIR"), "/anki.backend.rs"));
}
pub mod i18n {
    include!(concat!(env!("OUT_DIR"), "/anki.i18n.rs"));
}
pub mod generic {
    include!(concat!(env!("OUT_DIR"), "/anki.generic.rs"));
}

pub use backend::*;
pub use generic::*;
pub use i18n::*;
