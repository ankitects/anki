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
protobuf!(cards);
protobuf!(collection);
protobuf!(configs);
protobuf!(deckconfig);
protobuf!(decks);
protobuf!(generic);
protobuf!(i18n);
protobuf!(notes);
protobuf!(notetypes);
protobuf!(scheduler);
protobuf!(search);
protobuf!(sync);
