// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fmt::Write as _;

use super::{CardNodes, Node, OtherTag, Tag, TtsTag};
use crate::prelude::*;
use crate::{
    backend_proto as pb,
    text::{decode_entities, strip_html_for_tts},
};

impl<'a> CardNodes<'a> {
    pub(super) fn write_without_av_tags(&self) -> String {
        AvStripper::new().write(self)
    }

    pub(super) fn write_and_extract_av_tags(
        &self,
        question_side: bool,
        tr: &I18n,
    ) -> (String, Vec<pb::AvTag>) {
        let mut extractor = AvExtractor::new(question_side, tr);
        (extractor.write(self), extractor.tags)
    }
}

trait Write {
    fn write<'iter, 'nodes: 'iter, T>(&mut self, nodes: T) -> String
    where
        T: IntoIterator<Item = &'iter Node<'nodes>>,
    {
        let mut buf = String::new();
        for node in nodes {
            match &node {
                Node::Text(s) => self.write_text(&mut buf, s),
                Node::SoundOrVideo(r) => self.write_sound(&mut buf, r),
                Node::Tag(tag) => self.write_tag(&mut buf, tag),
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

    fn write_tag(&mut self, buf: &mut String, tag: &Tag) {
        match tag {
            Tag::Tts(tag) => self.write_tts_tag(buf, tag),
            Tag::Other(tag) => self.write_other_tag(buf, tag),
        };
    }

    fn write_tts_tag(&mut self, buf: &mut String, tag: &TtsTag) {
        write!(buf, "[anki:tts").unwrap();

        for (key, val) in [
            ("lang", tag.lang),
            ("voices", &tag.voices.join(",")),
            ("speed", &tag.speed.to_string()),
        ] {
            self.write_tag_option(buf, key, val);
        }
        if let Some(blank) = tag.blank {
            self.write_tag_option(buf, "cloze_blank", blank);
        }
        for (key, val) in &tag.options {
            self.write_tag_option(buf, key, val);
        }

        write!(buf, "]{}[/anki:tts]", tag.content).unwrap();
    }

    fn write_other_tag(&mut self, buf: &mut String, tag: &OtherTag) {
        write!(buf, "[anki:{}", tag.name).unwrap();
        for (key, val) in &tag.options {
            self.write_tag_option(buf, key, val);
        }
        buf.push(']');
        self.write_tag_content(buf, tag.content);
        write!(buf, "[/anki:{}]", tag.name).unwrap();
    }

    fn write_tag_option(&mut self, buf: &mut String, key: &str, val: &str) {
        if val.contains::<&[char]>(&[']', ' ', '\t', '\r', '\n']) {
            write!(buf, " {}=\"{}\"", key, val).unwrap();
        } else {
            write!(buf, " {}={}", key, val).unwrap();
        }
    }

    fn write_tag_content(&mut self, buf: &mut String, content: &str) {
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

    fn write_tts_tag(&mut self, _buf: &mut String, _tag: &TtsTag) {}
}

struct AvExtractor<'a> {
    side: char,
    tags: Vec<pb::AvTag>,
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

    fn transform_tts_content(&self, tag: &TtsTag) -> String {
        strip_html_for_tts(tag.content).replace(
            "[...]",
            tag.blank.unwrap_or(&self.tr.card_templates_blank()),
        )
    }
}

impl Write for AvExtractor<'_> {
    fn write_sound(&mut self, buf: &mut String, resource: &str) {
        self.write_play_tag(buf);
        self.tags.push(pb::AvTag {
            value: Some(pb::av_tag::Value::SoundOrVideo(
                decode_entities(resource).into(),
            )),
        });
    }

    fn write_tts_tag(&mut self, buf: &mut String, tag: &TtsTag) {
        self.write_play_tag(buf);
        self.tags.push(pb::AvTag {
            value: Some(pb::av_tag::Value::Tts(pb::TtsTag {
                field_text: self.transform_tts_content(tag),
                lang: tag.lang.into(),
                voices: tag.voices.iter().map(ToString::to_string).collect(),
                speed: tag.speed,
                other_args: tag
                    .options
                    .iter()
                    .map(|(key, val)| format!("{}={}", key, val))
                    .collect(),
            })),
        });
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
        roundtrip!(
            "[anki:tts][/anki:tts]",
            "[anki:tts lang= voices= speed=1][/anki:tts]"
        );
    }
}
