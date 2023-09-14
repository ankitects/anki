// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use difflib::sequencematcher::Opcode;
use difflib::sequencematcher::SequenceMatcher;
use itertools::Itertools;
use lazy_static::lazy_static;
use regex::Regex;
use unic_ucd_category::GeneralCategory;

use crate::card_rendering::strip_av_tags;
use crate::text::normalize_to_nfc;
use crate::text::strip_html;

lazy_static! {
    static ref LINEBREAKS: Regex = Regex::new(
        r"(?six)
        (
            \n
            |
            <br\s?/?>
            |
            </?div>
        )+
    "
    )
    .unwrap();
}

struct DiffContext {
    expected: Vec<char>,
    provided: Vec<char>,
}

impl DiffContext {
    fn new(expected: &str, provided: &str) -> Self {
        DiffContext {
            provided: prepare_provided(provided).chars().collect_vec(),
            expected: prepare_expected(expected).chars().collect_vec(),
        }
    }

    fn slice_expected(&self, opcode: &Opcode) -> String {
        self.expected[opcode.second_start..opcode.second_end]
            .iter()
            .cloned()
            .collect()
    }

    fn slice_provided(&self, opcode: &Opcode) -> String {
        self.provided[opcode.first_start..opcode.first_end]
            .iter()
            .cloned()
            .collect()
    }

    fn to_tokens(&self) -> DiffOutput {
        let mut matcher = SequenceMatcher::new(&self.provided, &self.expected);
        let opcodes = matcher.get_opcodes();
        let mut provided = vec![];
        let mut expected = vec![];
        for opcode in opcodes {
            match opcode.tag.as_str() {
                "equal" => {
                    provided.push(DiffToken::good(self.slice_provided(&opcode)));
                    expected.push(DiffToken::good(self.slice_expected(&opcode)));
                }
                "delete" => {
                    provided.push(DiffToken::bad(self.slice_provided(&opcode)));
                }
                "insert" => {
                    let expected_str = self.slice_expected(&opcode);
                    provided.push(DiffToken::missing("-".repeat(expected_str.chars().count())));
                    expected.push(DiffToken::missing(expected_str));
                }
                "replace" => {
                    provided.push(DiffToken::bad(self.slice_provided(&opcode)));
                    expected.push(DiffToken::missing(self.slice_expected(&opcode)));
                }
                _ => unreachable!(),
            }
        }
        DiffOutput { provided, expected }
    }

    fn to_html(&self) -> String {
        let output = self.to_tokens();
        let provided = render_tokens(&output.provided);
        let expected = render_tokens(&output.expected);
        format!(
            "<code id=typeans>{}</code>",
            if self.provided.is_empty() {
                htmlescape::encode_minimal(&self.expected.iter().collect::<String>())
            } else if self.provided == self.expected {
                provided
            } else {
                format!("{provided}<br><span id=typearrow>&darr;</span><br>{expected}")
            }
        )
    }
}

fn prepare_expected(expected: &str) -> String {
    let without_av = strip_av_tags(expected);
    let without_newlines = LINEBREAKS.replace_all(&without_av, " ");
    let without_html = strip_html(&without_newlines);
    let without_outer_whitespace = without_html.trim();
    normalize_to_nfc(without_outer_whitespace).into()
}

fn prepare_provided(provided: &str) -> String {
    normalize_to_nfc(provided).into()
}

#[derive(Debug, PartialEq, Eq)]
enum DiffTokenKind {
    Good,
    Bad,
    Missing,
}

#[derive(Debug, PartialEq, Eq)]
struct DiffToken {
    kind: DiffTokenKind,
    text: String,
}

impl DiffToken {
    fn bad(text: String) -> Self {
        Self {
            kind: DiffTokenKind::Bad,
            text,
        }
    }

    fn good(text: String) -> Self {
        Self {
            kind: DiffTokenKind::Good,
            text,
        }
    }

    fn missing(text: String) -> Self {
        Self {
            kind: DiffTokenKind::Missing,
            text,
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
struct DiffOutput {
    provided: Vec<DiffToken>,
    expected: Vec<DiffToken>,
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
    use super::*;

    macro_rules! token_factory {
        ($name:ident) => {
            fn $name(text: &str) -> DiffToken {
                DiffToken::$name(String::from(text))
            }
        };
    }
    token_factory!(bad);
    token_factory!(good);
    token_factory!(missing);

    #[test]
    fn tokens() {
        let ctx = DiffContext::new("¿Y ahora qué vamos a hacer?", "y ahora qe vamosa hacer");
        let output = ctx.to_tokens();
        assert_eq!(
            output.provided,
            vec![
                bad("y"),
                good(" ahora q"),
                bad("e"),
                good(" vamos"),
                missing("-"),
                good("a hacer"),
                missing("-"),
            ]
        );
        assert_eq!(
            output.expected,
            vec![
                missing("¿Y"),
                good(" ahora q"),
                missing("ué"),
                good(" vamos"),
                missing(" "),
                good("a hacer"),
                missing("?"),
            ]
        );
    }

    #[test]
    fn html_and_media() {
        let ctx = DiffContext::new("[sound:foo.mp3]<b>1</b> &nbsp;2", "1  2");
        // the spacing is handled by wrapping html output in white-space: pre-wrap
        assert_eq!(ctx.to_tokens().expected, &[good("1  2")]);
    }

    #[test]
    fn missed_chars_only_shown_in_provided_when_after_good() {
        let ctx = DiffContext::new("1", "23");
        assert_eq!(ctx.to_tokens().provided, &[bad("23")]);
        let ctx = DiffContext::new("12", "1");
        assert_eq!(ctx.to_tokens().provided, &[good("1"), missing("-"),]);
    }

    #[test]
    fn missed_chars_counted_correctly() {
        let ctx = DiffContext::new("нос", "нс");
        assert_eq!(
            ctx.to_tokens().provided,
            &[good("н"), missing("-"), good("с")]
        );
    }

    #[test]
    fn handles_certain_unicode_as_expected() {
        // this was not parsed as expected with dissimilar 1.0.4
        let ctx = DiffContext::new("쓰다듬다", "스다뜸다");
        assert_eq!(
            ctx.to_tokens().provided,
            &[bad("스"), good("다"), bad("뜸"), good("다"),]
        );
    }

    #[test]
    fn does_not_panic_with_certain_unicode() {
        // this was causing a panic with dissimilar 1.0.4
        let ctx = DiffContext::new(
            "Сущность должна быть ответственна только за одно дело",
            concat!(
                "Single responsibility Сущность выполняет только одну задачу.",
                "Повод для изменения сущности только один."
            ),
        );
        ctx.to_tokens();
    }

    #[test]
    fn whitespace_is_trimmed() {
        assert_eq!(prepare_expected("<div>foo</div>"), "foo");
    }

    #[test]
    fn empty_input_shows_as_code() {
        let ctx = DiffContext::new("123", "");
        assert_eq!(ctx.to_html(), "<code id=typeans>123</code>");
    }

    #[test]
    fn correct_input_is_collapsed() {
        let ctx = DiffContext::new("123", "123");
        assert_eq!(
            ctx.to_html(),
            "<code id=typeans><span class=typeGood>123</span></code>"
        );
    }

    #[test]
    fn incorrect_input_is_not_collapsed() {
        let ctx = DiffContext::new("123", "1123");
        assert_eq!(
            ctx.to_html(),
            "<code id=typeans><span class=typeBad>1</span><span class=typeGood>123</span><br><span id=typearrow>&darr;</span><br><span class=typeGood>123</span></code>"
        );
    }
}
