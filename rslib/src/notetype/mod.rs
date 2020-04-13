// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod schema11;
mod stock;

pub use crate::backend_proto::{
    card_requirement::CardRequirementKind, CardRequirement, CardTemplate, CardTemplateConfig,
    NoteField, NoteFieldConfig, NoteType as NoteTypeProto, NoteTypeConfig, NoteTypeKind,
};
pub use schema11::{CardTemplateSchema11, NoteFieldSchema11, NoteTypeSchema11};
pub use stock::all_stock_notetypes;

use crate::{
    define_newtype,
    template::{without_legacy_template_directives, FieldRequirements, ParsedTemplate},
    text::ensure_string_in_nfc,
    timestamp::TimestampSecs,
    types::Usn,
};
use std::collections::{HashMap, HashSet};
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

pub struct NoteType {
    pub id: NoteTypeID,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub fields: Vec<NoteField>,
    pub templates: Vec<CardTemplate>,
    pub config: NoteTypeConfig,
}

impl Default for NoteType {
    fn default() -> Self {
        let mut conf = NoteTypeConfig::default();
        conf.css = DEFAULT_CSS.into();
        conf.latex_pre = DEFAULT_LATEX_HEADER.into();
        conf.latex_post = DEFAULT_LATEX_FOOTER.into();
        NoteType {
            id: NoteTypeID(0),
            name: "".into(),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            fields: vec![],
            templates: vec![],
            config: conf,
        }
    }
}

impl NoteType {
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

    fn update_requirements(&mut self) {
        let field_map: HashMap<&str, u16> = self
            .fields
            .iter()
            .enumerate()
            .map(|(idx, field)| (field.name.as_str(), idx as u16))
            .collect();
        let reqs: Vec<_> = self
            .templates
            .iter()
            .enumerate()
            .map(|(ord, tmpl)| {
                let conf = tmpl.config.as_ref().unwrap();
                let normalized = without_legacy_template_directives(&conf.q_format);
                if let Ok(tmpl) = ParsedTemplate::from_text(normalized.as_ref()) {
                    let mut req = match tmpl.requirements(&field_map) {
                        FieldRequirements::Any(ords) => CardRequirement {
                            card_ord: ord as u32,
                            kind: CardRequirementKind::Any as i32,
                            field_ords: ords.into_iter().map(|n| n as u32).collect(),
                        },
                        FieldRequirements::All(ords) => CardRequirement {
                            card_ord: ord as u32,
                            kind: CardRequirementKind::All as i32,
                            field_ords: ords.into_iter().map(|n| n as u32).collect(),
                        },
                        FieldRequirements::None => CardRequirement {
                            card_ord: ord as u32,
                            kind: CardRequirementKind::None as i32,
                            field_ords: vec![],
                        },
                    };
                    req.field_ords.sort_unstable();
                    req
                } else {
                    // template parsing failures make card unsatisfiable
                    CardRequirement {
                        card_ord: ord as u32,
                        kind: CardRequirementKind::None as i32,
                        field_ords: vec![],
                    }
                }
            })
            .collect();
        self.config.reqs = reqs;
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

    pub(crate) fn add_field<S: Into<String>>(&mut self, name: S) {
        let mut config = NoteFieldConfig::default();
        config.font_name = "Arial".to_string();
        config.font_size = 20;
        let mut field = NoteField::default();
        field.name = name.into();
        field.config = Some(config);
        self.fields.push(field);
    }

    pub(crate) fn add_template<S1, S2, S3>(&mut self, name: S1, qfmt: S2, afmt: S3)
    where
        S1: Into<String>,
        S2: Into<String>,
        S3: Into<String>,
    {
        let mut config = CardTemplateConfig::default();
        config.q_format = qfmt.into();
        config.a_format = afmt.into();

        let mut tmpl = CardTemplate::default();
        tmpl.name = name.into();
        tmpl.config = Some(config);

        self.templates.push(tmpl);
    }

    pub(crate) fn prepare_for_adding(&mut self) {
        self.normalize_names();
        self.ensure_names_unique();
        self.update_requirements();
    }
}

impl From<NoteType> for NoteTypeProto {
    fn from(nt: NoteType) -> Self {
        NoteTypeProto {
            id: nt.id.0,
            name: nt.name,
            mtime_secs: nt.mtime_secs.0 as u32,
            usn: nt.usn.0,
            config: Some(nt.config),
            fields: nt.fields,
            templates: nt.templates,
        }
    }
}
