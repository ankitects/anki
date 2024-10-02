// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;

use difflib::sequencematcher::SequenceMatcher;
use once_cell::sync::Lazy;
use regex::Regex;
use unic_ucd_category::GeneralCategory;

use crate::card_rendering::strip_av_tags;
use crate::text::normalize_to_nfc;
use crate::text::strip_html;

static LINEBREAKS: Lazy<Regex> = Lazy::new(|| {
    Regex::new(
        r"(?six)
        (
            \n
            |
            <br\s?/?>
            |
            </?div>
        )+",
    )
    .unwrap()
});

macro_rules! format_typeans {
    ($typeans:expr) => {
        format!("<code id=typeans>{}</code>", $typeans)
    };
}

// Public API
pub fn compare_answer(expected: &str, provided: &str) -> String {
    if provided.is_empty() {
        format_typeans!(htmlescape::encode_minimal(expected))
    } else {
        Diff::new(expected, provided).to_html()
    }
}

struct Diff {
    provided: Vec<char>,
    expected: Vec<char>,
    expected_original: String,
}

impl Diff {
    fn new(expected: &str, provided: &str) -> Self {
        Self {
            provided: normalize_to_nfc(provided).chars().collect(),
            expected: normalize_to_nfc(&prepare_expected(expected))
                .chars()
                .collect(),
            expected_original: expected.to_string(),
        }
    }

    // Entry Point
    fn to_html(&self) -> String {
        if self.provided == self.expected {
            format_typeans!(format!(
                "<span class=typeGood>{}</span>",
                self.expected_original
            ))
        } else {
            let output = self.to_tokens();
            let provided_html = render_tokens(&output.provided_tokens);
            let expected_html = render_tokens(&output.expected_tokens);

            format_typeans!(format!(
                "{provided_html}<br><span id=typearrow>&darr;</span><br>{expected_html}"
            ))
        }
    }

    fn to_tokens(&self) -> DiffTokens {
        let mut matcher = SequenceMatcher::new(&self.provided, &self.expected);
        let mut provided_tokens = Vec::new();
        let mut expected_tokens = Vec::new();

        for opcode in matcher.get_opcodes() {
            let provided_slice = slice(&self.provided, opcode.first_start, opcode.first_end);
            let expected_slice = slice(&self.expected, opcode.second_start, opcode.second_end);

            match opcode.tag.as_str() {
                "equal" => {
                    provided_tokens.push(DiffToken::good(provided_slice));
                    expected_tokens.push(DiffToken::good(expected_slice));
                }
                "delete" => provided_tokens.push(DiffToken::bad(provided_slice)),
                "insert" => {
                    provided_tokens.push(DiffToken::missing(
                        "-".repeat(expected_slice.chars().count()),
                    ));
                    expected_tokens.push(DiffToken::missing(expected_slice));
                }
                "replace" => {
                    provided_tokens.push(DiffToken::bad(provided_slice));
                    expected_tokens.push(DiffToken::missing(expected_slice));
                }
                _ => unreachable!(),
            }
        }
        DiffTokens {
            provided_tokens,
            expected_tokens,
        }
    }
}

// Utility Functions
fn slice(chars: &[char], start: usize, end: usize) -> String {
    chars[start..end].iter().collect()
}

fn prepare_expected(expected: &str) -> String {
    let no_av_tags = strip_av_tags(expected);
    let no_linebreaks = LINEBREAKS.replace_all(&no_av_tags, " ");
    strip_html(&no_linebreaks).trim().to_string()
}

// Render Functions
fn render_tokens(tokens: &[DiffToken]) -> String {
    tokens.iter().fold(String::new(), |mut acc, token| {
        let isolated_text = isolate_leading_mark(&token.text);
        let encoded_text = htmlescape::encode_minimal(&isolated_text);
        let class = token.to_class();
        acc.push_str(&format!("<span class={class}>{encoded_text}</span>"));
        acc
    })
}

/// Prefixes a leading mark character with a non-breaking space to prevent
/// it from joining the previous token.
fn isolate_leading_mark(text: &str) -> Cow<str> {
    if text
        .chars()
        .next()
        .map_or(false, |ch| GeneralCategory::of(ch).is_mark())
    {
        format!("\u{a0}{text}").into()
    } else {
        text.into()
    }
}

#[derive(Debug, PartialEq, Eq)]
struct DiffTokens {
    provided_tokens: Vec<DiffToken>,
    expected_tokens: Vec<DiffToken>,
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
    fn new(kind: DiffTokenKind, text: String) -> Self {
        Self { kind, text }
    }

    fn good(text: String) -> Self {
        Self::new(DiffTokenKind::Good, text)
    }

    fn bad(text: String) -> Self {
        Self::new(DiffTokenKind::Bad, text)
    }

    fn missing(text: String) -> Self {
        Self::new(DiffTokenKind::Missing, text)
    }

    fn to_class(&self) -> &'static str {
        match self.kind {
            DiffTokenKind::Good => "typeGood",
            DiffTokenKind::Bad => "typeBad",
            DiffTokenKind::Missing => "typeMissed",
        }
    }
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
        let ctx = Diff::new("¿Y ahora qué vamos a hacer?", "y ahora qe vamosa hacer");
        let output = ctx.to_tokens();
        assert_eq!(
            output.provided_tokens,
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
            output.expected_tokens,
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
        let ctx = Diff::new("[sound:foo.mp3]<b>1</b> &nbsp;2", "1  2");
        // the spacing is handled by wrapping html output in white-space: pre-wrap
        assert_eq!(ctx.to_tokens().expected_tokens, &[good("1  2")]);
    }

    #[test]
    fn missed_chars_only_shown_in_provided_when_after_good() {
        let ctx = Diff::new("1", "23");
        assert_eq!(ctx.to_tokens().provided_tokens, &[bad("23")]);
        let ctx = Diff::new("12", "1");
        assert_eq!(ctx.to_tokens().provided_tokens, &[good("1"), missing("-"),]);
    }

    #[test]
    fn missed_chars_counted_correctly() {
        let ctx = Diff::new("нос", "нс");
        assert_eq!(
            ctx.to_tokens().provided_tokens,
            &[good("н"), missing("-"), good("с")]
        );
    }

    #[test]
    fn handles_certain_unicode_as_expected() {
        // this was not parsed as expected with dissimilar 1.0.4
        let ctx = Diff::new("쓰다듬다", "스다뜸다");
        assert_eq!(
            ctx.to_tokens().provided_tokens,
            &[bad("스"), good("다"), bad("뜸"), good("다"),]
        );
    }

    #[test]
    fn does_not_panic_with_certain_unicode() {
        // this was causing a panic with dissimilar 1.0.4
        let ctx = Diff::new(
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
        let ctx = compare_answer("123", "");
        assert_eq!(ctx, "<code id=typeans>123</code>");
    }

    #[test]
    fn correct_input_is_collapsed() {
        let ctx = Diff::new("123", "123");
        assert_eq!(
            ctx.to_html(),
            "<code id=typeans><span class=typeGood>123</span></code>"
        );
    }

    #[test]
    fn incorrect_input_is_not_collapsed() {
        let ctx = Diff::new("123", "1123");
        assert_eq!(
            ctx.to_html(),
            "<code id=typeans><span class=typeBad>1</span><span class=typeGood>123</span><br><span id=typearrow>&darr;</span><br><span class=typeGood>123</span></code>"
        );
    }
}
