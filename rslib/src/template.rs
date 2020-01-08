use crate::err::{AnkiError, Result};
use nom;
use nom::branch::alt;
use nom::bytes::complete::tag;
use nom::error::ErrorKind;
use nom::sequence::delimited;
use std::borrow::Cow;
use std::collections::{HashMap, HashSet};

pub type FieldMap<'a> = HashMap<&'a str, u16>;

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
    let start = s.trim();
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

fn tokens(template: &str) -> impl Iterator<Item = Result<Token>> {
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
            Err(e) => Some(Err(AnkiError::parse(format!("{:?}", e)))),
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

impl ParsedTemplate<'_> {
    /// Create a template from the provided text.
    ///
    /// The legacy alternate syntax is not supported, so the provided text
    /// should be run through without_legacy_template_directives() first.
    pub fn from_text(template: &str) -> Result<ParsedTemplate> {
        let mut iter = tokens(template);
        Ok(Self(parse_inner(&mut iter, None)?))
    }
}

fn parse_inner<'a, I: Iterator<Item = Result<Token<'a>>>>(
    iter: &mut I,
    open_tag: Option<&'a str>,
) -> Result<Vec<ParsedNode<'a>>> {
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
                if let Some(open) = open_tag {
                    if open == t {
                        // matching closing tag, move back to parent
                        return Ok(nodes);
                    }
                }
                return Err(AnkiError::parse(format!(
                    "unbalanced closing tag: {:?} / {}",
                    open_tag, t
                )));
            }
        });
    }

    if let Some(open) = open_tag {
        Err(AnkiError::parse(format!("unclosed conditional {}", open)))
    } else {
        Ok(nodes)
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
            NegatedConditional { .. } => {
                // negated conditionals ignored when determining card generation
                continue;
            }
        }
    }

    true
}

// Flattening
//----------------------------------------

#[derive(Debug, PartialEq)]
pub enum FlattenedNode {
    Text {
        text: String,
    },
    /// Filters are in the order they should be applied.
    Replacement {
        field: String,
        filters: Vec<String>,
    },
}

impl ParsedTemplate<'_> {
    /// Resolve conditional replacements, returning a list of nodes.
    ///
    /// This leaves the field replacement (with any filters that were provided)
    /// up to the calling code to handle.
    pub fn flatten(&self, nonempty_fields: &HashSet<&str>) -> Vec<FlattenedNode> {
        let mut flattened = vec![];

        flatten_into(&mut flattened, self.0.as_ref(), nonempty_fields);

        flattened
    }
}

fn flatten_into(
    rendered_nodes: &mut Vec<FlattenedNode>,
    nodes: &[ParsedNode],
    fields: &HashSet<&str>,
) {
    use ParsedNode::*;
    for node in nodes {
        match node {
            Text(t) => {
                if let Some(FlattenedNode::Text { ref mut text }) = rendered_nodes.last_mut() {
                    text.push_str(t)
                } else {
                    rendered_nodes.push(FlattenedNode::Text {
                        text: (*t).to_string(),
                    })
                }
            }
            Replacement { key, filters } => rendered_nodes.push(FlattenedNode::Replacement {
                field: (*key).to_string(),
                filters: filters.iter().map(|&e| e.to_string()).collect(),
            }),
            Conditional { key, children } => {
                if fields.contains(key) {
                    flatten_into(rendered_nodes, children.as_ref(), fields);
                }
            }
            NegatedConditional { key, children } => {
                if !fields.contains(key) {
                    flatten_into(rendered_nodes, children.as_ref(), fields);
                }
            }
        };
    }
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
    use crate::template::{without_legacy_template_directives, FieldRequirements};
    use std::collections::HashSet;
    use std::iter::FromIterator;

    #[test]
    fn test_parsing() {
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
    fn test_nonempty() {
        let fields = HashSet::from_iter(vec!["1", "3"].into_iter());
        let mut tmpl = PT::from_text("{{2}}{{1}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), true);
        tmpl = PT::from_text("{{2}}{{type:cloze:1}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), false);
        tmpl = PT::from_text("{{2}}{{4}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), false);
        tmpl = PT::from_text("{{#3}}{{^2}}{{1}}{{/2}}{{/3}}").unwrap();
        assert_eq!(tmpl.renders_with_fields(&fields), false);
    }

    #[test]
    fn test_requirements() {
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
        assert_eq!(tmpl.requirements(&field_map), FieldRequirements::None);

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
    }

    #[test]
    fn test_alt_syntax() {
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
    fn test_render() {
        let map: HashSet<_> = vec!["F", "B"].into_iter().collect();

        use crate::template::FlattenedNode as FN;
        let mut tmpl = PT::from_text("{{B}}A{{F}}").unwrap();
        assert_eq!(
            tmpl.flatten(&map),
            vec![
                FN::Replacement {
                    field: "B".to_owned(),
                    filters: vec![]
                },
                FN::Text {
                    text: "A".to_owned()
                },
                FN::Replacement {
                    field: "F".to_owned(),
                    filters: vec![]
                },
            ]
        );

        // empty
        tmpl = PT::from_text("{{#E}}A{{/E}}").unwrap();
        assert_eq!(tmpl.flatten(&map), vec![]);

        // missing
        tmpl = PT::from_text("{{^M}}A{{/M}}").unwrap();
        assert_eq!(
            tmpl.flatten(&map),
            vec![FN::Text {
                text: "A".to_owned()
            },]
        );

        // nested
        tmpl = PT::from_text("{{^E}}1{{#F}}2{{#B}}{{F}}{{/B}}{{/F}}{{/E}}").unwrap();
        assert_eq!(
            tmpl.flatten(&map),
            vec![
                FN::Text {
                    text: "12".to_owned()
                },
                FN::Replacement {
                    field: "F".to_owned(),
                    filters: vec![]
                },
            ]
        );

        // filters
        tmpl = PT::from_text("{{one:two:B}}{{three:X}}").unwrap();
        assert_eq!(
            tmpl.flatten(&map),
            vec![
                FN::Replacement {
                    field: "B".to_owned(),
                    filters: vec!["two".to_string(), "one".to_string()]
                },
                FN::Replacement {
                    field: "X".to_owned(),
                    filters: vec!["three".to_string()]
                },
            ]
        );
    }
}
