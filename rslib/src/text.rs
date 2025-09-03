// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::sync::LazyLock;

use percent_encoding_iri::percent_decode_str;
use percent_encoding_iri::utf8_percent_encode;
use percent_encoding_iri::AsciiSet;
use percent_encoding_iri::CONTROLS;
use regex::Captures;
use regex::Regex;
use unicase::eq as uni_eq;
use unicode_normalization::char::is_combining_mark;
use unicode_normalization::is_nfc;
use unicode_normalization::is_nfkd_quick;
use unicode_normalization::IsNormalized;
use unicode_normalization::UnicodeNormalization;

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

pub(crate) trait CowMapping<'a, B: ?Sized + 'a + ToOwned> {
    /// Returns [self]
    /// - unchanged, if the given function returns [Cow::Borrowed]
    /// - with the new value, if the given function returns [Cow::Owned]
    fn map_cow(self, f: impl FnOnce(&B) -> Cow<B>) -> Self;
    fn get_owned(self) -> Option<B::Owned>;
}

impl<'a, B: ?Sized + 'a + ToOwned> CowMapping<'a, B> for Cow<'a, B> {
    fn map_cow(self, f: impl FnOnce(&B) -> Cow<B>) -> Self {
        if let Cow::Owned(o) = f(&self) {
            Cow::Owned(o)
        } else {
            self
        }
    }

    fn get_owned(self) -> Option<B::Owned> {
        match self {
            Cow::Borrowed(_) => None,
            Cow::Owned(s) => Some(s),
        }
    }
}

pub(crate) fn strip_utf8_bom(s: &str) -> &str {
    s.strip_prefix('\u{feff}').unwrap_or(s)
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

static HTML: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(concat!(
        "(?si)",
        // wrapped text
        r"(<!--.*?-->)|(<style.*?>.*?</style>)|(<script.*?>.*?</script>)",
        // html tags
        r"|(<.*?>)",
    ))
    .unwrap()
});
static HTML_LINEBREAK_TAGS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?xsi)
            </?
            (?:
                br|address|article|aside|blockquote|canvas|dd|div
                |dl|dt|fieldset|figcaption|figure|footer|form
                |h[1-6]|header|hr|li|main|nav|noscript|ol
                |output|p|pre|section|table|tfoot|ul|video
            )
            >
        "#,
    )
    .unwrap()
});

pub static HTML_MEDIA_TAGS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?xsi)
            # the start of the image, audio, object, or source tag
            <\b(?:img|audio|video|object|source)\b

            # any non-`>`, except inside `"` or `'`
            (?:
                [^>]
            |
                "[^"]+?"
            |
                '[^']+?'
            )+?

            # capture `src` or `data` attribute
            \b(?:src|data)\b=
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
            "#,
    )
    .unwrap()
});

// videos are also in sound tags
static AV_TAGS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?xs)
            \[sound:(.+?)\]     # 1 - the filename in a sound tag
            |
            \[anki:tts\]
                \[(.*?)\]       # 2 - arguments to tts call
                (.*?)           # 3 - field text
            \[/anki:tts\]
            ",
    )
    .unwrap()
});

static PERSISTENT_HTML_SPACERS: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)<br\s*/?>|<div>|\n").unwrap());

static TYPE_TAG: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\[\[type:[^]]+\]\]").unwrap());
pub(crate) static SOUND_TAG: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\[sound:([^]]+)\]").unwrap());

/// Files included in CSS with a leading underscore.
static UNDERSCORED_CSS_IMPORTS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?xi)
            (?:@import\s+           # import statement with a bare
                "(_[^"]*.css)"      # double quoted
                |                   # or
                '(_[^']*.css)'      # single quoted css filename
            )
            |                       # or
            (?:url\(\s*             # a url function with a
                "(_[^"]+)"          # double quoted
                |                   # or
                '(_[^']+)'          # single quoted
                |                   # or
                (_.+?)              # unquoted filename
            \s*\))
    "#,
    )
    .unwrap()
});

/// Strings, src and data attributes with a leading underscore.
static UNDERSCORED_REFERENCES: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?x)
                \[sound:(_[^]]+)\]  # a filename in an Anki sound tag
            |                       # or
                "(_[^"]+)"          # a double quoted
            |                       # or
                '(_[^']+)'          # single quoted string
            |                       # or
                \b(?:src|data)      # a 'src' or 'data' attribute
                =                   # followed by
                (_[^ >]+)           # an unquoted value
    "#,
    )
    .unwrap()
});

pub fn is_html(text: impl AsRef<str>) -> bool {
    HTML.is_match(text.as_ref())
}

pub fn html_to_text_line(html: &str, preserve_media_filenames: bool) -> Cow<'_, str> {
    let (html_stripper, sound_rep): (fn(&str) -> Cow<'_, str>, _) = if preserve_media_filenames {
        (strip_html_preserving_media_filenames, "$1")
    } else {
        (strip_html, "")
    };
    PERSISTENT_HTML_SPACERS
        .replace_all(html, " ")
        .map_cow(|s| TYPE_TAG.replace_all(s, ""))
        .map_cow(|s| SOUND_TAG.replace_all(s, sound_rep))
        .map_cow(html_stripper)
        .trim()
}

pub fn strip_html(html: &str) -> Cow<'_, str> {
    strip_html_preserving_entities(html).map_cow(decode_entities)
}

pub fn strip_html_preserving_entities(html: &str) -> Cow<'_, str> {
    HTML.replace_all(html, "")
}

pub fn decode_entities(html: &str) -> Cow<'_, str> {
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

pub(crate) fn newlines_to_spaces(text: &str) -> Cow<'_, str> {
    if text.contains('\n') {
        text.replace('\n', " ").into()
    } else {
        text.into()
    }
}

pub fn strip_html_for_tts(html: &str) -> Cow<'_, str> {
    HTML_LINEBREAK_TAGS
        .replace_all(html, " ")
        .map_cow(strip_html)
}

/// Truncate a String on a valid UTF8 boundary.
pub(crate) fn truncate_to_char_boundary(s: &mut String, mut max: usize) {
    if max >= s.len() {
        return;
    }
    while !s.is_char_boundary(max) {
        max -= 1;
    }
    s.truncate(max);
}

#[derive(Debug)]
pub(crate) struct MediaRef<'a> {
    pub full_ref: &'a str,
    pub fname: &'a str,
    /// audio files may have things like &amp; that need decoding
    pub fname_decoded: Cow<'a, str>,
}

pub(crate) fn extract_media_refs(text: &str) -> Vec<MediaRef<'_>> {
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

/// Calls `replacer` for every media reference in `text`, and optionally
/// replaces it with something else. [None] if no reference was found.
pub fn replace_media_refs(
    text: &str,
    mut replacer: impl FnMut(&str) -> Option<String>,
) -> Option<String> {
    let mut rep = |caps: &Captures| {
        let whole_match = caps.get(0).unwrap().as_str();
        let old_name = caps.iter().skip(1).find_map(|g| g).unwrap().as_str();
        let old_name_decoded = decode_entities(old_name);

        if let Some(mut new_name) = replacer(&old_name_decoded) {
            if matches!(old_name_decoded, Cow::Owned(_)) {
                new_name = htmlescape::encode_minimal(&new_name);
            }
            whole_match.replace(old_name, &new_name)
        } else {
            whole_match.to_owned()
        }
    };

    HTML_MEDIA_TAGS
        .replace_all(text, &mut rep)
        .map_cow(|s| AV_TAGS.replace_all(s, &mut rep))
        .get_owned()
}

pub(crate) fn extract_underscored_css_imports(text: &str) -> Vec<&str> {
    UNDERSCORED_CSS_IMPORTS
        .captures_iter(text)
        .map(extract_match)
        .collect()
}

pub(crate) fn extract_underscored_references(text: &str) -> Vec<&str> {
    UNDERSCORED_REFERENCES
        .captures_iter(text)
        .map(extract_match)
        .collect()
}

/// Returns the first matching group as a str. This is intended for regexes
/// where exactly one group matches, and will panic for matches without matching
/// groups.
fn extract_match(caps: Captures<'_>) -> &str {
    caps.iter().skip(1).find_map(|g| g).unwrap().as_str()
}

pub fn strip_html_preserving_media_filenames(html: &str) -> Cow<'_, str> {
    HTML_MEDIA_TAGS
        .replace_all(html, r" ${1}${2}${3} ")
        .map_cow(strip_html)
}

pub fn contains_media_tag(html: &str) -> bool {
    HTML_MEDIA_TAGS.is_match(html)
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

pub(crate) fn normalize_to_nfc(s: &str) -> Cow<'_, str> {
    match is_nfc(s) {
        false => s.chars().nfc().collect::<String>().into(),
        true => s.into(),
    }
}

pub(crate) fn ensure_string_in_nfc(s: &mut String) {
    if !is_nfc(s) {
        *s = s.chars().nfc().collect()
    }
}

static EXTRA_NO_COMBINING_REPLACEMENTS: phf::Map<char, &str> = phf::phf_map! {
'€'  =>  "E",
'Æ'  =>  "AE",
'Ð'  =>  "D",
'Ø'  =>  "O",
'Þ'  =>  "TH",
'ß'  =>  "s",
'æ'  =>  "ae",
'ð'  =>  "d",
'ø'  =>  "o",
'þ'  =>  "th",
'Đ'  =>  "D",
'đ'  =>  "d",
'Ħ'  =>  "H",
'ħ'  =>  "h",
'ı'  =>  "i",
'ĸ'  =>  "k",
'Ł'  =>  "L",
'ł'  =>  "l",
'Ŋ'  =>  "N",
'ŋ'  =>  "n",
'Œ'  =>  "OE",
'œ'  =>  "oe",
'Ŧ'  =>  "T",
'ŧ'  =>  "t",
'Ə'  =>  "E",
'ǝ'  =>  "e",
'ɑ'  =>  "a",
};

/// Convert provided string to NFKD form and strip combining characters.
pub(crate) fn without_combining(s: &str) -> Cow<'_, str> {
    // if the string is already normalized
    if matches!(is_nfkd_quick(s.chars()), IsNormalized::Yes) {
        // and no combining characters found, return unchanged
        if !s
            .chars()
            .any(|c| is_combining_mark(c) || EXTRA_NO_COMBINING_REPLACEMENTS.contains_key(&c))
        {
            return s.into();
        }
    }

    // we need to create a new string without the combining marks
    let mut out = String::with_capacity(s.len());
    for chr in s.chars().nfkd().filter(|c| !is_combining_mark(*c)) {
        if let Some(repl) = EXTRA_NO_COMBINING_REPLACEMENTS.get(&chr) {
            out.push_str(repl);
        } else {
            out.push(chr);
        }
    }

    out.into()
}

/// Check if string contains an unescaped wildcard.
pub(crate) fn is_glob(txt: &str) -> bool {
    // even number of \s followed by a wildcard
    static RE: LazyLock<Regex> = LazyLock::new(|| {
        Regex::new(
            r"(?x)
            (?:^|[^\\])     # not a backslash
            (?:\\\\)*       # even number of backslashes
            [*_]            # wildcard
            ",
        )
        .unwrap()
    });

    RE.is_match(txt)
}

/// Convert to a RegEx respecting Anki wildcards.
pub(crate) fn to_re(txt: &str) -> Cow<'_, str> {
    to_custom_re(txt, ".")
}

/// Convert Anki style to RegEx using the provided wildcard.
pub(crate) fn to_custom_re<'a>(txt: &'a str, wildcard: &str) -> Cow<'a, str> {
    static RE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\\?.").unwrap());
    RE.replace_all(txt, |caps: &Captures| {
        let s = &caps[0];
        match s {
            r"\\" | r"\*" => s.to_string(),
            r"\_" => "_".to_string(),
            "*" => format!("{wildcard}*"),
            "_" => wildcard.to_string(),
            s => regex::escape(s),
        }
    })
}

/// Convert to SQL respecting Anki wildcards.
pub(crate) fn to_sql(txt: &str) -> Cow<'_, str> {
    // escape sequences and unescaped special characters which need conversion
    static RE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\\[\\*]|[*%]").unwrap());
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
pub(crate) fn to_text(txt: &str) -> Cow<'_, str> {
    static RE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\\(.)").unwrap());
    RE.replace_all(txt, "$1")
}

/// Escape Anki wildcards and the backslash for escaping them: \*_
pub(crate) fn escape_anki_wildcards(txt: &str) -> String {
    static RE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"[\\*_]").unwrap());
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

pub(crate) static REMOTE_FILENAME: LazyLock<Regex> =
    LazyLock::new(|| Regex::new("(?i)^https?://").unwrap());

/// https://url.spec.whatwg.org/#fragment-percent-encode-set
const FRAGMENT_QUERY_UNION: &AsciiSet = &CONTROLS
    .add(b' ')
    .add(b'"')
    .add(b'<')
    .add(b'>')
    .add(b'`')
    .add(b'#');

/// IRI-encode unescaped local paths in HTML fragment.
pub(crate) fn encode_iri_paths(unescaped_html: &str) -> Cow<'_, str> {
    transform_html_paths(unescaped_html, |fname| {
        utf8_percent_encode(fname, FRAGMENT_QUERY_UNION).into()
    })
}

/// URI-decode escaped local paths in HTML fragment.
pub(crate) fn decode_iri_paths(escaped_html: &str) -> Cow<'_, str> {
    transform_html_paths(escaped_html, |fname| {
        percent_decode_str(fname).decode_utf8_lossy()
    })
}

/// Apply a transform to local filename references in tags like IMG.
/// Required at display time, as Anki unfortunately stores the references
/// in unencoded form in the database.
fn transform_html_paths<F>(html: &str, transform: F) -> Cow<'_, str>
where
    F: Fn(&str) -> Cow<'_, str>,
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

    #[test]
    fn extracting() {
        assert_eq!(
            extract_underscored_css_imports(concat!(
                "@IMPORT '_foo.css'\n",
                "@import \"_bar.css\"\n",
                "@import '_baz.css'\n",
                "@import 'nope.css'\n",
                "url(_foo.css)\n",
                "URL(\"_bar.css\")\n",
                "@import url('_baz.css')\n",
                "url('nope.css')\n",
                "url(_foo.woff2) format('woff2')",
            )),
            vec![
                "_foo.css",
                "_bar.css",
                "_baz.css",
                "_foo.css",
                "_bar.css",
                "_baz.css",
                "_foo.woff2"
            ]
        );
        assert_eq!(
            extract_underscored_references(concat!(
                "<img src=\"_foo.jpg\">",
                "<object data=\"_bar\">",
                "\"_baz.js\"",
                "\"nope.js\"",
                "<img src=_foo.jpg>",
                "<object data=_bar>",
                "'_baz.js'",
            )),
            vec!["_foo.jpg", "_bar", "_baz.js", "_foo.jpg", "_bar", "_baz.js",]
        );
    }

    #[test]
    fn replacing() {
        assert_eq!(
            &replace_media_refs("<img src=foo.jpg>[sound:bar.mp3]<img src=baz.jpg>", |s| {
                (s != "baz.jpg").then(|| "spam".to_string())
            })
            .unwrap(),
            "<img src=spam>[sound:spam]<img src=baz.jpg>",
        );
    }

    #[test]
    fn truncate() {
        let mut s = "日本語".to_string();
        truncate_to_char_boundary(&mut s, 6);
        assert_eq!(&s, "日本");
        let mut s = "日本語".to_string();
        truncate_to_char_boundary(&mut s, 1);
        assert_eq!(&s, "");
    }

    #[test]
    fn iri_encoding() {
        for (input, output) in [
            ("foo.jpg", "foo.jpg"),
            ("bar baz", "bar%20baz"),
            ("sub/path.jpg", "sub/path.jpg"),
            ("日本語", "日本語"),
            ("a=b", "a=b"),
            ("a&b", "a&b"),
        ] {
            assert_eq!(
                &encode_iri_paths(&format!("<img src=\"{input}\">")),
                &format!("<img src=\"{output}\">")
            );
        }
    }
}
