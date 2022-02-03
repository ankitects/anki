// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, ptr};

use lazy_static::lazy_static;
use pct_str::{IriReserved, PctStr, PctString};
use regex::{Captures, Regex};
use unicase::eq as uni_eq;
use unicode_normalization::{
    char::is_combining_mark, is_nfc, is_nfkd_quick, IsNormalized, UnicodeNormalization,
};

pub trait Trimming {
    fn trim(self) -> Self;
}

impl Trimming for Cow<'_, str> {
    fn trim(self) -> Self {
        match self {
            Cow::Borrowed(text) => text.trim().into(),
            Cow::Owned(text) => {
                let trimmed = text.as_str().trim();
                if trimmed.len() == text.len() {
                    text.into()
                } else {
                    trimmed.to_string().into()
                }
            }
        }
    }
}

#[derive(Debug, PartialEq)]
pub enum AvTag {
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

    static ref HTML_LINEBREAK_TAGS: Regex = Regex::new(
        r#"(?xsi)
            </?
            (?:
                br|address|article|aside|blockquote|canvas|dd|div
                |dl|dt|fieldset|figcaption|figure|footer|form
                |h[1-6]|header|hr|li|main|nav|noscript|ol
                |output|p|pre|section|table|tfoot|ul|video
            )
            >
        "#
    ).unwrap();

    static ref HTML_MEDIA_TAGS: Regex = Regex::new(
        r#"(?xsi)
            # the start of the image, audio, or object tag
            <\b(?:img|audio|object)\b[^>]+\b(?:src|data)\b=
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
            \[sound:(.+?)\]     # 1 - the filename in a sound tag
            |
            \[anki:tts\]
                \[(.*?)\]       # 2 - arguments to tts call
                (.*?)           # 3 - field text
            \[/anki:tts\]
            "#).unwrap();

    static ref PERSISTENT_HTML_SPACERS: Regex = Regex::new(r#"(?i)<br\s*/?>|<div>|\n"#).unwrap();

    static ref UNPRINTABLE_TAGS: Regex = Regex::new(
        r"(?xs)
        \[sound:[^]]+\]
        |
        \[\[type:[^]]+\]\]
    ").unwrap();
}

pub fn html_to_text_line(html: &str) -> Cow<str> {
    let mut out: Cow<str> = html.into();
    if let Cow::Owned(o) = PERSISTENT_HTML_SPACERS.replace_all(&out, " ") {
        out = o.into();
    }
    if let Cow::Owned(o) = UNPRINTABLE_TAGS.replace_all(&out, "") {
        out = o.into();
    }
    if let Cow::Owned(o) = strip_html_preserving_media_filenames(&out) {
        out = o.into();
    }
    out.trim()
}

pub fn strip_html(html: &str) -> Cow<str> {
    let mut out: Cow<str> = html.into();

    if let Cow::Owned(o) = strip_html_preserving_entities(html) {
        out = o.into();
    }

    if let Cow::Owned(o) = decode_entities(out.as_ref()) {
        out = o.into();
    }

    out
}

pub fn strip_html_preserving_entities(html: &str) -> Cow<str> {
    HTML.replace_all(html, "")
}

pub fn decode_entities(html: &str) -> Cow<str> {
    if html.contains('&') {
        match htmlescape::decode_html(html) {
            Ok(text) => text.replace('\u{a0}', " ").into(),
            Err(_) => html.into(),
        }
    } else {
        // nothing to do
        html.into()
    }
}

pub fn strip_html_for_tts(html: &str) -> Cow<str> {
    let mut out: Cow<str> = html.into();

    if let Cow::Owned(o) = HTML_LINEBREAK_TAGS.replace_all(html, " ") {
        out = o.into();
    }

    if let Cow::Owned(o) = strip_html(out.as_ref()) {
        out = o.into();
    }

    out
}

#[derive(Debug)]
pub(crate) struct MediaRef<'a> {
    pub full_ref: &'a str,
    pub fname: &'a str,
    /// audio files may have things like &amp; that need decoding
    pub fname_decoded: Cow<'a, str>,
}

pub(crate) fn extract_media_refs(text: &str) -> Vec<MediaRef> {
    let mut out = vec![];

    for caps in HTML_MEDIA_TAGS.captures_iter(text) {
        let fname = caps
            .get(1)
            .or_else(|| caps.get(2))
            .or_else(|| caps.get(3))
            .unwrap()
            .as_str();
        let fname_decoded = decode_entities(fname);
        out.push(MediaRef {
            full_ref: caps.get(0).unwrap().as_str(),
            fname,
            fname_decoded,
        });
    }

    for caps in AV_TAGS.captures_iter(text) {
        if let Some(m) = caps.get(1) {
            let fname = m.as_str();
            let fname_decoded = decode_entities(fname);
            out.push(MediaRef {
                full_ref: caps.get(0).unwrap().as_str(),
                fname,
                fname_decoded,
            });
        }
    }

    out
}

pub fn strip_html_preserving_media_filenames(html: &str) -> Cow<str> {
    let without_fnames = HTML_MEDIA_TAGS.replace_all(html, r" ${1}${2}${3} ");
    let without_html = strip_html(&without_fnames);
    // no changes?
    if let Cow::Borrowed(b) = without_html {
        if ptr::eq(b, html) {
            return Cow::Borrowed(html);
        }
    }
    // make borrow checker happy
    without_html.into_owned().into()
}

#[allow(dead_code)]
pub(crate) fn sanitize_html(html: &str) -> String {
    ammonia::clean(html)
}

pub(crate) fn sanitize_html_no_images(html: &str) -> String {
    ammonia::Builder::default()
        .rm_tags(&["img"])
        .clean(html)
        .to_string()
}

pub(crate) fn normalize_to_nfc(s: &str) -> Cow<str> {
    if !is_nfc(s) {
        s.chars().nfc().collect::<String>().into()
    } else {
        s.into()
    }
}

pub(crate) fn ensure_string_in_nfc(s: &mut String) {
    if !is_nfc(s) {
        *s = s.chars().nfc().collect()
    }
}

/// Convert provided string to NFKD form and strip combining characters.
pub(crate) fn without_combining(s: &str) -> Cow<str> {
    // if the string is already normalized
    if matches!(is_nfkd_quick(s.chars()), IsNormalized::Yes) {
        // and no combining characters found, return unchanged
        if !s.chars().any(is_combining_mark) {
            return s.into();
        }
    }

    // we need to create a new string without the combining marks
    s.chars()
        .nfkd()
        .filter(|c| !is_combining_mark(*c))
        .collect::<String>()
        .into()
}

/// Check if string contains an unescaped wildcard.
pub(crate) fn is_glob(txt: &str) -> bool {
    // even number of \s followed by a wildcard
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r#"(?x)
            (?:^|[^\\])     # not a backslash
            (?:\\\\)*       # even number of backslashes
            [*_]            # wildcard
            "#
        )
        .unwrap();
    }

    RE.is_match(txt)
}

/// Convert to a RegEx respecting Anki wildcards.
pub(crate) fn to_re(txt: &str) -> Cow<str> {
    to_custom_re(txt, ".")
}

/// Convert Anki style to RegEx using the provided wildcard.
pub(crate) fn to_custom_re<'a>(txt: &'a str, wildcard: &str) -> Cow<'a, str> {
    lazy_static! {
        static ref RE: Regex = Regex::new(r"\\?.").unwrap();
    }
    RE.replace_all(txt, |caps: &Captures| {
        let s = &caps[0];
        match s {
            r"\\" | r"\*" => s.to_string(),
            r"\_" => "_".to_string(),
            "*" => format!("{}*", wildcard),
            "_" => wildcard.to_string(),
            s => regex::escape(s),
        }
    })
}

/// Convert to SQL respecting Anki wildcards.
pub(crate) fn to_sql(txt: &str) -> Cow<str> {
    // escape sequences and unescaped special characters which need conversion
    lazy_static! {
        static ref RE: Regex = Regex::new(r"\\[\\*]|[*%]").unwrap();
    }
    RE.replace_all(txt, |caps: &Captures| {
        let s = &caps[0];
        match s {
            r"\\" => r"\\",
            r"\*" => "*",
            "*" => "%",
            "%" => r"\%",
            _ => unreachable!(),
        }
    })
}

/// Unescape everything.
pub(crate) fn to_text(txt: &str) -> Cow<str> {
    lazy_static! {
        static ref RE: Regex = Regex::new(r"\\(.)").unwrap();
    }
    RE.replace_all(txt, "$1")
}

/// Escape Anki wildcards and the backslash for escaping them: \*_
pub(crate) fn escape_anki_wildcards(txt: &str) -> String {
    lazy_static! {
        static ref RE: Regex = Regex::new(r"[\\*_]").unwrap();
    }
    RE.replace_all(txt, r"\$0").into()
}

/// Escape Anki wildcards unless it's _*
pub(crate) fn escape_anki_wildcards_for_search_node(txt: &str) -> String {
    if txt == "_*" {
        txt.to_string()
    } else {
        escape_anki_wildcards(txt)
    }
}

/// Return a function to match input against `search`,
/// which may contain wildcards.
pub(crate) fn glob_matcher(search: &str) -> impl Fn(&str) -> bool + '_ {
    let mut regex = None;
    let mut cow = None;
    if is_glob(search) {
        regex = Some(Regex::new(&format!("^(?i){}$", to_re(search))).unwrap());
    } else {
        cow = Some(to_text(search));
    }

    move |text| {
        if let Some(r) = &regex {
            r.is_match(text)
        } else {
            uni_eq(text, cow.as_ref().unwrap())
        }
    }
}

lazy_static! {
    pub(crate) static ref REMOTE_FILENAME: Regex = Regex::new("(?i)^https?://").unwrap();
}

/// IRI-encode unescaped local paths in HTML fragment.
pub(crate) fn encode_iri_paths(unescaped_html: &str) -> Cow<str> {
    transform_html_paths(unescaped_html, |fname| {
        PctString::encode(fname.chars(), IriReserved::Segment)
            .into_string()
            .into()
    })
}

/// URI-decode escaped local paths in HTML fragment.
pub(crate) fn decode_iri_paths(escaped_html: &str) -> Cow<str> {
    transform_html_paths(escaped_html, |fname| {
        match PctStr::new(fname) {
            Ok(s) => s.decode().into(),
            Err(_e) => {
                // invalid percent encoding; return unchanged
                fname.into()
            }
        }
    })
}

/// Apply a transform to local filename references in tags like IMG.
/// Required at display time, as Anki unfortunately stores the references
/// in unencoded form in the database.
fn transform_html_paths<F>(html: &str, transform: F) -> Cow<str>
where
    F: Fn(&str) -> Cow<str>,
{
    HTML_MEDIA_TAGS.replace_all(html, |caps: &Captures| {
        let fname = caps
            .get(1)
            .or_else(|| caps.get(2))
            .or_else(|| caps.get(3))
            .unwrap()
            .as_str();
        let full = caps.get(0).unwrap().as_str();
        if REMOTE_FILENAME.is_match(fname) {
            full.into()
        } else {
            full.replace(fname, &transform(fname))
        }
    })
}

#[cfg(test)]
mod test {
    use std::borrow::Cow;

    use super::*;

    #[test]
    fn stripping() {
        assert_eq!(strip_html("test"), "test");
        assert_eq!(strip_html("t<b>e</b>st"), "test");
        assert_eq!(strip_html("so<SCRIPT>t<b>e</b>st</script>me"), "some");

        assert_eq!(
            strip_html_preserving_media_filenames("<img src=foo.jpg>"),
            " foo.jpg "
        );
        assert_eq!(
            strip_html_preserving_media_filenames("<img src='foo.jpg'><html>"),
            " foo.jpg "
        );
        assert_eq!(strip_html_preserving_media_filenames("<html>"), "");
    }

    #[test]
    fn combining() {
        assert!(matches!(without_combining("test"), Cow::Borrowed(_)));
        assert!(matches!(without_combining("Über"), Cow::Owned(_)));
    }

    #[test]
    fn conversion() {
        assert_eq!(&to_re(r"[te\*st]"), r"\[te\*st\]");
        assert_eq!(&to_custom_re("f_o*", r"\d"), r"f\do\d*");
        assert_eq!(&to_sql("%f_o*"), r"\%f_o%");
        assert_eq!(&to_text(r"\*\_*_"), "*_*_");
        assert!(is_glob(r"\\\\_"));
        assert!(!is_glob(r"\\\_"));
        assert!(glob_matcher(r"foo\*bar*")("foo*bar123"));
    }
}
