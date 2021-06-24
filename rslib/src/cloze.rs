// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, collections::HashSet};

use lazy_static::lazy_static;
use regex::{Captures, Regex};

use crate::{latex::contains_latex, template::RenderContext, text::strip_html_preserving_entities};

lazy_static! {
    static ref CLOZE: Regex = Regex::new(
        r#"(?xsi)
            \{\{
            c(\d+)::    # 1 = cloze number
            (.*?)       # 2 = clozed text
            (?:
              ::(.*?)   # 3 = optional hint
            )?
            \}\}
        "#
    )
    .unwrap();
    static ref MATHJAX: Regex = Regex::new(
        r#"(?xsi)
            (\\[(\[])       # 1 = mathjax opening tag
            (.*?)           # 2 = inner content
            (\\[])])        # 3 = mathjax closing tag 
           "#
    )
    .unwrap();
}

mod cloze_caps {
    // cloze ordinal
    pub const ORD: usize = 1;
    // the occluded text
    pub const TEXT: usize = 2;
    // optional hint
    pub const HINT: usize = 3;
}

mod mathjax_caps {
    pub const OPENING_TAG: usize = 1;
    pub const INNER_TEXT: usize = 2;
    pub const CLOSING_TAG: usize = 3;
}

pub fn reveal_cloze_text(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    let mut cloze_ord_was_in_text = false;

    let output = CLOZE.replace_all(text, |caps: &Captures| {
        let captured_ord = caps
            .get(cloze_caps::ORD)
            .unwrap()
            .as_str()
            .parse()
            .unwrap_or(0);

        if captured_ord != cloze_ord {
            // other cloze deletions are unchanged
            return caps.get(cloze_caps::TEXT).unwrap().as_str().to_owned();
        } else {
            cloze_ord_was_in_text = true;
        }

        let replacement;
        if question {
            // hint provided?
            if let Some(hint) = caps.get(cloze_caps::HINT) {
                replacement = format!("[{}]", hint.as_str());
            } else {
                replacement = "[...]".to_string()
            }
        } else {
            replacement = caps.get(cloze_caps::TEXT).unwrap().as_str().to_owned();
        }

        format!("<span class=cloze>{}</span>", replacement)
    });

    if !cloze_ord_was_in_text {
        return "".into();
    }

    // if no cloze deletions are found, Anki returns an empty string
    match output {
        Cow::Borrowed(_) => "".into(),
        other => other,
    }
}

pub fn reveal_cloze_text_only(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    CLOZE
        .captures_iter(text)
        .filter(|caps| {
            let captured_ord = caps
                .get(cloze_caps::ORD)
                .unwrap()
                .as_str()
                .parse()
                .unwrap_or(0);

            captured_ord == cloze_ord
        })
        .map(|caps| {
            let cloze = if question {
                // hint provided?
                if let Some(hint) = caps.get(cloze_caps::HINT) {
                    hint.as_str()
                } else {
                    "..."
                }
            } else {
                caps.get(cloze_caps::TEXT).unwrap().as_str()
            };

            cloze
        })
        .collect::<Vec<_>>()
        .join(", ")
        .into()
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
    CLOZE.is_match(text)
}

pub fn cloze_numbers_in_string(html: &str) -> HashSet<u16> {
    let mut set = HashSet::with_capacity(4);
    add_cloze_numbers_in_string(html, &mut set);
    set
}

#[allow(clippy::implicit_hasher)]
pub fn add_cloze_numbers_in_string(field: &str, set: &mut HashSet<u16>) {
    for cap in CLOZE.captures_iter(field) {
        if let Ok(n) = cap[1].parse() {
            set.insert(n);
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
    fn mathjax_html() {
        // escaped angle brackets should be preserved
        assert_eq!(
            strip_html_inside_mathjax(r"\(<foo>&lt;&gt;</foo>\)"),
            r"\(&lt;&gt;\)"
        );
    }
}
