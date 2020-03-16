// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::types::ObjID;
use nom::branch::alt;
use nom::bytes::complete::{escaped, is_not, tag, take_while1};
use nom::character::complete::{char, one_of};
use nom::character::is_digit;
use nom::combinator::{all_consuming, map, map_res};
use nom::sequence::{delimited, preceded};
use nom::{multi::many0, IResult};
use std::{borrow::Cow, num};

// fixme: need to preserve \ when used twice in string

struct ParseError {}

impl From<num::ParseIntError> for ParseError {
    fn from(_: num::ParseIntError) -> Self {
        ParseError {}
    }
}

impl From<num::ParseFloatError> for ParseError {
    fn from(_: num::ParseFloatError) -> Self {
        ParseError {}
    }
}

impl<I> From<nom::Err<(I, nom::error::ErrorKind)>> for ParseError {
    fn from(_: nom::Err<(I, nom::error::ErrorKind)>) -> Self {
        ParseError {}
    }
}

type ParseResult<T> = Result<T, ParseError>;

#[derive(Debug, PartialEq)]
pub(super) enum Node<'a> {
    And,
    Or,
    Not(Box<Node<'a>>),
    Group(Vec<Node<'a>>),
    Search(SearchNode<'a>),
}

#[derive(Debug, PartialEq)]
pub(super) enum SearchNode<'a> {
    // text without a colon
    UnqualifiedText(Cow<'a, str>),
    // foo:bar, where foo doesn't match a term below
    SingleField {
        field: Cow<'a, str>,
        text: Cow<'a, str>,
    },
    AddedInDays(u32),
    CardTemplate(Cow<'a, str>),
    Deck(Cow<'a, str>),
    NoteTypeID(ObjID),
    NoteType(Cow<'a, str>),
    Rated {
        days: u32,
        ease: Option<u8>,
    },
    Tag(Cow<'a, str>),
    Duplicates {
        note_type_id: ObjID,
        text: String,
    },
    State(StateKind),
    Flag(u8),
    NoteIDs(Cow<'a, str>),
    CardIDs(Cow<'a, str>),
    Property {
        operator: String,
        kind: PropertyKind,
    },
}

#[derive(Debug, PartialEq)]
pub(super) enum PropertyKind {
    Due(i32),
    Interval(u32),
    Reps(u32),
    Lapses(u32),
    Ease(f32),
}

#[derive(Debug, PartialEq)]
pub(super) enum StateKind {
    New,
    Review,
    Learning,
    Due,
    Buried,
    Suspended,
}

/// Parse the input string into a list of nodes.
#[allow(dead_code)]
pub(super) fn parse(input: &str) -> std::result::Result<Vec<Node>, String> {
    let (_, nodes) = all_consuming(group_inner)(input).map_err(|e| format!("{:?}", e))?;
    Ok(nodes)
}

/// One or more nodes surrounded by brackets, eg (one OR two)
fn group(s: &str) -> IResult<&str, Node> {
    map(delimited(char('('), group_inner, char(')')), |nodes| {
        Node::Group(nodes)
    })(s)
}

/// One or more nodes inside brackets, er 'one OR two -three'
fn group_inner(input: &str) -> IResult<&str, Vec<Node>> {
    let mut remaining = input;
    let mut nodes = vec![];

    loop {
        match node(remaining) {
            Ok((rem, node)) => {
                remaining = rem;

                if nodes.len() % 2 == 0 {
                    // before adding the node, if the length is even then the node
                    // must not be a boolean
                    if matches!(node, Node::And | Node::Or) {
                        return Err(nom::Err::Failure(("", nom::error::ErrorKind::NoneOf)));
                    }
                } else {
                    // if the length is odd, the next item must be a boolean. if it's
                    // not, add an implicit and
                    if !matches!(node, Node::And | Node::Or) {
                        nodes.push(Node::And);
                    }
                }
                nodes.push(node);
            }
            Err(e) => match e {
                nom::Err::Error(_) => break,
                _ => return Err(e),
            },
        };
    }

    Ok((remaining, nodes))
}

/// Optional leading space, then a (negated) group or text
fn node(s: &str) -> IResult<&str, Node> {
    let whitespace0 = many0(one_of(" \u{3000}"));
    preceded(whitespace0, alt((negated_node, group, text)))(s)
}

fn negated_node(s: &str) -> IResult<&str, Node> {
    map(preceded(char('-'), alt((group, text))), |node| {
        Node::Not(Box::new(node))
    })(s)
}

/// Either quoted or unquoted text
fn text(s: &str) -> IResult<&str, Node> {
    alt((quoted_term, unquoted_term))(s)
}

/// Determine if text is a qualified search, and handle escaped chars.
fn search_node_for_text(s: &str) -> ParseResult<SearchNode> {
    let mut it = s.splitn(2, ':');
    let (head, tail) = (
        without_escapes(it.next().unwrap()),
        it.next().map(without_escapes),
    );

    if let Some(tail) = tail {
        search_node_for_text_with_argument(head, tail)
    } else {
        Ok(SearchNode::UnqualifiedText(head))
    }
}

/// Strip the \ escaping character
fn without_escapes(s: &str) -> Cow<str> {
    if s.find('\\').is_some() {
        s.replace('\\', "").into()
    } else {
        s.into()
    }
}

/// Unquoted text, terminated by a space or )
fn unquoted_term(s: &str) -> IResult<&str, Node> {
    map_res(
        take_while1(|c| c != ' ' && c != ')'),
        |text: &str| -> ParseResult<Node> {
            Ok(if text.len() == 2 && text.to_ascii_lowercase() == "or" {
                Node::Or
            } else if text.len() == 3 && text.to_ascii_lowercase() == "and" {
                Node::And
            } else {
                Node::Search(search_node_for_text(text)?)
            })
        },
    )(s)
}

/// Quoted text, including the outer double quotes.
fn quoted_term(s: &str) -> IResult<&str, Node> {
    delimited(char('"'), quoted_term_inner, char('"'))(s)
}

/// Quoted text, terminated by a non-escaped double quote
/// Can escape " and \
fn quoted_term_inner(s: &str) -> IResult<&str, Node> {
    map_res(
        escaped(is_not(r#""\"#), '\\', one_of(r#""\"#)),
        |o| -> ParseResult<Node> { Ok(Node::Search(search_node_for_text(o)?)) },
    )(s)
}

/// Convert a colon-separated key/val pair into the relevant search type.
fn search_node_for_text_with_argument<'a>(
    key: Cow<'a, str>,
    val: Cow<'a, str>,
) -> ParseResult<SearchNode<'a>> {
    Ok(match key.to_ascii_lowercase().as_str() {
        "added" => SearchNode::AddedInDays(val.parse()?),
        "card" => SearchNode::CardTemplate(val),
        "deck" => SearchNode::Deck(val),
        "note" => SearchNode::NoteType(val),
        "tag" => SearchNode::Tag(val),
        "mid" => SearchNode::NoteTypeID(val.parse()?),
        "nid" => SearchNode::NoteIDs(check_id_list(val)?),
        "cid" => SearchNode::CardIDs(check_id_list(val)?),
        "is" => parse_state(val.as_ref())?,
        "flag" => parse_flag(val.as_ref())?,
        "rated" => parse_rated(val.as_ref())?,
        "dupes" => parse_dupes(val.as_ref())?,
        "prop" => parse_prop(val.as_ref())?,

        // anything else is a field search
        _ => SearchNode::SingleField {
            field: key,
            text: val,
        },
    })
}

/// ensure a list of ids contains only numbers and commas, returning unchanged if true
/// used by nid: and cid:
fn check_id_list(s: Cow<str>) -> ParseResult<Cow<str>> {
    if s.is_empty() || s.as_bytes().iter().any(|&c| !is_digit(c) && c != b',') {
        Err(ParseError {})
    } else {
        Ok(s)
    }
}

/// eg is:due
fn parse_state(s: &str) -> ParseResult<SearchNode<'static>> {
    use StateKind::*;
    Ok(SearchNode::State(match s {
        "new" => New,
        "review" => Review,
        "learn" => Learning,
        "due" => Due,
        "buried" => Buried,
        "suspended" => Suspended,
        _ => return Err(ParseError {}),
    }))
}

/// flag:0-4
fn parse_flag(s: &str) -> ParseResult<SearchNode<'static>> {
    let n: u8 = s.parse()?;
    if n > 4 {
        Err(ParseError {})
    } else {
        Ok(SearchNode::Flag(n))
    }
}

/// eg rated:3 or rated:10:2
fn parse_rated(val: &str) -> ParseResult<SearchNode<'static>> {
    let mut it = val.splitn(2, ':');
    let days = it.next().unwrap().parse()?;
    let ease = match it.next() {
        Some(v) => Some(v.parse()?),
        None => None,
    };

    Ok(SearchNode::Rated { days, ease })
}

/// eg dupes:1231,hello
fn parse_dupes(val: &str) -> ParseResult<SearchNode<'static>> {
    let mut it = val.splitn(2, ",");
    let mid: ObjID = it.next().unwrap().parse()?;
    let text = it.next().ok_or(ParseError {})?;
    Ok(SearchNode::Duplicates {
        note_type_id: mid,
        text: text.into(),
    })
}

/// eg prop:ivl>3, prop:ease!=2.5
fn parse_prop(val: &str) -> ParseResult<SearchNode<'static>> {
    let (val, key) = alt((
        tag("ivl"),
        tag("due"),
        tag("reps"),
        tag("lapses"),
        tag("ease"),
    ))(val)?;

    let (val, operator) = alt((
        tag("<="),
        tag(">="),
        tag("!="),
        tag("="),
        tag("<"),
        tag(">"),
    ))(val)?;

    let kind = if key == "ease" {
        let num: f32 = val.parse()?;
        PropertyKind::Ease(num)
    } else if key == "due" {
        let num: i32 = val.parse()?;
        PropertyKind::Due(num)
    } else {
        let num: u32 = val.parse()?;
        match key {
            "ivl" => PropertyKind::Interval(num),
            "reps" => PropertyKind::Reps(num),
            "lapses" => PropertyKind::Lapses(num),
            _ => unreachable!(),
        }
    };

    Ok(SearchNode::Property {
        operator: operator.to_string(),
        kind,
    })
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn parsing() -> Result<(), String> {
        use Node::*;
        use SearchNode::*;

        assert_eq!(
            parse(r#"hello  -(world and "foo:bar baz") OR test"#)?,
            vec![
                Search(UnqualifiedText("hello".into())),
                And,
                Not(Box::new(Group(vec![
                    Search(UnqualifiedText("world".into())),
                    And,
                    Search(SingleField {
                        field: "foo".into(),
                        text: "bar baz".into()
                    })
                ]))),
                Or,
                Search(UnqualifiedText("test".into()))
            ]
        );

        assert_eq!(parse("added:3")?, vec![Search(AddedInDays(3))]);
        assert_eq!(
            parse("card:front")?,
            vec![Search(CardTemplate("front".into()))]
        );
        assert_eq!(parse("deck:default")?, vec![Search(Deck("default".into()))]);
        assert_eq!(parse("note:basic")?, vec![Search(NoteType("basic".into()))]);
        assert_eq!(parse("tag:hard")?, vec![Search(Tag("hard".into()))]);
        assert_eq!(
            parse("nid:1237123712,2,3")?,
            vec![Search(NoteIDs("1237123712,2,3".into()))]
        );
        assert!(parse("nid:1237123712_2,3").is_err());
        assert_eq!(parse("is:due")?, vec![Search(State(StateKind::Due))]);
        assert_eq!(parse("flag:3")?, vec![Search(Flag(3))]);
        assert!(parse("flag:-1").is_err());
        assert!(parse("flag:5").is_err());

        assert_eq!(
            parse("prop:ivl>3")?,
            vec![Search(Property {
                operator: ">".into(),
                kind: PropertyKind::Interval(3)
            })]
        );
        assert!(parse("prop:ivl>3.3").is_err());
        assert_eq!(
            parse("prop:ease<=3.3")?,
            vec![Search(Property {
                operator: "<=".into(),
                kind: PropertyKind::Ease(3.3)
            })]
        );

        Ok(())
    }
}
