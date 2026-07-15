// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fmt::Write;

use anki_proto::image_occlusion::get_image_occlusion_note_response::ImageOcclusionProperty;
use anki_proto::image_occlusion::get_image_occlusion_note_response::ImageOcclusionShape;
use htmlescape::encode_attribute;

fn unescape(text: &str) -> String {
    text.replace("\\:", ":")
}

/// Split on each `:` that is not escaped by a preceding backslash. Property
/// values escape a literal colon as `\:` (backslashes are not otherwise
/// escaped), so an unescaped `:` always separates one property from the next.
fn split_on_unescaped_colon(text: &str) -> Vec<&str> {
    let mut parts = vec![];
    let mut start = 0;
    let bytes = text.as_bytes();
    for i in 0..bytes.len() {
        if bytes[i] == b':' {
            // An even number of preceding backslashes means they pair off and
            // the colon itself is unescaped (a separator); an odd number means
            // the colon is escaped (`\:`) and part of the value.
            let preceding_backslashes =
                bytes[..i].iter().rev().take_while(|&&b| b == b'\\').count();
            if preceding_backslashes % 2 == 0 {
                parts.push(&text[start..i]);
                start = i + 1;
            }
        }
    }
    parts.push(&text[start..]);
    parts
}

pub fn parse_image_cloze(text: &str) -> Option<ImageOcclusionShape> {
    let (shape, rest) = text.split_once(':')?;
    // Parse every `name=value` property. Values may be empty or contain a
    // backslash (e.g. LaTeX), so we don't stop at the first one that fails a
    // strict grammar - that previously discarded the rest of the properties.
    let properties = split_on_unescaped_colon(rest)
        .into_iter()
        .filter_map(|prop| {
            let (name, value) = prop.split_once('=')?;
            Some(ImageOcclusionProperty {
                name: name.to_string(),
                value: unescape(value),
            })
        })
        .collect();
    Some(ImageOcclusionShape {
        shape: shape.to_string(),
        properties,
    })
}

// convert text like
// rect:left=.2325:top=.3261:width=.202:height=.0975
// to something like
// result = "data-shape="rect" data-left="399.01" data-top="99.52"
// data-width="167.09" data-height="33.78"
pub fn get_image_cloze_data(text: &str) -> String {
    let mut result = String::new();

    if let Some(occlusion) = parse_image_cloze(text) {
        if !occlusion.shape.is_empty()
            && matches!(
                occlusion.shape.as_str(),
                "rect" | "ellipse" | "polygon" | "text"
            )
        {
            result.push_str(&format!("data-shape=\"{}\" ", occlusion.shape));
        }
        for property in occlusion.properties {
            match property.name.as_str() {
                "left" | "top" | "angle" | "fill" => {
                    if !property.value.is_empty() {
                        result.push_str(&format!("data-{}=\"{}\" ", property.name, property.value));
                    }
                }
                "width" => {
                    if !is_empty_or_zero(&property.value) {
                        result.push_str(&format!("data-width=\"{}\" ", property.value));
                    }
                }
                "height" => {
                    if !is_empty_or_zero(&property.value) {
                        result.push_str(&format!("data-height=\"{}\" ", property.value));
                    }
                }
                "rx" => {
                    if !is_empty_or_zero(&property.value) {
                        result.push_str(&format!("data-rx=\"{}\" ", property.value));
                    }
                }
                "ry" => {
                    if !is_empty_or_zero(&property.value) {
                        result.push_str(&format!("data-ry=\"{}\" ", property.value));
                    }
                }
                "points" => {
                    if !property.value.is_empty() {
                        let mut point_str = String::new();
                        for point_pair in property.value.split(' ') {
                            let Some((x, y)) = point_pair.split_once(',') else {
                                continue;
                            };
                            write!(&mut point_str, "{x},{y} ").unwrap();
                        }
                        // remove the trailing space
                        point_str.pop();
                        if !point_str.is_empty() {
                            result.push_str(&format!("data-points=\"{point_str}\" "));
                        }
                    }
                }
                "oi" => {
                    if !property.value.is_empty() {
                        result.push_str(&format!("data-occludeInactive=\"{}\" ", property.value));
                    }
                }
                "text" => {
                    if !property.value.is_empty() {
                        result.push_str(&format!(
                            "data-text=\"{}\" ",
                            encode_attribute(&property.value)
                        ));
                    }
                }
                "scale" => {
                    if !is_empty_or_zero(&property.value) {
                        result.push_str(&format!("data-scale=\"{}\" ", property.value));
                    }
                }
                "fs" => {
                    if !property.value.is_empty() {
                        result.push_str(&format!("data-font-size=\"{}\" ", property.value));
                    }
                }
                _ => {}
            }
        }
    }

    result
}

fn is_empty_or_zero(text: &str) -> bool {
    text.is_empty() || text == "0"
}

//----------------------------------------
// Tests
//----------------------------------------

#[test]
fn test_get_image_cloze_data() {
    assert_eq!(
        get_image_cloze_data("rect:left=10:top=20:width=30:height=10"),
        format!(
            r#"data-shape="rect" data-left="10" data-top="20" data-width="30" data-height="10" "#,
        )
    );
    assert_eq!(
        get_image_cloze_data("ellipse:left=15:top=20:width=10:height=20:rx=10:ry=5"),
        r#"data-shape="ellipse" data-left="15" data-top="20" data-width="10" data-height="20" data-rx="10" data-ry="5" "#,
    );
    assert_eq!(
        get_image_cloze_data("polygon:points=0,0 10,10 20,0"),
        r#"data-shape="polygon" data-points="0,0 10,10 20,0" "#,
    );
    assert_eq!(
        get_image_cloze_data("text:text=foo\\:bar:left=10"),
        r#"data-shape="text" data-text="foo&#x3A;bar" data-left="10" "#,
    );
}

#[test]
fn parses_empty_and_backslash_values() {
    // an empty intermediate value must not drop the properties that follow it
    assert_eq!(
        get_image_cloze_data("text:left=10:text=:top=20"),
        r#"data-shape="text" data-left="10" data-top="20" "#,
    );
    // a backslash in a value (e.g. LaTeX) must not drop it or the props after
    // it (the backslash is HTML-escaped to &#x5C; in the data attribute)
    assert_eq!(
        get_image_cloze_data("text:left=10:text=\\frac:top=20"),
        r#"data-shape="text" data-left="10" data-text="&#x5C;frac" data-top="20" "#,
    );
}
