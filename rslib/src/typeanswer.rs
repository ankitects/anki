// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use dissimilar::Chunk;
use lazy_static::lazy_static;
use regex::Regex;
use unic_ucd_category::GeneralCategory;

use crate::{
    card_rendering::strip_av_tags,
    text::{normalize_to_nfc, strip_html},
};

lazy_static! {
    static ref LINEBREAKS: Regex = Regex::new(
        r#"(?six)
        (
            \n
            |
            <br\s?/?>
            |
            </?div>
        )+
    "#
    )
    .unwrap();
}

struct DiffContext {
    expected: String,
    provided: String,
}

impl DiffContext {
    fn new(expected: &str, provided: &str) -> Self {
        DiffContext {
            expected: prepare_expected(expected),
            provided: prepare_provided(provided),
        }
    }

    fn to_tokens(&self) -> DiffOutput<'_> {
        let chunks = dissimilar::diff(&self.provided, &self.expected);
        let mut provided = vec![];
        let mut expected = vec![];
        for chunk in chunks {
            match chunk {
                Chunk::Equal(text) => {
                    provided.push(DiffToken {
                        kind: DiffTokenKind::Good,
                        text: text.into(),
                    });
                    expected.push(DiffToken {
                        kind: DiffTokenKind::Good,
                        text: text.into(),
                    });
                }
                Chunk::Delete(text) => {
                    provided.push(DiffToken {
                        kind: DiffTokenKind::Bad,
                        text: text.into(),
                    });
                }
                Chunk::Insert(text) => {
                    // If the preceding text was correct, indicate text was missing
                    if provided
                        .last()
                        .map(|v| v.kind == DiffTokenKind::Good)
                        .unwrap_or_default()
                    {
                        provided.push(DiffToken {
                            kind: DiffTokenKind::Missing,
                            text: text.into(),
                        });
                    }
                    expected.push(DiffToken {
                        kind: DiffTokenKind::Missing,
                        text: text.into(),
                    });
                }
            }
        }
        DiffOutput { provided, expected }
    }

    fn to_html(&self) -> String {
        let output = self.to_tokens();
        let provided = render_tokens(&output.provided);
        let expected = render_tokens(&output.expected);
        format!(
            "<div style='white-space: pre-wrap;'>{}</div>",
            if no_mistakes(&output.expected) {
                provided
            } else {
                format!("{provided}<br><span id=typearrow>&darr;</span><br>{expected}")
            }
        )
    }
}

fn no_mistakes(tokens: &[DiffToken]) -> bool {
    tokens.iter().all(|v| v.kind == DiffTokenKind::Good)
}

fn prepare_expected(expected: &str) -> String {
    let without_av = strip_av_tags(expected);
    let without_newlines = LINEBREAKS.replace_all(&without_av, " ");
    let without_html = strip_html(&without_newlines);
    normalize_to_nfc(&without_html).into()
}

fn prepare_provided(provided: &str) -> String {
    normalize_to_nfc(provided).into()
}

#[derive(Debug, PartialEq)]
enum DiffTokenKind {
    Good,
    Bad,
    Missing,
}

#[derive(Debug, PartialEq)]
struct DiffToken<'a> {
    kind: DiffTokenKind,
    text: Cow<'a, str>,
}

#[derive(Debug, PartialEq)]
struct DiffOutput<'a> {
    provided: Vec<DiffToken<'a>>,
    expected: Vec<DiffToken<'a>>,
}

pub fn compare_answer(expected: &str, provided: &str) -> String {
    DiffContext::new(expected, provided).to_html()
}

fn render_tokens(tokens: &[DiffToken]) -> String {
    let text_tokens: Vec<_> = tokens
        .iter()
        .map(|token| {
            let text = with_isolated_leading_mark(&token.text);
            let encoded = htmlescape::encode_minimal(&text);
            let class = match token.kind {
                DiffTokenKind::Good => "typeGood",
                DiffTokenKind::Bad => "typeBad",
                DiffTokenKind::Missing => "typeMissed",
            };
            format!("<span class={class}>{encoded}</span>")
        })
        .collect();
    text_tokens.join("")
}

/// If text begins with a mark character, prefix it with a non-breaking
/// space to prevent the mark from joining to the previous token.
fn with_isolated_leading_mark(text: &str) -> Cow<str> {
    if let Some(ch) = text.chars().next() {
        if GeneralCategory::of(ch).is_mark() {
            return format!("\u{a0}{text}").into();
        }
    }
    text.into()
}

#[cfg(test)]
mod test {
    use DiffTokenKind::*;

    use super::*;

    macro_rules! token {
        ($kind:ident, $text:expr) => {
            DiffToken {
                kind: $kind,
                text: $text.into(),
            }
        };
    }

    #[test]
    fn tokens() {
        let ctx = DiffContext::new("¿Y ahora qué vamos a hacer?", "y ahora qe vamosa hacer");
        let output = ctx.to_tokens();
        assert_eq!(
            output.provided,
            vec![
                token!(Bad, "y"),
                token!(Good, " ahora q"),
                token!(Bad, "e"),
                token!(Good, " vamos"),
                token!(Missing, " "),
                token!(Good, "a hacer"),
                token!(Missing, "?"),
            ]
        );
        assert_eq!(
            output.expected,
            vec![
                token!(Missing, "¿Y"),
                token!(Good, " ahora q"),
                token!(Missing, "ué"),
                token!(Good, " vamos"),
                token!(Missing, " "),
                token!(Good, "a hacer"),
                token!(Missing, "?"),
            ]
        );
    }

    #[test]
    fn html_and_media() {
        let ctx = DiffContext::new("[sound:foo.mp3]<b>1</b> &nbsp;2", "1  2");
        // the spacing is handled by wrapping html output in white-space: pre-wrap
        assert_eq!(ctx.to_tokens().expected, &[token!(Good, "1  2")]);
    }

    #[test]
    fn missed_chars_only_shown_in_provided_when_after_good() {
        let ctx = DiffContext::new("1", "23");
        assert_eq!(ctx.to_tokens().provided, &[token!(Bad, "23")]);
        let ctx = DiffContext::new("12", "1");
        assert_eq!(
            ctx.to_tokens().provided,
            &[token!(Good, "1"), token!(Missing, "2"),]
        );
    }
}
