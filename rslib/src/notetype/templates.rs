// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto::{CardTemplate as CardTemplateProto, CardTemplateConfig, OptionalUInt32},
    decks::DeckID,
    err::{AnkiError, Result},
    template::ParsedTemplate,
    timestamp::TimestampSecs,
    types::Usn,
};

#[derive(Debug, PartialEq)]
pub struct CardTemplate {
    pub ord: Option<u32>,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub name: String,
    pub config: CardTemplateConfig,
}

impl CardTemplate {
    pub(crate) fn parsed_question(&self) -> Option<ParsedTemplate> {
        ParsedTemplate::from_text(&self.config.q_format).ok()
    }

    pub(crate) fn parsed_answer(&self) -> Option<ParsedTemplate> {
        ParsedTemplate::from_text(&self.config.a_format).ok()
    }

    pub(crate) fn question_format_for_browser(&self) -> &str {
        if !self.config.q_format_browser.is_empty() {
            &self.config.q_format_browser
        } else {
            &self.config.q_format
        }
    }

    pub(crate) fn answer_format_for_browser(&self) -> &str {
        if !self.config.a_format_browser.is_empty() {
            &self.config.a_format_browser
        } else {
            &self.config.a_format
        }
    }

    pub(crate) fn target_deck_id(&self) -> Option<DeckID> {
        if self.config.target_deck_id > 0 {
            Some(DeckID(self.config.target_deck_id))
        } else {
            None
        }
    }
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

    /// Return whether the name is valid. Remove quote characters if it leads to a valid name.
    pub(crate) fn fix_name(&mut self) -> Result<()> {
        let bad_chars = |c| c == '"';
        if self.name.is_empty() {
            return Err(AnkiError::invalid_input("Empty template name"));
        }
        let trimmed = self.name.replace(bad_chars, "");
        if trimmed.is_empty() {
            return Err(AnkiError::invalid_input(
                "Template name contain only quotes",
            ));
        }
        if self.name.len() != trimmed.len() {
            self.name = trimmed;
        }
        Ok(())
    }
}
