// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

macro_rules! protobuf {
    ($ident:ident, $name:literal) => {
        pub mod $ident {
            include!(concat!(env!("OUT_DIR"), "/anki.", $name, ".rs"));
        }
    };
}

protobuf!(ankidroid, "ankidroid");
protobuf!(backend, "backend");
protobuf!(card_rendering, "card_rendering");
protobuf!(cards, "cards");
protobuf!(collection, "collection");
protobuf!(config, "config");
protobuf!(deckconfig, "deckconfig");
protobuf!(decks, "decks");
protobuf!(generic, "generic");
protobuf!(i18n, "i18n");
protobuf!(import_export, "import_export");
protobuf!(links, "links");
protobuf!(media, "media");
protobuf!(notes, "notes");
protobuf!(notetypes, "notetypes");
protobuf!(scheduler, "scheduler");
protobuf!(search, "search");
protobuf!(stats, "stats");
protobuf!(sync, "sync");
protobuf!(tags, "tags");
