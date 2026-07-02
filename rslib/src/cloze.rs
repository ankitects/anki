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
use itertools::Itertools;
use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::bytes::complete::take_while;
use nom::combinator::map;
use nom::IResult;
use nom::Parser;
use regex::Captures;
use regex::Regex;

use crate::image_occlusion::imageocclusion::get_image_cloze_data;
use crate::image_occlusion::imageocclusion::parse_image_cloze;
use crate::latex::contains_latex;
use crate::template::RenderContext;
use crate::text::strip_html_preserving_entities;

static CLOZE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?s)\{\{c[\d,]+::(.*?)(::.*?)?\}\}").unwrap());

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
    OpenCloze(Vec<u16>),
    Text(&'a str),
    CloseCloze,
}

/// Tokenize string
fn tokenize(mut text: &str) -> impl Iterator<Item = Token<'_>> {
    fn open_cloze(text: &str) -> IResult<&str, Token<'_>> {
        // opening brackets and 'c'
        let (text, _opening_brackets_and_c) = tag("{{c")(text)?;
        // following comma-seperated numbers
        let (text, ordinals) = take_while(|c: char| c.is_ascii_digit() || c == ',')(text)?;
        let ordinals: Vec<u16> = ordinals
            .split(',')
            .filter_map(|s| s.parse().ok())
            .collect::<HashSet<_>>() // deduplicate
            .into_iter()
            .sorted() // set conversion can de-order
            .collect();
        if ordinals.is_empty() {
            return Err(nom::Err::Error(nom::error::make_error(
                text,
                nom::error::ErrorKind::Digit,
            )));
        }
        // ::
        let (text, _colons) = tag("::")(text)?;
        Ok((text, Token::OpenCloze(ordinals)))
    }

    fn close_cloze(text: &str) -> IResult<&str, Token<'_>> {
        map(tag("}}"), |_| Token::CloseCloze).parse(text)
    }

    /// Match a run of text until an open/close marker is encountered.
    fn normal_text(text: &str) -> IResult<&str, Token<'_>> {
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
            if other_token.parse(&text[idx..]).is_ok() {
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
            let (remaining_text, token) = alt((open_cloze, close_cloze, normal_text))
                .parse(text)
                .unwrap();
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
    ordinals: Vec<u16>,
    nodes: Vec<TextOrCloze<'a>>,
    hint: Option<&'a str>,
}

/// Generate a string representation of the ordinals for HTML
fn ordinals_str(ordinals: &[u16]) -> String {
    ordinals
        .iter()
        .map(|o| o.to_string())
        .collect::<Vec<_>>()
        .join(",")
}

impl ExtractedCloze<'_> {
    /// Return the cloze's hint, or "..." if none was provided.
    fn hint(&self) -> &str {
        self.hint.unwrap_or("...")
    }

    fn clozed_text(&self) -> Cow<'_, str> {
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

    /// Checks if this cloze is active for a given ordinal
    fn contains_ordinal(&self, ordinal: u16) -> bool {
        self.ordinals.contains(&ordinal)
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
            Token::OpenCloze(ordinals) => {
                if open_clozes.len() < 10 {
                    open_clozes.push(ExtractedCloze {
                        ordinals,
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
        if cloze.contains_ordinal(cloze_ord) {
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
    let active = cloze.contains_ordinal(cloze_ord);
    *active_cloze_found_in_text |= active;

    if let Some(image_occlusion_text) = cloze.image_occlusion() {
        buf.push_str(&render_image_occlusion(
            image_occlusion_text,
            question,
            active,
            &cloze.ordinals,
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
                ordinals_str(&cloze.ordinals),
                cloze.hint()
            )
            .unwrap();
        }
        (false, true) => {
            write!(
                buf,
                r#"<span class="cloze" data-ordinal="{}">"#,
                ordinals_str(&cloze.ordinals)
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
                ordinals_str(&cloze.ordinals)
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

fn render_image_occlusion(
    text: &str,
    question_side: bool,
    active: bool,
    ordinals: &[u16],
) -> String {
    if (question_side && active) || ordinals.contains(&0) {
        format!(
            r#"<div class="cloze" data-ordinal="{}" {}></div>"#,
            ordinals_str(ordinals),
            &get_image_cloze_data(text)
        )
    } else if !active {
        format!(
            r#"<div class="cloze-inactive" data-ordinal="{}" {}></div>"#,
            ordinals_str(ordinals),
            &get_image_cloze_data(text)
        )
    } else if !question_side && active {
        format!(
            r#"<div class="cloze-highlight" data-ordinal="{}" {}></div>"#,
            ordinals_str(ordinals),
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
                    // Associate this occlusion with all ordinals in this cloze
                    for &ordinal in &cloze.ordinals {
                        occlusions.entry(ordinal).or_default().push(shape.clone());
                    }
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

pub fn reveal_cloze_text(text: &str, cloze_ord: u16, question: bool) -> Cow<'_, str> {
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

pub fn reveal_cloze_text_only(text: &str, cloze_ord: u16, question: bool) -> Cow<'_, str> {
    let mut output = Vec::new();
    for node in &parse_text_with_clozes(text) {
        reveal_cloze_text_in_nodes(node, cloze_ord, question, &mut output);
    }
    output.join(", ").into()
}

pub fn extract_cloze_for_typing(text: &str, cloze_ord: u16) -> Cow<'_, str> {
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

// Whether `text` contains any cloze number above 0
pub(crate) fn contains_cloze(text: &str) -> bool {
    parse_text_with_clozes(text)
        .iter()
        .any(|node| matches!(node, TextOrCloze::Cloze(e) if e.ordinals.iter().any(|&o| o != 0)))
}

/// Returns the set of cloze number as they appear in the fields's content.
pub fn cloze_numbers_in_string(html: &str) -> HashSet<u16> {
    let mut set = HashSet::with_capacity(4);
    add_cloze_numbers_in_string(html, &mut set);
    set
}

fn add_cloze_numbers_in_text_with_clozes(nodes: &[TextOrCloze], set: &mut HashSet<u16>) {
    for node in nodes {
        if let TextOrCloze::Cloze(cloze) = node {
            for &ordinal in &cloze.ordinals {
                if ordinal != 0 {
                    set.insert(ordinal);
                }
            }
            add_cloze_numbers_in_text_with_clozes(&cloze.nodes, set);
        }
    }
}

/// Add to `set` the cloze numbers as they appear in `field`.
#[allow(clippy::implicit_hasher)]
pub fn add_cloze_numbers_in_string(field: &str, set: &mut HashSet<u16>) {
    add_cloze_numbers_in_text_with_clozes(&parse_text_with_clozes(field), set)
}

/// The set of cloze numbers as they appear in any of the fields from `fields`.
pub fn cloze_number_in_fields(fields: impl IntoIterator<Item: AsRef<str>>) -> HashSet<u16> {
    let mut set = HashSet::with_capacity(4);
    for field in fields {
        add_cloze_numbers_in_string(field.as_ref(), &mut set);
    }
    set
}

pub(crate) fn strip_clozes(text: &str) -> Cow<'_, str> {
    CLOZE.replace_all(text, "$1")
}

fn strip_html_inside_mathjax(text: &str) -> Cow<'_, str> {
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
    fn strip_clozes_regex() {
        assert_eq!(
            strip_clozes(
                r#"The {{c1::moon::🌛}} {{c2::orbits::this hint has "::" in it}} the {{c3::🌏}}."#
            ),
            "The moon orbits the 🌏."
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

    #[test]
    fn multi_card_card_generation() {
        let text = "{{c1,2,3::multi}}";
        assert_eq!(
            cloze_number_in_fields(vec![text]),
            vec![1, 2, 3].into_iter().collect::<HashSet<u16>>()
        );
    }

    #[test]
    fn multi_card_cloze_basic() {
        let text = "{{c1,2::shared}} word and {{c1::first}} vs {{c2::second}}";

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 1, true)).as_ref(),
            "[...] word and [...] vs second"
        );
        assert_eq!(
            strip_html(&reveal_cloze_text(text, 2, true)).as_ref(),
            "[...] word and first vs [...]"
        );
        assert_eq!(
            strip_html(&reveal_cloze_text(text, 1, false)).as_ref(),
            "shared word and first vs second"
        );
        assert_eq!(
            strip_html(&reveal_cloze_text(text, 2, false)).as_ref(),
            "shared word and first vs second"
        );
        assert_eq!(
            cloze_numbers_in_string(text),
            vec![1, 2].into_iter().collect::<HashSet<u16>>()
        );
    }

    #[test]
    fn multi_card_cloze_html_attributes() {
        let text = "{{c1,2,3::multi}}";

        let card1_html = reveal_cloze_text(text, 1, true);
        assert!(card1_html.contains(r#"data-ordinal="1,2,3""#));

        let card2_html = reveal_cloze_text(text, 2, true);
        assert!(card2_html.contains(r#"data-ordinal="1,2,3""#));

        let card3_html = reveal_cloze_text(text, 3, true);
        assert!(card3_html.contains(r#"data-ordinal="1,2,3""#));
    }

    #[test]
    fn multi_card_cloze_with_hints() {
        let text = "{{c1,2::answer::hint}}";

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 1, true)).as_ref(),
            "[hint]"
        );
        assert_eq!(
            strip_html(&reveal_cloze_text(text, 2, true)).as_ref(),
            "[hint]"
        );

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 1, false)).as_ref(),
            "answer"
        );
        assert_eq!(
            strip_html(&reveal_cloze_text(text, 2, false)).as_ref(),
            "answer"
        );
    }

    #[test]
    fn multi_card_cloze_edge_cases() {
        assert_eq!(
            cloze_numbers_in_string("{{c1,1,2::test}}"),
            vec![1, 2].into_iter().collect::<HashSet<u16>>()
        );

        assert_eq!(
            cloze_numbers_in_string("{{c0,1,2::test}}"),
            vec![1, 2].into_iter().collect::<HashSet<u16>>()
        );

        assert_eq!(
            cloze_numbers_in_string("{{c1,,3::test}}"),
            vec![1, 3].into_iter().collect::<HashSet<u16>>()
        );
    }

    #[test]
    fn multi_card_cloze_only_filter() {
        let text = "{{c1,2::shared}} and {{c1::first}} vs {{c2::second}}";

        assert_eq!(reveal_cloze_text_only(text, 1, true), "..., ...");
        assert_eq!(reveal_cloze_text_only(text, 2, true), "..., ...");
        assert_eq!(reveal_cloze_text_only(text, 1, false), "shared, first");
        assert_eq!(reveal_cloze_text_only(text, 2, false), "shared, second");
    }

    #[test]
    fn multi_card_nested_cloze() {
        let text = "{{c1,2::outer {{c3::inner}}}}";

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 1, true)).as_ref(),
            "[...]"
        );

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 2, true)).as_ref(),
            "[...]"
        );

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 3, true)).as_ref(),
            "outer [...]"
        );

        assert_eq!(
            cloze_numbers_in_string(text),
            vec![1, 2, 3].into_iter().collect::<HashSet<u16>>()
        );
    }

    #[test]
    fn nested_parent_child_card_same_cloze() {
        let text = "{{c1::outer {{c1::inner}}}}";

        assert_eq!(
            strip_html(&reveal_cloze_text(text, 1, true)).as_ref(),
            "[...]"
        );

        assert_eq!(
            cloze_numbers_in_string(text),
            vec![1].into_iter().collect::<HashSet<u16>>()
        );
    }

    #[test]
    fn multi_card_image_occlusion() {
        let text = "{{c1,2::image-occlusion:rect:left=10:top=20:width=30:height=40}}";

        let occlusions = parse_image_occlusions(text);
        assert_eq!(occlusions.len(), 2);
        assert!(occlusions.iter().any(|o| o.ordinal == 1));
        assert!(occlusions.iter().any(|o| o.ordinal == 2));

        let card1_html = reveal_cloze_text(text, 1, true);
        assert!(card1_html.contains(r#"data-ordinal="1,2""#));

        let card2_html = reveal_cloze_text(text, 2, true);
        assert!(card2_html.contains(r#"data-ordinal="1,2""#));
    }

    #[test]
    fn image_occlusion_modes() {
        // Mode 1 (HideAll): should include data-occludeinactive="1"
        let hide_all = "{{c1::image-occlusion:rect:left=10:top=20:width=30:height=40:oi=1}}";
        let html = reveal_cloze_text(hide_all, 1, true);
        assert!(html.contains(r#"data-occludeInactive="1""#));

        // Mode 2 (HideAllButOne): should include data-occludeinactive="2"
        let hide_all_but_one = "{{c1::image-occlusion:rect:left=10:top=20:width=30:height=40:oi=2}}";
        let html = reveal_cloze_text(hide_all_but_one, 1, true);
        assert!(html.contains(r#"data-occludeInactive="2""#));

        // Mode 0 (HideOne): should not include data-occludeinactive attribute
        let hide_one = "{{c1::image-occlusion:rect:left=10:top=20:width=30:height=40}}";
        let html = reveal_cloze_text(hide_one, 1, true);
        assert!(!html.contains("data-occludeInactive"));
    }
}
