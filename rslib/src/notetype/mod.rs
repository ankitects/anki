// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cardgen;
mod fields;
mod schema11;
mod schemachange;
mod stock;
mod templates;

pub use crate::backend_proto::{
    card_requirement::Kind as CardRequirementKind, note_type_config::Kind as NoteTypeKind,
    CardRequirement, CardTemplateConfig, NoteFieldConfig, NoteType as NoteTypeProto,
    NoteTypeConfig,
};
pub(crate) use cardgen::{AlreadyGeneratedCardInfo, CardGenContext};
pub use fields::NoteField;
pub use schema11::{CardTemplateSchema11, NoteFieldSchema11, NoteTypeSchema11};
pub use stock::all_stock_notetypes;
pub use templates::CardTemplate;

use crate::{
    collection::Collection,
    decks::DeckID,
    define_newtype,
    err::{AnkiError, Result},
    notes::Note,
    template::{FieldRequirements, ParsedTemplate},
    text::ensure_string_in_nfc,
    timestamp::TimestampSecs,
    types::Usn,
};
use std::{
    collections::{HashMap, HashSet},
    sync::Arc,
};
use unicase::UniCase;

define_newtype!(NoteTypeID, i64);

pub(crate) const DEFAULT_CSS: &str = include_str!("styling.css");
pub(crate) const DEFAULT_LATEX_HEADER: &str = include_str!("header.tex");
pub(crate) const DEFAULT_LATEX_FOOTER: &str = r"\end{document}";

#[derive(Debug)]
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

    fn updated_requirements(
        &self,
        parsed: &[(Option<ParsedTemplate>, Option<ParsedTemplate>)],
    ) -> Vec<CardRequirement> {
        let field_map: HashMap<&str, u16> = self
            .fields
            .iter()
            .enumerate()
            .map(|(idx, field)| (field.name.as_str(), idx as u16))
            .collect();
        parsed
            .iter()
            .enumerate()
            .map(|(ord, (qtmpl, _atmpl))| {
                if let Some(tmpl) = qtmpl {
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
            .collect()
    }

    fn reposition_sort_idx(&mut self) {
        let adjusted_idx = self.fields.iter().enumerate().find_map(|(idx, f)| {
            if f.ord == Some(self.config.sort_field_idx) {
                Some(idx)
            } else {
                None
            }
        });
        self.config.sort_field_idx = adjusted_idx.unwrap_or(0) as u32;
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
        self.fields.push(NoteField::new(name));
    }

    pub(crate) fn add_template<S1, S2, S3>(&mut self, name: S1, qfmt: S2, afmt: S3)
    where
        S1: Into<String>,
        S2: Into<String>,
        S3: Into<String>,
    {
        self.templates.push(CardTemplate::new(name, qfmt, afmt));
    }

    pub(crate) fn prepare_for_adding(&mut self) -> Result<()> {
        // defaults to 0
        if self.config.target_deck_id == 0 {
            self.config.target_deck_id = 1;
        }
        self.prepare_for_update(None)
    }

    pub(crate) fn prepare_for_update(&mut self, existing: Option<&NoteType>) -> Result<()> {
        if self.fields.is_empty() {
            return Err(AnkiError::invalid_input("1 field required"));
        }
        if self.templates.is_empty() {
            return Err(AnkiError::invalid_input("1 template required"));
        }
        self.normalize_names();
        self.ensure_names_unique();
        self.reposition_sort_idx();

        let parsed_templates = self.parsed_templates();
        let invalid_card_idx = parsed_templates
            .iter()
            .enumerate()
            .find_map(|(idx, (q, a))| {
                if q.is_none() || a.is_none() {
                    Some(idx)
                } else {
                    None
                }
            });
        if let Some(idx) = invalid_card_idx {
            return Err(AnkiError::TemplateError {
                info: format!("invalid card {}", idx + 1),
            });
        }
        let reqs = self.updated_requirements(&parsed_templates);

        // handle renamed fields
        if let Some(existing) = existing {
            let renamed_fields = self.renamed_fields(existing);
            if !renamed_fields.is_empty() {
                let updated_templates =
                    self.updated_templates_for_renamed_fields(renamed_fields, parsed_templates);
                for (idx, (q, a)) in updated_templates.into_iter().enumerate() {
                    if let Some(q) = q {
                        self.templates[idx].config.q_format = q
                    }
                    if let Some(a) = a {
                        self.templates[idx].config.a_format = a
                    }
                }
            }
        }
        self.config.reqs = reqs;

        // fixme: deal with duplicate note type names on update
        Ok(())
    }

    fn renamed_fields(&self, current: &NoteType) -> HashMap<String, String> {
        self.fields
            .iter()
            .filter_map(|field| {
                if let Some(existing_ord) = field.ord {
                    if let Some(existing_field) = current.fields.get(existing_ord as usize) {
                        if existing_field.name != field.name {
                            return Some((existing_field.name.clone(), field.name.clone()));
                        }
                    }
                }
                None
            })
            .collect()
    }

    fn updated_templates_for_renamed_fields(
        &self,
        renamed_fields: HashMap<String, String>,
        parsed: Vec<(Option<ParsedTemplate>, Option<ParsedTemplate>)>,
    ) -> Vec<(Option<String>, Option<String>)> {
        parsed
            .into_iter()
            .map(|(q, a)| {
                let q = q.and_then(|mut q| {
                    if q.rename_fields(&renamed_fields) {
                        Some(q.template_to_string())
                    } else {
                        None
                    }
                });
                let a = a.and_then(|mut a| {
                    if a.rename_fields(&renamed_fields) {
                        Some(a.template_to_string())
                    } else {
                        None
                    }
                });

                (q, a)
            })
            .collect()
    }

    fn parsed_templates(&self) -> Vec<(Option<ParsedTemplate>, Option<ParsedTemplate>)> {
        self.templates
            .iter()
            .map(|t| (t.parsed_question(), t.parsed_answer()))
            .collect()
    }

    pub fn new_note(&self) -> Note {
        Note::new(&self)
    }

    pub fn target_deck_id(&self) -> DeckID {
        DeckID(self.config.target_deck_id)
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
            fields: nt.fields.into_iter().map(Into::into).collect(),
            templates: nt.templates.into_iter().map(Into::into).collect(),
        }
    }
}

impl Collection {
    /// Add a new notetype, and allocate it an ID.
    pub fn add_notetype(&mut self, nt: &mut NoteType) -> Result<()> {
        nt.prepare_for_adding()?;
        self.transact(None, |col| col.storage.add_new_notetype(nt))
    }

    /// Saves changes to a note type. This will force a full sync if templates
    /// or fields have been added/removed/reordered.
    pub fn update_notetype(&mut self, nt: &mut NoteType, preserve_usn: bool) -> Result<()> {
        let existing = self.get_notetype(nt.id)?;
        nt.prepare_for_update(existing.as_ref().map(AsRef::as_ref))?;
        if !preserve_usn {
            nt.mtime_secs = TimestampSecs::now();
            nt.usn = self.usn()?;
        }
        self.transact(None, |col| {
            if let Some(existing_notetype) = existing {
                col.update_notes_for_changed_fields(
                    nt,
                    existing_notetype.fields.len(),
                    existing_notetype.config.sort_field_idx,
                )?;
                col.update_cards_for_changed_templates(nt, existing_notetype.templates.len())?;
            }

            col.storage.update_notetype_config(&nt)?;
            col.storage.update_notetype_fields(nt.id, &nt.fields)?;
            col.storage
                .update_notetype_templates(nt.id, &nt.templates)?;

            // fixme: update cache instead of clearing
            col.state.notetype_cache.remove(&nt.id);

            Ok(())
        })
    }

    pub fn get_notetype_by_name(&mut self, name: &str) -> Result<Option<Arc<NoteType>>> {
        if let Some(ntid) = self.storage.get_notetype_id(name)? {
            self.get_notetype(ntid)
        } else {
            Ok(None)
        }
    }

    pub fn get_notetype(&mut self, ntid: NoteTypeID) -> Result<Option<Arc<NoteType>>> {
        if let Some(nt) = self.state.notetype_cache.get(&ntid) {
            return Ok(Some(nt.clone()));
        }
        if let Some(nt) = self.storage.get_notetype(ntid)? {
            let nt = Arc::new(nt);
            self.state.notetype_cache.insert(ntid, nt.clone());
            Ok(Some(nt))
        } else {
            Ok(None)
        }
    }

    pub fn get_all_notetypes(&mut self) -> Result<HashMap<NoteTypeID, Arc<NoteType>>> {
        self.storage
            .get_all_notetype_names()?
            .into_iter()
            .map(|(ntid, _)| {
                self.get_notetype(ntid)
                    .transpose()
                    .unwrap()
                    .map(|nt| (ntid, nt))
            })
            .collect()
    }
}
