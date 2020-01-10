// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use lazy_static::lazy_static;
use regex::Regex;
use std::borrow::Cow;
use std::collections::HashSet;
use std::ptr;

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
        // group 1 is filename
        r#"(?i)<img[^>]+src=["']?([^"'>]+)["']?[^>]*>"#
    ).unwrap();

    static ref SOUND_TAG: Regex = Regex::new(
        r"\[sound:(.*?)\]"
    ).unwrap();

    static ref CLOZED_TEXT: Regex = Regex::new(
        r"(?s)\{\{c(\d+)::.+?\}\}"
    ).unwrap();
}

pub fn strip_html(html: &str) -> Cow<str> {
    HTML.replace_all(html, "")
}

pub fn strip_sounds(html: &str) -> Cow<str> {
    SOUND_TAG.replace_all(html, "")
}

pub fn strip_html_preserving_image_filenames(html: &str) -> Cow<str> {
    let without_fnames = IMG_TAG.replace_all(html, r" $1 ");
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

pub fn cloze_numbers_in_string(html: &str) -> HashSet<u16> {
    let mut hash = HashSet::with_capacity(4);
    for cap in CLOZED_TEXT.captures_iter(html) {
        if let Ok(n) = cap[1].parse() {
            hash.insert(n);
        }
    }
    hash
}

#[cfg(test)]
mod test {
    use crate::text::{cloze_numbers_in_string, strip_html, strip_html_preserving_image_filenames};
    use std::collections::HashSet;

    #[test]
    fn test_stripping() {
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
    fn test_cloze() {
        assert_eq!(
            cloze_numbers_in_string("test"),
            vec![].into_iter().collect::<HashSet<u16>>()
        );
        assert_eq!(
            cloze_numbers_in_string("{{c2::te}}{{c1::s}}t{{"),
            vec![1, 2].into_iter().collect::<HashSet<u16>>()
        );
    }
}
