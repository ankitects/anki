// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

macro_rules! protobuf {
    ($ident:ident) => {
        pub mod $ident {
            include!(concat!(
                env!("OUT_DIR"),
                concat!("/anki.", stringify!($ident), ".rs")
            ));
        }
        pub use $ident::*;
    };
}

protobuf!(backend);
protobuf!(notes);
protobuf!(notetypes);
protobuf!(decks);
protobuf!(deckconfig);
protobuf!(i18n);
protobuf!(cards);
protobuf!(generic);
protobuf!(collection);
