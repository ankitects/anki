// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::sync::LazyLock;

use blake3::Hasher;
use regex::Captures;
use regex::Regex;

use crate::cloze::cloze_filter;
use crate::cloze::cloze_only_filter;
use crate::template::RenderContext;
use crate::text::strip_html;

// Filtering
//----------------------------------------

/// Applies built in filters, returning the resulting text and remaining
/// filters.
///
/// If [context.partial_for_python] is true, the first non-standard filter that
/// is encountered will terminate processing, so non-standard filters must come
/// at the end. If false, missing filters are ignored.
pub(crate) fn apply_filters<'a>(
    text: &'a str,
    filters: &[&str],
    field_name: &str,
    context: &RenderContext,
) -> (Cow<'a, str>, Vec<String>) {
    let mut text: Cow<str> = text.into();

    // type:cloze & type:nc are handled specially
    // other type: are passed as the default one
    let filters = match filters {
        ["cloze", "type"] => &["type-cloze"],
        ["nc", "type"] => &["type-nc"],
        [.., "type"] => &["type"],
        _ => filters,
    };

    for (idx, &filter_name) in filters.iter().enumerate() {
        match apply_filter(filter_name, text.as_ref(), field_name, context) {
            (true, None) => {
                // filter did not change text
            }
            (true, Some(output)) => {
                // text updated
                text = output.into();
            }
            (false, _) => {
                // unrecognized filter
                if context.partial_for_python {
                    //  return current text and remaining filters
                    return (
                        text,
                        filters.iter().skip(idx).map(ToString::to_string).collect(),
                    );
                }
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
fn apply_filter(
    filter_name: &str,
    text: &str,
    field_name: &str,
    context: &RenderContext,
) -> (bool, Option<String>) {
    let output_text = match filter_name {
        "text" => strip_html(text),
        "furigana" => furigana_filter(text),
        "kanji" => kanji_filter(text),
        "kana" => kana_filter(text),
        "type" => type_filter(field_name),
        "type-cloze" => type_cloze_filter(field_name),
        "type-nc" => type_nc_filter(field_name),
        "hint" => hint_filter(text, field_name),
        "cloze" => cloze_filter(text, context),
        "cloze-only" => cloze_only_filter(text, context),
        // an empty filter name (caused by using two colons) is ignored
        "" => text.into(),
        _ => {
            if let Some(options) = filter_name.strip_prefix("tts ") {
                tts_filter(options, text).into()
            } else {
                // unrecognized filter
                return (false, None);
            }
        }
    };

    (
        true,
        match output_text {
            Cow::Owned(o) => Some(o),
            _ => None,
        },
    )
}

// Ruby filters
//----------------------------------------

static FURIGANA: LazyLock<Regex> = LazyLock::new(|| Regex::new(r" ?([^ >]+?)\[(.+?)\]").unwrap());

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

/// convert to [[type:...]] for the gui code to process
fn type_filter<'a>(field_name: &str) -> Cow<'a, str> {
    format!("[[type:{}]]", field_name).into()
}

fn type_cloze_filter<'a>(field_name: &str) -> Cow<'a, str> {
    format!("[[type:cloze:{}]]", field_name).into()
}

fn type_nc_filter<'a>(field_name: &str) -> Cow<'a, str> {
    format!("[[type:nc:{}]]", field_name).into()
}

fn hint_filter<'a>(text: &'a str, field_name: &str) -> Cow<'a, str> {
    if text.trim().is_empty() {
        return text.into();
    }

    // generate a unique DOM id
    let mut hasher = Hasher::new();
    hasher.update(text.as_bytes());
    hasher.update(field_name.as_bytes());
    let id = hex::encode(&hasher.finalize().as_bytes()[0..8]);

    format!(
        r##"
<a class=hint href="#"
onclick="this.style.display='none';
document.getElementById('hint{}').style.display='block';
return false;" draggable=false>
{}</a>
<div id="hint{}" class=hint style="display: none">{}</div>
"##,
        id, field_name, id, text
    )
    .into()
}

fn tts_filter(options: &str, text: &str) -> String {
    format!("[anki:tts lang={}]{}[/anki:tts]", options, text)
}

// Tests
//----------------------------------------

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn furigana() {
        let text = "test first[second] third[fourth]";
        assert_eq!(kana_filter(text).as_ref(), "testsecondfourth");
        assert_eq!(kanji_filter(text).as_ref(), "testfirstthird");
        assert_eq!(
            furigana_filter("first[second]").as_ref(),
            "<ruby><rb>first</rb><rt>second</rt></ruby>"
        );
    }

    #[allow(clippy::needless_raw_string_hashes)]
    #[test]
    fn hint() {
        assert_eq!(
            hint_filter("foo", "field"),
            r##"
<a class=hint href="#"
onclick="this.style.display='none';
document.getElementById('hint83fe48607f0f3a66').style.display='block';
return false;" draggable=false>
field</a>
<div id="hint83fe48607f0f3a66" class=hint style="display: none">foo</div>
"##
        );
    }

    #[test]
    fn typing() {
        assert_eq!(type_filter("Front"), "[[type:Front]]");
        assert_eq!(type_cloze_filter("Front"), "[[type:cloze:Front]]");
        assert_eq!(type_nc_filter("Front"), "[[type:nc:Front]]");
        let ctx = RenderContext {
            fields: &Default::default(),
            nonempty_fields: &Default::default(),
            frontside: Some(""),
            card_ord: 0,
            partial_for_python: true,
        };
        assert_eq!(
            apply_filters("ignored", &["cloze", "type"], "Text", &ctx),
            ("[[type:cloze:Text]]".into(), vec![])
        );
        assert_eq!(
            apply_filters("ignored", &["nc", "type"], "Text", &ctx),
            ("[[type:nc:Text]]".into(), vec![])
        );
        assert_eq!(
            apply_filters("ignored", &["some", "unknown", "type"], "Text", &ctx),
            ("[[type:Text]]".into(), vec![])
        );
    }

    #[test]
    fn cloze() {
        let text = "{{c1::one}} {{c2::two::hint}}";
        let mut ctx = RenderContext {
            fields: &Default::default(),
            nonempty_fields: &Default::default(),
            frontside: None,
            card_ord: 0,
            partial_for_python: true,
        };
        assert_eq!(strip_html(&cloze_filter(text, &ctx)).as_ref(), "[...] two");
        assert_eq!(
            cloze_filter(text, &ctx),
            r#"<span class="cloze" data-cloze="one" data-ordinal="1">[...]</span> <span class="cloze-inactive" data-ordinal="2">two</span>"#
        );

        ctx.card_ord = 1;
        assert_eq!(strip_html(&cloze_filter(text, &ctx)).as_ref(), "one [hint]");

        ctx.frontside = Some("");
        assert_eq!(strip_html(&cloze_filter(text, &ctx)).as_ref(), "one two");

        // if the provided ordinal did not match any cloze deletions,
        // Anki treats the string as blank, which add-ons like
        // cloze overlapper take advantage of.
        ctx.card_ord = 2;
        assert_eq!(cloze_filter(text, &ctx).as_ref(), "");
    }

    #[test]
    fn tts() {
        assert_eq!(
            tts_filter("en_US voices=Bob,Jane", "foo"),
            "[anki:tts lang=en_US voices=Bob,Jane]foo[/anki:tts]"
        );
    }
}
