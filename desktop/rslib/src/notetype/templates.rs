// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardTemplateConfig;
use super::CardTemplateProto;
use crate::prelude::*;
use crate::template::ParsedTemplate;

#[derive(Debug, PartialEq, Clone)]
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

    pub(crate) fn parsed_question_format_for_browser(&self) -> Option<ParsedTemplate> {
        ParsedTemplate::from_text(&self.config.q_format_browser).ok()
    }

    pub(crate) fn parsed_answer_format_for_browser(&self) -> Option<ParsedTemplate> {
        ParsedTemplate::from_text(&self.config.a_format_browser).ok()
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

    pub(crate) fn target_deck_id(&self) -> Option<DeckId> {
        if self.config.target_deck_id > 0 {
            Some(DeckId(self.config.target_deck_id))
        } else {
            None
        }
    }
}

impl From<CardTemplate> for CardTemplateProto {
    fn from(t: CardTemplate) -> Self {
        CardTemplateProto {
            ord: t.ord.map(Into::into),
            mtime_secs: t.mtime_secs.0,
            usn: t.usn.0,
            name: t.name,
            config: Some(t.config),
        }
    }
}

impl From<CardTemplateProto> for CardTemplate {
    fn from(t: CardTemplateProto) -> Self {
        CardTemplate {
            ord: t.ord.map(|n| n.val),
            mtime_secs: t.mtime_secs.into(),
            usn: t.usn.into(),
            name: t.name,
            config: t.config.unwrap_or_default(),
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
                id: Some(rand::random()),
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

    /// Return whether the name is valid. Remove quote characters if it leads to
    /// a valid name.
    pub(crate) fn fix_name(&mut self) -> Result<()> {
        let bad_chars = |c| c == '"';
        require!(!self.name.is_empty(), "Empty template name");
        let trimmed = self.name.replace(bad_chars, "");
        require!(!trimmed.is_empty(), "Template name contains only quotes");
        if self.name.len() != trimmed.len() {
            self.name = trimmed;
        }
        Ok(())
    }
}
