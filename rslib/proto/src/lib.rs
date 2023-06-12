// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod generic_helpers;

macro_rules! protobuf {
    ($ident:ident, $name:literal) => {
        pub mod $ident {
            include!(concat!(env!("OUT_DIR"), "/anki.", $name, ".rs"));
        }
    };
}

use snafu::Snafu;

#[derive(Debug, Snafu)]
pub enum ProtoError {
    InvalidMethodIndex,
    #[snafu(context(false))]
    DecodeError {
        source: prost::DecodeError,
    },
    #[snafu(context(false))]
    EncodeError {
        source: prost::EncodeError,
    },
}

include!(concat!(env!("OUT_DIR"), "/service_index.rs"));

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
protobuf!(image_occlusion, "image_occlusion");
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
