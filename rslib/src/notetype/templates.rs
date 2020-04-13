// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto::{CardTemplate as CardTemplateProto, CardTemplateConfig, OptionalUInt32},
    timestamp::TimestampSecs,
    types::Usn,
};

pub struct CardTemplate {
    pub ord: Option<u32>,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub name: String,
    pub config: CardTemplateConfig,
}

impl From<CardTemplate> for CardTemplateProto {
    fn from(t: CardTemplate) -> Self {
        CardTemplateProto {
            ord: t.ord.map(|n| OptionalUInt32 { val: n }),
            mtime_secs: t.mtime_secs.0 as u32,
            usn: t.usn.0,
            name: t.name,
            config: Some(t.config),
        }
    }
}

impl CardTemplate {
    pub fn new<S1, S2, S3>(name: S1, qfmt: S2, afmt: S3) -> Self
    where
        S1: Into<String>,
        S2: Into<String>,
        S3: Into<String>,
    {
        CardTemplate {
            ord: None,
            name: name.into(),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            config: CardTemplateConfig {
                q_format: qfmt.into(),
                a_format: afmt.into(),
                q_format_browser: "".into(),
                a_format_browser: "".into(),
                target_deck_id: 0,
                browser_font_name: "".into(),
                browser_font_size: 0,
                other: vec![],
            },
        }
    }
}
