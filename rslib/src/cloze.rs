// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fmt::Write;
use std::sync::LazyLock;

use anki_proto::image_occlusion::get_image_occlusion_note_response::ImageOcclusion;
use anki_proto::image_occlusion::get_image_occlusion_note_response::ImageOcclusionShape;
use htmlescape::encode_attribute;
use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::bytes::complete::take_while;
use nom::combinator::map;
use nom::IResult;
use regex::Captures;
use regex::Regex;

use crate::image_occlusion::imageocclusion::get_image_cloze_data;
use crate::image_occlusion::imageocclusion::parse_image_cloze;
use crate::latex::contains_latex;
use crate::template::RenderContext;
use crate::text::strip_html_preserving_entities;

static MATHJAX: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?xsi)
            (\\[(\[])       # 1 = mathjax opening tag
            (.*?)           # 2 = inner content
            (\\[])])        # 3 = mathjax closing tag
           ",
    )
    .unwrap()
});

mod mathjax_caps {
    pub const OPENING_TAG: usize = 1;
    pub const INNER_TEXT: usize = 2;
    pub const CLOSING_TAG: usize = 3;
}

#[derive(Debug)]
enum Token<'a> {
    // The parameter is the cloze number as is appears in the field content.
    OpenCloze(u16),
    Text(&'a str),
    CloseCloze,
}

/// Tokenize string
fn tokenize(mut text: &str) -> impl Iterator<Item = Token> {
    fn open_cloze(text: &str) -> IResult<&str, Token> {
        // opening brackets and 'c'
        let (text, _opening_brackets_and_c) = tag("{{c")(text)?;
        // following number
        let (text, digits) = take_while(|c: char| c.is_ascii_digit())(text)?;
        let digits: u16 = match digits.parse() {
            Ok(digits) => digits,
            Err(_) => {
                // not a valid number; fail to recognize
                return Err(nom::Err::Error(nom::error::make_error(
                    text,
                    nom::error::ErrorKind::Digit,
                )));
            }
        };
        // ::
        let (text, _colons) = tag("::")(text)?;
        Ok((text, Token::OpenCloze(digits)))
    }

    fn close_cloze(text: &str) -> IResult<&str, Token> {
        map(tag("}}"), |_| Token::CloseCloze)(text)
    }

    /// Match a run of text until an open/close marker is encountered.
    fn normal_text(text: &str) -> IResult<&str, Token> {
        if text.is_empty() {
            return Err(nom::Err::Error(nom::error::make_error(
                text,
                nom::error::ErrorKind::Eof,
            )));
        }
        let mut other_token = alt((open_cloze, close_cloze));
        // start with the no-match case
        let mut index = text.len();
        for (idx, _) in text.char_indices() {
            if other_token(&text[idx..]).is_ok() {
                index = idx;
                break;
            }
        }
        Ok((&text[index..], Token::Text(&text[0..index])))
    }

    std::iter::from_fn(move || {
        if text.is_empty() {
            None
        } else {
            let (remaining_text, token) =
                alt((open_cloze, close_cloze, normal_text))(text).unwrap();
            text = remaining_text;
            Some(token)
        }
    })
}

#[derive(Debug)]
enum TextOrCloze<'a> {
    Text(&'a str),
    Cloze(ExtractedCloze<'a>),
}

#[derive(Debug)]
struct ExtractedCloze<'a> {
    // `ordinal` is the cloze number as is appears in the field content.
    ordinal: u16,
    nodes: Vec<TextOrCloze<'a>>,
    hint: Option<&'a str>,
}

impl ExtractedCloze<'_> {
    /// Return the cloze's hint, or "..." if none was provided.
    fn hint(&self) -> &str {
        self.hint.unwrap_or("...")
    }

    fn clozed_text(&self) -> Cow<str> {
        // happy efficient path?
        if self.nodes.len() == 1 {
            if let TextOrCloze::Text(text) = self.nodes.last().unwrap() {
                return (*text).into();
            }
        }

        let mut buf = String::new();
        for node in &self.nodes {
            match node {
                TextOrCloze::Text(text) => buf.push_str(text),
                TextOrCloze::Cloze(cloze) => buf.push_str(&cloze.clozed_text()),
            }
        }

        buf.into()
    }

    /// If cloze starts with image-occlusion:, return the text following that.
    fn image_occlusion(&self) -> Option<&str> {
        let TextOrCloze::Text(text) = self.nodes.first()? else {
            return None;
        };
        text.strip_prefix("image-occlusion:")
    }
}

fn parse_text_with_clozes(text: &str) -> Vec<TextOrCloze<'_>> {
    let mut open_clozes: Vec<ExtractedCloze> = vec![];
    let mut output = vec![];
    for token in tokenize(text) {
        match token {
            Token::OpenCloze(ordinal) => {
                if open_clozes.len() < 10 {
                    open_clozes.push(ExtractedCloze {
                        ordinal,
                        nodes: Vec::with_capacity(1), // common case
                        hint: None,
                    })
                }
            }
            Token::Text(mut text) => {
                if let Some(cloze) = open_clozes.last_mut() {
                    // extract hint if found
                    if let Some((head, tail)) = text.split_once("::") {
                        text = head;
                        cloze.hint = Some(tail);
                    }
                    cloze.nodes.push(TextOrCloze::Text(text));
                } else {
                    output.push(TextOrCloze::Text(text));
                }
            }
            Token::CloseCloze => {
                // take the currently active cloze
                if let Some(cloze) = open_clozes.pop() {
                    let target = if let Some(outer_cloze) = open_clozes.last_mut() {
                        // and place it into the cloze layer above
                        &mut outer_cloze.nodes
                    } else {
                        // or the top level if no other clozes active
                        &mut output
                    };
                    target.push(TextOrCloze::Cloze(cloze));
                } else {
                    // closing marker outside of any clozes
                    output.push(TextOrCloze::Text("}}"))
                }
            }
        }
    }
    output
}

fn reveal_cloze_text_in_nodes(
    node: &TextOrCloze,
    cloze_ord: u16,
    question: bool,
    output: &mut Vec<String>,
) {
    if let TextOrCloze::Cloze(cloze) = node {
        if cloze.ordinal == cloze_ord {
            if question {
                output.push(cloze.hint().into())
            } else {
                output.push(cloze.clozed_text().into())
            }
        }
        for node in &cloze.nodes {
            reveal_cloze_text_in_nodes(node, cloze_ord, question, output);
        }
    }
}

fn reveal_cloze(
    cloze: &ExtractedCloze,
    cloze_ord: u16,
    question: bool,
    active_cloze_found_in_text: &mut bool,
    buf: &mut String,
) {
    let active = cloze.ordinal == cloze_ord;
    *active_cloze_found_in_text |= active;
    if let Some(image_occlusion_text) = cloze.image_occlusion() {
        buf.push_str(&render_image_occlusion(
            image_occlusion_text,
            question,
            active,
            cloze.ordinal,
        ));
        return;
    }
    match (question, active) {
        (true, true) => {
            // question side with active cloze; all inner content is elided
            let mut content_buf = String::new();
            for node in &cloze.nodes {
                match node {
                    TextOrCloze::Text(text) => content_buf.push_str(text),
                    TextOrCloze::Cloze(cloze) => reveal_cloze(
                        cloze,
                        cloze_ord,
                        question,
                        active_cloze_found_in_text,
                        &mut content_buf,
                    ),
                }
            }
            write!(
                buf,
                r#"<span class="cloze" data-cloze="{}" data-ordinal="{}">[{}]</span>"#,
                encode_attribute(&content_buf),
                cloze.ordinal,
                cloze.hint()
            )
            .unwrap();
        }
        (false, true) => {
            write!(
                buf,
                r#"<span class="cloze" data-ordinal="{}">"#,
                cloze.ordinal
            )
            .unwrap();
            for node in &cloze.nodes {
                match node {
                    TextOrCloze::Text(text) => buf.push_str(text),
                    TextOrCloze::Cloze(cloze) => {
                        reveal_cloze(cloze, cloze_ord, question, active_cloze_found_in_text, buf)
                    }
                }
            }
            buf.push_str("</span>");
        }
        (_, false) => {
            // question or answer side inactive cloze; text shown, children may be active
            write!(
                buf,
                r#"<span class="cloze-inactive" data-ordinal="{}">"#,
                cloze.ordinal
            )
            .unwrap();
            for node in &cloze.nodes {
                match node {
                    TextOrCloze::Text(text) => buf.push_str(text),
                    TextOrCloze::Cloze(cloze) => {
                        reveal_cloze(cloze, cloze_ord, question, active_cloze_found_in_text, buf)
                    }
                }
            }
            buf.push_str("</span>")
        }
    }
}

fn render_image_occlusion(text: &str, question_side: bool, active: bool, ordinal: u16) -> String {
    if (question_side && active) || ordinal == 0 {
        format!(
            r#"<div class="cloze" data-ordinal="{}" {}></div>"#,
            ordinal,
            &get_image_cloze_data(text)
        )
    } else if !active {
        format!(
            r#"<div class="cloze-inactive" data-ordinal="{}" {}></div>"#,
            ordinal,
            &get_image_cloze_data(text)
        )
    } else if !question_side && active {
        format!(
            r#"<div class="cloze-highlight" data-ordinal="{}" {}></div>"#,
            ordinal,
            &get_image_cloze_data(text)
        )
    } else {
        "".into()
    }
}

pub fn parse_image_occlusions(text: &str) -> Vec<ImageOcclusion> {
    let mut occlusions: HashMap<u16, Vec<ImageOcclusionShape>> = HashMap::new();
    for node in parse_text_with_clozes(text) {
        if let TextOrCloze::Cloze(cloze) = node {
            if cloze.image_occlusion().is_some() {
                if let Some(shape) = parse_image_cloze(cloze.image_occlusion().unwrap()) {
                    occlusions.entry(cloze.ordinal).or_default().push(shape);
                }
            }
        }
    }

    occlusions
        .iter()
        .map(|(k, v)| ImageOcclusion {
            ordinal: *k as u32,
            shapes: v.to_vec(),
        })
        .collect()
}

pub fn reveal_cloze_text(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    let mut buf = String::new();
    let mut active_cloze_found_in_text = false;
    for node in &parse_text_with_clozes(text) {
        match node {
            // top-level text is indiscriminately added
            TextOrCloze::Text(text) => buf.push_str(text),
            TextOrCloze::Cloze(cloze) => reveal_cloze(
                cloze,
                cloze_ord,
                question,
                &mut active_cloze_found_in_text,
                &mut buf,
            ),
        }
    }
    if active_cloze_found_in_text {
        buf.into()
    } else {
        Cow::from("")
    }
}

pub fn reveal_cloze_text_only(text: &str, cloze_ord: u16, question: bool) -> Cow<str> {
    let mut output = Vec::new();
    for node in &parse_text_with_clozes(text) {
        reveal_cloze_text_in_nodes(node, cloze_ord, question, &mut output);
    }
    output.join(", ").into()
}

pub fn extract_cloze_for_typing(text: &str, cloze_ord: u16) -> Cow<str> {
    let mut output = Vec::new();
    for node in &parse_text_with_clozes(text) {
        reveal_cloze_text_in_nodes(node, cloze_ord, false, &mut output);
    }
    if output.is_empty() {
        "".into()
    } else if output.iter().min() == output.iter().max() {
        // If all matches are identical text, they get collapsed into a single entry
        output.pop().unwrap().into()
    } else {
        output.join(", ").into()
    }
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

// Whether `text` contains any cloze with strictly positive number.
pub(crate) fn contains_cloze(text: &str) -> bool {
    parse_text_with_clozes(text)
        .iter()
        .any(|node| matches!(node, TextOrCloze::Cloze(e) if e.ordinal != 0))
}

/// Returns the set of cloze number as the appear in the fields's content.
pub fn cloze_numbers_in_string(html: &str) -> HashSet<u16> {
    let mut set = HashSet::with_capacity(4);
    add_cloze_numbers_in_string(html, &mut set);
    set
}

fn add_cloze_numbers_in_text_with_clozes(nodes: &[TextOrCloze], set: &mut HashSet<u16>) {
    for node in nodes {
        if let TextOrCloze::Cloze(cloze) = node {
            if cloze.ordinal != 0 {
                set.insert(cloze.ordinal);
                add_cloze_numbers_in_text_with_clozes(&cloze.nodes, set);
            }
        }
    }
}

/// Add to `set` the cloze numbers as they appear in `field`.
#[allow(clippy::implicit_hasher)]
pub fn add_cloze_numbers_in_string(field: &str, set: &mut HashSet<u16>) {
    add_cloze_numbers_in_text_with_clozes(&parse_text_with_clozes(field), set)
}

/// The set of cloze numbers as they appear in any of the field from `fields`.
pub fn cloze_number_in_fields(fields: impl IntoIterator<Item: AsRef<str>>) -> HashSet<u16> {
    let mut set = HashSet::with_capacity(4);
    for field in fields {
        add_cloze_numbers_in_string(field.as_ref(), &mut set);
    }
    set
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
        reveal_cloze_text(text, context.card_ord + 1, context.frontside.is_none()).as_ref(),
    )
    .into_owned()
    .into()
}

pub(crate) fn cloze_only_filter<'a>(text: &'a str, context: &RenderContext) -> Cow<'a, str> {
    reveal_cloze_text_only(text, context.card_ord + 1, context.frontside.is_none())
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
            cloze_numbers_in_string("{{c0::te}}s{{c2::t}}s"),
            vec![2].into_iter().collect::<HashSet<u16>>()
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
    fn clozes_for_typing() {
        assert_eq!(extract_cloze_for_typing("{{c2::foo}}", 1), "");
        assert_eq!(
            extract_cloze_for_typing("{{c1::foo}} {{c1::bar}} {{c1::foo}}", 1),
            "foo, bar, foo"
        );
        assert_eq!(
            extract_cloze_for_typing("{{c1::foo}} {{c1::foo}} {{c1::foo}}", 1),
            "foo"
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

    #[test]
    fn non_latin() {
        assert!(cloze_numbers_in_string("öaöaöööaö").is_empty());
    }

    #[test]
    fn image_cloze() {
        assert_eq!(
            reveal_cloze_text(
                "{{c1::image-occlusion:rect:left=10.0:top=20:width=30:height=10}}",
                1,
                true
            ),
            format!(
                r#"<div class="cloze" data-ordinal="1" data-shape="rect" data-left="10.0" data-top="20" data-width="30" data-height="10" ></div>"#,
            )
        );
    }
}
