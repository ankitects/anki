// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, collections::HashSet};

use lazy_static::lazy_static;
use regex::{Captures, Regex};

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

/// States for cloze parsing state machine
#[derive(PartialEq, Copy, Clone)]
enum State {
    Root,
    Text,
    Open,
    Open2,
    COpen,
    Ord,
    TextOpen1,
    TextOpen2,
    TextClose1,
    HintOpen1,
    HintOpen2,
    Hint,
    HintClose1,
    Close,
    Abandon
}
/// Struct for storing data for one cloze
struct Cloze {
    buf: String,
    ord_str: String,
    text: String,
    hint: String
}

impl Cloze {
    /// Create empty Cloze struct
    fn new() -> Self {
        Self {
            buf: String::new(),
            ord_str: String::new(),
            text: String::new(),
            hint: String::new()
        }
    }
    /// Create Cloze struct with buf set to c
    fn new_from(c: char) -> Self {
        Self {
            buf: String::from(c),
            ord_str: String::new(),
            text: String::new(),
            hint: String::new()
        }
    }
}

/// Process char for next state
fn process_char(state: State, c: char) -> State {
    use self::State::*;
    match (state, c) {
        (Root, '{') => Open,
        (Open, '{') => Open2,
        (Open, _) => Abandon,
        (Open2, '{') => Open2,
        (Open2, 'c') => COpen,
        (Open2, _) => Abandon,
        (COpen, '0'..='9') => Ord,
        (Ord, '0'..='9') => Ord,
        (Ord, ':') => TextOpen1,
        (Ord, _) => Abandon,
        (TextOpen1, ':') => TextOpen2,
        (TextOpen1, _) => Abandon,
        (TextOpen2, ':') => HintOpen1,
        (TextOpen2, '}') => TextClose1,
        (TextOpen2, _) => Text,
        (Text, '{') => Open,
        (Text, ':') => HintOpen1,
        (Text, '}') => TextClose1,
        (Text, _) => Text,
        (HintOpen1, ':') => HintOpen2,
        (HintOpen1, '}') => TextClose1,
        (HintOpen1, _) => Text,
        (HintOpen2, '}') => HintClose1,
        (HintOpen2, _) => Hint,
        (Hint, '}') => HintClose1,
        (Hint, _) => Hint,
        (TextClose1 | HintClose1, '}') => Close,
        (TextClose1, _) => Text,
        (HintClose1, _) => Hint,
        _ => Root
    }
}

/// Minimal encoding of string for storage in attribute (", &, \n, <, >)
pub fn encode_attribute(text: &str) -> Cow<str> {
    let mut out = String::new();
    for c in text.chars() {
        match c {
            '"' => out.push_str("&quot;"),
            '&' => out.push_str("&amp;"),
            '\n' => out.push_str("&NewLine;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            _ => out.push(c)
        }
    }
    
    Cow::from(out)
}

/// Parse string for clozes and return:
/// cloze_only == false: resulting HTML string
/// cloze_only == true: resulting HTML for the cloze texts only
/// no cloze found: empty string
fn reveal_clozes(text: &str, cloze_ord: u16, question: bool, cloze_only: bool) -> Cow<str> {
    let mut state = State::Root;
    let mut stack: Vec<Cloze> = vec![Cloze::new()];
    let mut current_found = false;

    for c in text.chars() {
        let old_state = state;
        state = process_char(state, c);

        match state {
            State::Open => stack.push(Cloze::new_from(c)),
            State::Close => current_found |= close(&mut state, &mut stack, cloze_only, cloze_ord, question),
            State::Abandon => abandon(&mut state, &mut stack, c),
            _ => {
                let last = stack.last_mut().unwrap();
                last.buf.push(c);
                match state {
                    State::Root => if !cloze_only {last.text.push(c)},
                    State::Open2 => if old_state == State::Open2 {shift_open(&mut stack)},
                    State::Ord => last.ord_str.push(c),
                    State::Text => last.text.push(c),
                    State::Hint => last.hint.push(c),
                    _ => {}
                }
            }
        }
    }
    
    if !current_found { return Cow::Borrowed("") }
    if cloze_only { return stack.last().unwrap().text[2..].to_owned().into() }

    if stack.len() > 1 {
        let cloze = stack.pop().unwrap();
        stack.last_mut().unwrap().text.push_str(&cloze.buf);
    }
    return Cow::from(stack.last().unwrap().text.to_owned());

    // 3 consecutive {{{, shift the first onto the "parent" text
    fn shift_open(stack: &mut Vec<Cloze>) {
        let i = stack.len() - 1;
        let fc = stack[i].buf.remove(0);
        stack[i - 1].buf.push(fc);
    }
    
    // Close cloze and set state
    fn close(state: &mut State, stack: &mut Vec<Cloze>,
            cloze_only: bool, cloze_ord: u16, question: bool) -> bool {
        let cloze = stack.pop().unwrap();
        *state = if stack.len() > 1 { State::Text } else { State::Root };

        let ordinal = cloze.ord_str.parse::<u16>().unwrap();
        let last = stack.last_mut().unwrap();

        match (cloze_only, question, ordinal == cloze_ord, cloze.hint.is_empty()) {
            // Cloze text only
            (true, true, true, true) => last.text.push_str(", ..."),
            (true, true, true, false) => last.text.push_str(&format!(", {}", &cloze.hint)),
            (true, _, _, _) =>  last.text.push_str(&format!(", {}", &cloze.text)),

            // Full cloze
            // Question - active cloze, no hint
            (false, true, true, true) => last.text.push_str(
                &format!(r#"<span class="cloze active" data-text="{}" data-ordinal="{}">[...]</span>"#,
                encode_attribute(cloze.text.as_str()), ordinal)),
            // Question - active cloze, hint
            (false, true, true, false) => last.text.push_str(
                &format!(r#"<span class="cloze active" data-text="{}" data-ordinal="{}">[{}]</span>"#,
                encode_attribute(cloze.text.as_str()), ordinal, &cloze.hint)),
            // Question - inactive cloze
            (false, true, false, _) => last.text.push_str(
                &format!(r#"<span class="cloze" data-ordinal="{}">{}</span>"#,
                ordinal, cloze.text.as_str())),
            // Answer - active cloze
            (false, false, true, _) => last.text.push_str(
                &format!(r#"<span class="cloze active" data-ordinal="{}">{}</span>"#,
                ordinal, cloze.text.as_str())),
            // Answer - inactive cloze
            (false, false, false, _) => last.text.push_str(
                &format!(r#"<span class="cloze" data-ordinal="{}">{}</span>"#,
                ordinal, cloze.text.as_str()))
        }

        ordinal == cloze_ord
    }

    // Abandon cloze and set state
    fn abandon(state: &mut State, stack: &mut Vec<Cloze>, c: char) {
        let cloze = stack.pop().unwrap();
        *state = if stack.len() > 1 { State::Text } else { State::Root };
        let last = stack.last_mut().unwrap();
        last.text.push_str(&cloze.buf);
        last.text.push(c);
    }
}

pub fn reveal_cloze_text(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    reveal_clozes(text, cloze_ord, question, false)
}

pub fn reveal_cloze_text_only(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    reveal_clozes(text, cloze_ord, question, true)
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
    let mut state = State::Root;
    let mut stack: Vec<Cloze> = vec![Cloze::new()];
    let mut i = 0;

    for c in text.chars() {
        state = process_char(state, c);
        match state {
            State::Root => stack[i].text.push(c),
            State::Open => i += 1,
            State::Close => return true,
            State::Abandon => state = if i > 0 { State::Text } else { State::Root },
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
    let mut state = State::Root;
    let mut stack: Vec<String> = vec![];

    for c in field.chars() {
        state = process_char(state, c);
        match state {
            State::Open => stack.push(String::new()),
            State::Ord => stack.last_mut().unwrap().push(c),
            State::Close => _ = set.insert(close(&mut state, &mut stack)),
            State::Abandon => abandon(&mut state, &mut stack),
            _ => {}
        }
    }

    // Close cloze and set state, return ordinal
    fn close(state: &mut State, stack: &mut Vec<String>) -> u16 {
        let ord_str = stack.pop().unwrap();
        *state = if stack.len() > 0 { State::Text } else { State::Root }; 
        ord_str.parse::<u16>().unwrap()
    }
    // Abandon cloze and set state
    fn abandon(state: &mut State, stack: &mut Vec<String>) {
        stack.pop();
        *state = if stack.is_empty() { State::Root } else { State::Text };
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
            reveal_cloze_text("foo {{c1::bar &lt;{{c2::baz}}}}", 1, true),
            r#"foo <span class="cloze active" data-text="bar &amp;lt;&lt;span class=&quot;cloze&quot; data-ordinal=&quot;2&quot;&gt;baz&lt;/span&gt;" data-ordinal="1">[...]</span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar &lt;{{c2::baz}}}}", 1, false),
            r#"foo <span class="cloze active" data-ordinal="1">bar &lt;<span class="cloze" data-ordinal="2">baz</span></span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 2, true),
            r#"foo <span class="cloze" data-ordinal="1">bar <span class="cloze active" data-text="baz" data-ordinal="2">[...]</span></span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 2, false),
            r#"foo <span class="cloze" data-ordinal="1">bar <span class="cloze active" data-ordinal="2">baz</span></span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 1, true),
            r#"foo <span class="cloze active" data-text="bar &lt;span class=&quot;cloze&quot; data-ordinal=&quot;2&quot;&gt;baz&lt;/span&gt;" data-ordinal="1">[qux]</span>"#
        );
        assert_eq!(
            reveal_cloze_text("foo {{c1::bar {{c2::baz}}::qux}}", 1, false),
            r#"foo <span class="cloze active" data-ordinal="1">bar <span class="cloze" data-ordinal="2">baz</span></span>"#
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
