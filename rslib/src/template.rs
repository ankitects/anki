// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fmt::Write;
use std::iter;
use std::sync::LazyLock;

use anki_i18n::I18n;
use nom::bytes::complete::tag;
use nom::bytes::complete::take_until;
use nom::combinator::map;
use nom::sequence::delimited;
use regex::Regex;

use crate::cloze::cloze_number_in_fields;
use crate::error::AnkiError;
use crate::error::Result;
use crate::error::TemplateError;
use crate::invalid_input;
use crate::template_filters::apply_filters;

pub type FieldMap<'a> = HashMap<&'a str, u16>;
type TemplateResult<T> = std::result::Result<T, TemplateError>;

static TEMPLATE_ERROR_LINK: &str =
    "https://docs.ankiweb.net/templates/errors.html#template-syntax-error";
static TEMPLATE_BLANK_LINK: &str =
    "https://docs.ankiweb.net/templates/errors.html#front-of-card-is-blank";
static TEMPLATE_BLANK_CLOZE_LINK: &str =
    "https://docs.ankiweb.net/templates/errors.html#no-cloze-filter-on-cloze-note-type";

// Template comment delimiters
static COMMENT_START: &str = "<!--";
static COMMENT_END: &str = "-->";

static ALT_HANDLEBAR_DIRECTIVE: &str = "{{=<% %>=}}";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum TemplateMode {
    Standard,
    LegacyAltSyntax,
}

impl TemplateMode {
    fn start_tag(&self) -> &'static str {
        match self {
            TemplateMode::Standard => "{{",
            TemplateMode::LegacyAltSyntax => "<%",
        }
    }

    fn end_tag(&self) -> &'static str {
        match self {
            TemplateMode::Standard => "}}",
            TemplateMode::LegacyAltSyntax => "%>",
        }
    }

    fn handlebar_token<'b>(&self, s: &'b str) -> nom::IResult<&'b str, Token<'b>> {
        map(
            delimited(
                tag(self.start_tag()),
                take_until(self.end_tag()),
                tag(self.end_tag()),
            ),
            |out| classify_handle(out),
        )(s)
    }

    /// Return the next handlebar, comment or text token.
    fn next_token<'b>(&self, input: &'b str) -> Option<(&'b str, Token<'b>)> {
        if input.is_empty() {
            return None;
        }

        // Loop, starting from the first character
        for (i, _) in input.char_indices() {
            let remaining = &input[i..];

            // Valid handlebar clause?
            if let Ok((after_handlebar, token)) = self.handlebar_token(remaining) {
                // Found at the start of string, so that's the next token
                return Some(if i == 0 {
                    (after_handlebar, token)
                } else {
                    // There was some text prior to this, so return it instead
                    (remaining, Token::Text(&input[..i]))
                });
            }

            // Check comments too
            if let Ok((after_comment, token)) = comment_token(remaining) {
                return Some(if i == 0 {
                    (after_comment, token)
                } else {
                    (remaining, Token::Text(&input[..i]))
                });
            }
        }

        // If no matches, return the entire input as text, with nothing remaining
        Some(("", Token::Text(input)))
    }
}

// Lexing
//----------------------------------------

#[derive(Debug)]
pub enum Token<'a> {
    Text(&'a str),
    Comment(&'a str),
    Replacement(&'a str),
    OpenConditional(&'a str),
    OpenNegated(&'a str),
    CloseConditional(&'a str),
}

fn comment_token(s: &str) -> nom::IResult<&str, Token> {
    map(
        delimited(
            tag(COMMENT_START),
            take_until(COMMENT_END),
            tag(COMMENT_END),
        ),
        Token::Comment,
    )(s)
}

fn tokens(mut template: &str) -> impl Iterator<Item = TemplateResult<Token<'_>>> {
    let mode = if template.trim_start().starts_with(ALT_HANDLEBAR_DIRECTIVE) {
        template = template
            .trim_start()
            .trim_start_matches(ALT_HANDLEBAR_DIRECTIVE);

        TemplateMode::LegacyAltSyntax
    } else {
        TemplateMode::Standard
    };
    iter::from_fn(move || {
        let token;
        (template, token) = mode.next_token(template)?;
        Some(Ok(token))
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

// Parsing
//----------------------------------------

#[derive(Debug, PartialEq, Eq)]
enum ParsedNode {
    Text(String),
    Comment(String),
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
            Comment(t) => ParsedNode::Comment(t.into()),
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
            Text(_) | Comment(_) => (),
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
    pub card_ord: u16,
    /// Should be set before rendering the answer, even if `partial_for_python`
    /// is true.
    pub frontside: Option<&'a str>,
    /// If true, question/answer will not be fully rendered if an unknown filter
    /// is encountered, and the frontend code will need to complete the
    /// rendering.
    pub partial_for_python: bool,
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
            Comment(comment) => {
                append_str_to_nodes(rendered_nodes, COMMENT_START);
                append_str_to_nodes(rendered_nodes, comment);
                append_str_to_nodes(rendered_nodes, COMMENT_END);
            }
            Replacement { key, .. } if key == "FrontSide" => {
                let frontside = context.frontside.as_ref().copied().unwrap_or_default();
                if context.partial_for_python {
                    // defer FrontSide rendering to Python, as extra
                    // filters may be required
                    rendered_nodes.push(RenderedNode::Replacement {
                        field_name: (*key).to_string(),
                        filters: vec![],
                        current_text: "".into(),
                    });
                } else {
                    append_str_to_nodes(rendered_nodes, frontside);
                }
            }
            Replacement { key, filters } => {
                if key.is_empty() && !filters.is_empty() {
                    if context.partial_for_python {
                        // if a filter is provided, we accept an empty field name to
                        // mean 'pass an empty string to the filter, and it will add
                        // its own text'
                        rendered_nodes.push(RenderedNode::Replacement {
                            field_name: "".to_string(),
                            current_text: "".to_string(),
                            filters: filters.clone(),
                        });
                    } else {
                        // nothing to do
                    }
                } else {
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

impl RenderContext<'_> {
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
    static RE: LazyLock<Regex> = LazyLock::new(|| {
        Regex::new(
            r"(?xsi)
            ^(?:
            [[:space:]]
            |
            </?(?:br|div)\ ?/?>
            )*$
        ",
        )
        .unwrap()
    });
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

#[derive(Clone)]
pub struct RenderCardRequest<'a> {
    pub qfmt: &'a str,
    pub afmt: &'a str,
    pub field_map: &'a HashMap<&'a str, Cow<'a, str>>,
    pub card_ord: u16,
    pub is_cloze: bool,
    pub browser: bool,
    pub tr: &'a I18n,
    pub partial_render: bool,
}

pub struct RenderCardResponse {
    pub qnodes: Vec<RenderedNode>,
    pub anodes: Vec<RenderedNode>,
    pub is_empty: bool,
}

/// Returns `(qnodes, anodes, is_empty)`
pub fn render_card(
    RenderCardRequest {
        qfmt,
        afmt,
        field_map,
        card_ord,
        is_cloze,
        browser,
        tr,
        partial_render: partial_for_python,
    }: RenderCardRequest<'_>,
) -> Result<RenderCardResponse> {
    // prepare context
    let mut context = RenderContext {
        fields: field_map,
        nonempty_fields: &nonempty_fields(field_map),
        frontside: None,
        card_ord,
        partial_for_python,
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
    } else if !is_cloze && !browser && !qtmpl.renders_with_fields(context.nonempty_fields) {
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
        return Ok(RenderCardResponse {
            qnodes,
            anodes: vec![RenderedNode::Text { text }],
            is_empty: true,
        });
    }

    // answer side
    context.frontside = if context.partial_for_python {
        Some("")
    } else {
        let Some(RenderedNode::Text { text }) = &qnodes.first() else {
            invalid_input!("should not happen: first node not text");
        };
        Some(text)
    };
    let anodes = ParsedTemplate::from_text(afmt)
        .and_then(|tmpl| tmpl.render(&context, tr))
        .map_err(|e| template_error_to_anki_error(e, false, browser, tr))?;

    Ok(RenderCardResponse {
        qnodes,
        anodes,
        is_empty: false,
    })
}

fn cloze_is_empty(field_map: &HashMap<&str, Cow<str>>, card_ord: u16) -> bool {
    !cloze_number_in_fields(field_map.values()).contains(&(card_ord + 1))
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

    pub(crate) fn contains_cloze_replacement(&self) -> bool {
        self.0.iter().any(|node| {
            matches!(
                node,
                ParsedNode::Replacement {key:_, filters} if filters.iter().any(|f| f=="cloze")
            )
        })
    }

    pub(crate) fn contains_field_replacement(&self) -> bool {
        let mut set = HashSet::new();
        find_field_references(&self.0, &mut set, false, false);
        !set.is_empty()
    }

    pub(crate) fn add_missing_field_replacement(&mut self, field_name: &str, is_cloze: bool) {
        let key = String::from(field_name);
        let filters = match is_cloze {
            true => vec![String::from("cloze")],
            false => Vec::new(),
        };
        self.0.push(ParsedNode::Replacement { key, filters });
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
            ParsedNode::Comment(text) => out.push(ParsedNode::Comment(text)),
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
            ParsedNode::Comment(text) => {
                buf.push_str(COMMENT_START);
                buf.push_str(text);
                buf.push_str(COMMENT_END);
            }
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
            ParsedNode::Comment(_) => {}
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
        .is_some_and(|s| s.parse::<u32>().is_ok())
}

// Tests
//---------------------------------------

#[cfg(test)]
mod test {
    use std::collections::HashMap;

    use anki_i18n::I18n;

    use super::FieldMap;
    use super::ParsedNode::*;
    use super::ParsedTemplate as PT;
    use crate::error::TemplateError;
    use crate::template::field_is_empty;
    use crate::template::nonempty_fields;
    use crate::template::FieldRequirements;
    use crate::template::RenderCardRequest;
    use crate::template::RenderContext;
    use crate::template::COMMENT_END;
    use crate::template::COMMENT_START;

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
        let orig = "";
        let tmpl = PT::from_text(orig).unwrap();
        assert_eq!(tmpl.0, vec![]);
        assert_eq!(orig, &tmpl.template_to_string());

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

        // Hardcode comment delimiters into tests to keep them concise
        assert_eq!(COMMENT_START, "<!--");
        assert_eq!(COMMENT_END, "-->");

        let orig = "foo <!--{{bar }} --> {{#baz}} --> <!-- <!-- {{#def}} --> \u{123}-->\u{456}<!-- 2 --><!----> <!-- quux {{/baz}} <!-- {{nc:abc}}";
        let tmpl = PT::from_text(orig).unwrap();
        assert_eq!(
            tmpl.0,
            vec![
                Text("foo ".into()),
                Comment("{{bar }} ".into()),
                Text(" ".into()),
                Conditional {
                    key: "baz".into(),
                    children: vec![
                        Text(" --> ".into()),
                        Comment(" <!-- {{#def}} ".into()),
                        Text(" \u{123}-->\u{456}".into()),
                        Comment(" 2 ".into()),
                        Comment("".into()),
                        Text(" <!-- quux ".into()),
                    ]
                },
                Text(" <!-- ".into()),
                Replacement {
                    key: "abc".into(),
                    filters: vec!["nc".into()]
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
        PT::from_text("{{#mis}}<!--{{/matched}}-->").unwrap_err();
        PT::from_text("<!--{{#mis}}{{/matched}}-->").unwrap();
        PT::from_text("<!--{{foo}}").unwrap();
        PT::from_text("{{foo}}-->").unwrap();

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

        // make sure filters and so on are round-tripped correctly
        let orig = "foo {{one:two}} {{one:two:three}} {{^baz}} {{/baz}} {{foo:}}";
        let tmpl = PT::from_text(orig).unwrap();
        assert_eq!(orig, &tmpl.template_to_string());

        let orig =
            "foo {{one:two}} <!--<!--abc {{^def}}-->--> {{one:two:three}} {{^baz}} <!-- {{/baz}} 🙂 --> {{/baz}} {{foo:}}";
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
        let field_map: FieldMap = ["a", "b", "c"]
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

        // Hardcode comment delimiters into tests to keep them concise
        assert_eq!(COMMENT_START, "<!--");
        assert_eq!(COMMENT_END, "-->");

        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(vec![0, 1].into_iter().collect())
        );

        tmpl = PT::from_text(
            r#"
<!--{{^a}}-->
    {{b}}
<!--{{/a}}-->
{{#c}}
    <!--{{a}}-->
    {{b}}
    <!--{{c}}-->
{{/c}}
"#,
        )
        .unwrap();

        assert_eq!(
            tmpl.requirements(&field_map),
            FieldRequirements::Any(vec![1].into_iter().collect())
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
                Text("\n{{#foo}}\n".into()),
                Replacement {
                    key: "Front".into(),
                    filters: vec![]
                },
                Text("\n{{/foo}}\n".into())
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
            frontside: None,
            card_ord: 1,
            partial_for_python: true,
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

        // Hardcode comment delimiters into tests to keep them concise
        assert_eq!(COMMENT_START, "<!--");
        assert_eq!(COMMENT_END, "-->");

        // commented
        tmpl = PT::from_text(
            "{{^E}}1<!--{{#F}}2{{#B}}{{F}}{{/B}}{{/F}}-->\u{123}<!-- this is a comment -->{{/E}}\u{456}",
        )
        .unwrap();
        assert_eq!(
            tmpl.render(&ctx, &tr).unwrap(),
            vec![FN::Text {
                text:
                    "1<!--{{#F}}2{{#B}}{{F}}{{/B}}{{/F}}-->\u{123}<!-- this is a comment -->\u{456}"
                        .to_owned()
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
        let map: HashMap<_, _> = vec![("E", ""), ("N", "N")]
            .into_iter()
            .map(|r| (r.0, r.1.into()))
            .collect();

        let tr = I18n::template_only();
        use crate::template::RenderedNode as FN;

        let mut req = RenderCardRequest {
            qfmt: "test{{E}}",
            afmt: "",
            field_map: &map,
            card_ord: 1,
            is_cloze: false,
            browser: false,
            tr: &tr,
            partial_render: true,
        };
        let response = super::render_card(req.clone()).unwrap();
        assert_eq!(
            response.qnodes[0],
            FN::Text {
                text: "test".into()
            }
        );
        assert!(response.is_empty);
        if let FN::Text { ref text } = response.qnodes[1] {
            assert!(text.contains("card is blank"));
        } else {
            unreachable!();
        }

        // a popular card template expects {{FrontSide}} to resolve to an empty
        // string on the front side :-(
        req.qfmt = "{{FrontSide}}{{N}}";
        let response = super::render_card(req.clone()).unwrap();
        assert_eq!(
            &response.qnodes,
            &[
                FN::Replacement {
                    field_name: "FrontSide".into(),
                    current_text: "".into(),
                    filters: vec![]
                },
                FN::Text { text: "N".into() }
            ]
        );
        assert!(!response.is_empty);
        req.partial_render = false;
        let response = super::render_card(req.clone()).unwrap();
        assert_eq!(&response.qnodes, &[FN::Text { text: "N".into() }]);
        assert!(!response.is_empty);
    }
}
