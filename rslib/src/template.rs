// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result, TemplateError};
use crate::i18n::{tr_strs, I18n, TR};
use crate::template_filters::apply_filters;
use lazy_static::lazy_static;
use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::error::ErrorKind;
use nom::sequence::delimited;
use regex::Regex;
use std::borrow::Cow;
use std::collections::{HashMap, HashSet};
use std::iter;

pub type FieldMap<'a> = HashMap<&'a str, u16>;
type TemplateResult<T> = std::result::Result<T, TemplateError>;

static TEMPLATE_ERROR_LINK: &str =
    "https://anki.tenderapp.com/kb/problems/card-template-has-a-problem";
static TEMPLATE_BLANK_LINK: &str =
    "https://anki.tenderapp.com/kb/card-appearance/the-front-of-this-card-is-blank";

// Lexing
//----------------------------------------

#[derive(Debug)]
pub enum Token<'a> {
    Text(&'a str),
    Replacement(&'a str),
    OpenConditional(&'a str),
    OpenNegated(&'a str),
    CloseConditional(&'a str),
}

/// a span of text, terminated by {{ or end of string
pub(crate) fn text_until_open_handlebars(s: &str) -> nom::IResult<&str, &str> {
    let end = s.len();

    let limited_end = end.min(s.find("{{").unwrap_or(end));
    let (output, input) = s.split_at(limited_end);
    if output.is_empty() {
        Err(nom::Err::Error((input, ErrorKind::TakeUntil)))
    } else {
        Ok((input, output))
    }
}

/// a span of text, terminated by }} or end of string
pub(crate) fn text_until_close_handlebars(s: &str) -> nom::IResult<&str, &str> {
    let end = s.len();

    let limited_end = end.min(s.find("}}").unwrap_or(end));
    let (output, input) = s.split_at(limited_end);
    if output.is_empty() {
        Err(nom::Err::Error((input, ErrorKind::TakeUntil)))
    } else {
        Ok((input, output))
    }
}

/// text outside handlebars
fn text_token(s: &str) -> nom::IResult<&str, Token> {
    text_until_open_handlebars(s).map(|(input, output)| (input, Token::Text(output)))
}

/// text wrapped in handlebars
fn handle_token(s: &str) -> nom::IResult<&str, Token> {
    delimited(tag("{{"), text_until_close_handlebars, tag("}}"))(s)
        .map(|(input, output)| (input, classify_handle(output)))
}

/// classify handle based on leading character
fn classify_handle(s: &str) -> Token {
    let start = s.trim_start_matches('{').trim();
    if start.len() < 2 {
        return Token::Replacement(start);
    }
    if start.starts_with('#') {
        Token::OpenConditional(&start[1..].trim_start())
    } else if start.starts_with('/') {
        Token::CloseConditional(&start[1..].trim_start())
    } else if start.starts_with('^') {
        Token::OpenNegated(&start[1..].trim_start())
    } else {
        Token::Replacement(start)
    }
}

fn next_token(input: &str) -> nom::IResult<&str, Token> {
    alt((handle_token, text_token))(input)
}

fn tokens(template: &str) -> impl Iterator<Item = TemplateResult<Token>> {
    let mut data = template;

    std::iter::from_fn(move || {
        if data.is_empty() {
            return None;
        }
        match next_token(data) {
            Ok((i, o)) => {
                data = i;
                Some(Ok(o))
            }
            Err(_e) => Some(Err(TemplateError::NoClosingBrackets(data.to_string()))),
        }
    })
}

// Parsing
//----------------------------------------

#[derive(Debug, PartialEq)]
enum ParsedNode<'a> {
    Text(&'a str),
    Replacement {
        key: &'a str,
        filters: Vec<&'a str>,
    },
    Conditional {
        key: &'a str,
        children: Vec<ParsedNode<'a>>,
    },
    NegatedConditional {
        key: &'a str,
        children: Vec<ParsedNode<'a>>,
    },
}

#[derive(Debug)]
pub struct ParsedTemplate<'a>(Vec<ParsedNode<'a>>);

impl ParsedTemplate<'_> {
    /// Create a template from the provided text.
    ///
    /// The legacy alternate syntax is not supported, so the provided text
    /// should be run through without_legacy_template_directives() first.
    pub fn from_text(template: &str) -> TemplateResult<ParsedTemplate> {
        let mut iter = tokens(template);
        Ok(Self(parse_inner(&mut iter, None)?))
    }
}

fn parse_inner<'a, I: Iterator<Item = TemplateResult<Token<'a>>>>(
    iter: &mut I,
    open_tag: Option<&'a str>,
) -> TemplateResult<Vec<ParsedNode<'a>>> {
    let mut nodes = vec![];

    while let Some(token) = iter.next() {
        use Token::*;
        nodes.push(match token? {
            Text(t) => ParsedNode::Text(t),
            Replacement(t) => {
                let mut it = t.rsplit(':');
                ParsedNode::Replacement {
                    key: it.next().unwrap(),
                    filters: it.collect(),
                }
            }
            OpenConditional(t) => ParsedNode::Conditional {
                key: t,
                children: parse_inner(iter, Some(t))?,
            },
            OpenNegated(t) => ParsedNode::NegatedConditional {
                key: t,
                children: parse_inner(iter, Some(t))?,
            },
            CloseConditional(t) => {
                let currently_open = if let Some(open) = open_tag {
                    if open == t {
                        // matching closing tag, move back to parent
                        return Ok(nodes);
                    } else {
                        Some(open.to_string())
                    }
                } else {
                    None
                };
                return Err(TemplateError::ConditionalNotOpen {
                    closed: t.to_string(),
                    currently_open,
                });
            }
        });
    }

    if let Some(open) = open_tag {
        Err(TemplateError::ConditionalNotClosed(open.to_string()))
    } else {
        Ok(nodes)
    }
}

fn template_error_to_anki_error(err: TemplateError, q_side: bool, i18n: &I18n) -> AnkiError {
    let header = i18n.tr(if q_side {
        TR::CardTemplateRenderingFrontSideProblem
    } else {
        TR::CardTemplateRenderingBackSideProblem
    });
    let details = localized_template_error(i18n, err);
    let more_info = i18n.tr(TR::CardTemplateRenderingMoreInfo);
    let info = format!(
        "{}<br>{}<br><a href='{}'>{}</a>",
        header, details, TEMPLATE_ERROR_LINK, more_info
    );

    AnkiError::TemplateError { info }
}

fn localized_template_error(i18n: &I18n, err: TemplateError) -> String {
    match err {
        TemplateError::NoClosingBrackets(tag) => i18n.trn(
            TR::CardTemplateRenderingNoClosingBrackets,
            tr_strs!("tag"=>tag, "missing"=>"}}"),
        ),
        TemplateError::ConditionalNotClosed(tag) => i18n.trn(
            TR::CardTemplateRenderingConditionalNotClosed,
            tr_strs!("missing"=>format!("{{{{/{}}}}}", tag)),
        ),
        TemplateError::ConditionalNotOpen {
            closed,
            currently_open,
        } => {
            if let Some(open) = currently_open {
                i18n.trn(
                    TR::CardTemplateRenderingWrongConditionalClosed,
                    tr_strs!(
                "found"=>format!("{{{{/{}}}}}", closed),
                "expected"=>format!("{{{{/{}}}}}", open)),
                )
            } else {
                i18n.trn(
                    TR::CardTemplateRenderingConditionalNotOpen,
                    tr_strs!(
                    "found"=>format!("{{{{/{}}}}}", closed),
                    "missing1"=>format!("{{{{#{}}}}}", closed),
                    "missing2"=>format!("{{{{^{}}}}}", closed)
                    ),
                )
            }
        }
        TemplateError::FieldNotFound { field, filters } => i18n.trn(
            TR::CardTemplateRenderingNoSuchField,
            tr_strs!(
            "found"=>format!("{{{{{}{}}}}}", filters, field),
            "field"=>field),
        ),
    }
}

// Legacy support
//----------------------------------------

static ALT_HANDLEBAR_DIRECTIVE: &str = "{{=<% %>=}}";

/// Convert legacy alternate syntax to standard syntax.
pub fn without_legacy_template_directives(text: &str) -> Cow<str> {
    if text.trim_start().starts_with(ALT_HANDLEBAR_DIRECTIVE) {
        text.trim_start()
            .trim_start_matches(ALT_HANDLEBAR_DIRECTIVE)
            .replace("<%", "{{")
            .replace("%>", "}}")
            .into()
    } else {
        text.into()
    }
}

// Checking if template is empty
//----------------------------------------

impl ParsedTemplate<'_> {
    /// true if provided fields are sufficient to render the template
    pub fn renders_with_fields(&self, nonempty_fields: &HashSet<&str>) -> bool {
        !template_is_empty(nonempty_fields, &self.0)
    }
}

fn template_is_empty<'a>(nonempty_fields: &HashSet<&str>, nodes: &[ParsedNode<'a>]) -> bool {
    use ParsedNode::*;
    for node in nodes {
        match node {
            // ignore normal text
            Text(_) => (),
            Replacement { key, filters } => {
                // Anki doesn't consider a type: reference as a required field
                if filters.contains(&"type") {
                    continue;
                }

                if nonempty_fields.contains(*key) {
                    // a single replacement is enough
                    return false;
                }
            }
            Conditional { key, children } => {
                if !nonempty_fields.contains(*key) {
                    continue;
                }
                if !template_is_empty(nonempty_fields, children) {
                    return false;
                }
            }
            NegatedConditional { children, .. } => {
                // negated conditionals ignored when determining card generation
                if !template_is_empty(nonempty_fields, children) {
                    return false;
                }
            }
        }
    }

    true
}

// Rendering
//----------------------------------------

#[derive(Debug, PartialEq)]
pub enum RenderedNode {
    Text {
        text: String,
    },
    Replacement {
        field_name: String,
        current_text: String,
        /// Filters are in the order they should be applied.
        filters: Vec<String>,
    },
}

pub(crate) struct RenderContext<'a> {
    pub fields: &'a HashMap<&'a str, &'a str>,
    pub nonempty_fields: &'a HashSet<&'a str>,
    pub question_side: bool,
    pub card_ord: u16,
}

impl ParsedTemplate<'_> {
    /// Render the template with the provided fields.
    ///
    /// Replacements that use only standard filters will become part of
    /// a text node. If a non-standard filter is encountered, a partially
    /// rendered Replacement is returned for the calling code to complete.
    fn render(&self, context: &RenderContext) -> TemplateResult<Vec<RenderedNode>> {
        let mut rendered = vec![];

        render_into(&mut rendered, self.0.as_ref(), context)?;

        Ok(rendered)
    }
}

fn render_into(
    rendered_nodes: &mut Vec<RenderedNode>,
    nodes: &[ParsedNode],
    context: &RenderContext,
) -> TemplateResult<()> {
    use ParsedNode::*;
    for node in nodes {
        match node {
            Text(text) => {
                append_str_to_nodes(rendered_nodes, text);
            }
            Replacement {
                key: key @ "FrontSide",
                ..
            } => {
                // defer FrontSide rendering to Python, as extra
                // filters may be required
                rendered_nodes.push(RenderedNode::Replacement {
                    field_name: (*key).to_string(),
                    filters: vec![],
                    current_text: "".into(),
                });
            }
            Replacement { key: "", filters } if !filters.is_empty() => {
                // if a filter is provided, we accept an empty field name to
                // mean 'pass an empty string to the filter, and it will add
                // its own text'
                rendered_nodes.push(RenderedNode::Replacement {
                    field_name: "".to_string(),
                    current_text: "".to_string(),
                    filters: filters.iter().map(|&f| f.to_string()).collect(),
                })
            }
            Replacement { key, filters } => {
                // apply built in filters if field exists
                let (text, remaining_filters) = match context.fields.get(key) {
                    Some(text) => apply_filters(text, filters, key, context),
                    None => {
                        // unknown field encountered
                        let filters_str = filters
                            .iter()
                            .rev()
                            .cloned()
                            .chain(iter::once(""))
                            .collect::<Vec<_>>()
                            .join(":");
                        return Err(TemplateError::FieldNotFound {
                            field: (*key).to_string(),
                            filters: filters_str,
                        });
                    }
                };

                // fully processed?
                if remaining_filters.is_empty() {
                    append_str_to_nodes(rendered_nodes, text.as_ref())
                } else {
                    rendered_nodes.push(RenderedNode::Replacement {
                        field_name: (*key).to_string(),
                        filters: remaining_filters,
                        current_text: text.into(),
                    });
                }
            }
            Conditional { key, children } => {
                if context.nonempty_fields.contains(key) {
                    render_into(rendered_nodes, children.as_ref(), context)?;
                }
            }
            NegatedConditional { key, children } => {
                if !context.nonempty_fields.contains(key) {
                    render_into(rendered_nodes, children.as_ref(), context)?;
                }
            }
        };
    }

    Ok(())
}

/// Append to last node if last node is a string, else add new node.
fn append_str_to_nodes(nodes: &mut Vec<RenderedNode>, text: &str) {
    if let Some(RenderedNode::Text {
        text: ref mut existing_text,
    }) = nodes.last_mut()
    {
        // append to existing last node
        existing_text.push_str(text)
    } else {
        // otherwise, add a new string node
        nodes.push(RenderedNode::Text {
            text: text.to_string(),
        })
    }
}

/// True if provided text contains only whitespace and/or empty BR/DIV tags.
fn field_is_empty(text: &str) -> bool {
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r#"(?xsi)
            ^(?:
            [[:space:]]
            |
            </?(?:br|div)\ ?/?>
            )*$
        "#
        )
        .unwrap();
    }
    RE.is_match(text)
}

fn nonempty_fields<'a>(fields: &'a HashMap<&str, &str>) -> HashSet<&'a str> {
    fields
        .iter()
        .filter_map(|(name, val)| {
            if !field_is_empty(val) {
                Some(*name)
            } else {
                None
            }
        })
        .collect()
}

// Rendering both sides
//----------------------------------------

#[allow(clippy::implicit_hasher)]
pub fn render_card(
    qfmt: &str,
    afmt: &str,
    field_map: &HashMap<&str, &str>,
    card_ord: u16,
    i18n: &I18n,
) -> Result<(Vec<RenderedNode>, Vec<RenderedNode>)> {
    // prepare context
    let mut context = RenderContext {
        fields: field_map,
        nonempty_fields: &nonempty_fields(field_map),
        question_side: true,
        card_ord,
    };

    // question side
    let qnorm = without_legacy_template_directives(qfmt);
    let (qnodes, qtmpl) = ParsedTemplate::from_text(qnorm.as_ref())
        .and_then(|tmpl| Ok((tmpl.render(&context)?, tmpl)))
        .map_err(|e| template_error_to_anki_error(e, true, i18n))?;

    // check if the front side was empty
    if !qtmpl.renders_with_fields(context.nonempty_fields) {
        let info = format!(
            "{}<br><a href='{}'>{}</a>",
            i18n.tr(TR::CardTemplateRenderingEmptyFront),
            TEMPLATE_BLANK_LINK,
            i18n.tr(TR::CardTemplateRenderingMoreInfo)
        );
        return Err(AnkiError::TemplateError { info });
    };

    // answer side
    context.question_side = false;
    let anorm = without_legacy_template_directives(afmt);
    let anodes = ParsedTemplate::from_text(anorm.as_ref())
        .and_then(|tmpl| tmpl.render(&context))
        .map_err(|e| template_error_to_anki_error(e, false, i18n))?;

    Ok((qnodes, anodes))
}

// Field requirements
//----------------------------------------

#[derive(Debug, Clone, PartialEq)]
pub enum FieldRequirements {
    Any(HashSet<u16>),
    All(HashSet<u16>),
    None,
}

impl ParsedTemplate<'_> {
    /// Return fields required by template.
    ///
    /// This is not able to represent negated expressions or combinations of
    /// Any and All, but is compatible with older Anki clients.
    ///
    /// In the future, it may be feasible to calculate the requirements
    /// when adding cards, instead of caching them up front, which would mean
    /// the above restrictions could be lifted. We would probably
    /// want to add a cache of non-zero fields -> available cards to avoid
    /// slowing down bulk operations like importing too much.
    pub fn requirements(&self, field_map: &FieldMap) -> FieldRequirements {
        let mut nonempty: HashSet<_> = Default::default();
        let mut ords = HashSet::new();
        for (name, ord) in field_map {
            nonempty.clear();
            nonempty.insert(*name);
            if self.renders_with_fields(&nonempty) {
                ords.insert(*ord);
            }
        }
        if !ords.is_empty() {
            return FieldRequirements::Any(ords);
        }

        nonempty.extend(field_map.keys());
        ords.extend(field_map.values().copied());
        for (name, ord) in field_map {
            // can we remove this field and still render?
            nonempty.remove(name);
            if self.renders_with_fields(&nonempty) {
                ords.remove(ord);
            }
            nonempty.insert(*name);
        }
        if !ords.is_empty() && self.renders_with_fields(&nonempty) {
            FieldRequirements::All(ords)
        } else {
            FieldRequirements::None
        }
    }
}

// Tests
//---------------------------------------

#[cfg(test)]
mod test {
    use super::{FieldMap, ParsedNode::*, ParsedTemplate as PT};
    use crate::err::TemplateError;
    use crate::template::{
        field_is_empty, nonempty_fields, without_legacy_template_directives, FieldRequirements,
        RenderContext,
    };
    use std::collections::{HashMap, HashSet};
    use std::iter::FromIterator;

    #[test]
    fn field_empty() {
        assert_eq!(field_is_empty(""), true);
        assert_eq!(field_is_empty(" "), true);
        assert_eq!(field_is_empty("x"), false);
        assert_eq!(field_is_empty("<BR>"), true);
        assert_eq!(field_is_empty("<div />"), true);
        assert_eq!(field_is_empty(" <div> <br> </div>\n"), true);
        assert_eq!(field_is_empty(" <div>x</div>\n"), false);
    }

    #[test]
    fn parsing() {
        let tmpl = PT::from_text("foo {{bar}} {{#baz}} quux {{/baz}}").unwrap();
        assert_eq!(
            tmpl.0,
            vec![
                Text("foo "),
                Replacement {
                    key: "bar",
                    filters: vec![]
                },
                Text(" "),
                Conditional {
                    key: "baz",
                    children: vec![Text(" quux ")]
                }
            ]
        );

        let tmpl = PT::from_text("{{^baz}}{{/baz}}").unwrap();
        assert_eq!(
            tmpl.0,
            vec![NegatedConditional {
                key: "baz",
                children: vec![]
            }]
        );

        PT::from_text("{{#mis}}{{/matched}}").unwrap_err();
        PT::from_text("{{/matched}}").unwrap_err();
        PT::from_text("{{#mis}}").unwrap_err();

        // whitespace
        assert_eq!(
            PT::from_text("{{ tag }}").unwrap().0,
            vec![Replacement {
                key: "tag",
                filters: vec![]
            }]
        );

        // stray closing characters (like in javascript) are ignored
        assert_eq!(
            PT::from_text("text }} more").unwrap().0,
            vec![Text("text }} more")]
        );
    }

    #[test]
    fn nonempty() {
        let fields = HashSet::from_iter(vec!["1", "3"].into_iter());
        let mut tmpl = PT::from_text("{{2}}{{1}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), true);
        tmpl = PT::from_text("{{2}}{{type:cloze:1}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), false);
        tmpl = PT::from_text("{{2}}{{4}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), false);
        tmpl = PT::from_text("{{#3}}{{^2}}{{1}}{{/2}}{{/3}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), true);
    }

    #[test]
    fn requirements() {
        let field_map: FieldMap = vec!["a", "b"]
            .iter()
            .enumerate()
            .map(|(a, b)| (*b, a as u16))
            .collect();

        let mut tmpl = PT::from_text("{{a}}{{b}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(HashSet::from_iter(vec![0, 1].into_iter()))
        );

        tmpl = PT::from_text("{{#a}}{{b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::All(HashSet::from_iter(vec![0, 1].into_iter()))
        );

        tmpl = PT::from_text("{{c}}").unwrap();
        assert_eq!(tmpl.requirements(&field_map), FieldRequirements::None);

        tmpl = PT::from_text("{{^a}}{{b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(HashSet::from_iter(vec![1].into_iter()))
        );

        tmpl = PT::from_text("{{#a}}{{#b}}{{a}}{{/b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::All(HashSet::from_iter(vec![0, 1].into_iter()))
        );

        tmpl = PT::from_text("{{a}}{{type:b}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(HashSet::from_iter(vec![0].into_iter()))
        );

        tmpl = PT::from_text(
            r#"
{{^a}}
    {{b}}
{{/a}}

{{#a}}
    {{a}}
    {{b}}
{{/a}}
"#,
        )
        .unwrap();

        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(HashSet::from_iter(vec![0, 1].into_iter()))
        );
    }

    #[test]
    fn alt_syntax() {
        let input = "
{{=<% %>=}}
<%Front%>
<% #Back %>
<%/Back%>";
        let output = "
{{Front}}
{{ #Back }}
{{/Back}}";

        assert_eq!(without_legacy_template_directives(input), output);
    }

    #[test]
    fn render_single() {
        let map: HashMap<_, _> = vec![("F", "f"), ("B", "b"), ("E", " ")]
            .into_iter()
            .collect();

        let ctx = RenderContext {
            fields: &map,
            nonempty_fields: &nonempty_fields(&map),
            question_side: true,
            card_ord: 1,
        };

        use crate::template::RenderedNode as FN;
        let mut tmpl = PT::from_text("{{B}}A{{F}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Text {
                text: "bAf".to_owned()
            },]
        );

        // empty
        tmpl = PT::from_text("{{#E}}A{{/E}}").unwrap();
        assert_eq!(tmpl.render(&ctx).unwrap(), vec![]);

        // missing
        tmpl = PT::from_text("{{^M}}A{{/M}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Text {
                text: "A".to_owned()
            },]
        );

        // nested
        tmpl = PT::from_text("{{^E}}1{{#F}}2{{#B}}{{F}}{{/B}}{{/F}}{{/E}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Text {
                text: "12f".to_owned()
            },]
        );

        // unknown filters
        tmpl = PT::from_text("{{one:two:B}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Replacement {
                field_name: "B".to_owned(),
                filters: vec!["two".to_string(), "one".to_string()],
                current_text: "b".to_owned()
            },]
        );

        // partially unknown filters
        // excess colons are ignored
        tmpl = PT::from_text("{{one::text:B}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Replacement {
                field_name: "B".to_owned(),
                filters: vec!["one".to_string()],
                current_text: "b".to_owned()
            },]
        );

        // known filter
        tmpl = PT::from_text("{{text:B}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Text {
                text: "b".to_owned()
            }]
        );

        // unknown field
        tmpl = PT::from_text("{{X}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap_err(),
            TemplateError::FieldNotFound {
                field: "X".to_owned(),
                filters: "".to_owned()
            }
        );

        // unknown field with filters
        tmpl = PT::from_text("{{foo:text:X}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap_err(),
            TemplateError::FieldNotFound {
                field: "X".to_owned(),
                filters: "foo:text:".to_owned()
            }
        );

        // a blank field is allowed if it has filters
        tmpl = PT::from_text("{{filter:}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx).unwrap(),
            vec![FN::Replacement {
                field_name: "".to_string(),
                current_text: "".to_string(),
                filters: vec!["filter".to_string()]
            }]
        );
    }
}
