// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    decks::DeckID,
    err::{ParseError, Result, SearchErrorKind as FailKind},
    notetype::NoteTypeID,
};
use lazy_static::lazy_static;
use nom::{
    branch::alt,
    bytes::complete::{escaped, is_not, tag},
    character::complete::{anychar, char, none_of, one_of},
    combinator::{map, verify},
    error::ErrorKind as NomErrorKind,
    multi::many0,
    sequence::{preceded, separated_pair},
};
use regex::{Captures, Regex};
use std::borrow::Cow;

type IResult<'a, O> = std::result::Result<(&'a str, O), nom::Err<ParseError<'a>>>;
type ParseResult<'a, O> = std::result::Result<O, nom::Err<ParseError<'a>>>;

fn parse_failure(input: &str, kind: FailKind) -> nom::Err<ParseError<'_>> {
    nom::Err::Failure(ParseError::Anki(input, kind))
}

fn parse_error(input: &str) -> nom::Err<ParseError<'_>> {
    nom::Err::Error(ParseError::Anki(input, FailKind::Other(None)))
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
        ease: RatingKind,
    },
    Tag(Cow<'a, str>),
    Duplicates {
        note_type_id: NoteTypeID,
        text: Cow<'a, str>,
    },
    State(StateKind),
    Flag(u8),
    NoteIDs(Cow<'a, str>),
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
    Rated(i32, RatingKind),
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

#[derive(Debug, PartialEq, Clone)]
pub enum RatingKind {
    AnswerButton(u8),
    AnyAnswerButton,
    ManualReschedule,
}

/// Parse the input string into a list of nodes.
pub(super) fn parse(input: &str) -> Result<Vec<Node>> {
    let input = input.trim();
    if input.is_empty() {
        return Ok(vec![Node::Search(SearchNode::WholeCollection)]);
    }

    match group_inner(input) {
        Ok(("", nodes)) => Ok(nodes),
        // unmatched ) is only char not consumed by any node parser
        Ok((remaining, _)) => Err(parse_failure(remaining, FailKind::UnopenedGroup).into()),
        Err(err) => Err(err.into()),
    }
}

/// Zero or more nodes inside brackets, eg 'one OR two -three'.
/// Empty vec must be handled by caller.
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
                        return Err(parse_failure(input, FailKind::MisplacedAnd));
                    } else if node == Node::Or {
                        return Err(parse_failure(input, FailKind::MisplacedOr));
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

    if let Some(last) = nodes.last() {
        match last {
            Node::And => return Err(parse_failure(input, FailKind::MisplacedAnd)),
            Node::Or => return Err(parse_failure(input, FailKind::MisplacedOr)),
            _ => (),
        }
    }
    let (remaining, _) = whitespace0(remaining)?;

    Ok((remaining, nodes))
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
    let (opened, _) = char('(')(s)?;
    let (tail, inner) = group_inner(opened)?;
    if let Some(remaining) = tail.strip_prefix(')') {
        if inner.is_empty() {
            Err(parse_failure(s, FailKind::EmptyGroup))
        } else {
            Ok((remaining, Node::Group(inner)))
        }
    } else {
        Err(parse_failure(s, FailKind::UnclosedGroup))
    }
}

/// Either quoted or unquoted text
fn text(s: &str) -> IResult<Node> {
    alt((quoted_term, partially_quoted_term, unquoted_term))(s)
}

/// Quoted text, including the outer double quotes.
fn quoted_term(s: &str) -> IResult<Node> {
    let (remaining, term) = quoted_term_str(s)?;
    Ok((remaining, Node::Search(search_node_for_text(term)?)))
}

/// eg deck:"foo bar" - quotes must come after the :
fn partially_quoted_term(s: &str) -> IResult<Node> {
    let (remaining, (key, val)) = separated_pair(
        escaped(is_not("\"(): \u{3000}\\"), '\\', none_of(" \u{3000}")),
        char(':'),
        quoted_term_str,
    )(s)?;
    Ok((
        remaining,
        Node::Search(search_node_for_text_with_argument(key, val)?),
    ))
}

/// Unquoted text, terminated by whitespace or unescaped ", ( or )
fn unquoted_term(s: &str) -> IResult<Node> {
    match escaped(is_not("\"() \u{3000}\\"), '\\', none_of(" \u{3000}"))(s) {
        Ok((tail, term)) => {
            if term.is_empty() {
                Err(parse_error(s))
            } else if term.eq_ignore_ascii_case("and") {
                Ok((tail, Node::And))
            } else if term.eq_ignore_ascii_case("or") {
                Ok((tail, Node::Or))
            } else {
                Ok((tail, Node::Search(search_node_for_text(term)?)))
            }
        }
        Err(err) => {
            if let nom::Err::Error((c, NomErrorKind::NoneOf)) = err {
                Err(parse_failure(
                    s,
                    FailKind::UnknownEscape(format!("\\{}", c)),
                ))
            } else if "\"() \u{3000}".contains(s.chars().next().unwrap()) {
                Err(parse_error(s))
            } else {
                // input ends in an odd number of backslashes
                Err(parse_failure(s, FailKind::UnknownEscape('\\'.to_string())))
            }
        }
    }
}

/// Non-empty string delimited by unescaped double quotes.
fn quoted_term_str(s: &str) -> IResult<&str> {
    let (opened, _) = char('"')(s)?;
    if let Ok((tail, inner)) =
        escaped::<_, ParseError, _, _, _, _>(is_not(r#""\"#), '\\', anychar)(opened)
    {
        if let Ok((remaining, _)) = char::<_, ParseError>('"')(tail) {
            Ok((remaining, inner))
        } else {
            Err(parse_failure(s, FailKind::UnclosedQuote))
        }
    } else {
        Err(parse_failure(
            s,
            match opened.chars().next().unwrap() {
                '"' => FailKind::EmptyQuote,
                // no unescaped " and a trailing \
                _ => FailKind::UnclosedQuote,
            },
        ))
    }
}

/// Determine if text is a qualified search, and handle escaped chars.
/// Expect well-formed input: unempty and no trailing \.
fn search_node_for_text(s: &str) -> ParseResult<SearchNode> {
    // leading : is only possible error for well-formed input
    let (tail, head) = verify(escaped(is_not(r":\"), '\\', anychar), |t: &str| {
        !t.is_empty()
    })(s)
    .map_err(|_: nom::Err<ParseError>| parse_failure(s, FailKind::MissingKey))?;
    if tail.is_empty() {
        Ok(SearchNode::UnqualifiedText(unescape(head)?))
    } else {
        search_node_for_text_with_argument(head, &tail[1..])
    }
}

/// Convert a colon-separated key/val pair into the relevant search type.
fn search_node_for_text_with_argument<'a>(
    key: &'a str,
    val: &'a str,
) -> ParseResult<'a, SearchNode<'a>> {
    Ok(match key.to_ascii_lowercase().as_str() {
        "deck" => SearchNode::Deck(unescape(val)?),
        "note" => SearchNode::NoteType(unescape(val)?),
        "tag" => SearchNode::Tag(unescape(val)?),
        "card" => parse_template(val)?,
        "flag" => parse_flag(val)?,
        "resched" => parse_resched(val)?,
        "prop" => parse_prop(val)?,
        "added" => parse_added(val)?,
        "edited" => parse_edited(val)?,
        "rated" => parse_rated(val)?,
        "is" => parse_state(val)?,
        "did" => parse_did(val)?,
        "mid" => parse_mid(val)?,
        "nid" => SearchNode::NoteIDs(check_id_list(val, key)?.into()),
        "cid" => SearchNode::CardIDs(check_id_list(val, key)?),
        "re" => SearchNode::Regex(unescape_quotes(val)),
        "nc" => SearchNode::NoCombining(unescape(val)?),
        "w" => SearchNode::WordBoundary(unescape(val)?),
        "dupe" => parse_dupe(val)?,
        // anything else is a field search
        _ => parse_single_field(key, val)?,
    })
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
            Err(parse_failure(s, FailKind::InvalidFlag))
        } else {
            Ok(SearchNode::Flag(flag))
        }
    } else {
        Err(parse_failure(s, FailKind::InvalidFlag))
    }
}

/// eg resched:3
fn parse_resched(s: &str) -> ParseResult<SearchNode> {
    parse_u32(s, "resched:").map(|days| SearchNode::Rated {
        days,
        ease: RatingKind::ManualReschedule,
    })
}

/// eg prop:ivl>3, prop:ease!=2.5
fn parse_prop(prop_clause: &str) -> ParseResult<SearchNode> {
    let (tail, prop) = alt::<_, _, ParseError, _>((
        tag("ivl"),
        tag("due"),
        tag("reps"),
        tag("lapses"),
        tag("ease"),
        tag("pos"),
        tag("rated"),
        tag("resched"),
    ))(prop_clause)
    .map_err(|_| {
        parse_failure(
            prop_clause,
            FailKind::InvalidPropProperty(prop_clause.into()),
        )
    })?;

    let (num, operator) = alt::<_, _, ParseError, _>((
        tag("<="),
        tag(">="),
        tag("!="),
        tag("="),
        tag("<"),
        tag(">"),
    ))(tail)
    .map_err(|_| parse_failure(prop_clause, FailKind::InvalidPropOperator(prop.to_string())))?;

    let kind = match prop {
        "ease" => PropertyKind::Ease(parse_f32(num, prop_clause)?),
        "due" => PropertyKind::Due(parse_i32(num, prop_clause)?),
        "rated" => parse_prop_rated(num, prop_clause)?,
        "resched" => PropertyKind::Rated(
            parse_negative_i32(num, prop_clause)?,
            RatingKind::ManualReschedule,
        ),
        "ivl" => PropertyKind::Interval(parse_u32(num, prop_clause)?),
        "reps" => PropertyKind::Reps(parse_u32(num, prop_clause)?),
        "lapses" => PropertyKind::Lapses(parse_u32(num, prop_clause)?),
        "pos" => PropertyKind::Position(parse_u32(num, prop_clause)?),
        _ => unreachable!(),
    };

    Ok(SearchNode::Property {
        operator: operator.to_string(),
        kind,
    })
}

fn parse_u32<'a>(num: &str, context: &'a str) -> ParseResult<'a, u32> {
    num.parse().map_err(|_e| {
        parse_failure(
            context,
            FailKind::InvalidPositiveWholeNumber {
                context: context.into(),
                provided: num.into(),
            },
        )
    })
}

fn parse_i32<'a>(num: &str, context: &'a str) -> ParseResult<'a, i32> {
    num.parse().map_err(|_e| {
        parse_failure(
            context,
            FailKind::InvalidWholeNumber {
                context: context.into(),
                provided: num.into(),
            },
        )
    })
}

fn parse_negative_i32<'a>(num: &str, context: &'a str) -> ParseResult<'a, i32> {
    num.parse()
        .map_err(|_| ())
        .and_then(|n| if n > 0 { Err(()) } else { Ok(n) })
        .map_err(|_| {
            parse_failure(
                context,
                FailKind::InvalidNegativeWholeNumber {
                    context: context.into(),
                    provided: num.into(),
                },
            )
        })
}

fn parse_f32<'a>(num: &str, context: &'a str) -> ParseResult<'a, f32> {
    num.parse().map_err(|_e| {
        parse_failure(
            context,
            FailKind::InvalidNumber {
                context: context.into(),
                provided: num.into(),
            },
        )
    })
}

fn parse_i64<'a>(num: &str, context: &'a str) -> ParseResult<'a, i64> {
    num.parse().map_err(|_e| {
        parse_failure(
            context,
            FailKind::InvalidWholeNumber {
                context: context.into(),
                provided: num.into(),
            },
        )
    })
}

fn parse_answer_button<'a>(num: Option<&str>, context: &'a str) -> ParseResult<'a, RatingKind> {
    Ok(if let Some(num) = num {
        RatingKind::AnswerButton(
            num.parse()
                .map_err(|_| ())
                .and_then(|n| if matches!(n, 1..=4) { Ok(n) } else { Err(()) })
                .map_err(|_| {
                    parse_failure(
                        context,
                        FailKind::InvalidAnswerButton {
                            context: context.into(),
                            provided: num.into(),
                        },
                    )
                })?,
        )
    } else {
        RatingKind::AnyAnswerButton
    })
}

fn parse_prop_rated<'a>(num: &str, context: &'a str) -> ParseResult<'a, PropertyKind> {
    let mut it = num.splitn(2, ':');
    let days = parse_negative_i32(it.next().unwrap(), context)?;
    let button = parse_answer_button(it.next(), context)?;
    Ok(PropertyKind::Rated(days, button))
}

/// eg added:1
fn parse_added(s: &str) -> ParseResult<SearchNode> {
    parse_u32(s, "added:").map(|n| SearchNode::AddedInDays(n.max(1)))
}

/// eg edited:1
fn parse_edited(s: &str) -> ParseResult<SearchNode> {
    parse_u32(s, "edited:").map(|n| SearchNode::EditedInDays(n.max(1)))
}

/// eg rated:3 or rated:10:2
/// second arg must be between 1-4
fn parse_rated(s: &str) -> ParseResult<SearchNode> {
    let mut it = s.splitn(2, ':');
    let days = parse_u32(it.next().unwrap(), "rated:")?.max(1);
    let button = parse_answer_button(it.next(), s)?;
    Ok(SearchNode::Rated { days, ease: button })
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
        _ => return Err(parse_failure(s, FailKind::InvalidState(s.into()))),
    }))
}

fn parse_did(s: &str) -> ParseResult<SearchNode> {
    parse_i64(s, "did:").map(|n| SearchNode::DeckID(n.into()))
}

fn parse_mid(s: &str) -> ParseResult<SearchNode> {
    parse_i64(s, "mid:").map(|n| SearchNode::NoteTypeID(n.into()))
}

/// ensure a list of ids contains only numbers and commas, returning unchanged if true
/// used by nid: and cid:
fn check_id_list<'a, 'b>(s: &'a str, context: &'b str) -> ParseResult<'a, &'a str> {
    lazy_static! {
        static ref RE: Regex = Regex::new(r"^(\d+,)*\d+$").unwrap();
    }
    if RE.is_match(s) {
        Ok(s)
    } else {
        Err(parse_failure(
            s,
            // id lists are undocumented, so no translation
            FailKind::Other(Some(format!(
                "expected only digits and commas in {}:",
                context
            ))),
        ))
    }
}

/// eg dupe:1231,hello
fn parse_dupe(s: &str) -> ParseResult<SearchNode> {
    let mut it = s.splitn(2, ',');
    let ntid = parse_i64(it.next().unwrap(), s)?;
    if let Some(text) = it.next() {
        Ok(SearchNode::Duplicates {
            note_type_id: ntid.into(),
            text: unescape_quotes_and_backslashes(text),
        })
    } else {
        // this is an undocumented keyword, so no translation/help
        Err(parse_failure(
            s,
            FailKind::Other(Some("invalid 'dupe:' search".into())),
        ))
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

/// For non-globs like dupe text without any assumption about the content
fn unescape_quotes_and_backslashes(s: &str) -> Cow<str> {
    if s.contains('"') || s.contains('\\') {
        s.replace(r#"\""#, "\"").replace(r"\\", r"\").into()
    } else {
        s.into()
    }
}

/// Unescape chars with special meaning to the parser.
fn unescape(txt: &str) -> ParseResult<Cow<str>> {
    if let Some(seq) = invalid_escape_sequence(txt) {
        Err(parse_failure(txt, FailKind::UnknownEscape(seq)))
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
    use crate::err::SearchErrorKind;

    use super::*;

    #[test]
    fn parsing() -> Result<()> {
        use Node::*;
        use SearchNode::*;

        assert_eq!(parse("")?, vec![Search(SearchNode::WholeCollection)]);
        assert_eq!(parse("  ")?, vec![Search(SearchNode::WholeCollection)]);

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

        // escaping : is optional if it is preceded by another :
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
        assert_eq!(parse("is:due")?, vec![Search(State(StateKind::Due))]);
        assert_eq!(parse("flag:3")?, vec![Search(Flag(3))]);

        assert_eq!(
            parse("prop:ivl>3")?,
            vec![Search(Property {
                operator: ">".into(),
                kind: PropertyKind::Interval(3)
            })]
        );
        assert_eq!(
            parse("prop:ease<=3.3")?,
            vec![Search(Property {
                operator: "<=".into(),
                kind: PropertyKind::Ease(3.3)
            })]
        );

        Ok(())
    }

    #[test]
    fn errors() -> Result<()> {
        use crate::err::AnkiError;
        use FailKind::*;

        fn assert_err_kind(input: &str, kind: FailKind) {
            assert_eq!(parse(input), Err(AnkiError::SearchError(kind)));
        }

        fn failkind(input: &str) -> SearchErrorKind {
            if let Err(AnkiError::SearchError(err)) = parse(input) {
                err
            } else {
                panic!("expected search error");
            }
        }

        assert_err_kind("foo and", MisplacedAnd);
        assert_err_kind("and foo", MisplacedAnd);
        assert_err_kind("and", MisplacedAnd);

        assert_err_kind("foo or", MisplacedOr);
        assert_err_kind("or foo", MisplacedOr);
        assert_err_kind("or", MisplacedOr);

        assert_err_kind("()", EmptyGroup);
        assert_err_kind("( )", EmptyGroup);
        assert_err_kind("(foo () bar)", EmptyGroup);

        assert_err_kind(")", UnopenedGroup);
        assert_err_kind("foo ) bar", UnopenedGroup);
        assert_err_kind("(foo) bar)", UnopenedGroup);

        assert_err_kind("(", UnclosedGroup);
        assert_err_kind("foo ( bar", UnclosedGroup);
        assert_err_kind("(foo (bar)", UnclosedGroup);

        assert_err_kind(r#""""#, EmptyQuote);
        assert_err_kind(r#"foo:"""#, EmptyQuote);

        assert_err_kind(r#" " "#, UnclosedQuote);
        assert_err_kind(r#"" foo"#, UnclosedQuote);
        assert_err_kind(r#""\"#, UnclosedQuote);
        assert_err_kind(r#"foo:"bar"#, UnclosedQuote);
        assert_err_kind(r#"foo:"bar\"#, UnclosedQuote);

        assert_err_kind(":", MissingKey);
        assert_err_kind(":foo", MissingKey);
        assert_err_kind(r#":"foo""#, MissingKey);

        assert_err_kind(r"\", UnknownEscape(r"\".to_string()));
        assert_err_kind(r"\%", UnknownEscape(r"\%".to_string()));
        assert_err_kind(r"foo\", UnknownEscape(r"\".to_string()));
        assert_err_kind(r"\foo", UnknownEscape(r"\f".to_string()));
        assert_err_kind(r"\ ", UnknownEscape(r"\".to_string()));
        assert_err_kind(r#""\ ""#, UnknownEscape(r"\ ".to_string()));

        for term in &[
            "nid:1_2,3",
            "nid:1,2,x",
            "nid:,2,3",
            "nid:1,2,",
            "cid:1_2,3",
            "cid:1,2,x",
            "cid:,2,3",
            "cid:1,2,",
        ] {
            assert!(matches!(failkind(term), SearchErrorKind::Other(_)));
        }

        assert_err_kind("is:foo", InvalidState("foo".into()));
        assert_err_kind("is:DUE", InvalidState("DUE".into()));
        assert_err_kind("is:New", InvalidState("New".into()));
        assert_err_kind("is:", InvalidState("".into()));
        assert_err_kind(r#""is:learn ""#, InvalidState("learn ".into()));

        assert_err_kind(r#""flag: ""#, InvalidFlag);
        assert_err_kind("flag:-0", InvalidFlag);
        assert_err_kind("flag:", InvalidFlag);
        assert_err_kind("flag:5", InvalidFlag);
        assert_err_kind("flag:1.1", InvalidFlag);

        for term in &["added", "edited", "rated", "resched"] {
            assert!(
                matches!(failkind(&format!("{}:1.1", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
            assert!(
                matches!(failkind(&format!("{}:-1", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
            assert!(
                matches!(failkind(&format!("{}:", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
            assert!(
                matches!(failkind(&format!("{}:foo", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
        }

        assert!(matches!(failkind("rated:1:"), SearchErrorKind::InvalidAnswerButton { .. }));
        assert!(matches!(failkind("rated:2:-1"), SearchErrorKind::InvalidAnswerButton { .. }));
        assert!(matches!(failkind("rated:3:1.1"), SearchErrorKind::InvalidAnswerButton { .. }));
        assert!(matches!(failkind("rated:0:foo"), SearchErrorKind::InvalidAnswerButton { .. }));

        assert!(matches!(failkind("dupe:"), SearchErrorKind::InvalidWholeNumber { .. }));
        assert!(matches!(failkind("dupe:1.1"), SearchErrorKind::InvalidWholeNumber { .. }));
        assert!(matches!(failkind("dupe:foo"), SearchErrorKind::InvalidWholeNumber { .. }));

        assert_err_kind("prop:", InvalidPropProperty("".into()));
        assert_err_kind("prop:=1", InvalidPropProperty("=1".into()));
        assert_err_kind("prop:DUE<5", InvalidPropProperty("DUE<5".into()));

        assert_err_kind("prop:lapses", InvalidPropOperator("lapses".to_string()));
        assert_err_kind("prop:pos~1", InvalidPropOperator("pos".to_string()));
        assert_err_kind("prop:reps10", InvalidPropOperator("reps".to_string()));

        // unsigned

        for term in &["ivl", "reps", "lapses", "pos"] {
            assert!(
                matches!(failkind(&format!("prop:{}>", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
            assert!(
                matches!(failkind(&format!("prop:{}=0.5", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
            assert!(
                matches!(failkind(&format!("prop:{}!=-1", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
            assert!(
                matches!(failkind(&format!("prop:{}<foo", term)), SearchErrorKind::InvalidPositiveWholeNumber { .. })
            );
        }

        // signed

        assert!(matches!(failkind("prop:due>"), SearchErrorKind::InvalidWholeNumber { .. }));
        assert!(matches!(failkind("prop:due=0.5"), SearchErrorKind::InvalidWholeNumber { .. }));

        // float

        assert!(matches!(failkind("prop:ease>"), SearchErrorKind::InvalidNumber { .. }));
        assert!(matches!(failkind("prop:ease!=one"), SearchErrorKind::InvalidNumber { .. }));
        assert!(matches!(failkind("prop:ease<1,3"), SearchErrorKind::InvalidNumber { .. }));

        Ok(())
    }
}
