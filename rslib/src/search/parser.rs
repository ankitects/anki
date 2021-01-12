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
    bytes::complete::{escaped, is_not, tag, tag_no_case},
    character::complete::{anychar, char, none_of, one_of},
    combinator::{all_consuming, map, map_parser, verify},
    error::{ErrorKind as NomErrorKind, ParseError as NomParseError},
    multi::many0,
    sequence::{delimited, preceded, separated_pair},
    Err::Failure,
};
use regex::{Captures, Regex};
use std::borrow::Cow;

#[derive(Debug)]
enum ParseError<'a> {
    Anki(&'a str, ErrorKind),
    Nom(&'a str, NomErrorKind),
}

#[derive(Debug)]
enum ErrorKind {
    MisplacedAnd,
    MisplacedOr,
    EmptyGroup,
    UnknownEscape(String),
    InvalidIdList,
    InvalidState,
    InvalidFlag,
    InvalidAdded,
    InvalidEdited,
    InvalidRatedDays,
    InvalidRatedEase,
    InvalidDupesMid,
    InvalidDupesText,
    InvalidPropProperty,
    InvalidPropOperator,
    InvalidPropFloat,
    InvalidPropInteger,
    InvalidPropUnsigned,
    InvalidDid,
    InvalidMid,
}

type IResult<'a, O> = std::result::Result<(&'a str, O), nom::Err<ParseError<'a>>>;
type ParseResult<'a, O> = std::result::Result<O, nom::Err<ParseError<'a>>>;

impl<'a> NomParseError<&'a str> for ParseError<'a> {
    fn from_error_kind(input: &'a str, kind: NomErrorKind) -> Self {
        ParseError::Nom(input, kind)
    }

    fn append(_: &str, _: NomErrorKind, other: Self) -> Self {
        other
    }
}

#[derive(Debug, PartialEq)]
pub enum Node<'a> {
    And,
    Or,
    Not(Box<Node<'a>>),
    Group(Vec<Node<'a>>),
    Search(SearchNode<'a>),
}

#[derive(Debug, PartialEq, Clone)]
pub enum SearchNode<'a> {
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

#[derive(Debug, PartialEq, Clone)]
pub enum PropertyKind {
    Due(i32),
    Interval(u32),
    Reps(u32),
    Lapses(u32),
    Ease(f32),
    Position(u32),
}

#[derive(Debug, PartialEq, Clone)]
pub enum StateKind {
    New,
    Review,
    Learning,
    Due,
    Buried,
    UserBuried,
    SchedBuried,
    Suspended,
}

#[derive(Debug, PartialEq, Clone)]
pub enum TemplateKind<'a> {
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
        all_consuming(group_inner)(input).map_err(|e| { dbg!(e); AnkiError::SearchError(None) })?;
    Ok(nodes)
}

/// One or more nodes inside brackets, er 'one OR two -three'
fn group_inner(input: &str) -> IResult<Vec<Node>> {
    let mut remaining = input;
    let mut nodes = vec![];

    loop {
        match node(remaining) {
            Ok((rem, node)) => {
                remaining = rem;

                if nodes.len() % 2 == 0 {
                    // before adding the node, if the length is even then the node
                    // must not be a boolean
                    if node == Node::And {
                        return Err(Failure(ParseError::Anki(input, ErrorKind::MisplacedAnd)));
                    } else if node == Node::Or {
                        return Err(Failure(ParseError::Anki(input, ErrorKind::MisplacedOr)));
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
                // fixme: add context to failure
                _ => return Err(e),
            },
        };
    }

    if nodes.is_empty() {
        Err(Failure(ParseError::Anki(input, ErrorKind::EmptyGroup)))
    } else if nodes.last().unwrap() == &Node::And {
        Err(Failure(ParseError::Anki(input, ErrorKind::MisplacedAnd)))
    } else if nodes.last().unwrap() == &Node::Or {
        Err(Failure(ParseError::Anki(input, ErrorKind::MisplacedOr)))
    } else {
        // chomp any trailing whitespace
        let (remaining, _) = whitespace0(remaining)?;

        Ok((remaining, nodes))
    }
}

fn whitespace0(s: &str) -> IResult<Vec<char>> {
    many0(one_of(" \u{3000}"))(s)
}

/// Optional leading space, then a (negated) group or text
fn node(s: &str) -> IResult<Node> {
    preceded(whitespace0, alt((negated_node, group, text)))(s)
}

fn negated_node(s: &str) -> IResult<Node> {
    map(preceded(char('-'), alt((group, text))), |node| {
        Node::Not(Box::new(node))
    })(s)
}

/// One or more nodes surrounded by brackets, eg (one OR two)
fn group(s: &str) -> IResult<Node> {
    map(delimited(char('('), group_inner, char(')')), |nodes| {
        Node::Group(nodes)
    })(s)
}

/// Either quoted or unquoted text
fn text(s: &str) -> IResult<Node> {
    alt((quoted_term, partially_quoted_term, unquoted_term))(s)
}

/// Quoted text, including the outer double quotes.
fn quoted_term(s: &str) -> IResult<Node> {
    map_parser(quoted_term_str, map(search_node_for_text, Node::Search))(s)
}

/// eg deck:"foo bar" - quotes must come after the :
fn partially_quoted_term(s: &str) -> IResult<Node> {
    let (remaining, (key, val)) = separated_pair(
        verify(
            escaped(is_not("\"(): \u{3000}\\"), '\\', none_of(": \u{3000}")),
            |s: &str| !s.is_empty(),
        ),
        char(':'),
        quoted_term_str,
    )(s)?;
    let (_, node) = search_node_for_text_with_argument(key, val)?;

    Ok((remaining, Node::Search(node)))
}

/// Unquoted text, terminated by whitespace or unescaped ", ( or )
fn unquoted_term(s: &str) -> IResult<Node> {
    map_parser(
        verify(
            escaped(is_not("\"() \u{3000}\\"), '\\', none_of(" \u{3000}")),
            |s: &str| !s.is_empty(),
        ),
        alt((
            map(all_consuming(tag_no_case("and")), |_| Node::And),
            map(all_consuming(tag_no_case("or")), |_| Node::Or),
            map(search_node_for_text, Node::Search),
        )),
    )(s)
}

/// Non-empty string delimited by unescaped double quotes.
fn quoted_term_str(s: &str) -> IResult<&str> {
    unempty(delimited(
        char('"'),
        escaped(is_not(r#""\"#), '\\', anychar),
        char('"'),
    )(s))
}

fn unempty<'a>(res: IResult<'a, &'a str>) -> IResult<'a, &'a str> {
    if let Ok((_, parsed)) = res {
        if parsed.is_empty() {
            Err(nom::Err::Failure(ParseError::Anki(
                "",
                ErrorKind::EmptyGroup,
            )))
        } else {
            res
        }
    } else {
        res
    }
}

/// Determine if text is a qualified search, and handle escaped chars.
fn search_node_for_text(s: &str) -> IResult<SearchNode> {
    let (tail, head) = escaped(is_not(r":\"), '\\', anychar)(s)?;
    if tail.is_empty() {
        Ok(("", SearchNode::UnqualifiedText(unescape(head)?)))
    } else {
        search_node_for_text_with_argument(head, &tail[1..])
    }
}

/// Convert a colon-separated key/val pair into the relevant search type.
fn search_node_for_text_with_argument<'a>(
    key: &'a str,
    val: &'a str,
) -> IResult<'a, SearchNode<'a>> {
    Ok((
        "",
        match key.to_ascii_lowercase().as_str() {
            "deck" => SearchNode::Deck(unescape(val)?),
            "note" => SearchNode::NoteType(unescape(val)?),
            "tag" => SearchNode::Tag(unescape(val)?),
            "card" => parse_template(val)?,
            "flag" => parse_flag(val)?,
            "prop" => parse_prop(val)?,
            "added" => parse_added(val)?,
            "edited" => parse_edited(val)?,
            "rated" => parse_rated(val)?,
            "is" => parse_state(val)?,
            "did" => parse_did(val)?,
            "mid" => parse_mid(val)?,
            "nid" => SearchNode::NoteIDs(check_id_list(val)?),
            "cid" => SearchNode::CardIDs(check_id_list(val)?),
            "re" => SearchNode::Regex(unescape_quotes(val)),
            "nc" => SearchNode::NoCombining(unescape(val)?),
            "w" => SearchNode::WordBoundary(unescape(val)?),
            "dupe" => parse_dupes(val)?,
            // anything else is a field search
            _ => parse_single_field(key, val)?,
        },
    ))
}

fn parse_template(s: &str) -> ParseResult<SearchNode> {
    Ok(SearchNode::CardTemplate(match s.parse::<u16>() {
        Ok(n) => TemplateKind::Ordinal(n.max(1) - 1),
        Err(_) => TemplateKind::Name(unescape(s)?),
    }))
}

/// flag:0-4
fn parse_flag(s: &str) -> ParseResult<SearchNode> {
    if let Ok(flag) = s.parse::<u8>() {
        if flag > 4 {
            Err(Failure(ParseError::Anki(s, ErrorKind::InvalidFlag)))
        } else {
            Ok(SearchNode::Flag(flag))
        }
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidEdited)))
    }
}

/// eg prop:ivl>3, prop:ease!=2.5
fn parse_prop(s: &str) -> ParseResult<SearchNode<'static>> {
    let (tail, prop) = alt::<&str, &str, ParseError, _>((
        tag("ivl"),
        tag("due"),
        tag("reps"),
        tag("lapses"),
        tag("ease"),
        tag("pos"),
    ))(s)
    .map_err(|_| Failure(ParseError::Anki(s, ErrorKind::InvalidPropProperty)))?;

    let (num, operator) = alt::<&str, &str, ParseError, _>((
        tag("<="),
        tag(">="),
        tag("!="),
        tag("="),
        tag("<"),
        tag(">"),
    ))(tail)
    .map_err(|_| Failure(ParseError::Anki(s, ErrorKind::InvalidPropOperator)))?;

    let kind = if prop == "ease" {
        if let Ok(f) = num.parse::<f32>() {
            PropertyKind::Ease(f)
        } else {
            return Err(Failure(ParseError::Anki(s, ErrorKind::InvalidPropFloat)));
        }
    } else if prop == "due" {
        if let Ok(i) = num.parse::<i32>() {
            PropertyKind::Due(i)
        } else {
            return Err(Failure(ParseError::Anki(s, ErrorKind::InvalidPropInteger)));
        }
    } else if let Ok(u) = num.parse::<u32>() {
        match prop {
            "ivl" => PropertyKind::Interval(u),
            "reps" => PropertyKind::Reps(u),
            "lapses" => PropertyKind::Lapses(u),
            "pos" => PropertyKind::Position(u),
            _ => unreachable!(),
        }
    } else {
        return Err(Failure(ParseError::Anki(s, ErrorKind::InvalidPropUnsigned)));
    };

    Ok(SearchNode::Property {
        operator: operator.to_string(),
        kind,
    })
}

/// eg added:1
fn parse_added(s: &str) -> ParseResult<SearchNode> {
    if let Ok(days) = s.parse::<u32>() {
        Ok(SearchNode::AddedInDays(days.max(1)))
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidAdded)))
    }
}

/// eg edited:1
fn parse_edited(s: &str) -> ParseResult<SearchNode> {
    if let Ok(days) = s.parse::<u32>() {
        Ok(SearchNode::EditedInDays(days.max(1)))
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidEdited)))
    }
}

/// eg rated:3 or rated:10:2
/// second arg must be between 0-4
fn parse_rated(s: &str) -> ParseResult<SearchNode> {
    let mut it = s.splitn(2, ':');
    if let Ok(d) = it.next().unwrap().parse::<u32>() {
        let days = d.max(1).min(365);
        let ease = if let Some(tail) = it.next() {
            if let Ok(u) = tail.parse::<u8>() {
                if u < 5 {
                    Some(u)
                } else {
                    return Err(Failure(ParseError::Anki(s, ErrorKind::InvalidRatedEase)));
                }
            } else {
                return Err(Failure(ParseError::Anki(s, ErrorKind::InvalidRatedEase)));
            }
        } else {
            None
        };
        Ok(SearchNode::Rated { days, ease })
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidRatedDays)))
    }
}

/// eg is:due
fn parse_state(s: &str) -> ParseResult<SearchNode> {
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
        _ => return Err(Failure(ParseError::Anki(s, ErrorKind::InvalidState))),
    }))
}

fn parse_did(s: &str) -> ParseResult<SearchNode> {
    if let Ok(did) = s.parse() {
        Ok(SearchNode::DeckID(did))
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidDid)))
    }
}

fn parse_mid(s: &str) -> ParseResult<SearchNode> {
    if let Ok(mid) = s.parse() {
        Ok(SearchNode::NoteTypeID(mid))
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidMid)))
    }
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
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidIdList)))
    }
}

/// eg dupes:1231,hello
fn parse_dupes(s: &str) -> ParseResult<SearchNode> {
    let mut it = s.splitn(2, ',');
    if let Ok(mid) = it.next().unwrap().parse::<NoteTypeID>() {
        if let Some(text) = it.next() {
            Ok(SearchNode::Duplicates {
                note_type_id: mid,
                text: unescape_quotes(text),
            })
        } else {
            Err(Failure(ParseError::Anki(s, ErrorKind::InvalidDupesText)))
        }
    } else {
        Err(Failure(ParseError::Anki(s, ErrorKind::InvalidDupesMid)))
    }
}

fn parse_single_field<'a>(key: &'a str, val: &'a str) -> ParseResult<'a, SearchNode<'a>> {
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
    if let Some(seq) = invalid_escape_sequence(txt) {
        Err(Failure(ParseError::Anki(
            txt,
            ErrorKind::UnknownEscape(seq),
        )))
    } else {
        Ok(if is_parser_escape(txt) {
            lazy_static! {
                static ref RE: Regex = Regex::new(r#"\\[\\":()-]"#).unwrap();
            }
            RE.replace_all(&txt, |caps: &Captures| match &caps[0] {
                r"\\" => r"\\",
                "\\\"" => "\"",
                r"\:" => ":",
                r"\(" => "(",
                r"\)" => ")",
                r"\-" => "-",
                _ => unreachable!(),
            })
        } else {
            txt.into()
        })
    }
}

/// Return invalid escape sequence if any.
fn invalid_escape_sequence(txt: &str) -> Option<String> {
    // odd number of \s not followed by an escapable character
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r#"(?x)
            (?:^|[^\\])         # not a backslash
            (?:\\\\)*           # even number of backslashes
            (\\                 # single backslash
            (?:[^\\":*_()-]|$)) # anything but an escapable char
            "#
        )
        .unwrap();
    }
    let caps = RE.captures(txt)?;

    Some(caps[1].to_string())
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
            vec![Search(NoteIDs("1237123712,2,3"))]
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
