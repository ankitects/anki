// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use lazy_static::lazy_static;
use regex::{Captures, Regex};
use std::borrow::Cow;
use std::ptr;
use unicode_normalization::{is_nfc, UnicodeNormalization};

#[derive(Debug, PartialEq)]
pub enum AVTag {
    SoundOrVideo(String),
    TextToSpeech {
        field_text: String,
        lang: String,
        voices: Vec<String>,
        speed: f32,
        other_args: Vec<String>,
    },
}

lazy_static! {
    static ref HTML: Regex = Regex::new(concat!(
        "(?si)",
        // wrapped text
        r"(<!--.*?-->)|(<style.*?>.*?</style>)|(<script.*?>.*?</script>)",
        // html tags
        r"|(<.*?>)",
    ))
    .unwrap();

    static ref IMG_TAG: Regex = Regex::new(
        r#"(?xsi)
            # the start of the image tag
            <img[^>]+src=
            (?:
                    # 1: double-quoted filename
                    "
                    ([^"]+?)
                    "
                    [^>]*>                    
                |
                    # 2: single-quoted filename
                    '
                    ([^']+?)
                    '
                    [^>]*>
                |
                    # 3: unquoted filename
                    ([^ >]+?)
                    (?:
                        # then either a space and the rest
                        \x20[^>]*>
                        |
                        # or the tag immediately ends
                        >
                    )
            )
            "#
    ).unwrap();

    // videos are also in sound tags
    static ref AV_TAGS: Regex = Regex::new(
        r#"(?xs)
            \[sound:(.*?)\]     # 1 - the filename in a sound tag
            |
            \[anki:tts\]
                \[(.*?)\]       # 2 - arguments to tts call
                (.*?)           # 3 - field text
            \[/anki:tts\]
            "#).unwrap();
}

pub fn strip_html(html: &str) -> Cow<str> {
    let mut out: Cow<str> = html.into();

    if let Cow::Owned(o) = HTML.replace_all(html, "") {
        out = o.into();
    }

    if let Cow::Owned(o) = decode_entities(out.as_ref()) {
        out = o.into();
    }

    out
}

pub fn decode_entities(html: &str) -> Cow<str> {
    if html.contains('&') {
        match htmlescape::decode_html(html) {
            Ok(text) => text.replace("\u{a0}", " "),
            Err(e) => format!("{:?}", e),
        }
        .into()
    } else {
        // nothing to do
        html.into()
    }
}

pub fn strip_html_for_tts(html: &str) -> Cow<str> {
    match HTML.replace_all(html, " ") {
        Cow::Borrowed(_) => decode_entities(html),
        Cow::Owned(s) => decode_entities(&s).to_string().into(),
    }
}

pub fn strip_av_tags(text: &str) -> Cow<str> {
    AV_TAGS.replace_all(text, "")
}

/// Extract audio tags from string, replacing them with [anki:play] refs
pub fn extract_av_tags<'a>(text: &'a str, question_side: bool) -> (Cow<'a, str>, Vec<AVTag>) {
    let mut tags = vec![];
    let context = if question_side { 'q' } else { 'a' };
    let replaced_text = AV_TAGS.replace_all(text, |caps: &Captures| {
        // extract
        let tag = if let Some(av_file) = caps.get(1) {
            AVTag::SoundOrVideo(decode_entities(av_file.as_str()).into())
        } else {
            let args = caps.get(2).unwrap();
            let field_text = caps.get(3).unwrap();
            tts_tag_from_string(field_text.as_str(), args.as_str())
        };
        tags.push(tag);

        // and replace with reference
        format!("[anki:play:{}:{}]", context, tags.len() - 1)
    });

    (replaced_text, tags)
}

#[derive(Debug)]
pub(crate) struct MediaRef<'a> {
    pub full_ref: &'a str,
    pub fname: &'a str,
}

pub(crate) fn extract_media_refs(text: &str) -> Vec<MediaRef> {
    let mut out = vec![];

    for caps in IMG_TAG.captures_iter(text) {
        out.push(MediaRef {
            full_ref: caps.get(0).unwrap().as_str(),
            fname: caps
                .get(1)
                .or_else(|| caps.get(2))
                .or_else(|| caps.get(3))
                .unwrap()
                .as_str(),
        });
    }

    for caps in AV_TAGS.captures_iter(text) {
        if let Some(m) = caps.get(1) {
            out.push(MediaRef {
                full_ref: caps.get(0).unwrap().as_str(),
                fname: m.as_str(),
            });
        }
    }

    out
}

fn tts_tag_from_string<'a>(field_text: &'a str, args: &'a str) -> AVTag {
    let mut other_args = vec![];
    let mut split_args = args.split_ascii_whitespace();
    let lang = split_args.next().unwrap_or("");
    let mut voices = None;
    let mut speed = 1.0;

    for remaining_arg in split_args {
        if remaining_arg.starts_with("voices=") {
            voices = remaining_arg
                .split('=')
                .nth(1)
                .map(|voices| voices.split(',').map(ToOwned::to_owned).collect());
        } else if remaining_arg.starts_with("speed=") {
            speed = remaining_arg
                .split('=')
                .nth(1)
                .unwrap()
                .parse()
                .unwrap_or(1.0);
        } else {
            other_args.push(remaining_arg.to_owned());
        }
    }

    AVTag::TextToSpeech {
        field_text: strip_html_for_tts(field_text).into(),
        lang: lang.into(),
        voices: voices.unwrap_or_else(Vec::new),
        speed,
        other_args,
    }
}

pub fn strip_html_preserving_image_filenames(html: &str) -> Cow<str> {
    let without_fnames = IMG_TAG.replace_all(html, r" ${1}${2}${3} ");
    let without_html = HTML.replace_all(&without_fnames, "");
    // no changes?
    if let Cow::Borrowed(b) = without_html {
        if ptr::eq(b, html) {
            return Cow::Borrowed(html);
        }
    }
    // make borrow checker happy
    without_html.into_owned().into()
}

pub(crate) fn normalize_to_nfc(s: &str) -> Cow<str> {
    if !is_nfc(s) {
        s.chars().nfc().collect::<String>().into()
    } else {
        s.into()
    }
}

#[cfg(test)]
mod test {
    use crate::text::{
        extract_av_tags, strip_av_tags, strip_html, strip_html_preserving_image_filenames, AVTag,
    };

    #[test]
    fn stripping() {
        assert_eq!(strip_html("test"), "test");
        assert_eq!(strip_html("t<b>e</b>st"), "test");
        assert_eq!(strip_html("so<SCRIPT>t<b>e</b>st</script>me"), "some");

        assert_eq!(
            strip_html_preserving_image_filenames("<img src=foo.jpg>"),
            " foo.jpg "
        );
        assert_eq!(
            strip_html_preserving_image_filenames("<img src='foo.jpg'><html>"),
            " foo.jpg "
        );
        assert_eq!(strip_html_preserving_image_filenames("<html>"), "");
    }

    #[test]
    fn audio() {
        let s =
            "abc[sound:fo&amp;o.mp3]def[anki:tts][en_US voices=Bob,Jane speed=1.2]foo<br>1&gt;2[/anki:tts]gh";
        assert_eq!(strip_av_tags(s), "abcdefgh");

        let (text, tags) = extract_av_tags(s, true);
        assert_eq!(text, "abc[anki:play:q:0]def[anki:play:q:1]gh");

        assert_eq!(
            tags,
            vec![
                AVTag::SoundOrVideo("fo&o.mp3".into()),
                AVTag::TextToSpeech {
                    field_text: "foo 1>2".into(),
                    lang: "en_US".into(),
                    voices: vec!["Bob".into(), "Jane".into()],
                    other_args: vec![],
                    speed: 1.2
                },
            ]
        );
    }
}
