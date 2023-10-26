// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_i18n::I18n;
use anki_proto::notetypes::notetype::config::Kind as NotetypeKind;
use anki_proto::notetypes::stock_notetype::Kind;
pub(crate) use anki_proto::notetypes::stock_notetype::Kind as StockKind;
use anki_proto::notetypes::stock_notetype::OriginalStockKind;
use anki_proto::notetypes::ClozeField;

use super::NotetypeConfig;
use crate::config::ConfigEntry;
use crate::config::ConfigKey;
use crate::error::Result;
use crate::image_occlusion::notetype::image_occlusion_notetype;
use crate::invalid_input;
use crate::notetype::Notetype;
use crate::storage::SqliteStorage;
use crate::timestamp::TimestampSecs;

impl SqliteStorage {
    pub(crate) fn add_stock_notetypes(&self, tr: &I18n) -> Result<()> {
        for (idx, mut nt) in all_stock_notetypes(tr).into_iter().enumerate() {
            nt.prepare_for_update(None, true)?;
            self.add_notetype(&mut nt)?;
            if idx == 0 {
                self.set_config_entry(&ConfigEntry::boxed(
                    ConfigKey::CurrentNotetypeId.into(),
                    serde_json::to_vec(&nt.id)?,
                    self.usn(false)?,
                    TimestampSecs::now(),
                ))?;
            }
        }
        Ok(())
    }
}

// If changing this, make sure to update StockNotetype enum. Other parts of the
// code expect the order here to be the same as the enum.
pub fn all_stock_notetypes(tr: &I18n) -> Vec<Notetype> {
    vec![
        basic(tr),
        basic_forward_reverse(tr),
        basic_optional_reverse(tr),
        basic_typing(tr),
        cloze(tr),
        image_occlusion_notetype(tr),
    ]
}

/// returns {{name}}
fn fieldref<S: AsRef<str>>(name: S) -> String {
    format!("{{{{{}}}}}", name.as_ref())
}

/// Create an empty notetype with a given name and stock kind.
pub(crate) fn empty_stock(
    nt_kind: NotetypeKind,
    original_stock_kind: OriginalStockKind,
    name: impl Into<String>,
) -> Notetype {
    Notetype {
        name: name.into(),
        config: NotetypeConfig {
            kind: nt_kind as i32,
            original_stock_kind: original_stock_kind as i32,
            ..if nt_kind == NotetypeKind::Cloze {
                Notetype::new_cloze_config()
            } else {
                Notetype::new_config()
            }
        },
        ..Default::default()
    }
}

pub(crate) fn get_stock_notetype(kind: StockKind, tr: &I18n) -> Notetype {
    match kind {
        Kind::Basic => basic(tr),
        Kind::BasicAndReversed => basic_forward_reverse(tr),
        Kind::BasicOptionalReversed => basic_optional_reverse(tr),
        Kind::BasicTyping => basic_typing(tr),
        Kind::Cloze => cloze(tr),
        Kind::ImageOcclusion => image_occlusion_notetype(tr),
    }
}

pub(crate) fn get_original_stock_notetype(kind: OriginalStockKind, tr: &I18n) -> Result<Notetype> {
    Ok(match kind {
        OriginalStockKind::Unknown => invalid_input!("original stock kind not provided"),
        OriginalStockKind::Basic => basic(tr),
        OriginalStockKind::BasicAndReversed => basic_forward_reverse(tr),
        OriginalStockKind::BasicOptionalReversed => basic_optional_reverse(tr),
        OriginalStockKind::BasicTyping => basic_typing(tr),
        OriginalStockKind::Cloze => cloze(tr),
        OriginalStockKind::ImageOcclusion => image_occlusion_notetype(tr),
    })
}

pub(crate) fn basic(tr: &I18n) -> Notetype {
    let mut nt = empty_stock(
        NotetypeKind::Normal,
        OriginalStockKind::Basic,
        tr.notetypes_basic_name(),
    );
    let front = tr.notetypes_front_field();
    let back = tr.notetypes_back_field();
    nt.add_field(front.as_ref());
    nt.add_field(back.as_ref());
    nt.add_template(
        tr.notetypes_card_1_name(),
        fieldref(front),
        format!(
            "{}\n\n<hr id=answer>\n\n{}",
            fieldref("FrontSide"),
            fieldref(back),
        ),
    );
    nt
}

pub(crate) fn basic_typing(tr: &I18n) -> Notetype {
    let mut nt = basic(tr);
    nt.config.original_stock_kind = StockKind::BasicTyping as i32;
    nt.name = tr.notetypes_basic_type_answer_name().into();
    let front = tr.notetypes_front_field();
    let back = tr.notetypes_back_field();
    let tmpl = &mut nt.templates[0].config;
    tmpl.q_format = format!("{}\n\n{{{{type:{}}}}}", fieldref(front.as_ref()), back);
    tmpl.a_format = format!(
        "{}\n\n<hr id=answer>\n\n{{{{type:{}}}}}",
        fieldref(front),
        back
    );
    nt
}

pub(crate) fn basic_forward_reverse(tr: &I18n) -> Notetype {
    let mut nt = basic(tr);
    nt.config.original_stock_kind = StockKind::BasicAndReversed as i32;
    nt.name = tr.notetypes_basic_reversed_name().into();
    let front = tr.notetypes_front_field();
    let back = tr.notetypes_back_field();
    nt.add_template(
        tr.notetypes_card_2_name(),
        fieldref(back),
        format!(
            "{}\n\n<hr id=answer>\n\n{}",
            fieldref("FrontSide"),
            fieldref(front),
        ),
    );
    nt
}

pub(crate) fn basic_optional_reverse(tr: &I18n) -> Notetype {
    let mut nt = basic_forward_reverse(tr);
    nt.config.original_stock_kind = StockKind::BasicOptionalReversed as i32;
    nt.name = tr.notetypes_basic_optional_reversed_name().into();
    let addrev = tr.notetypes_add_reverse_field();
    nt.add_field(addrev.as_ref());
    let tmpl = &mut nt.templates[1].config;
    tmpl.q_format = format!("{{{{#{}}}}}{}{{{{/{}}}}}", addrev, tmpl.q_format, addrev);
    nt
}

pub(crate) fn cloze(tr: &I18n) -> Notetype {
    let mut nt = empty_stock(
        NotetypeKind::Cloze,
        OriginalStockKind::Cloze,
        tr.notetypes_cloze_name(),
    );
    let text = tr.notetypes_text_field();
    let mut config = nt.add_field(text.as_ref());
    config.tag = Some(ClozeField::Text as u32);
    config.prevent_deletion = true;

    let back_extra = tr.notetypes_back_extra_field();
    config = nt.add_field(back_extra.as_ref());
    config.tag = Some(ClozeField::BackExtra as u32);
    let qfmt = format!("{{{{cloze:{}}}}}", text);
    let afmt = format!("{}<br>\n{{{{{}}}}}", qfmt, back_extra);
    nt.add_template(nt.name.clone(), qfmt, afmt);
    nt
}
