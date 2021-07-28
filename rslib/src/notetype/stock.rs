// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::NotetypeKind;
use crate::{
    backend_proto::stock_notetype::Kind,
    config::{ConfigEntry, ConfigKey},
    error::Result,
    i18n::I18n,
    notetype::Notetype,
    storage::SqliteStorage,
    timestamp::TimestampSecs,
};

impl SqliteStorage {
    pub(crate) fn add_stock_notetypes(&self, tr: &I18n) -> Result<()> {
        for (idx, mut nt) in all_stock_notetypes(tr).into_iter().enumerate() {
            nt.prepare_for_update(None, true)?;
            self.add_notetype(&mut nt)?;
            if idx == Kind::Basic as usize {
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

// if changing this, make sure to update StockNotetype enum
pub fn all_stock_notetypes(tr: &I18n) -> Vec<Notetype> {
    vec![
        basic(tr),
        basic_forward_reverse(tr),
        basic_optional_reverse(tr),
        basic_typing(tr),
        cloze(tr),
    ]
}

/// returns {{name}}
fn fieldref<S: AsRef<str>>(name: S) -> String {
    format!("{{{{{}}}}}", name.as_ref())
}

pub(crate) fn basic(tr: &I18n) -> Notetype {
    let mut nt = Notetype {
        name: tr.notetypes_basic_name().into(),
        ..Default::default()
    };
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
    nt.name = tr.notetypes_basic_optional_reversed_name().into();
    let addrev = tr.notetypes_add_reverse_field();
    nt.add_field(addrev.as_ref());
    let tmpl = &mut nt.templates[1].config;
    tmpl.q_format = format!("{{{{#{}}}}}{}{{{{/{}}}}}", addrev, tmpl.q_format, addrev);
    nt
}

pub(crate) fn cloze(tr: &I18n) -> Notetype {
    let mut nt = Notetype {
        name: tr.notetypes_cloze_name().into(),
        ..Default::default()
    };
    let text = tr.notetypes_text_field();
    nt.add_field(text.as_ref());
    let back_extra = tr.notetypes_back_extra_field();
    nt.add_field(back_extra.as_ref());
    let qfmt = format!("{{{{cloze:{}}}}}", text);
    let afmt = format!("{}<br>\n{{{{{}}}}}", qfmt, back_extra);
    nt.add_template(nt.name.clone(), qfmt, afmt);
    nt.config.kind = NotetypeKind::Cloze as i32;
    nt.config.css += "
.cloze {
 font-weight: bold;
 color: blue;
}
.nightMode .cloze {
 color: lightblue;
}
";
    nt
}
