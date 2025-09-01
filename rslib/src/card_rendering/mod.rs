// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use crate::prelude::*;

mod parser;
pub(crate) mod service;
pub mod tts;
mod writer;

pub fn strip_av_tags<S: Into<String> + AsRef<str>>(txt: S) -> String {
    nodes_or_text_only(txt.as_ref())
        .map(|nodes| nodes.write_without_av_tags())
        .unwrap_or_else(|| txt.into())
}

pub fn extract_av_tags<S: Into<String> + AsRef<str>>(
    txt: S,
    question_side: bool,
    tr: &I18n,
) -> (String, Vec<anki_proto::card_rendering::AvTag>) {
    nodes_or_text_only(txt.as_ref())
        .map(|nodes| nodes.write_and_extract_av_tags(question_side, tr))
        .unwrap_or_else(|| (txt.into(), vec![]))
}

pub fn prettify_av_tags<S: Into<String> + AsRef<str>>(txt: S) -> String {
    nodes_or_text_only(txt.as_ref())
        .map(|nodes| nodes.write_with_pretty_av_tags())
        .unwrap_or_else(|| txt.into())
}

/// Parse `txt` into [CardNodes] and return the result,
/// or [None] if it only contains text nodes.
fn nodes_or_text_only(txt: &str) -> Option<CardNodes<'_>> {
    let nodes = CardNodes::parse(txt);
    (!nodes.text_only).then_some(nodes)
}

#[derive(Debug, PartialEq)]
struct CardNodes<'a> {
    nodes: Vec<Node<'a>>,
    text_only: bool,
}

impl<'iter, 'nodes> IntoIterator for &'iter CardNodes<'nodes> {
    type Item = &'iter Node<'nodes>;
    type IntoIter = std::slice::Iter<'iter, Node<'nodes>>;

    fn into_iter(self) -> Self::IntoIter {
        self.nodes.iter()
    }
}

#[derive(Debug, PartialEq)]
enum Node<'a> {
    Text(&'a str),
    SoundOrVideo(&'a str),
    Directive(Directive<'a>),
}

#[derive(Debug, PartialEq)]
enum Directive<'a> {
    Tts(TtsDirective<'a>),
    Other(OtherDirective<'a>),
}

#[derive(Debug, PartialEq)]
struct TtsDirective<'a> {
    content: &'a str,
    lang: &'a str,
    voices: Vec<&'a str>,
    speed: f32,
    blank: Option<&'a str>,
    options: HashMap<&'a str, &'a str>,
}

#[derive(Debug, PartialEq, Eq)]
struct OtherDirective<'a> {
    name: &'a str,
    content: &'a str,
    options: HashMap<&'a str, &'a str>,
}

#[cfg(feature = "bench")]
#[inline]
pub fn anki_directive_benchmark() {
    CardNodes::parse("[anki:foo bar=baz][/anki:foo][anki:tts lang=jp_JP voices=Alice,Bob speed=0.5 cloze_blank= bar=baz][/anki:tts]");
}

#[cfg(test)]
mod test {
    use super::*;

    /// Strip av tags and assert equality with input or separately passed
    /// output.
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
            "foo [sound:bar.mp3] baz [anki:tts lang=en_US][...][/anki:tts]",
            true,
            &tr,
        );
        assert_eq!(
            (txt.as_str(), tags),
            (
                "foo [anki:play:q:0] baz [anki:play:q:1]",
                vec![
                    anki_proto::card_rendering::AvTag {
                        value: Some(anki_proto::card_rendering::av_tag::Value::SoundOrVideo(
                            "bar.mp3".to_string()
                        ))
                    },
                    anki_proto::card_rendering::AvTag {
                        value: Some(anki_proto::card_rendering::av_tag::Value::Tts(
                            anki_proto::card_rendering::TtsTag {
                                field_text: tr.card_templates_blank().to_string(),
                                lang: "en_US".to_string(),
                                voices: vec![],
                                speed: 1.0,
                                other_args: vec![],
                            }
                        ))
                    }
                ],
            ),
        );

        assert_eq!(
            extract_av_tags("[anki:tts]foo[/anki:tts]", true, &tr),
            (
                format!(
                    "[{}]",
                    tr.errors_bad_directive("anki:tts", tr.errors_option_not_set("lang"))
                ),
                vec![],
            ),
        );
    }
}
