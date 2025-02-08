// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fmt::Write as _;

use super::CardNodes;
use super::Directive;
use super::Node;
use super::OtherDirective;
use super::TtsDirective;
use crate::prelude::*;
use crate::text::decode_entities;
use crate::text::strip_html_for_tts;

impl CardNodes<'_> {
    pub(super) fn write_without_av_tags(&self) -> String {
        AvStripper::new().write(self)
    }

    pub(super) fn write_and_extract_av_tags(
        &self,
        question_side: bool,
        tr: &I18n,
    ) -> (String, Vec<anki_proto::card_rendering::AvTag>) {
        let mut extractor = AvExtractor::new(question_side, tr);
        (extractor.write(self), extractor.tags)
    }

    pub(super) fn write_with_pretty_av_tags(&self) -> String {
        AvPrettifier::new().write(self)
    }
}

trait Write {
    fn write<'iter, 'nodes: 'iter, T>(&mut self, nodes: T) -> String
    where
        T: IntoIterator<Item = &'iter Node<'nodes>>,
    {
        let mut buf = String::new();
        for node in nodes {
            match node {
                Node::Text(s) => self.write_text(&mut buf, s),
                Node::SoundOrVideo(r) => self.write_sound(&mut buf, r),
                Node::Directive(directive) => self.write_directive(&mut buf, directive),
            };
        }
        buf
    }

    fn write_text(&mut self, buf: &mut String, txt: &str) {
        buf.push_str(txt);
    }

    fn write_sound(&mut self, buf: &mut String, resource: &str) {
        write!(buf, "[sound:{}]", resource).unwrap();
    }

    fn write_directive(&mut self, buf: &mut String, directive: &Directive) {
        match directive {
            Directive::Tts(directive) => self.write_tts_directive(buf, directive),
            Directive::Other(directive) => self.write_other_directive(buf, directive),
        };
    }

    fn write_tts_directive(&mut self, buf: &mut String, directive: &TtsDirective) {
        write!(buf, "[anki:tts").unwrap();

        for (key, val) in [
            ("lang", directive.lang),
            ("voices", &directive.voices.join(",")),
            ("speed", &directive.speed.to_string()),
        ] {
            self.write_directive_option(buf, key, val);
        }
        if let Some(blank) = directive.blank {
            self.write_directive_option(buf, "cloze_blank", blank);
        }
        for (key, val) in &directive.options {
            self.write_directive_option(buf, key, val);
        }

        write!(buf, "]{}[/anki:tts]", directive.content).unwrap();
    }

    fn write_other_directive(&mut self, buf: &mut String, directive: &OtherDirective) {
        write!(buf, "[anki:{}", directive.name).unwrap();
        for (key, val) in &directive.options {
            self.write_directive_option(buf, key, val);
        }
        buf.push(']');
        self.write_directive_content(buf, directive.content);
        write!(buf, "[/anki:{}]", directive.name).unwrap();
    }

    fn write_directive_option(&mut self, buf: &mut String, key: &str, val: &str) {
        if val.contains([']', ' ', '\t', '\r', '\n']) {
            write!(buf, " {}=\"{}\"", key, val).unwrap();
        } else {
            write!(buf, " {}={}", key, val).unwrap();
        }
    }

    fn write_directive_content(&mut self, buf: &mut String, content: &str) {
        buf.push_str(content);
    }
}

struct AvStripper;

impl AvStripper {
    fn new() -> Self {
        Self {}
    }
}

impl Write for AvStripper {
    fn write_sound(&mut self, _buf: &mut String, _resource: &str) {}

    fn write_tts_directive(&mut self, _buf: &mut String, _directive: &TtsDirective) {}
}

struct AvExtractor<'a> {
    side: char,
    tags: Vec<anki_proto::card_rendering::AvTag>,
    tr: &'a I18n,
}

impl<'a> AvExtractor<'a> {
    fn new(question_side: bool, tr: &'a I18n) -> Self {
        Self {
            side: if question_side { 'q' } else { 'a' },
            tags: vec![],
            tr,
        }
    }

    fn write_play_tag(&self, buf: &mut String) {
        write!(buf, "[anki:play:{}:{}]", self.side, self.tags.len()).unwrap();
    }

    fn transform_tts_content(&self, directive: &TtsDirective) -> String {
        strip_html_for_tts(directive.content).replace(
            "[...]",
            directive.blank.unwrap_or(&self.tr.card_templates_blank()),
        )
    }
}

impl Write for AvExtractor<'_> {
    fn write_sound(&mut self, buf: &mut String, resource: &str) {
        self.write_play_tag(buf);
        self.tags.push(anki_proto::card_rendering::AvTag {
            value: Some(anki_proto::card_rendering::av_tag::Value::SoundOrVideo(
                decode_entities(resource).into(),
            )),
        });
    }

    fn write_tts_directive(&mut self, buf: &mut String, directive: &TtsDirective) {
        if let Some(error) = directive.error(self.tr) {
            write!(buf, "[{}]", error).unwrap();
            return;
        }

        self.write_play_tag(buf);
        self.tags.push(anki_proto::card_rendering::AvTag {
            value: Some(anki_proto::card_rendering::av_tag::Value::Tts(
                anki_proto::card_rendering::TtsTag {
                    field_text: self.transform_tts_content(directive),
                    lang: directive.lang.into(),
                    voices: directive.voices.iter().map(ToString::to_string).collect(),
                    speed: directive.speed,
                    other_args: directive
                        .options
                        .iter()
                        .map(|(key, val)| format!("{}={}", key, val))
                        .collect(),
                },
            )),
        });
    }
}

impl TtsDirective<'_> {
    fn error(&self, tr: &I18n) -> Option<String> {
        if self.lang.is_empty() {
            Some(
                tr.errors_bad_directive("anki:tts", tr.errors_option_not_set("lang"))
                    .into(),
            )
        } else {
            None
        }
    }
}

struct AvPrettifier;

impl AvPrettifier {
    fn new() -> Self {
        Self {}
    }
}

impl Write for AvPrettifier {
    fn write_sound(&mut self, buf: &mut String, resource: &str) {
        write!(buf, "🔉{}🔉", resource).unwrap();
    }

    fn write_tts_directive(&mut self, buf: &mut String, directive: &TtsDirective) {
        write!(buf, "💬{}💬", directive.content).unwrap();
    }
}

#[cfg(test)]
mod test {
    use super::*;

    struct Writer;
    impl Write for Writer {}
    impl Writer {
        fn new() -> Self {
            Self {}
        }
    }

    /// Parse input, write it out, and assert equality with input or separately
    /// passed output.
    macro_rules! roundtrip {
        ($input:expr) => {
            assert_eq!($input, Writer::new().write(&CardNodes::parse($input)));
        };
        ($input:expr, $output:expr) => {
            assert_eq!(Writer::new().write(&CardNodes::parse($input)), $output);
        };
    }

    #[test]
    fn writing() {
        roundtrip!("foo");
        roundtrip!("[sound:foo]");
        roundtrip!("[anki:foo bar=baz]spam[/anki:foo]");

        // normalizing (not currently exposed)
        roundtrip!(
            "[anki:foo\nbar=baz ][/anki:foo]",
            "[anki:foo bar=baz][/anki:foo]"
        );
    }
}
