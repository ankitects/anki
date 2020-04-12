// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod schema11;

pub use crate::backend_proto::{
    CardRequirement, CardTemplate, CardTemplateConfig, NoteField, NoteFieldConfig, NoteType,
    NoteTypeConfig, NoteTypeKind,
};
pub use schema11::{CardTemplateSchema11, NoteFieldSchema11, NoteTypeSchema11};

use crate::{define_newtype, text::ensure_string_in_nfc};
use std::collections::HashSet;
use unicase::UniCase;

define_newtype!(NoteTypeID, i64);

pub(crate) const DEFAULT_CSS: &str = "\
.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
";

pub(crate) const DEFAULT_LATEX_HEADER: &str = r#"\documentclass[12pt]{article}
\special{papersize=3in,5in}
\usepackage[utf8]{inputenc}
\usepackage{amssymb,amsmath}
\pagestyle{empty}
\setlength{\parindent}{0in}
\begin{document}
"#;

pub(crate) const DEFAULT_LATEX_FOOTER: &str = r#"\end{document}"#;

// other: vec![], // fixme: ensure empty map converted to empty bytes
// fixme: rollback savepoint when tags not changed

impl NoteType {
    pub fn new() -> Self {
        let mut nt = Self::default();
        let mut conf = NoteTypeConfig::default();
        conf.css = DEFAULT_CSS.into();
        conf.latex_pre = DEFAULT_LATEX_HEADER.into();
        conf.latex_post = DEFAULT_LATEX_FOOTER.into();
        nt.config = Some(conf);
        nt
    }

    pub fn id(&self) -> NoteTypeID {
        NoteTypeID(self.id)
    }

    pub fn sort_field_idx(&self) -> usize {
        self.config.as_ref().unwrap().sort_field_idx as usize
    }

    pub fn latex_uses_svg(&self) -> bool {
        self.config.as_ref().unwrap().latex_svg
    }

    pub(crate) fn ensure_names_unique(&mut self) {
        let mut names = HashSet::new();
        for t in &mut self.templates {
            loop {
                let name = UniCase::new(t.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                t.name.push('_');
            }
        }
        names.clear();
        for t in &mut self.fields {
            loop {
                let name = UniCase::new(t.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                t.name.push('_');
            }
        }
    }

    pub(crate) fn normalize_names(&mut self) {
        ensure_string_in_nfc(&mut self.name);
        for f in &mut self.fields {
            ensure_string_in_nfc(&mut f.name);
        }
        for t in &mut self.templates {
            ensure_string_in_nfc(&mut t.name);
        }
    }
}
