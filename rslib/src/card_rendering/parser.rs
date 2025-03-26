// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use nom::branch::alt;
use nom::bytes::complete::is_not;
use nom::bytes::complete::tag;
use nom::character::complete::anychar;
use nom::character::complete::multispace0;
use nom::combinator::map;
use nom::combinator::not;
use nom::combinator::recognize;
use nom::combinator::rest;
use nom::combinator::success;
use nom::combinator::value;
use nom::multi::fold_many0;
use nom::multi::many0;
use nom::sequence::delimited;
use nom::sequence::pair;
use nom::sequence::preceded;
use nom::sequence::separated_pair;
use nom::sequence::terminated;
use nom::sequence::tuple;

use super::CardNodes;
use super::Directive;
use super::Node;
use super::OtherDirective;
use super::TtsDirective;

type IResult<'a, O> = nom::IResult<&'a str, O>;

impl<'a> CardNodes<'a> {
    pub(super) fn parse(mut txt: &'a str) -> Self {
        let mut nodes = Vec::new();
        while let Ok((remaining, node)) = node(txt) {
            txt = remaining;
            nodes.push(node);
        }

        Self(nodes)
    }
}

impl<'a> Directive<'a> {
    fn new(name: &'a str, options: Vec<(&'a str, &'a str)>, content: &'a str) -> Self {
        match name {
            "tts" => {
                let mut lang = "";
                let mut voices = vec![];
                let mut speed = 1.0;
                let mut blank = None;
                let mut other_options = HashMap::new();

                for option in options {
                    match option.0 {
                        "lang" => lang = option.1,
                        "voices" => voices = option.1.split(',').collect(),
                        "speed" => speed = option.1.parse().unwrap_or(1.0),
                        "cloze_blank" => blank = Some(option.1),
                        _ => {
                            other_options.insert(option.0, option.1);
                        }
                    }
                }

                Self::Tts(TtsDirective {
                    content,
                    lang,
                    voices,
                    speed,
                    blank,
                    options: other_options,
                })
            }
            _ => Self::Other(OtherDirective {
                name,
                content,
                options: options.into_iter().collect(),
            }),
        }
    }
}

/// Consume 0 or more of anything in " \t\r\n" after `parser`.
fn trailing_whitespace0<'parser, 's, P, O>(parser: P) -> impl FnMut(&'s str) -> IResult<'s, O>
where
    P: FnMut(&'s str) -> IResult<'s, O> + 'parser,
{
    terminated(parser, multispace0)
}

/// Parse until char in `arr` is found. Always succeeds.
fn is_not0<'parser, 'arr: 'parser, 's: 'parser>(
    arr: &'arr str,
) -> impl FnMut(&'s str) -> IResult<'s, &'s str> + 'parser {
    alt((is_not(arr), success("")))
}

fn node(s: &str) -> IResult<Node> {
    alt((sound_node, tag_node, text_node))(s)
}

/// A sound tag `[sound:resource]`, where `resource` is pointing to a sound or
/// video file.
fn sound_node(s: &str) -> IResult<Node> {
    map(
        delimited(tag("[sound:"), is_not("]"), tag("]")),
        Node::SoundOrVideo,
    )(s)
}

fn take_till_potential_tag_start(s: &str) -> IResult<&str> {
    use nom::InputTake;
    // first char could be '[', but wasn't part of a node, so skip (eof ends parse)
    let (after, offset) = anychar(s).map(|(s, c)| (s, c.len_utf8()))?;
    Ok(match after.find('[') {
        Some(pos) => s.take_split(offset + pos),
        _ => rest(s)?,
    })
}

/// An Anki tag `[anki:tag...]...[/anki:tag]`.
fn tag_node(s: &str) -> IResult<Node> {
    /// Match the start of an opening tag and return its name.
    fn name(s: &str) -> IResult<&str> {
        preceded(tag("[anki:"), is_not("] \t\r\n"))(s)
    }

    /// Return a parser to match an opening `name` tag and return its options.
    fn opening_parser<'name, 's: 'name>(
        name: &'name str,
    ) -> impl FnMut(&'s str) -> IResult<'s, Vec<(&'s str, &'s str)>> + 'name {
        /// List of whitespace-separated `key=val` tuples, where `val` may be
        /// empty.
        fn options(s: &str) -> IResult<Vec<(&str, &str)>> {
            fn key(s: &str) -> IResult<&str> {
                is_not("] \t\r\n=")(s)
            }

            fn val(s: &str) -> IResult<&str> {
                alt((
                    delimited(tag("\""), is_not0("\""), tag("\"")),
                    is_not0("] \t\r\n\""),
                ))(s)
            }

            many0(trailing_whitespace0(separated_pair(key, tag("="), val)))(s)
        }

        delimited(
            pair(tag("[anki:"), trailing_whitespace0(tag(name))),
            options,
            tag("]"),
        )
    }

    /// Return a parser to match a closing `name` tag.
    fn closing_parser<'parser, 'name: 'parser, 's: 'parser>(
        name: &'name str,
    ) -> impl FnMut(&'s str) -> IResult<'s, ()> + 'parser {
        value((), tuple((tag("[/anki:"), tag(name), tag("]"))))
    }

    /// Return a parser to match and return anything until a closing `name` tag
    /// is found.
    fn content_parser<'parser, 'name: 'parser, 's: 'parser>(
        name: &'name str,
    ) -> impl FnMut(&'s str) -> IResult<'s, &'s str> + 'parser {
        recognize(fold_many0(
            pair(not(closing_parser(name)), take_till_potential_tag_start),
            // we don't need to accumulate anything
            || (),
            |_, _| (),
        ))
    }

    let (_, tag_name) = name(s)?;
    map(
        terminated(
            pair(opening_parser(tag_name), content_parser(tag_name)),
            closing_parser(tag_name),
        ),
        |(options, content)| Node::Directive(Directive::new(tag_name, options, content)),
    )(s)
}

fn text_node(s: &str) -> IResult<Node> {
    map(take_till_potential_tag_start, Node::Text)(s)
}

#[cfg(test)]
mod test {
    use super::*;

    macro_rules! assert_parsed_nodes {
        ($txt:expr $(, $node:expr)*) => {
            assert_eq!(CardNodes::parse($txt).nodes, vec![$($node),*]);
        }
    }

    #[test]
    fn parsing() {
        use Node::*;

        // empty
        assert_parsed_nodes!("");

        // text
        assert_parsed_nodes!("foo", Text("foo"));
        // broken sound/tags are just text as well
        assert_parsed_nodes!("[sound:]", Text("[sound:]"));
        assert_parsed_nodes!("[anki:][/anki:]", Text("[anki:]"), Text("[/anki:]"));
        assert_parsed_nodes!(
            "[anki:foo][/anki:bar]",
            Text("[anki:foo]"),
            Text("[/anki:bar]")
        );

        // sound
        assert_parsed_nodes!("[sound:foo]", SoundOrVideo("foo"));
        assert_parsed_nodes!(
            "foo [sound:bar] baz",
            Text("foo "),
            SoundOrVideo("bar"),
            Text(" baz")
        );
        assert_parsed_nodes!(
            "[sound:foo][sound:bar]",
            SoundOrVideo("foo"),
            SoundOrVideo("bar")
        );

        // tags
        assert_parsed_nodes!(
            "[anki:foo]bar[/anki:foo]",
            Directive(super::Directive::Other(OtherDirective {
                name: "foo",
                content: "bar",
                options: HashMap::new()
            }))
        );
        assert_parsed_nodes!(
            "[anki:foo bar=baz][/anki:foo]",
            Directive(super::Directive::Other(OtherDirective {
                name: "foo",
                content: "",
                options: [("bar", "baz")].into_iter().collect(),
            }))
        );
        // unquoted white space separates options, "]" terminates
        assert_parsed_nodes!(
            "[anki:foo\na=b\tc=d e=f][/anki:foo]",
            Directive(super::Directive::Other(OtherDirective {
                name: "foo",
                content: "",
                options: [("a", "b"), ("c", "d"), ("e", "f")].into_iter().collect(),
            }))
        );
        assert_parsed_nodes!(
            "[anki:foo a=\"b \t\n c ]\"][/anki:foo]",
            Directive(super::Directive::Other(OtherDirective {
                name: "foo",
                content: "",
                options: [("a", "b \t\n c ]")].into_iter().collect(),
            }))
        );

        // tts tags
        assert_parsed_nodes!(
            "[anki:tts lang=jp_JP voices=Alice,Bob speed=0.5 cloze_blank= bar=baz][/anki:tts]",
            Directive(super::Directive::Tts(TtsDirective {
                content: "",
                lang: "jp_JP",
                voices: vec!["Alice", "Bob"],
                speed: 0.5,
                blank: Some(""),
                options: [("bar", "baz")].into_iter().collect(),
            }))
        );
        assert_parsed_nodes!(
            "[anki:tts speed=foo][/anki:tts]",
            Directive(super::Directive::Tts(TtsDirective {
                content: "",
                lang: "",
                voices: vec![],
                speed: 1.0,
                blank: None,
                options: HashMap::new(),
            }))
        );
    }
}
