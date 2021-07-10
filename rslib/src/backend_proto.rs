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
pub mod cards {
    include!(concat!(env!("OUT_DIR"), "/anki.cards.rs"));
}
pub mod collection {
    include!(concat!(env!("OUT_DIR"), "/anki.collection.rs"));
}

pub use backend::*;
pub use cards::*;
pub use collection::*;
pub use generic::*;
pub use i18n::*;
