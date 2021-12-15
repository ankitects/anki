// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use crate::backend_proto as pb;
use crate::prelude::*;

mod parser;
mod writer;

pub fn strip_av_tags(txt: &str) -> String {
    CardNodes::parse(txt).write_without_av_tags()
}

pub fn extract_av_tags(txt: &str, question_side: bool, tr: &I18n) -> (String, Vec<pb::AvTag>) {
    CardNodes::parse(txt).write_and_extract_av_tags(question_side, tr)
}

#[derive(Debug, PartialEq)]
struct CardNodes<'a>(Vec<Node<'a>>);

impl<'iter, 'nodes> IntoIterator for &'iter CardNodes<'nodes> {
    type Item = &'iter Node<'nodes>;
    type IntoIter = std::slice::Iter<'iter, Node<'nodes>>;

    fn into_iter(self) -> Self::IntoIter {
        self.0.iter()
    }
}

#[derive(Debug, PartialEq)]
enum Node<'a> {
    Text(&'a str),
    SoundOrVideo(&'a str),
    Tag(Tag<'a>),
}

#[derive(Debug, PartialEq)]
enum Tag<'a> {
    Tts(TtsTag<'a>),
    Other(OtherTag<'a>),
}

#[derive(Debug, PartialEq)]
struct TtsTag<'a> {
    content: &'a str,
    lang: &'a str,
    voices: Vec<&'a str>,
    speed: f32,
    blank: Option<&'a str>,
    options: HashMap<&'a str, &'a str>,
}

#[derive(Debug, PartialEq)]
struct OtherTag<'a> {
    name: &'a str,
    content: &'a str,
    options: HashMap<&'a str, &'a str>,
}

#[cfg(test)]
mod test {
    use super::*;

    /// Strip av tags and assert equality with input or separately passed output.
    macro_rules! assert_av_stripped {
        ($input:expr) => {
            assert_eq!($input, strip_av_tags($input));
        };
        ($input:expr, $output:expr) => {
            assert_eq!(strip_av_tags($input), $output);
        };
    }

    #[test]
    fn av_stripping() {
        assert_av_stripped!("foo [sound:bar] baz", "foo  baz");
        assert_av_stripped!("[anki:tts bar=baz]spam[/anki:tts]", "");
        assert_av_stripped!("[anki:foo bar=baz]spam[/anki:foo]");
    }

    #[test]
    fn av_extracting() {
        let tr = I18n::template_only();
        let (txt, tags) = extract_av_tags(
            "foo [sound:bar.mp3] baz [anki:tts][...][/anki:tts]",
            true,
            &tr,
        );
        assert_eq!(
            (txt.as_str(), tags),
            (
                "foo [anki:play:q:0] baz [anki:play:q:1]",
                vec![
                    pb::AvTag {
                        value: Some(pb::av_tag::Value::SoundOrVideo("bar.mp3".to_string()))
                    },
                    pb::AvTag {
                        value: Some(pb::av_tag::Value::Tts(pb::TtsTag {
                            field_text: tr.card_templates_blank().to_string(),
                            lang: "".to_string(),
                            voices: vec![],
                            speed: 1.0,
                            other_args: vec![],
                        }))
                    }
                ],
            ),
        );
    }
}
