// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto::{NoteField as NoteFieldProto, NoteFieldConfig, OptionalUInt32};

#[derive(Debug, PartialEq)]
pub struct NoteField {
    pub ord: Option<u32>,
    pub name: String,
    pub config: NoteFieldConfig,
}

impl From<NoteField> for NoteFieldProto {
    fn from(f: NoteField) -> Self {
        NoteFieldProto {
            ord: f.ord.map(|n| OptionalUInt32 { val: n }),
            name: f.name,
            config: Some(f.config),
        }
    }
}

impl NoteField {
    pub fn new(name: impl Into<String>) -> Self {
        NoteField {
            ord: None,
            name: name.into(),
            config: NoteFieldConfig {
                sticky: false,
                rtl: false,
                font_name: "Arial".into(),
                font_size: 20,
                other: vec![],
            },
        }
    }

    pub(crate) fn fix_name(&mut self) {
        // remove special characters
        let bad_chars = |c| c == ':' || c == '{' || c == '}' || c == '"';
        if self.name.contains(bad_chars) {
            self.name = self.name.replace(bad_chars, "");
        }
        // and leading/trailing whitespace and special chars
        let bad_start_chars = |c: char| c == '#' || c == '/' || c == '^' || c.is_whitespace();
        let trimmed = self.name.trim().trim_start_matches(bad_start_chars);
        if trimmed.len() != self.name.len() {
            self.name = trimmed.into();
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn name() {
        let mut field = NoteField::new("  # /^ t:e{s\"t} field name #/^  ");
        field.fix_name();
        assert_eq!(&field.name, "test field name #/^");
    }
}
