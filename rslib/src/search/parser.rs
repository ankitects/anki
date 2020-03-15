// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use nom::branch::alt;
use nom::bytes::complete::{escaped, is_not, take_while1};
use nom::character::complete::{char, one_of};
use nom::combinator::{all_consuming, map};
use nom::sequence::{delimited, preceded};
use nom::{multi::many0, IResult};

#[derive(Debug, PartialEq)]
pub(super) enum Node<'a> {
    And,
    Or,
    Not(Box<Node<'a>>),
    Group(Vec<Node<'a>>),
    Text(&'a str),
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

/// Unquoted text, terminated by a space or )
fn unquoted_term(s: &str) -> IResult<&str, Node> {
    map(take_while1(|c| c != ' ' && c != ')'), |text: &str| {
        if text.len() == 2 && text.to_ascii_lowercase() == "or" {
            Node::Or
        } else if text.len() == 3 && text.to_ascii_lowercase() == "and" {
            Node::And
        } else {
            Node::Text(text)
        }
    })(s)
}

// Quoted text, including the outer double quotes.
fn quoted_term(s: &str) -> IResult<&str, Node> {
    delimited(char('"'), quoted_term_inner, char('"'))(s)
}

/// Quoted text, terminated by a non-escaped double quote
/// Can escape :, " and \
fn quoted_term_inner(s: &str) -> IResult<&str, Node> {
    map(escaped(is_not(r#""\"#), '\\', one_of(r#"":\"#)), |o| {
        Node::Text(o)
    })(s)
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn parsing() -> Result<(), String> {
        use Node::*;
        assert_eq!(
            parse(r#"hello  -(world and "foo bar") OR test"#)?,
            vec![
                Text("hello"),
                And,
                Not(Box::new(Group(vec![Text("world"), And, Text("foo bar")]))),
                Or,
                Text("test")
            ]
        );

        Ok(())
    }
}
