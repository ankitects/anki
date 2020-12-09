// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::NoteTypeKind;
use crate::{
    config::ConfigKey, err::Result, i18n::I18n, i18n::TR, notetype::NoteType,
    storage::SqliteStorage, timestamp::TimestampSecs,
};

pub use crate::backend_proto::StockNoteType;

impl SqliteStorage {
    pub(crate) fn add_stock_notetypes(&self, i18n: &I18n) -> Result<()> {
        for (idx, mut nt) in all_stock_notetypes(i18n).into_iter().enumerate() {
            self.add_new_notetype(&mut nt)?;
            if idx == StockNoteType::CodingTask as usize {
                self.set_config_value(
                    ConfigKey::CurrentNoteTypeID.into(),
                    &nt.id,
                    self.usn(false)?,
                    TimestampSecs::now(),
                )?;
            }
        }
        Ok(())
    }
}

// if changing this, make sure to update StockNoteType enum
pub fn all_stock_notetypes(i18n: &I18n) -> Vec<NoteType> {
    vec![
        basic(i18n),
        basic_forward_reverse(i18n),
        basic_optional_reverse(i18n),
        basic_typing(i18n),
        cloze(i18n),
        basic_coding_task(i18n),
    ]
}

/// returns {{name}}
fn fieldref<S: AsRef<str>>(name: S) -> String {
    format!("{{{{{}}}}}", name.as_ref())
}

pub(crate) fn basic(i18n: &I18n) -> NoteType {
    let mut nt = NoteType::default();
    nt.name = i18n.tr(TR::NotetypesBasicName).into();
    let front = i18n.tr(TR::NotetypesFrontField);
    let back = i18n.tr(TR::NotetypesBackField);
    nt.add_field(front.as_ref());
    nt.add_field(back.as_ref());
    nt.add_template(
        i18n.tr(TR::NotetypesCard1Name),
        fieldref(front),
        format!(
            "{}\n\n<hr id=answer>\n\n{}",
            fieldref("FrontSide"),
            fieldref(back),
        ),
    );
    nt.prepare_for_adding().unwrap();
    nt
}

pub(crate) fn basic_typing(i18n: &I18n) -> NoteType {
    let mut nt = basic(i18n);
    nt.name = i18n.tr(TR::NotetypesBasicTypeAnswerName).into();
    let front = i18n.tr(TR::NotetypesFrontField);
    let back = i18n.tr(TR::NotetypesBackField);
    let tmpl = &mut nt.templates[0].config;
    tmpl.q_format = format!("{}\n\n{{{{type:{}}}}}", fieldref(front.as_ref()), back);
    tmpl.a_format = format!(
        "{}\n\n<hr id=answer>\n\n{{{{type:{}}}}}",
        fieldref(front),
        back
    );
    nt.prepare_for_adding().unwrap();
    nt
}

pub(crate) fn basic_forward_reverse(i18n: &I18n) -> NoteType {
    let mut nt = basic(i18n);
    nt.name = i18n.tr(TR::NotetypesBasicReversedName).into();
    let front = i18n.tr(TR::NotetypesFrontField);
    let back = i18n.tr(TR::NotetypesBackField);
    nt.add_template(
        i18n.tr(TR::NotetypesCard2Name),
        fieldref(back),
        format!(
            "{}\n\n<hr id=answer>\n\n{}",
            fieldref("FrontSide"),
            fieldref(front),
        ),
    );
    nt.prepare_for_adding().unwrap();
    nt
}

pub(crate) fn basic_optional_reverse(i18n: &I18n) -> NoteType {
    let mut nt = basic_forward_reverse(i18n);
    nt.name = i18n.tr(TR::NotetypesBasicOptionalReversedName).into();
    let addrev = i18n.tr(TR::NotetypesAddReverseField);
    nt.add_field(addrev.as_ref());
    let tmpl = &mut nt.templates[1].config;
    tmpl.q_format = format!("{{{{#{}}}}}{}{{{{/{}}}}}", addrev, tmpl.q_format, addrev);
    nt.prepare_for_adding().unwrap();
    nt
}

pub(crate) fn basic_coding_task(i18n: &I18n) -> NoteType {
    let mut nt = NoteType::default();
    nt.name = "Coding Challenge".to_string();
    let title = "Title";
    let description = "Description";
    let function_name = "Function Name";
    let solution = "Solution";
    let test_cases = "Test Cases (.tsv)";
    nt.add_field(title);
    nt.add_field(description);
    nt.add_field(function_name);
    nt.add_field(solution);
    nt.add_field(test_cases);
    let q_format = format!(
        "{}\n\n{{{{code:{}}}}}",
        fieldref(title),
        solution
    );
    let a_format = format!(
        "{{{{{}}}}}\n\n<hr id=answer>\n\n{{{{code:{}}}}}",
        solution, solution
    );
    nt.add_template(i18n.tr(TR::NotetypesCard1Name), q_format, a_format);
    nt.prepare_for_adding().unwrap();
    nt
}

pub(crate) fn cloze(i18n: &I18n) -> NoteType {
    let mut nt = NoteType::default();
    nt.name = i18n.tr(TR::NotetypesClozeName).into();
    let text = i18n.tr(TR::NotetypesTextField);
    nt.add_field(text.as_ref());
    let back_extra = i18n.tr(TR::NotetypesBackExtraField);
    nt.add_field(back_extra.as_ref());
    let qfmt = format!("{{{{cloze:{}}}}}", text);
    let afmt = format!("{}<br>\n{{{{{}}}}}", qfmt, back_extra);
    nt.add_template(nt.name.clone(), qfmt, afmt);
    nt.config.kind = NoteTypeKind::Cloze as i32;
    nt.config.css += "
.cloze {
 font-weight: bold;
 color: blue;
}
.nightMode .cloze {
 color: lightblue;
}
";
    nt.prepare_for_adding().unwrap();
    nt
}
