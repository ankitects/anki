// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::text::strip_html;
use lazy_static::lazy_static;
use regex::{Captures, Regex};
use std::borrow::Cow;

// Filtering
//----------------------------------------

/// Applies built in filters, returning the resulting text and remaining filters.
///
/// The first non-standard filter that is encountered will terminate processing,
/// so non-standard filters must come at the end.
pub(crate) fn apply_filters<'a>(text: &'a str, filters: &[&str]) -> (Cow<'a, str>, Vec<String>) {
    let mut text: Cow<str> = text.into();

    for (idx, &filter_name) in filters.iter().enumerate() {
        match apply_filter(filter_name, text.as_ref()) {
            (true, None) => {
                // filter did not change text
            }
            (true, Some(output)) => {
                // text updated
                text = output.into();
            }
            (false, _) => {
                // unrecognized filter, return current text and remaining filters
                return (
                    text,
                    filters.iter().skip(idx).map(ToString::to_string).collect(),
                );
            }
        }
    }

    // all filters processed
    (text, vec![])
}

/// Apply one filter.
///
/// Returns true if filter was valid.
/// Returns string if input text changed.
fn apply_filter<'a>(filter_name: &str, text: &'a str) -> (bool, Option<String>) {
    let output_text = match filter_name {
        "text" => strip_html(text),
        "furigana" => furigana_filter(text),
        "kanji" => kanji_filter(text),
        "kana" => kana_filter(text),
        _ => return (false, None),
    };

    (
        true,
        match output_text {
            Cow::Owned(o) => Some(o),
            _ => None,
        },
    )
}

// Cloze filter
//----------------------------------------

// Ruby filters
//----------------------------------------

lazy_static! {
    static ref FURIGANA: Regex = Regex::new(r" ?([^ >]+?)\[(.+?)\]").unwrap();
}

/// Did furigana regex match a sound tag?
fn captured_sound(caps: &Captures) -> bool {
    caps.get(2).unwrap().as_str().starts_with("sound:")
}

fn kana_filter(text: &str) -> Cow<str> {
    FURIGANA
        .replace_all(&text.replace("&nbsp;", " "), |caps: &Captures| {
            if captured_sound(caps) {
                caps.get(0).unwrap().as_str().to_owned()
            } else {
                caps.get(2).unwrap().as_str().to_owned()
            }
        })
        .into_owned()
        .into()
}

fn kanji_filter(text: &str) -> Cow<str> {
    FURIGANA
        .replace_all(&text.replace("&nbsp;", " "), |caps: &Captures| {
            if captured_sound(caps) {
                caps.get(0).unwrap().as_str().to_owned()
            } else {
                caps.get(1).unwrap().as_str().to_owned()
            }
        })
        .into_owned()
        .into()
}

fn furigana_filter(text: &str) -> Cow<str> {
    FURIGANA
        .replace_all(&text.replace("&nbsp;", " "), |caps: &Captures| {
            if captured_sound(caps) {
                caps.get(0).unwrap().as_str().to_owned()
            } else {
                format!(
                    "<ruby><rb>{}</rb><rt>{}</rt></ruby>",
                    caps.get(1).unwrap().as_str(),
                    caps.get(2).unwrap().as_str()
                )
            }
        })
        .into_owned()
        .into()
}

// Other filters
//----------------------------------------

// - type
// - hint

// Tests
//----------------------------------------

#[cfg(test)]
mod test {
    use crate::template_filters::{furigana_filter, kana_filter, kanji_filter};

    #[test]
    fn test_furigana() {
        let text = "test first[second] third[fourth]";
        assert_eq!(kana_filter(text).as_ref(), "testsecondfourth");
        assert_eq!(kanji_filter(text).as_ref(), "testfirstthird");
        assert_eq!(
            furigana_filter("first[second]").as_ref(),
            "<ruby><rb>first</rb><rt>second</rt></ruby>"
        );
    }
}
