// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// split following
// text = "rect:399.01,99.52,167.09,33.78:fill=#0a2cee:stroke=1"
// with
// result = "data-shape="rect" data-left="399.01" data-top="99.52" data-width="167.09" data-height="33.78" data-fill="\#0a2cee" data-stroke="1""
pub fn get_image_cloze_data(text: &str) -> String {
    let mut result = String::new();
    let mut shape = "";
    let mut angle = "";
    let mut fill = "";
    let mut height = "";
    let mut hideinactive = "";
    let mut left = "";
    let mut points = "";
    let mut questionmaskcolor = "";
    let mut rx = "";
    let mut ry = "";
    let mut top = "";
    let mut width = "";

    let parts: Vec<&str> = text.split(':').collect();

    if parts.len() >= 2 {
        shape = parts[0];
        for part in parts[1..].iter() {
            let values: Vec<&str> = part.split('=').collect();
            if values.len() >= 2 {
                match values[0] {
                    "left" => left = values[1],
                    "top" => top = values[1],
                    "width" => width = values[1],
                    "height" => height = values[1],
                    "fill" => fill = values[1],
                    "rx" => rx = values[1],
                    "ry" => ry = values[1],
                    "angle" => angle = values[1],
                    "points" => points = values[1],
                    "questionmaskcolor" => questionmaskcolor = values[1],
                    "hideinactive" => hideinactive = values[1],
                    _ => {}
                }
            }
        }
    }

    if !shape.is_empty() {
        result.push_str(&format!("data-shape=\"{}\" ", shape));
    }

    // special properties to shapes
    match shape {
        "ellipse" => {
            if !rx.is_empty() {
                result.push_str(&format!("data-rx=\"{}\" ", rx));
            }
            if !ry.is_empty() {
                result.push_str(&format!("data-ry=\"{}\" ", ry));
            }
        }

        "polygon" => {
            if !points.is_empty() {
                let points_vec: Vec<&str> = points.split(' ').collect();
                let mut point_str = String::new();
                for point in &points_vec {
                    let xy: Vec<&str> = point.split(',').collect();
                    point_str.push_str(&format!("[{},{}],", xy[0], xy[1]));
                }
                point_str.pop();

                if !point_str.is_empty() {
                    result.push_str(&format!("data-points=\"[{}]\" ", point_str));
                }
            }
        }

        _ => {}
    }

    if !left.is_empty() {
        result.push_str(&format!("data-left=\"{}\" ", left));
    }

    if !top.is_empty() {
        result.push_str(&format!("data-top=\"{}\" ", top));
    }

    if !width.is_empty() {
        result.push_str(&format!("data-width=\"{}\" ", width));
    }

    if !height.is_empty() {
        result.push_str(&format!("data-height=\"{}\" ", height));
    }

    if !angle.is_empty() {
        result.push_str(&format!("data-angle=\"{}\" ", angle));
    }

    if !fill.is_empty() {
        result.push_str(&format!("data-fill=\"{}\" ", fill));
    }

    if !questionmaskcolor.is_empty() {
        result.push_str(&format!(
            "data-questionmaskcolor=\"{}\" ",
            questionmaskcolor
        ));
    }

    if !hideinactive.is_empty() {
        result.push_str(&format!("data-hideinactive=\"{}\" ", hideinactive));
    }

    result
}

//----------------------------------------
// Tests
//----------------------------------------

#[test]
fn test_get_image_cloze_data() {
    assert_eq!(
        get_image_cloze_data(
            "rect:left=10:top=20:width=30:height=10:fill=#ffe34d:questionmaskcolor=#ff0000"
        ),
        format!(
            r#"data-shape="rect" data-left="10" data-top="20" data-width="30" data-height="10" data-fill="{}" data-questionmaskcolor="{}" "#,
            "#ffe34d", "#ff0000"
        )
    );
    assert_eq!(
        get_image_cloze_data("ellipse:left=15:top=20:width=10:height=20:rx=10:ry=5:fill=red"),
        r#"data-shape="ellipse" data-rx="10" data-ry="5" data-left="15" data-top="20" data-width="10" data-height="20" data-fill="red" "#
    );
    assert_eq!(
        get_image_cloze_data("polygon:points=0,0 10,10 20,0:fill=blue"),
        r#"data-shape="polygon" data-points="[[0,0],[10,10],[20,0]]" data-fill="blue" "#
    );
}
