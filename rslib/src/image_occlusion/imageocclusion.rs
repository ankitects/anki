// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fmt::Write;

// convert text like
// rect:left=.2325:top=.3261:width=.202:height=.0975
// to something like
// result = "data-shape="rect" data-left="399.01" data-top="99.52"
// data-width="167.09" data-height="33.78"
pub fn get_image_cloze_data(text: &str) -> String {
    let mut result = String::new();
    let parts: Vec<&str> = text.split(':').collect();

    if parts.len() >= 2 {
        if !parts[0].is_empty()
            && (parts[0] == "rect" || parts[0] == "ellipse" || parts[0] == "polygon")
        {
            result.push_str(&format!("data-shape=\"{}\" ", parts[0]));
        }

        for part in parts[1..].iter() {
            let values: Vec<&str> = part.split('=').collect();
            if values.len() >= 2 {
                match values[0] {
                    "left" => {
                        if !values[1].is_empty() {
                            result.push_str(&format!("data-left=\"{}\" ", values[1]));
                        }
                    }
                    "top" => {
                        if !values[1].is_empty() {
                            result.push_str(&format!("data-top=\"{}\" ", values[1]));
                        }
                    }
                    "width" => {
                        if !is_empty_or_zero(values[1]) {
                            result.push_str(&format!("data-width=\"{}\" ", values[1]));
                        }
                    }
                    "height" => {
                        if !is_empty_or_zero(values[1]) {
                            result.push_str(&format!("data-height=\"{}\" ", values[1]));
                        }
                    }
                    "rx" => {
                        if !is_empty_or_zero(values[1]) {
                            result.push_str(&format!("data-rx=\"{}\" ", values[1]));
                        }
                    }
                    "ry" => {
                        if !is_empty_or_zero(values[1]) {
                            result.push_str(&format!("data-ry=\"{}\" ", values[1]));
                        }
                    }
                    "points" => {
                        if !values[1].is_empty() {
                            let mut point_str = String::new();
                            for point_pair in values[1].split(' ') {
                                let Some((x, y)) = point_pair.split_once(',') else { continue };
                                write!(&mut point_str, "{},{} ", x, y).unwrap();
                            }
                            // remove the trailing space
                            point_str.pop();
                            if !point_str.is_empty() {
                                result.push_str(&format!("data-points=\"{point_str}\" "));
                            }
                        }
                    }
                    "oi" => {
                        if !values[1].is_empty() {
                            result.push_str(&format!("data-occludeInactive=\"{}\" ", values[1]));
                        }
                    }
                    _ => {}
                }
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
}
