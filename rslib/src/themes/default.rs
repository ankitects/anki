// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use crate::pb::themes::Color;
pub use crate::pb::themes::Colors;
pub use crate::pb::themes::Factors;
pub use crate::pb::themes::Vars;

macro_rules! rgba {
    ($r:expr, $g:expr, $b:expr, $a:expr) => {
        Some(Color {
            red: $r as f32 / 255.0,
            green: $g as f32 / 255.0,
            blue: $b as f32 / 255.0,
            alpha: $a as f32,
        })
    };
}

const DEFAULT_FACTORS: Factors = Factors {
    font_size: 1.0,
    transparency: 0.35,
    roundness: 1.0,
    spacing: 1.0,
    animation_speed: 1.0,

    other: Vec::new(),
};

// I'd like to define the default base colors in a helper object
// with light/dark pairs and then iterate that to create the Vars object,
// but struggle to make this work due to typing issues

const DEFAULT_LIGHT_PALETTE: Colors = Colors {
    canvas: rgba!(245, 245, 245, 1),
    canvas_secondary: rgba!(255, 255, 255, 1),
    canvas_code: rgba!(255, 255, 255, 1),
    fg: rgba!(2, 2, 2, 1),
    link: rgba!(29, 78, 216, 1),
    border: rgba!(196, 196, 196, 1),
    focus: rgba!(59, 1, 246, 1),
    shadow: rgba!(54, 54, 54, 0.6),
    glass: rgba!(255, 255, 255, 0.4),
    button: rgba!(252, 252, 252, 1),
    button_primary: rgba!(48, 107, 236, 1),
    scrollbar: rgba!(214, 214, 214, 1),
    input_bg: rgba!(255, 255, 255, 1),
    card: rgba!(96, 165, 250, 1),
    note: rgba!(34, 197, 94, 1),
    warning: rgba!(239, 68, 68, 1),
    flag_1: rgba!(239, 68, 68, 1),
    flag_2: rgba!(251, 146, 60, 1),
    flag_3: rgba!(74, 222, 128, 1),
    flag_4: rgba!(59, 1, 246, 1),
    flag_5: rgba!(232, 121, 249, 1),
    flag_6: rgba!(45, 212, 191, 1),
    flag_7: rgba!(168, 85, 247, 1),
    card_new: rgba!(59, 1, 246, 1),
    card_learn: rgba!(220, 38, 38, 1),
    card_review: rgba!(22, 163, 74, 1),
    card_buried: rgba!(245, 158, 11, 1),
    card_suspended: rgba!(250, 204, 21, 1),
    card_marked: rgba!(99, 102, 241, 1),
    text_highlighted_bg: rgba!(37, 99, 235, 0.5),
    text_highlighted_fg: rgba!(0, 0, 0, 1),
    item_selected_bg: rgba!(214, 214, 214, 0.5),
    item_selected_fg: rgba!(0, 0, 0, 1),
    search_match_bg: rgba!(214, 214, 214, 0.5),
    search_match_fg: rgba!(0, 0, 0, 1),

    other: Vec::new(),
};

const DEFAULT_DARK_PALETTE: Colors = Colors {
    canvas: rgba!(44, 44, 44, 1),
    canvas_secondary: rgba!(54, 54, 54, 1),
    canvas_code: rgba!(37, 37, 37, 1),
    fg: rgba!(252, 252, 252, 1),
    link: rgba!(191, 219, 254, 1),
    border: rgba!(32, 32, 32, 1),
    focus: rgba!(59, 130, 246, 1),
    shadow: rgba!(20, 20, 20, 1),
    glass: rgba!(54, 54, 54, 0.4),
    button: rgba!(64, 64, 64, 1),
    button_primary: rgba!(38, 82, 207, 1),
    scrollbar: rgba!(69, 69, 69, 1),
    input_bg: rgba!(44, 44, 44, 1),
    card: rgba!(147, 197, 253, 1),
    note: rgba!(74, 222, 128, 1),
    warning: rgba!(248, 113, 113, 1),
    flag_1: rgba!(248, 113, 113, 1),
    flag_2: rgba!(253, 186, 116, 1),
    flag_3: rgba!(134, 239, 172, 1),
    flag_4: rgba!(96, 165, 250, 1),
    flag_5: rgba!(240, 171, 252, 1),
    flag_6: rgba!(94, 234, 212, 1),
    flag_7: rgba!(192, 132, 252, 1),
    card_new: rgba!(147, 197, 253, 1),
    card_learn: rgba!(248, 113, 113, 1),
    card_review: rgba!(34, 197, 94, 1),
    card_buried: rgba!(146, 64, 14, 1),
    card_suspended: rgba!(254, 249, 195, 1),
    card_marked: rgba!(168, 85, 247, 1),
    text_highlighted_bg: rgba!(147, 197, 253, 0.5),
    text_highlighted_fg: rgba!(255, 255, 255, 1),
    item_selected_bg: rgba!(147, 197, 253, 0.5),
    item_selected_fg: rgba!(255, 255, 255, 1),
    search_match_bg: rgba!(147, 197, 253, 0.5),
    search_match_fg: rgba!(255, 255, 255, 1),

    other: Vec::new(),
};

pub const DEFAULT_VARS_LIGHT: Vars = Vars {
    factors: Some(DEFAULT_FACTORS),
    colors: Some(DEFAULT_LIGHT_PALETTE),
};

pub const DEFAULT_VARS_DARK: Vars = Vars {
    factors: Some(DEFAULT_FACTORS),
    colors: Some(DEFAULT_DARK_PALETTE),
};
