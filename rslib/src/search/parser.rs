// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    decks::DeckID,
    err::{AnkiError, Result},
    notetype::NoteTypeID,
};
use lazy_static::lazy_static;
use nom::{
    branch::alt,
    bytes::complete::{escaped, is_not, tag},
    character::complete::{anychar, char, none_of, one_of},
    combinator::{all_consuming, map, map_res, verify},
    sequence::{delimited, preceded, separated_pair},
    {multi::many0, IResult},
};
use regex::{Captures, Regex};
use std::{borrow::Cow, num};

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

type ParseResult<T> = std::result::Result<T, ParseError>;

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
        is_re: bool,
    },
    AddedInDays(u32),
    EditedInDays(u32),
    CardTemplate(TemplateKind<'a>),
    Deck(Cow<'a, str>),
    DeckID(DeckID),
    NoteTypeID(NoteTypeID),
    NoteType(Cow<'a, str>),
    Rated {
        days: u32,
        ease: Option<u8>,
    },
    Tag(Cow<'a, str>),
    Duplicates {
        note_type_id: NoteTypeID,
        text: Cow<'a, str>,
    },
    State(StateKind),
    Flag(u8),
    NoteIDs(&'a str),
    CardIDs(&'a str),
    Property {
        operator: String,
        kind: PropertyKind,
    },
    WholeCollection,
    Regex(Cow<'a, str>),
    NoCombining(Cow<'a, str>),
    WordBoundary(Cow<'a, str>),
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
    UserBuried,
    SchedBuried,
    Suspended,
}

#[derive(Debug, PartialEq)]
pub(super) enum TemplateKind<'a> {
    Ordinal(u16),
    Name(Cow<'a, str>),
}

/// Parse the input string into a list of nodes.
pub(super) fn parse(input: &str) -> Result<Vec<Node>> {
    let input = input.trim();
    if input.is_empty() {
        return Ok(vec![Node::Search(SearchNode::WholeCollection)]);
    }

    let (_, nodes) =
        all_consuming(group_inner)(input).map_err(|_e| AnkiError::SearchError(None))?;
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

    if nodes.is_empty() {
        Err(nom::Err::Error((remaining, nom::error::ErrorKind::Many1)))
    } else if matches!(nodes.last().unwrap(), Node::And | Node::Or) {
        // no trailing and/or
        Err(nom::Err::Failure(("", nom::error::ErrorKind::NoneOf)))
    } else {
        // chomp any trailing whitespace
        let (remaining, _) = whitespace0(remaining)?;

        Ok((remaining, nodes))
    }
}

fn whitespace0(s: &str) -> IResult<&str, Vec<char>> {
    many0(one_of(" \u{3000}"))(s)
}

/// Optional leading space, then a (negated) group or text
fn node(s: &str) -> IResult<&str, Node> {
    preceded(whitespace0, alt((negated_node, group, text)))(s)
}

fn negated_node(s: &str) -> IResult<&str, Node> {
    map(preceded(char('-'), alt((group, text))), |node| {
        Node::Not(Box::new(node))
    })(s)
}

/// Either quoted or unquoted text
fn text(s: &str) -> IResult<&str, Node> {
    alt((quoted_term, partially_quoted_term, unquoted_term))(s)
}

/// Determine if text is a qualified search, and handle escaped chars.
fn search_node_for_text(s: &str) -> ParseResult<SearchNode> {
    let (tail, head) = escaped(is_not(r":\"), '\\', anychar)(s)?;
    if tail.is_empty() {
        Ok(SearchNode::UnqualifiedText(unescape(head)?))
    } else {
        search_node_for_text_with_argument(head, &tail[1..])
    }
}

/// Unquoted text, terminated by whitespace or unescaped ", ( or )
fn unquoted_term(s: &str) -> IResult<&str, Node> {
    map_res(
        verify(
            escaped(is_not("\"() \u{3000}\\"), '\\', none_of(" \u{3000}")),
            |s: &str| !s.is_empty(),
        ),
        |text: &str| -> ParseResult<Node> {
            Ok(if text.eq_ignore_ascii_case("or") {
                Node::Or
            } else if text.eq_ignore_ascii_case("and") {
                Node::And
            } else {
                Node::Search(search_node_for_text(text)?)
            })
        },
    )(s)
}

/// Quoted text, including the outer double quotes.
fn quoted_term(s: &str) -> IResult<&str, Node> {
    map_res(quoted_term_str, |o| -> ParseResult<Node> {
        Ok(Node::Search(search_node_for_text(o)?))
    })(s)
}

fn quoted_term_str(s: &str) -> IResult<&str, &str> {
    delimited(char('"'), quoted_term_inner, char('"'))(s)
}

/// Quoted text, terminated by a non-escaped double quote
fn quoted_term_inner(s: &str) -> IResult<&str, &str> {
    verify(escaped(is_not(r#""\"#), '\\', anychar), |s: &str| {
        !s.is_empty()
    })(s)
}

/// eg deck:"foo bar" - quotes must come after the :
fn partially_quoted_term(s: &str) -> IResult<&str, Node> {
    map_res(
        separated_pair(
            verify(
                escaped(is_not("\"(): \u{3000}\\"), '\\', none_of(": \u{3000}")),
                |s: &str| !s.is_empty(),
            ),
            char(':'),
            quoted_term_str,
        ),
        |p| match search_node_for_text_with_argument(p.0, p.1) {
            Ok(search) => Ok(Node::Search(search)),
            Err(e) => Err(e),
        },
    )(s)
}

/// Convert a colon-separated key/val pair into the relevant search type.
fn search_node_for_text_with_argument<'a>(
    key: &'a str,
    val: &'a str,
) -> ParseResult<SearchNode<'a>> {
    Ok(match key.to_ascii_lowercase().as_str() {
        "added" => SearchNode::AddedInDays(val.parse()?),
        "edited" => SearchNode::EditedInDays(val.parse()?),
        "deck" => SearchNode::Deck(unescape(val)?),
        "note" => SearchNode::NoteType(unescape(val)?),
        "tag" => SearchNode::Tag(unescape(val)?),
        "mid" => SearchNode::NoteTypeID(val.parse()?),
        "nid" => SearchNode::NoteIDs(check_id_list(val)?),
        "cid" => SearchNode::CardIDs(check_id_list(val)?),
        "did" => SearchNode::DeckID(val.parse()?),
        "card" => parse_template(val)?,
        "is" => parse_state(val)?,
        "flag" => parse_flag(val)?,
        "rated" => parse_rated(val)?,
        "dupe" => parse_dupes(val)?,
        "prop" => parse_prop(val)?,
        "re" => SearchNode::Regex(unescape_quotes(val)),
        "nc" => SearchNode::NoCombining(unescape(val)?),
        "w" => SearchNode::WordBoundary(unescape(val)?),
        // anything else is a field search
        _ => parse_single_field(key, val)?,
    })
}

/// ensure a list of ids contains only numbers and commas, returning unchanged if true
/// used by nid: and cid:
fn check_id_list(s: &str) -> ParseResult<&str> {
    lazy_static! {
        static ref RE: Regex = Regex::new(r"^(\d+,)*\d+$").unwrap();
    }
    if RE.is_match(s) {
        Ok(s)
    } else {
        Err(ParseError {})
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
        "buried-manually" => UserBuried,
        "buried-sibling" => SchedBuried,
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
/// second arg must be between 0-4
fn parse_rated(val: &str) -> ParseResult<SearchNode<'static>> {
    let mut it = val.splitn(2, ':');
    let days = it.next().unwrap().parse()?;
    let ease = match it.next() {
        Some(v) => {
            let n: u8 = v.parse()?;
            if n < 5 {
                Some(n)
            } else {
                return Err(ParseError {});
            }
        }
        None => None,
    };

    Ok(SearchNode::Rated { days, ease })
}

/// eg dupes:1231,hello
fn parse_dupes(val: &str) -> ParseResult<SearchNode> {
    let mut it = val.splitn(2, ',');
    let mid: NoteTypeID = it.next().unwrap().parse()?;
    let text = it.next().ok_or(ParseError {})?;
    Ok(SearchNode::Duplicates {
        note_type_id: mid,
        text: unescape_quotes(text),
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

fn parse_template(val: &str) -> ParseResult<SearchNode> {
    Ok(SearchNode::CardTemplate(match val.parse::<u16>() {
        Ok(n) => TemplateKind::Ordinal(n.max(1) - 1),
        Err(_) => TemplateKind::Name(unescape(val)?),
    }))
}

fn parse_single_field<'a>(key: &'a str, val: &'a str) -> ParseResult<SearchNode<'a>> {
    Ok(if let Some(stripped) = val.strip_prefix("re:") {
        SearchNode::SingleField {
            field: unescape(key)?,
            text: unescape_quotes(stripped),
            is_re: true,
        }
    } else {
        SearchNode::SingleField {
            field: unescape(key)?,
            text: unescape(val)?,
            is_re: false,
        }
    })
}

/// For strings without unescaped ", convert \" to "
fn unescape_quotes(s: &str) -> Cow<str> {
    if s.contains('"') {
        s.replace(r#"\""#, "\"").into()
    } else {
        s.into()
    }
}

/// Unescape chars with special meaning to the parser.
fn unescape(txt: &str) -> ParseResult<Cow<str>> {
    if is_invalid_escape(txt) {
        Err(ParseError {})
    } else if is_parser_escape(txt) {
        lazy_static! {
            static ref RE: Regex = Regex::new(r#"\\[\\":()-]"#).unwrap();
        }
        Ok(RE.replace_all(&txt, |caps: &Captures| match &caps[0] {
            r"\\" => r"\\",
            "\\\"" => "\"",
            r"\:" => ":",
            r"\(" => "(",
            r"\)" => ")",
            r"\-" => "-",
            _ => unreachable!(),
        }))
    } else {
        Ok(txt.into())
    }
}

/// Check string for invalid escape sequences.
fn is_invalid_escape(txt: &str) -> bool {
    // odd number of \s not followed by an escapable character
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r#"(?x)
            (?:^|[^\\])         # not a backslash
            (?:\\\\)*           # even number of backslashes
            \\                  # single backslash
            (?:[^\\":*_()-]|$)  # anything but an escapable char
            "#
        )
        .unwrap();
    }

    RE.is_match(txt)
}

/// Check string for escape sequences handled by the parser: ":()-
fn is_parser_escape(txt: &str) -> bool {
    // odd number of \s followed by a char with special meaning to the parser
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r#"(?x)
            (?:^|[^\\])     # not a backslash
            (?:\\\\)*       # even number of backslashes
            \\              # single backslash
            [":()-]         # parser escape
            "#
        )
        .unwrap();
    }

    RE.is_match(txt)
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn parsing() -> Result<()> {
        use Node::*;
        use SearchNode::*;

        assert_eq!(parse("")?, vec![Search(SearchNode::WholeCollection)]);
        assert_eq!(parse("  ")?, vec![Search(SearchNode::WholeCollection)]);

        // leading/trailing boolean operators
        assert!(parse("foo and").is_err());
        assert!(parse("and foo").is_err());
        assert!(parse("and").is_err());

        // leading/trailing/interspersed whitespace
        assert_eq!(
            parse("  t   t2  ")?,
            vec![
                Search(UnqualifiedText("t".into())),
                And,
                Search(UnqualifiedText("t2".into()))
            ]
        );

        // including in groups
        assert_eq!(
            parse("(  t   t2  )")?,
            vec![Group(vec![
                Search(UnqualifiedText("t".into())),
                And,
                Search(UnqualifiedText("t2".into()))
            ])]
        );

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
                        text: "bar baz".into(),
                        is_re: false,
                    })
                ]))),
                Or,
                Search(UnqualifiedText("test".into()))
            ]
        );

        assert_eq!(
            parse("foo:re:bar")?,
            vec![Search(SingleField {
                field: "foo".into(),
                text: "bar".into(),
                is_re: true
            })]
        );

        // escaping is independent of quotation
        assert_eq!(
            parse(r#""field:va\"lue""#)?,
            vec![Search(SingleField {
                field: "field".into(),
                text: "va\"lue".into(),
                is_re: false
            })]
        );
        assert_eq!(parse(r#""field:va\"lue""#)?, parse(r#"field:"va\"lue""#)?,);
        assert_eq!(parse(r#""field:va\"lue""#)?, parse(r#"field:va\"lue"#)?,);

        // only \":()-*_ are escapable
        assert!(parse(r"\").is_err());
        assert!(parse(r"\a").is_err());
        assert!(parse(r"\%").is_err());

        // parser unescapes ":()-
        assert_eq!(
            parse(r#"\"\:\(\)\-"#)?,
            vec![Search(UnqualifiedText(r#"":()-"#.into())),]
        );

        // parser doesn't unescape unescape \*_
        assert_eq!(
            parse(r#"\\\*\_"#)?,
            vec![Search(UnqualifiedText(r#"\\\*\_"#.into())),]
        );

        // escaping parentheses is optional (only) inside quotes
        assert_eq!(parse(r#""\)\(""#), parse(r#"")(""#));
        assert!(parse(")(").is_err());

        // escaping : is optional if it is preceded by another :
        assert!(parse(":test").is_err());
        assert!(parse(":").is_err());
        assert_eq!(parse("field:val:ue"), parse(r"field:val\:ue"));
        assert_eq!(parse(r#""field:val:ue""#), parse(r"field:val\:ue"));
        assert_eq!(parse(r#"field:"val:ue""#), parse(r"field:val\:ue"));

        // escaping - is optional if it cannot be mistaken for a negator
        assert_eq!(parse("-"), parse(r"\-"));
        assert_eq!(parse("A-"), parse(r"A\-"));
        assert_eq!(parse(r#""-A""#), parse(r"\-A"));
        assert_ne!(parse("-A"), parse(r"\-A"));

        // any character should be escapable on the right side of re:
        assert_eq!(
            parse(r#""re:\btest\%""#)?,
            vec![Search(Regex(r"\btest\%".into()))]
        );

        // no exceptions for escaping "
        assert_eq!(
            parse(r#"re:te\"st"#)?,
            vec![Search(Regex(r#"te"st"#.into()))]
        );
        assert!(parse(r#"re:te"st"#).is_err());

        // spaces are optional if node separation is clear
        assert_eq!(parse(r#"a"b"(c)"#)?, parse("a b (c)")?);

        assert_eq!(parse("added:3")?, vec![Search(AddedInDays(3))]);
        assert_eq!(
            parse("card:front")?,
            vec![Search(CardTemplate(TemplateKind::Name("front".into())))]
        );
        assert_eq!(
            parse("card:3")?,
            vec![Search(CardTemplate(TemplateKind::Ordinal(2)))]
        );
        // 0 must not cause a crash due to underflow
        assert_eq!(
            parse("card:0")?,
            vec![Search(CardTemplate(TemplateKind::Ordinal(0)))]
        );
        assert_eq!(parse("deck:default")?, vec![Search(Deck("default".into()))]);
        assert_eq!(
            parse("deck:\"default one\"")?,
            vec![Search(Deck("default one".into()))]
        );

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
