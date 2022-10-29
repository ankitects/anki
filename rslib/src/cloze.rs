// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use htmlescape::encode_attribute;
use lazy_static::lazy_static;
use nom::{
    branch::alt,
    bytes::complete::{tag, take_while},
    combinator::map,
    multi::many0,
    IResult,
};
use regex::{Captures, Regex};
use std::{borrow::Cow, collections::HashSet};

use crate::{latex::contains_latex, template::RenderContext, text::strip_html_preserving_entities};

lazy_static! {
    static ref MATHJAX: Regex = Regex::new(
        r#"(?xsi)
            (\\[(\[])       # 1 = mathjax opening tag
            (.*?)           # 2 = inner content
            (\\[])])        # 3 = mathjax closing tag
           "#
    )
    .unwrap();
}

mod mathjax_caps {
    pub const OPENING_TAG: usize = 1;
    pub const INNER_TEXT: usize = 2;
    pub const CLOSING_TAG: usize = 3;
}

/// Contains raw string that represented the token
#[derive(Debug)]
enum Token<'a> {
    Open(&'a str, u16),
    Text(&'a str),
    Hint(&'a str),
    Close(&'a str),
}

/// Tokenize string
fn tokenize(txt: &str) -> Vec<Token> {
    /// Match Token::Open
    fn open(txt: &str) -> IResult<&str, Token> {
        // opening brackets and 'c'
        let (tail, _opening_brackets_and_c) = tag("{{c")(txt)?;
        // following ordinal
        let (tail, digits) = take_while(|c: char| c.is_ascii_digit())(tail)?;
        let ordinal: u16 = match digits.parse() {
            Ok(digits) => digits,
            Err(_) => {
                // not a valid ordinal; fail to recognize
                return Err(nom::Err::Error(nom::error::make_error(
                    tail,
                    nom::error::ErrorKind::Digit,
                )));
            }
        };
        // ::
        let (tail, _colons) = tag("::")(tail)?;
        Ok((tail, Token::Open(&txt[..5 + digits.len()], ordinal)))
    }

    /// Match a run of txt until an open/hint/close is encountered.
    fn text(txt: &str) -> IResult<&str, Token> {
        if txt.is_empty() {
            return Err(nom::Err::Error(nom::error::make_error(
                txt,
                nom::error::ErrorKind::Eof,
            )));
        }
        let mut index = 0;
        let mut other_token = alt((open, close, hint));
        while other_token(&txt[index..]).is_err() && index < txt.len() {
            index += 1;
        }
        Ok((&txt[index..], Token::Text(&txt[0..index])))
    }

    /// Match Token::Hint
    fn hint(txt: &str) -> IResult<&str, Token> {
        map(tag("::"), |_| Token::Hint(&txt[..2]))(txt)
    }

    /// Match Token::Close
    fn close(txt: &str) -> IResult<&str, Token> {
        map(tag("}}"), |_| Token::Close(&txt[..2]))(txt)
    }

    let (remaining, tokens) = many0(alt((open, close, hint, text)))(txt).unwrap();
    assert!(remaining.is_empty());

    tokens
}

/// Struct representing a cloze
#[derive(Debug)]
struct Cloze<'a> {
    open_str: &'a str, // Open token string
    ordinal: u16,
    content: Vec<String>,
    hint: bool,
    hint_content: Vec<&'a str>,
}
impl<'a> Cloze<'a> {
    fn new(open_str: &'a str, ordinal: u16) -> Self {
        Self {
            open_str,
            ordinal,
            content: vec![],
            hint: false,
            hint_content: vec![],
        }
    }
}

pub fn reveal_cloze_text(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    let tokens = &tokenize(text);
    let mut output: Vec<String> = vec![];
    let mut stack: Vec<Cloze> = vec![];
    let mut cloze_found = false;

    for token in tokens {
        if stack.is_empty() {
            // At root level, only valid token is open cloze
            match token {
                Token::Open(raw, ordinal) => stack.push(Cloze::new(raw, *ordinal)),
                Token::Text(text) => output.push(text.to_string()),
                Token::Hint(raw) => output.push(raw.to_string()),
                Token::Close(raw) => output.push(raw.to_string()),
            }
        } else {
            let mut current = stack.last_mut().unwrap();
            match (token, current.hint) {
                (Token::Open(raw, ordinal), false) => stack.push(Cloze::new(raw, *ordinal)),
                (Token::Open(raw, _), true) => current.hint_content.push(raw),
                (Token::Hint(_), _) => current.hint = true,
                (Token::Text(text), false) => current.content.push(text.to_string()),
                (Token::Text(text), true) => current.hint_content.push(text),
                (Token::Close(_), _) => {
                    let cloze = stack.pop().unwrap();
                    cloze_found |= cloze.ordinal == cloze_ord;
                    let content = cloze.content.join("");
                    let hint = cloze.hint_content.join("");

                    let out = match (question, cloze.ordinal == cloze_ord, hint.is_empty()) {
                        // Question, active close, no hint
                        (true, true, true) => format!(
                            r#"<span class="cloze" data-cloze="{}" data-ordinal="{}">[...]</span>"#,
                            encode_attribute(&content),
                            cloze.ordinal
                        ),
                        // Question - active cloze, hint
                        (true, true, false) => format!(
                            r#"<span class="cloze" data-cloze="{}" data-ordinal="{}">[{}]</span>"#,
                            encode_attribute(&content),
                            cloze.ordinal,
                            &hint
                        ),
                        // Question - inactive cloze
                        (true, false, _) => format!(
                            r#"<span class="cloze-inactive" data-ordinal="{}">{}</span>"#,
                            cloze.ordinal, content
                        ),
                        // Answer - active cloze
                        (false, true, _) => format!(
                            r#"<span class="cloze" data-ordinal="{}">{}</span>"#,
                            cloze.ordinal, content
                        ),
                        // Answer - inactive cloze
                        (false, false, _) => format!(
                            r#"<span class="cloze-inactive" data-ordinal="{}">{}</span>"#,
                            cloze.ordinal, content
                        ),
                    };
                    if stack.is_empty() {
                        output.push(out);
                    } else {
                        stack.last_mut().unwrap().content.push(out)
                    }
                }
            }
        }
    }

    if !cloze_found {
        return Cow::from("");
    }

    // Add any remaining (unclosed) clozes as text
    for rest in stack {
        output.push(rest.open_str.to_string());
        output.push(rest.content.join(""));
        if rest.hint {
            output.push("::".to_string());
            output.push(rest.hint_content.join(""));
        }
    }

    Cow::from(output.join(""))
}

pub fn reveal_cloze_text_only(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    let tokens = &tokenize(text);
    let mut output: Vec<String> = vec![];
    let mut stack: Vec<Cloze> = vec![];
    let mut cloze_found = false;

    for token in tokens {
        if stack.is_empty() {
            // At root level, only valid token is open cloze
            if let Token::Open(raw, ordinal) = token {
                stack.push(Cloze::new(raw, *ordinal));
            }
        } else {
            let mut current = stack.last_mut().unwrap();
            // Only store hints on question and active cloze
            match (
                token,
                current.hint,
                question && current.ordinal == cloze_ord,
            ) {
                (Token::Open(raw, ordinal), false, _) => stack.push(Cloze::new(raw, *ordinal)),
                (Token::Open(raw, _), true, true) => current.hint_content.push(raw),
                (Token::Hint(_), _, _) => current.hint = true,
                (Token::Text(text), false, _) => current.content.push(text.to_string()),
                (Token::Text(text), true, _) => current.hint_content.push(text),
                (Token::Close(_), _, _) => {
                    let cloze = stack.pop().unwrap();
                    cloze_found |= cloze.ordinal == cloze_ord;
                    let content = cloze.content.join(", ");
                    let mut hint = cloze.hint_content.join("");
                    if hint.is_empty() {
                        hint += "..."
                    }

                    match (question && cloze.ordinal == cloze_ord, stack.is_empty()) {
                        // Question and active cloze
                        (true, true) => output.push(hint),
                        (true, false) => stack.last_mut().unwrap().content.push(hint),
                        // Question and inactive or answer
                        (false, true) => output.push(content),
                        (false, false) => stack.last_mut().unwrap().content.push(content),
                    };
                }
                (_, _, _) => {}
            }
        }
    }

    if !cloze_found {
        return Cow::from("");
    }

    Cow::from(output.join(", "))
}

/// If text contains any LaTeX tags, render the front and back
/// of each cloze deletion so that LaTeX can be generated. If
/// no LaTeX is found, returns an empty string.
pub fn expand_clozes_to_reveal_latex(text: &str) -> String {
    if !contains_latex(text) {
        return "".into();
    }
    let ords = cloze_numbers_in_string(text);
    let mut buf = String::new();
    for ord in ords {
        buf += reveal_cloze_text(text, ord, true).as_ref();
        buf += reveal_cloze_text(text, ord, false).as_ref();
    }

    buf
}

pub(crate) fn contains_cloze(text: &str) -> bool {
    struct Cloze {
        in_hint: bool,
        ordinal: u16,
    }

    let tokens = tokenize(text);
    let mut stack: Vec<Cloze> = vec![];

    for token in tokens {
        let (in_root, in_hint) = match stack.last() {
            Some(Cloze {
                in_hint,
                ordinal: _,
            }) => (false, *in_hint),
            None => (true, false),
        };
        match (token, in_root, in_hint) {
            (Token::Open(_, ordinal), _, false) => stack.push(Cloze {
                in_hint: false,
                ordinal,
            }),
            (Token::Hint(_), false, false) => stack.last_mut().unwrap().in_hint = true,
            (Token::Close(_), false, _) => {
                return true;
            }
            _ => {}
        }
    }

    false
}

pub fn cloze_numbers_in_string(html: &str) -> HashSet<u16> {
    let mut set = HashSet::with_capacity(4);
    add_cloze_numbers_in_string(html, &mut set);
    set
}

#[allow(clippy::implicit_hasher)]
pub fn add_cloze_numbers_in_string(field: &str, set: &mut HashSet<u16>) {
    struct Cloze {
        in_hint: bool,
        ordinal: u16,
    }

    let tokens = tokenize(field);
    let mut stack: Vec<Cloze> = vec![];

    for token in tokens {
        let (in_root, in_hint) = match stack.last() {
            Some(Cloze {
                in_hint,
                ordinal: _,
            }) => (false, *in_hint),
            None => (true, false),
        };
        match (token, in_root, in_hint) {
            (Token::Open(_, ordinal), _, false) => stack.push(Cloze {
                in_hint: false,
                ordinal,
            }),
            (Token::Hint(_), false, false) => stack.last_mut().unwrap().in_hint = true,
            (Token::Close(_), false, _) => {
                let current = stack.pop().unwrap();
                set.insert(current.ordinal);
            }
            _ => {}
        }
    }
}

fn strip_html_inside_mathjax(text: &str) -> Cow<str> {
    MATHJAX.replace_all(text, |caps: &Captures| -> String {
        format!(
            "{}{}{}",
            caps.get(mathjax_caps::OPENING_TAG).unwrap().as_str(),
            strip_html_preserving_entities(caps.get(mathjax_caps::INNER_TEXT).unwrap().as_str())
                .as_ref(),
            caps.get(mathjax_caps::CLOSING_TAG).unwrap().as_str()
        )
    })
}

pub(crate) fn cloze_filter<'a>(text: &'a str, context: &RenderContext) -> Cow<'a, str> {
    strip_html_inside_mathjax(
        reveal_cloze_text(text, context.card_ord + 1, context.question_side).as_ref(),
    )
    .into_owned()
    .into()
}

pub(crate) fn cloze_only_filter<'a>(text: &'a str, context: &RenderContext) -> Cow<'a, str> {
    reveal_cloze_text_only(text, context.card_ord + 1, context.question_side)
}

#[cfg(test)]
mod test {
    use std::collections::HashSet;

    use super::*;
    use crate::text::strip_html;

    #[test]
    fn cloze() {
        assert_eq!(
            cloze_numbers_in_string("test"),
            vec![].into_iter().collect::<HashSet<u16>>()
        );
        assert_eq!(
            cloze_numbers_in_string("{{c2::te}}{{c1::s}}t{{"),
            vec![1, 2].into_iter().collect::<HashSet<u16>>()
        );

        assert_eq!(
            expand_clozes_to_reveal_latex("{{c1::foo}} {{c2::bar::baz}}"),
            "".to_string()
        );

        let expanded = expand_clozes_to_reveal_latex("[latex]{{c1::foo}} {{c2::bar::baz}}[/latex]");
        let expanded = strip_html(expanded.as_ref());
        assert!(expanded.contains("foo [baz]"));
        assert!(expanded.contains("[...] bar"));
        assert!(expanded.contains("foo bar"));
    }

    #[test]
    fn cloze_only() {
        assert_eq!(reveal_cloze_text_only("foo", 1, true), "");
        assert_eq!(reveal_cloze_text_only("foo {{c1::bar}}", 1, true), "...");
        assert_eq!(
            reveal_cloze_text_only("foo {{c1::bar::baz}}", 1, true),
            "baz"
        );
        assert_eq!(reveal_cloze_text_only("foo {{c1::bar}}", 1, false), "bar");
        assert_eq!(reveal_cloze_text_only("foo {{c1::bar}}", 2, false), "");
        assert_eq!(
            reveal_cloze_text_only("{{c1::foo}} {{c1::bar}}", 1, false),
            "foo, bar"
        );
    }

    #[test]
    fn nested_cloze_plain_text() {
        assert_eq!(
            strip_html(reveal_cloze_text("foo {{c1::bar {{c2::baz}}}}", 1, true).as_ref()),
            "foo [...]"
        );
        assert_eq!(
            strip_html(reveal_cloze_text("foo {{c1::bar {{c2::baz}}}}", 1, false).as_ref()),
            "foo bar baz"
        );
        assert_eq!(
            strip_html(reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 2, true).as_ref()),
            "foo bar [...]"
        );
        assert_eq!(
            strip_html(reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 2, false).as_ref()),
            "foo bar baz"
        );
        assert_eq!(
            strip_html(reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 1, true).as_ref()),
            "foo [qux]"
        );
        assert_eq!(
            strip_html(reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 1, false).as_ref()),
            "foo bar baz"
        );
    }

    #[test]
    fn nested_cloze_html() {
        assert_eq!(
            cloze_numbers_in_string("{{c2::te{{c1::s}}}}t{{"),
            vec![1, 2].into_iter().collect::<HashSet<u16>>()
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}}}", 1, true),
            format!(
                r#"foo <span class="cloze" data-cloze="{}" data-ordinal="1">[...]</span>"#,
                htmlescape::encode_attribute(
                    r#"bar <span class="cloze-inactive" data-ordinal="2">baz</span>"#
                )
            )
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}}}", 1, false),
            r#"foo <span class="cloze" data-ordinal="1">bar <span class="cloze-inactive" data-ordinal="2">baz</span></span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 2, true),
            r#"foo <span class="cloze-inactive" data-ordinal="1">bar <span class="cloze" data-cloze="baz" data-ordinal="2">[...]</span></span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 2, false),
            r#"foo <span class="cloze-inactive" data-ordinal="1">bar <span class="cloze" data-ordinal="2">baz</span></span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 1, true),
            format!(
                r#"foo <span class="cloze" data-cloze="{}" data-ordinal="1">[qux]</span>"#,
                htmlescape::encode_attribute(
                    r#"bar <span class="cloze-inactive" data-ordinal="2">baz</span>"#
                )
            )
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 1, false),
            r#"foo <span class="cloze" data-ordinal="1">bar <span class="cloze-inactive" data-ordinal="2">baz</span></span>"#
        );
    }

    #[test]
    fn mathjax_html() {
        // escaped angle brackets should be preserved
        assert_eq!(
            strip_html_inside_mathjax(r"\(<foo>&lt;&gt;</foo>\)"),
            r"\(&lt;&gt;\)"
        );
    }
}
