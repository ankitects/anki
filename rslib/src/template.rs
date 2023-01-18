// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fmt::Write;
use std::iter;

use lazy_static::lazy_static;
use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::bytes::complete::take_until;
use nom::combinator::map;
use nom::combinator::rest;
use nom::combinator::verify;
use nom::sequence::delimited;
use regex::Regex;

use crate::cloze::add_cloze_numbers_in_string;
use crate::error::AnkiError;
use crate::error::Result;
use crate::error::TemplateError;
use crate::i18n::I18n;
use crate::template_filters::apply_filters;

pub type FieldMap<'a> = HashMap<&'a str, u16>;
type TemplateResult<T> = std::result::Result<T, TemplateError>;

static TEMPLATE_ERROR_LINK: &str =
    "https://anki.tenderapp.com/kb/problems/card-template-has-a-problem";
static TEMPLATE_BLANK_LINK: &str =
    "https://anki.tenderapp.com/kb/card-appearance/the-front-of-this-card-is-blank";
static TEMPLATE_BLANK_CLOZE_LINK: &str =
    "https://anki.tenderapp.com/kb/problems/no-cloze-found-on-card";

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

/// text outside handlebars
fn text_token(s: &str) -> nom::IResult<&str, Token> {
    map(
        verify(alt((take_until("{{"), rest)), |out: &str| !out.is_empty()),
        Token::Text,
    )(s)
}

/// text wrapped in handlebars
fn handlebar_token(s: &str) -> nom::IResult<&str, Token> {
    map(delimited(tag("{{"), take_until("}}"), tag("}}")), |out| {
        classify_handle(out)
    })(s)
}

fn next_token(input: &str) -> nom::IResult<&str, Token> {
    alt((handlebar_token, text_token))(input)
}

fn tokens<'a>(template: &'a str) -> Box<dyn Iterator<Item = TemplateResult<Token>> + 'a> {
    if template.trim_start().starts_with(ALT_HANDLEBAR_DIRECTIVE) {
        Box::new(legacy_tokens(
            template
                .trim_start()
                .trim_start_matches(ALT_HANDLEBAR_DIRECTIVE),
        ))
    } else {
        Box::new(new_tokens(template))
    }
}

fn new_tokens(mut data: &str) -> impl Iterator<Item = TemplateResult<Token>> {
    iter::from_fn(move || {
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

/// classify handle based on leading character
fn classify_handle(s: &str) -> Token {
    let start = s.trim_start_matches('{').trim();
    if start.len() < 2 {
        return Token::Replacement(start);
    }
    if let Some(stripped) = start.strip_prefix('#') {
        Token::OpenConditional(stripped.trim_start())
    } else if let Some(stripped) = start.strip_prefix('/') {
        Token::CloseConditional(stripped.trim_start())
    } else if let Some(stripped) = start.strip_prefix('^') {
        Token::OpenNegated(stripped.trim_start())
    } else {
        Token::Replacement(start)
    }
}

// Legacy support
//----------------------------------------

static ALT_HANDLEBAR_DIRECTIVE: &str = "{{=<% %>=}}";

fn legacy_text_token(s: &str) -> nom::IResult<&str, Token> {
    if s.is_empty() {
        return Err(nom::Err::Error(nom::error::make_error(
            s,
            nom::error::ErrorKind::TakeUntil,
        )));
    }
    // if we locate a starting normal or alternate handlebar, use
    // whichever one we found first
    let normal_result: nom::IResult<&str, &str> = take_until("{{")(s);
    let (normal_remaining, normal_span) = normal_result.unwrap_or(("", s));
    let alt_result: nom::IResult<&str, &str> = take_until("<%")(s);
    let (alt_remaining, alt_span) = alt_result.unwrap_or(("", s));
    match (normal_span.len(), alt_span.len()) {
        (0, 0) => {
            // neither handlebar kind found
            map(rest, Token::Text)(s)
        }
        (n, a) => {
            if n < a {
                Ok((normal_remaining, Token::Text(normal_span)))
            } else {
                Ok((alt_remaining, Token::Text(alt_span)))
            }
        }
    }
}

fn legacy_next_token(input: &str) -> nom::IResult<&str, Token> {
    alt((
        handlebar_token,
        alternate_handlebar_token,
        legacy_text_token,
    ))(input)
}

/// text wrapped in <% %>
fn alternate_handlebar_token(s: &str) -> nom::IResult<&str, Token> {
    map(delimited(tag("<%"), take_until("%>"), tag("%>")), |out| {
        classify_handle(out)
    })(s)
}

fn legacy_tokens(mut data: &str) -> impl Iterator<Item = TemplateResult<Token>> {
    iter::from_fn(move || {
        if data.is_empty() {
            return None;
        }
        match legacy_next_token(data) {
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

#[derive(Debug, PartialEq, Eq)]
enum ParsedNode {
    Text(String),
    Replacement {
        key: String,
        filters: Vec<String>,
    },
    Conditional {
        key: String,
        children: Vec<ParsedNode>,
    },
    NegatedConditional {
        key: String,
        children: Vec<ParsedNode>,
    },
}

#[derive(Debug)]
pub struct ParsedTemplate(Vec<ParsedNode>);

impl ParsedTemplate {
    /// Create a template from the provided text.
    pub fn from_text(template: &str) -> TemplateResult<ParsedTemplate> {
        let mut iter = tokens(template);
        Ok(Self(parse_inner(&mut iter, None)?))
    }
}

fn parse_inner<'a, I: Iterator<Item = TemplateResult<Token<'a>>>>(
    iter: &mut I,
    open_tag: Option<&'a str>,
) -> TemplateResult<Vec<ParsedNode>> {
    let mut nodes = vec![];

    while let Some(token) = iter.next() {
        use Token::*;
        nodes.push(match token? {
            Text(t) => ParsedNode::Text(t.into()),
            Replacement(t) => {
                let mut it = t.rsplit(':');
                ParsedNode::Replacement {
                    key: it.next().unwrap().into(),
                    filters: it.map(Into::into).collect(),
                }
            }
            OpenConditional(t) => ParsedNode::Conditional {
                key: t.into(),
                children: parse_inner(iter, Some(t))?,
            },
            OpenNegated(t) => ParsedNode::NegatedConditional {
                key: t.into(),
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

fn template_error_to_anki_error(
    err: TemplateError,
    q_side: bool,
    browser: bool,
    tr: &I18n,
) -> AnkiError {
    let header = match (q_side, browser) {
        (true, false) => tr.card_template_rendering_front_side_problem(),
        (false, false) => tr.card_template_rendering_back_side_problem(),
        (true, true) => tr.card_template_rendering_browser_front_side_problem(),
        (false, true) => tr.card_template_rendering_browser_back_side_problem(),
    };
    let details = htmlescape::encode_minimal(&localized_template_error(tr, err));
    let more_info = tr.card_template_rendering_more_info();
    let source = format!(
        "{}<br>{}<br><a href='{}'>{}</a>",
        header, details, TEMPLATE_ERROR_LINK, more_info
    );

    AnkiError::TemplateError { info: source }
}

fn localized_template_error(tr: &I18n, err: TemplateError) -> String {
    match err {
        TemplateError::NoClosingBrackets(tag) => tr
            .card_template_rendering_no_closing_brackets("}}", tag)
            .into(),
        TemplateError::ConditionalNotClosed(tag) => tr
            .card_template_rendering_conditional_not_closed(format!("{{{{/{}}}}}", tag))
            .into(),
        TemplateError::ConditionalNotOpen {
            closed,
            currently_open,
        } => if let Some(open) = currently_open {
            tr.card_template_rendering_wrong_conditional_closed(
                format!("{{{{/{}}}}}", closed),
                format!("{{{{/{}}}}}", open),
            )
        } else {
            tr.card_template_rendering_conditional_not_open(
                format!("{{{{/{}}}}}", closed),
                format!("{{{{#{}}}}}", closed),
                format!("{{{{^{}}}}}", closed),
            )
        }
        .into(),
        TemplateError::FieldNotFound { field, filters } => tr
            .card_template_rendering_no_such_field(format!("{{{{{}{}}}}}", filters, field), field)
            .into(),
        TemplateError::NoSuchConditional(condition) => tr
            .card_template_rendering_no_such_field(
                format!("{{{{{}}}}}", condition),
                &condition[1..],
            )
            .into(),
    }
}

// Checking if template is empty
//----------------------------------------

impl ParsedTemplate {
    /// true if provided fields are sufficient to render the template
    pub fn renders_with_fields(&self, nonempty_fields: &HashSet<&str>) -> bool {
        !template_is_empty(nonempty_fields, &self.0, true)
    }

    pub fn renders_with_fields_for_reqs(&self, nonempty_fields: &HashSet<&str>) -> bool {
        !template_is_empty(nonempty_fields, &self.0, false)
    }
}

/// If check_negated is false, negated conditionals resolve to their children,
/// even if the referenced key is non-empty. This allows the legacy required
/// field cache to generate results closer to older Anki versions.
fn template_is_empty(
    nonempty_fields: &HashSet<&str>,
    nodes: &[ParsedNode],
    check_negated: bool,
) -> bool {
    use ParsedNode::*;
    for node in nodes {
        match node {
            // ignore normal text
            Text(_) => (),
            Replacement { key, .. } => {
                if nonempty_fields.contains(key.as_str()) {
                    // a single replacement is enough
                    return false;
                }
            }
            Conditional { key, children } => {
                if !nonempty_fields.contains(key.as_str()) {
                    continue;
                }
                if !template_is_empty(nonempty_fields, children, check_negated) {
                    return false;
                }
            }
            NegatedConditional { key, children } => {
                if check_negated && nonempty_fields.contains(key.as_str()) {
                    continue;
                }

                if !template_is_empty(nonempty_fields, children, check_negated) {
                    return false;
                }
            }
        }
    }

    true
}

// Rendering
//----------------------------------------

#[derive(Debug, PartialEq, Eq)]
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
    pub fields: &'a HashMap<&'a str, Cow<'a, str>>,
    pub nonempty_fields: &'a HashSet<&'a str>,
    pub question_side: bool,
    pub card_ord: u16,
}

impl ParsedTemplate {
    /// Render the template with the provided fields.
    ///
    /// Replacements that use only standard filters will become part of
    /// a text node. If a non-standard filter is encountered, a partially
    /// rendered Replacement is returned for the calling code to complete.
    fn render(&self, context: &RenderContext, _tr: &I18n) -> TemplateResult<Vec<RenderedNode>> {
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
            Replacement { key, .. } if key == "FrontSide" => {
                // defer FrontSide rendering to Python, as extra
                // filters may be required
                rendered_nodes.push(RenderedNode::Replacement {
                    field_name: (*key).to_string(),
                    filters: vec![],
                    current_text: "".into(),
                });
            }
            Replacement { key, filters } if key.is_empty() && !filters.is_empty() => {
                // if a filter is provided, we accept an empty field name to
                // mean 'pass an empty string to the filter, and it will add
                // its own text'
                rendered_nodes.push(RenderedNode::Replacement {
                    field_name: "".to_string(),
                    current_text: "".to_string(),
                    filters: filters.clone(),
                })
            }
            Replacement { key, filters } => {
                // apply built in filters if field exists
                let (text, remaining_filters) = match context.fields.get(key.as_str()) {
                    Some(text) => apply_filters(
                        text,
                        filters
                            .iter()
                            .map(|s| s.as_str())
                            .collect::<Vec<_>>()
                            .as_slice(),
                        key,
                        context,
                    ),
                    None => {
                        // unknown field encountered
                        let filters_str = filters
                            .iter()
                            .rev()
                            .cloned()
                            .chain(iter::once("".into()))
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
                if context.evaluate_conditional(key.as_str(), false)? {
                    render_into(rendered_nodes, children.as_ref(), context)?;
                } else {
                    // keep checking for errors, but discard rendered nodes
                    render_into(&mut vec![], children.as_ref(), context)?;
                }
            }
            NegatedConditional { key, children } => {
                if context.evaluate_conditional(key.as_str(), true)? {
                    render_into(rendered_nodes, children.as_ref(), context)?;
                } else {
                    render_into(&mut vec![], children.as_ref(), context)?;
                }
            }
        };
    }

    Ok(())
}

impl<'a> RenderContext<'a> {
    fn evaluate_conditional(&self, key: &str, negated: bool) -> TemplateResult<bool> {
        if self.nonempty_fields.contains(key) {
            Ok(true ^ negated)
        } else if self.fields.contains_key(key) || is_cloze_conditional(key) {
            Ok(false ^ negated)
        } else {
            let prefix = if negated { "^" } else { "#" };
            Err(TemplateError::NoSuchConditional(format!(
                "{}{}",
                prefix, key
            )))
        }
    }
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
pub(crate) fn field_is_empty(text: &str) -> bool {
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

fn nonempty_fields<'a, R>(fields: &'a HashMap<&str, R>) -> HashSet<&'a str>
where
    R: AsRef<str>,
{
    fields
        .iter()
        .filter_map(|(name, val)| {
            if !field_is_empty(val.as_ref()) {
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
    field_map: &HashMap<&str, Cow<str>>,
    card_ord: u16,
    is_cloze: bool,
    browser: bool,
    tr: &I18n,
) -> Result<(Vec<RenderedNode>, Vec<RenderedNode>)> {
    // prepare context
    let mut context = RenderContext {
        fields: field_map,
        nonempty_fields: &nonempty_fields(field_map),
        question_side: true,
        card_ord,
    };

    // question side
    let (mut qnodes, qtmpl) = ParsedTemplate::from_text(qfmt)
        .and_then(|tmpl| Ok((tmpl.render(&context, tr)?, tmpl)))
        .map_err(|e| template_error_to_anki_error(e, true, browser, tr))?;

    // check if the front side was empty
    let empty_message = if is_cloze && cloze_is_empty(field_map, card_ord) {
        Some(format!(
            "<div>{}<br><a href='{}'>{}</a></div>",
            tr.card_template_rendering_missing_cloze(card_ord + 1),
            TEMPLATE_BLANK_CLOZE_LINK,
            tr.card_template_rendering_more_info()
        ))
    } else if !is_cloze && !qtmpl.renders_with_fields(context.nonempty_fields) {
        Some(format!(
            "<div>{}<br><a href='{}'>{}</a></div>",
            tr.card_template_rendering_empty_front(),
            TEMPLATE_BLANK_LINK,
            tr.card_template_rendering_more_info()
        ))
    } else {
        None
    };
    if let Some(text) = empty_message {
        qnodes.push(RenderedNode::Text { text: text.clone() });
        return Ok((qnodes, vec![RenderedNode::Text { text }]));
    }

    // answer side
    context.question_side = false;
    let anodes = ParsedTemplate::from_text(afmt)
        .and_then(|tmpl| tmpl.render(&context, tr))
        .map_err(|e| template_error_to_anki_error(e, false, browser, tr))?;

    Ok((qnodes, anodes))
}

fn cloze_is_empty(field_map: &HashMap<&str, Cow<str>>, card_ord: u16) -> bool {
    let mut set = HashSet::with_capacity(4);
    for field in field_map.values() {
        add_cloze_numbers_in_string(field.as_ref(), &mut set);
    }
    !set.contains(&(card_ord + 1))
}

// Field requirements
//----------------------------------------

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FieldRequirements {
    Any(HashSet<u16>),
    All(HashSet<u16>),
    None,
}

impl ParsedTemplate {
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
            if self.renders_with_fields_for_reqs(&nonempty) {
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
            if self.renders_with_fields_for_reqs(&nonempty) {
                ords.remove(ord);
            }
            nonempty.insert(*name);
        }
        if !ords.is_empty() && self.renders_with_fields_for_reqs(&nonempty) {
            FieldRequirements::All(ords)
        } else {
            FieldRequirements::None
        }
    }
}

// Renaming & deleting fields
//----------------------------------------

impl ParsedTemplate {
    /// Given a map of old to new field names, update references to the new
    /// names. Returns true if any changes made.
    pub(crate) fn rename_and_remove_fields(&mut self, fields: &HashMap<String, Option<String>>) {
        let old_nodes = std::mem::take(&mut self.0);
        self.0 = rename_and_remove_fields(old_nodes, fields);
    }
}

fn rename_and_remove_fields(
    nodes: Vec<ParsedNode>,
    fields: &HashMap<String, Option<String>>,
) -> Vec<ParsedNode> {
    let mut out = vec![];
    for node in nodes {
        match node {
            ParsedNode::Text(text) => out.push(ParsedNode::Text(text)),
            ParsedNode::Replacement { key, filters } => {
                match fields.get(&key) {
                    // delete the field
                    Some(None) => (),
                    // rename it
                    Some(Some(new_name)) => out.push(ParsedNode::Replacement {
                        key: new_name.into(),
                        filters,
                    }),
                    // or leave it alone
                    None => out.push(ParsedNode::Replacement { key, filters }),
                }
            }
            ParsedNode::Conditional { key, children } => {
                let children = rename_and_remove_fields(children, fields);
                match fields.get(&key) {
                    // remove the field, preserving children
                    Some(None) => out.extend(children),
                    // rename it
                    Some(Some(new_name)) => out.push(ParsedNode::Conditional {
                        key: new_name.into(),
                        children,
                    }),
                    // or leave it alone
                    None => out.push(ParsedNode::Conditional { key, children }),
                }
            }
            ParsedNode::NegatedConditional { key, children } => {
                let children = rename_and_remove_fields(children, fields);
                match fields.get(&key) {
                    // remove the field, preserving children
                    Some(None) => out.extend(children),
                    // rename it
                    Some(Some(new_name)) => out.push(ParsedNode::NegatedConditional {
                        key: new_name.into(),
                        children,
                    }),
                    // or leave it alone
                    None => out.push(ParsedNode::NegatedConditional { key, children }),
                }
            }
        }
    }
    out
}

// Writing back to a string
//----------------------------------------

impl ParsedTemplate {
    pub(crate) fn template_to_string(&self) -> String {
        let mut buf = String::new();
        nodes_to_string(&mut buf, &self.0);
        buf
    }
}

fn nodes_to_string(buf: &mut String, nodes: &[ParsedNode]) {
    for node in nodes {
        match node {
            ParsedNode::Text(text) => buf.push_str(text),
            ParsedNode::Replacement { key, filters } => {
                write!(
                    buf,
                    "{{{{{}}}}}",
                    filters
                        .iter()
                        .rev()
                        .chain(iter::once(key))
                        .map(|s| s.to_string())
                        .collect::<Vec<_>>()
                        .join(":")
                )
                .unwrap();
            }
            ParsedNode::Conditional { key, children } => {
                write!(buf, "{{{{#{}}}}}", key).unwrap();
                nodes_to_string(buf, children);
                write!(buf, "{{{{/{}}}}}", key).unwrap();
            }
            ParsedNode::NegatedConditional { key, children } => {
                write!(buf, "{{{{^{}}}}}", key).unwrap();
                nodes_to_string(buf, children);
                write!(buf, "{{{{/{}}}}}", key).unwrap();
            }
        }
    }
}

// Detecting cloze fields
//----------------------------------------

impl ParsedTemplate {
    /// Field names may not be valid.
    pub(crate) fn all_referenced_field_names(&self) -> HashSet<&str> {
        let mut set = HashSet::new();
        find_field_references(&self.0, &mut set, false, true);
        set
    }

    /// Field names may not be valid.
    pub(crate) fn all_referenced_cloze_field_names(&self) -> HashSet<&str> {
        let mut set = HashSet::new();
        find_field_references(&self.0, &mut set, true, false);
        set
    }
}

fn find_field_references<'a>(
    nodes: &'a [ParsedNode],
    fields: &mut HashSet<&'a str>,
    cloze_only: bool,
    with_conditionals: bool,
) {
    for node in nodes {
        match node {
            ParsedNode::Text(_) => {}
            ParsedNode::Replacement { key, filters } => {
                if !cloze_only || filters.iter().any(|f| f == "cloze") {
                    fields.insert(key);
                }
            }
            ParsedNode::Conditional { key, children }
            | ParsedNode::NegatedConditional { key, children } => {
                if with_conditionals && !is_cloze_conditional(key) {
                    fields.insert(key);
                }
                find_field_references(children, fields, cloze_only, with_conditionals);
            }
        }
    }
}

fn is_cloze_conditional(key: &str) -> bool {
    key.strip_prefix('c')
        .map_or(false, |s| s.parse::<u32>().is_ok())
}

// Tests
//---------------------------------------

#[cfg(test)]
mod test {
    use std::collections::HashMap;

    use super::FieldMap;
    use super::ParsedNode::*;
    use super::ParsedTemplate as PT;
    use crate::error::TemplateError;
    use crate::i18n::I18n;
    use crate::template::field_is_empty;
    use crate::template::nonempty_fields;
    use crate::template::FieldRequirements;
    use crate::template::RenderContext;

    #[test]
    fn field_empty() {
        assert!(field_is_empty(""));
        assert!(field_is_empty(" "));
        assert!(!field_is_empty("x"));
        assert!(field_is_empty("<BR>"));
        assert!(field_is_empty("<div />"));
        assert!(field_is_empty(" <div> <br> </div>\n"));
        assert!(!field_is_empty(" <div>x</div>\n"));
    }

    #[test]
    fn parsing() {
        let orig = "foo {{bar}} {{#baz}} quux {{/baz}}";
        let tmpl = PT::from_text(orig).unwrap();
        assert_eq!(
            tmpl.0,
            vec![
                Text("foo ".into()),
                Replacement {
                    key: "bar".into(),
                    filters: vec![]
                },
                Text(" ".into()),
                Conditional {
                    key: "baz".into(),
                    children: vec![Text(" quux ".into())]
                }
            ]
        );
        assert_eq!(orig, &tmpl.template_to_string());

        let tmpl = PT::from_text("{{^baz}}{{/baz}}").unwrap();
        assert_eq!(
            tmpl.0,
            vec![NegatedConditional {
                key: "baz".into(),
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
                key: "tag".into(),
                filters: vec![]
            }]
        );

        // stray closing characters (like in javascript) are ignored
        assert_eq!(
            PT::from_text("text }} more").unwrap().0,
            vec![Text("text }} more".into())]
        );

        PT::from_text("{{").unwrap_err();
        PT::from_text(" {{").unwrap_err();
        PT::from_text(" {{ ").unwrap_err();

        // make sure filters and so on are round-tripped correctly
        let orig = "foo {{one:two}} {{one:two:three}} {{^baz}} {{/baz}} {{foo:}}";
        let tmpl = PT::from_text(orig).unwrap();
        assert_eq!(orig, &tmpl.template_to_string());
    }

    #[test]
    fn nonempty() {
        let fields = vec!["1", "3"].into_iter().collect();
        let mut tmpl = PT::from_text("{{2}}{{1}}").unwrap();
        assert!(tmpl.renders_with_fields(&fields));
        tmpl = PT::from_text("{{2}}").unwrap();
        assert!(!tmpl.renders_with_fields(&fields));
        tmpl = PT::from_text("{{2}}{{4}}").unwrap();
        assert!(!tmpl.renders_with_fields(&fields));
        tmpl = PT::from_text("{{#3}}{{^2}}{{1}}{{/2}}{{/3}}").unwrap();
        assert!(tmpl.renders_with_fields(&fields));

        tmpl = PT::from_text("{{^1}}{{3}}{{/1}}").unwrap();
        assert!(!tmpl.renders_with_fields(&fields));
        assert!(tmpl.renders_with_fields_for_reqs(&fields));
    }

    #[test]
    fn requirements() {
        let field_map: FieldMap = vec!["a", "b", "c"]
            .iter()
            .enumerate()
            .map(|(a, b)| (*b, a as u16))
            .collect();

        let mut tmpl = PT::from_text("{{a}}{{b}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(vec![0, 1].into_iter().collect())
        );

        tmpl = PT::from_text("{{#a}}{{b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::All(vec![0, 1].into_iter().collect())
        );

        tmpl = PT::from_text("{{z}}").unwrap();
        assert_eq!(tmpl.requirements(&field_map), FieldRequirements::None);

        tmpl = PT::from_text("{{^a}}{{b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(vec![1].into_iter().collect())
        );

        tmpl = PT::from_text("{{^a}}{{#b}}{{c}}{{/b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::All(vec![1, 2].into_iter().collect())
        );

        tmpl = PT::from_text("{{#a}}{{#b}}{{a}}{{/b}}{{/a}}").unwrap();
        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::All(vec![0, 1].into_iter().collect())
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
            FieldRequirements::Any(vec![0, 1].into_iter().collect())
        );
    }

    #[test]
    fn alt_syntax() {
        let input = "
{{=<% %>=}}
<%Front%>
<% #Back %>
<%/Back%>";
        assert_eq!(
            PT::from_text(input).unwrap().0,
            vec![
                Text("\n".into()),
                Replacement {
                    key: "Front".into(),
                    filters: vec![]
                },
                Text("\n".into()),
                Conditional {
                    key: "Back".into(),
                    children: vec![Text("\n".into())]
                }
            ]
        );
        let input = "
{{=<% %>=}}
{{#foo}}
<%Front%>
{{/foo}}
";
        assert_eq!(
            PT::from_text(input).unwrap().0,
            vec![
                Text("\n".into()),
                Conditional {
                    key: "foo".into(),
                    children: vec![
                        Text("\n".into()),
                        Replacement {
                            key: "Front".into(),
                            filters: vec![]
                        },
                        Text("\n".into())
                    ]
                },
                Text("\n".into())
            ]
        );
    }

    #[test]
    fn render_single() {
        let map: HashMap<_, _> = vec![("F", "f"), ("B", "b"), ("E", " "), ("c1", "1")]
            .into_iter()
            .map(|r| (r.0, r.1.into()))
            .collect();

        let ctx = RenderContext {
            fields: &map,
            nonempty_fields: &nonempty_fields(&map),
            question_side: true,
            card_ord: 1,
        };

        use crate::template::RenderedNode as FN;
        let mut tmpl = PT::from_text("{{B}}A{{F}}").unwrap();
        let tr = I18n::template_only();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Text {
                text: "bAf".to_owned()
            },]
        );

        // empty
        tmpl = PT::from_text("{{#E}}A{{/E}}").unwrap();
        assert_eq!(tmpl.render(&ctx, &tr).unwrap(), vec![]);

        // missing
        tmpl = PT::from_text("{{#E}}}{{^M}}A{{/M}}{{/E}}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap_err(),
            TemplateError::NoSuchConditional("^M".to_string())
        );

        // nested
        tmpl = PT::from_text("{{^E}}1{{#F}}2{{#B}}{{F}}{{/B}}{{/F}}{{/E}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Text {
                text: "12f".to_owned()
            },]
        );

        // card conditionals
        tmpl = PT::from_text("{{^c2}}1{{#c1}}2{{/c1}}{{/c2}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Text {
                text: "12".to_owned()
            },]
        );

        // unknown filters
        tmpl = PT::from_text("{{one:two:B}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
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
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Replacement {
                field_name: "B".to_owned(),
                filters: vec!["one".to_string()],
                current_text: "b".to_owned()
            },]
        );

        // known filter
        tmpl = PT::from_text("{{text:B}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Text {
                text: "b".to_owned()
            }]
        );

        // unknown field
        tmpl = PT::from_text("{{X}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap_err(),
            TemplateError::FieldNotFound {
                field: "X".to_owned(),
                filters: "".to_owned()
            }
        );

        // unknown field with filters
        tmpl = PT::from_text("{{foo:text:X}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap_err(),
            TemplateError::FieldNotFound {
                field: "X".to_owned(),
                filters: "foo:text:".to_owned()
            }
        );

        // a blank field is allowed if it has filters
        tmpl = PT::from_text("{{filter:}}").unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Replacement {
                field_name: "".to_string(),
                current_text: "".to_string(),
                filters: vec!["filter".to_string()]
            }]
        );
    }

    #[test]
    fn render_card() {
        let map: HashMap<_, _> = vec![("E", "")]
            .into_iter()
            .map(|r| (r.0, r.1.into()))
            .collect();

        let tr = I18n::template_only();
        use crate::template::RenderedNode as FN;

        let qnodes = super::render_card("test{{E}}", "", &map, 1, false, false, &tr)
            .unwrap()
            .0;
        assert_eq!(
            qnodes[0],
            FN::Text {
                text: "test".into()
            }
        );
        if let FN::Text { ref text } = qnodes[1] {
            assert!(text.contains("card is blank"));
        } else {
            unreachable!();
        }
    }
}
