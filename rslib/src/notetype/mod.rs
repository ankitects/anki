// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cardgen;
mod checks;
mod emptycards;
mod fields;
mod merge;
mod notetypechange;
mod render;
mod restore;
pub(crate) mod schema11;
mod schemachange;
mod service;
pub(crate) mod stock;
mod templates;
pub(crate) mod undo;

use std::collections::HashMap;
use std::collections::HashSet;
use std::iter::FromIterator;
use std::sync::Arc;
use std::sync::LazyLock;

pub use anki_proto::notetypes::notetype::config::card_requirement::Kind as CardRequirementKind;
pub use anki_proto::notetypes::notetype::config::CardRequirement;
pub use anki_proto::notetypes::notetype::config::Kind as NotetypeKind;
pub use anki_proto::notetypes::notetype::field::Config as NoteFieldConfig;
pub use anki_proto::notetypes::notetype::template::Config as CardTemplateConfig;
pub use anki_proto::notetypes::notetype::Config as NotetypeConfig;
pub use anki_proto::notetypes::notetype::Field as NoteFieldProto;
pub use anki_proto::notetypes::notetype::Template as CardTemplateProto;
pub use anki_proto::notetypes::Notetype as NotetypeProto;
pub(crate) use cardgen::AlreadyGeneratedCardInfo;
pub(crate) use cardgen::CardGenContext;
pub use fields::NoteField;
pub use notetypechange::ChangeNotetypeInput;
pub use notetypechange::NotetypeChangeInfo;
use regex::Regex;
pub(crate) use render::RenderCardOutput;
pub use schema11::CardTemplateSchema11;
pub use schema11::NoteFieldSchema11;
pub use schema11::NotetypeSchema11;
pub use stock::all_stock_notetypes;
pub use templates::CardTemplate;
use unicase::UniCase;

use crate::define_newtype;
use crate::error::CardTypeError;
use crate::error::CardTypeErrorDetails;
use crate::error::CardTypeSnafu;
use crate::error::MissingClozeSnafu;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::Node;
use crate::search::SearchNode;
use crate::storage::comma_separated_ids;
use crate::template::FieldRequirements;
use crate::template::ParsedTemplate;
use crate::text::ensure_string_in_nfc;
use crate::text::extract_underscored_css_imports;
use crate::text::extract_underscored_references;

define_newtype!(NotetypeId, i64);

pub(crate) const DEFAULT_CSS: &str = include_str!("styling.css");
pub(crate) const DEFAULT_CLOZE_CSS: &str = include_str!("cloze_styling.css");
pub(crate) const DEFAULT_LATEX_HEADER: &str = include_str!("header.tex");
pub(crate) const DEFAULT_LATEX_FOOTER: &str = r"\end{preview}\end{document}";
/// New entries must be handled in render.rs/add_special_fields().
static SPECIAL_FIELDS: LazyLock<HashSet<&'static str>> = LazyLock::new(|| {
    HashSet::from_iter(vec![
        "FrontSide",
        "Card",
        "CardFlag",
        "Deck",
        "Subdeck",
        "Tags",
        "Type",
    ])
});

#[derive(Debug, PartialEq, Clone)]
pub struct Notetype {
    pub id: NotetypeId,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub fields: Vec<NoteField>,
    pub templates: Vec<CardTemplate>,
    pub config: NotetypeConfig,
}

impl Default for Notetype {
    fn default() -> Self {
        Notetype {
            id: NotetypeId(0),
            name: "".into(),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            fields: vec![],
            templates: vec![],
            config: Notetype::new_config(),
        }
    }
}

impl Notetype {
    pub(crate) fn new_config() -> NotetypeConfig {
        NotetypeConfig {
            css: DEFAULT_CSS.into(),
            latex_pre: DEFAULT_LATEX_HEADER.into(),
            latex_post: DEFAULT_LATEX_FOOTER.into(),
            ..Default::default()
        }
    }

    pub(crate) fn new_cloze_config() -> NotetypeConfig {
        let mut config = Self::new_config();
        config.css += DEFAULT_CLOZE_CSS;
        config.kind = NotetypeKind::Cloze as i32;
        config
    }
}

impl Notetype {
    pub fn new_note(&self) -> Note {
        Note::new(self)
    }

    /// Return the template for the given card ordinal. Cloze notetypes
    /// always return the first and only template.
    pub fn get_template(&self, card_ord: u16) -> Result<&CardTemplate> {
        let template = if self.config.kind() == NotetypeKind::Cloze {
            self.templates.first()
        } else {
            self.templates.get(card_ord as usize)
        };

        template.or_not_found(card_ord)
    }
}

impl Collection {
    /// Add a new notetype, and allocate it an ID.
    pub fn add_notetype(
        &mut self,
        notetype: &mut Notetype,
        skip_checks: bool,
    ) -> Result<OpOutput<()>> {
        self.transact(Op::AddNotetype, |col| {
            let usn = col.usn()?;
            notetype.set_modified(usn);
            col.add_notetype_inner(notetype, usn, skip_checks)
        })
    }

    /// Saves changes to a note type. This will force a full sync if templates
    /// or fields have been added/removed/reordered.
    ///
    /// This does not assign ordinals to the provided notetype, so if you wish
    /// to make use of template_idx, the notetype must be fetched again.
    pub fn update_notetype(
        &mut self,
        notetype: &mut Notetype,
        skip_checks: bool,
    ) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateNotetype, |col| {
            let original = col
                .storage
                .get_notetype(notetype.id)?
                .or_not_found(notetype.id)?;
            let usn = col.usn()?;
            notetype.set_modified(usn);
            col.add_or_update_notetype_with_existing_id_inner(
                notetype,
                Some(original),
                usn,
                skip_checks,
            )
        })
    }

    /// Used to support the current importing code; does not mark notetype as
    /// modified, and does not support undo.
    pub fn add_or_update_notetype_with_existing_id(
        &mut self,
        notetype: &mut Notetype,
        skip_checks: bool,
    ) -> Result<()> {
        self.transact_no_undo(|col| {
            let usn = col.usn()?;
            let existing = col.storage.get_notetype(notetype.id)?;
            col.add_or_update_notetype_with_existing_id_inner(notetype, existing, usn, skip_checks)
        })
    }

    pub fn get_notetype_by_name(&mut self, name: &str) -> Result<Option<Arc<Notetype>>> {
        if let Some(ntid) = self.storage.get_notetype_id(name)? {
            self.get_notetype(ntid)
        } else {
            Ok(None)
        }
    }

    pub fn get_notetype(&mut self, ntid: NotetypeId) -> Result<Option<Arc<Notetype>>> {
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

    pub fn get_all_notetypes(&mut self) -> Result<Vec<Arc<Notetype>>> {
        self.storage
            .get_all_notetype_ids()?
            .into_iter()
            .filter_map(|ntid| self.get_notetype(ntid).transpose())
            .collect()
    }

    pub fn get_all_notetypes_of_search_notes(
        &mut self,
    ) -> Result<HashMap<NotetypeId, Arc<Notetype>>> {
        self.storage
            .all_notetypes_of_search_notes()?
            .into_iter()
            .map(|ntid| {
                self.get_notetype(ntid)
                    .transpose()
                    .unwrap()
                    .map(|nt| (ntid, nt))
            })
            .collect()
    }

    pub fn remove_notetype(&mut self, ntid: NotetypeId) -> Result<OpOutput<()>> {
        self.transact(Op::RemoveNotetype, |col| col.remove_notetype_inner(ntid))
    }

    /// Return the notetype used by `note_ids`, or an error if not exactly 1
    /// notetype is in use.
    pub fn get_single_notetype_of_notes(&mut self, note_ids: &[NoteId]) -> Result<NotetypeId> {
        require!(!note_ids.is_empty(), "no note id provided");

        let nids_node: Node = SearchNode::NoteIds(comma_separated_ids(note_ids)).into();
        let note1 = self
            .storage
            .get_note(*note_ids.first().unwrap())?
            .or_not_found(note_ids[0])?;

        if self
            .search_notes_unordered(note1.notetype_id.and(nids_node))?
            .len()
            != note_ids.len()
        {
            Err(AnkiError::MultipleNotetypesSelected)
        } else {
            Ok(note1.notetype_id)
        }
    }
}

impl Notetype {
    pub(crate) fn ensure_names_unique(&mut self) {
        let mut names = HashSet::new();
        for t in &mut self.templates {
            loop {
                let name = UniCase::new(t.name.clone());
                if !names.contains(&name) {
                    names.insert(name);
                    break;
                }
                t.name.push('+');
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
                t.name.push('+');
            }
        }
    }

    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime_secs = TimestampSecs::now();
        self.usn = usn;
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

    /// Adjust sort index to match repositioned fields.
    fn reposition_sort_idx(&mut self) {
        self.config.sort_field_idx = self
            .fields
            .iter()
            .enumerate()
            .find_map(|(idx, f)| {
                if f.ord == Some(self.config.sort_field_idx) {
                    Some(idx as u32)
                } else {
                    None
                }
            })
            .unwrap_or_else(|| {
                // provided ordinal not on any existing field; cap to bounds
                self.config
                    .sort_field_idx
                    .clamp(0, (self.fields.len() - 1) as u32)
            });
    }

    fn ensure_template_fronts_unique(&self) -> Result<(), CardTypeError> {
        static CARD_TAG: LazyLock<Regex> =
            LazyLock::new(|| Regex::new(r"\{\{\s*Card\s*\}\}").unwrap());

        let mut map = HashMap::new();
        for (index, card) in self.templates.iter().enumerate() {
            if let Some(old_index) = map.insert(&card.config.q_format, index) {
                if !CARD_TAG.is_match(&card.config.q_format) {
                    return Err(CardTypeError {
                        notetype: self.name.clone(),
                        ordinal: index,
                        source: CardTypeErrorDetails::Duplicate { index: old_index },
                    });
                }
            }
        }

        Ok(())
    }

    /// Ensure no templates are None, every front template contains at least one
    /// field, and all used field names belong to a field of this notetype.
    fn ensure_valid_parsed_templates(
        &self,
        templates: &[(Option<ParsedTemplate>, Option<ParsedTemplate>)],
    ) -> Result<(), CardTypeError> {
        for (ordinal, sides) in templates.iter().enumerate() {
            self.ensure_valid_parsed_card_templates(sides)
                .context(CardTypeSnafu {
                    notetype: &self.name,
                    ordinal,
                })?;
        }
        Ok(())
    }

    fn ensure_valid_parsed_card_templates(
        &self,
        sides: &(Option<ParsedTemplate>, Option<ParsedTemplate>),
    ) -> Result<(), CardTypeErrorDetails> {
        if let (Some(q), Some(a)) = sides {
            let q_fields = q.all_referenced_field_names();
            if q_fields.is_empty() {
                return Err(CardTypeErrorDetails::NoFrontField);
            }
            if let Some(unknown_field) =
                self.first_unknown_field_name(q_fields.union(&a.all_referenced_field_names()))
            {
                return Err(CardTypeErrorDetails::NoSuchField {
                    field: unknown_field.to_string(),
                });
            }
            Ok(())
        } else {
            Err(CardTypeErrorDetails::TemplateParseError)
        }
    }

    /// Return the first non-empty name in names that does not denote a special
    /// field or a field of this notetype.
    fn first_unknown_field_name<T, I>(&self, names: T) -> Option<I>
    where
        T: IntoIterator<Item = I>,
        I: AsRef<str>,
    {
        names.into_iter().find(|name| {
            // The empty field name is allowed as it may be used by add-ons.
            !name.as_ref().is_empty()
                && !SPECIAL_FIELDS.contains(&name.as_ref())
                && self.fields.iter().all(|field| field.name != name.as_ref())
        })
    }

    fn ensure_cloze_if_cloze_notetype(
        &self,
        parsed_templates: &[(Option<ParsedTemplate>, Option<ParsedTemplate>)],
    ) -> Result<(), CardTypeError> {
        if self.is_cloze() && missing_cloze_filter(parsed_templates) {
            MissingClozeSnafu.fail().context(CardTypeSnafu {
                notetype: &self.name,
                ordinal: 0usize,
            })
        } else {
            Ok(())
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

    pub(crate) fn add_field<S: Into<String>>(&mut self, name: S) -> &mut NoteFieldConfig {
        self.fields.push(NoteField::new(name));
        self.fields.last_mut().map(|f| &mut f.config).unwrap()
    }

    pub(crate) fn add_template<S1, S2, S3>(&mut self, name: S1, qfmt: S2, afmt: S3)
    where
        S1: Into<String>,
        S2: Into<String>,
        S3: Into<String>,
    {
        self.templates.push(CardTemplate::new(name, qfmt, afmt));
    }

    pub(crate) fn prepare_for_update(
        &mut self,
        existing: Option<&Notetype>,
        skip_checks: bool,
    ) -> Result<()> {
        require!(!self.fields.is_empty(), "1 field required");
        require!(!self.templates.is_empty(), "1 template required");
        let bad_chars = |c| c == '"';
        if self.name.contains(bad_chars) {
            self.name = self.name.replace(bad_chars, "");
        }
        require!(!self.name.is_empty(), "Empty notetype name");
        self.normalize_names();
        self.fix_field_names()?;
        self.fix_template_names()?;
        self.ensure_names_unique();
        self.reposition_sort_idx();

        let mut parsed_templates = self.parsed_templates();
        let mut parsed_browser_templates = self.parsed_browser_templates();
        let reqs = self.updated_requirements(&parsed_templates);

        // handle renamed+deleted fields
        if let Some(existing) = existing {
            let fields = self.renamed_and_removed_fields(existing);
            if !fields.is_empty() {
                self.update_templates_for_renamed_and_removed_fields(
                    fields,
                    &mut parsed_templates,
                    &mut parsed_browser_templates,
                );
            }
        }
        self.config.reqs = reqs;
        if !skip_checks {
            self.check_templates(parsed_templates)?;
        }

        Ok(())
    }

    fn check_templates(
        &self,
        parsed_templates: Vec<(Option<ParsedTemplate>, Option<ParsedTemplate>)>,
    ) -> Result<()> {
        self.ensure_template_fronts_unique()
            .and(self.ensure_valid_parsed_templates(&parsed_templates))
            .and(self.ensure_cloze_if_cloze_notetype(&parsed_templates))?;
        Ok(())
    }

    fn renamed_and_removed_fields(&self, current: &Notetype) -> HashMap<String, Option<String>> {
        let mut remaining_ords = HashSet::new();
        // gather renames
        let mut map: HashMap<String, Option<String>> = self
            .fields
            .iter()
            .filter_map(|field| {
                if let Some(existing_ord) = field.ord {
                    remaining_ords.insert(existing_ord);
                    if let Some(existing_field) = current.fields.get(existing_ord as usize) {
                        if existing_field.name != field.name {
                            return Some((existing_field.name.clone(), Some(field.name.clone())));
                        }
                    }
                }
                None
            })
            .collect();
        // and add any fields that have been removed
        for (idx, field) in current.fields.iter().enumerate() {
            if !remaining_ords.contains(&(idx as u32)) {
                map.insert(field.name.clone(), None);
            }
        }

        map
    }

    /// Update templates to reflect field deletions and renames.
    /// Any templates that failed to parse will be ignored.
    fn update_templates_for_renamed_and_removed_fields(
        &mut self,
        fields: HashMap<String, Option<String>>,
        parsed: &mut [(Option<ParsedTemplate>, Option<ParsedTemplate>)],
        parsed_browser: &mut [(Option<ParsedTemplate>, Option<ParsedTemplate>)],
    ) {
        let first_remaining_field_name = &self.fields.first().unwrap().name;
        let is_cloze = self.is_cloze();

        let q_update_fields = |q_opt: &mut Option<ParsedTemplate>, template_target: &mut String| {
            if let Some(q) = q_opt {
                q.rename_and_remove_fields(&fields);
                if !q.contains_field_replacement() || is_cloze && !q.contains_cloze_replacement() {
                    q.add_missing_field_replacement(first_remaining_field_name, is_cloze);
                }
                *template_target = q.template_to_string();
            }
        };

        let a_update_fields = |a_opt: &mut Option<ParsedTemplate>, template_target: &mut String| {
            if let Some(a) = a_opt {
                a.rename_and_remove_fields(&fields);
                if is_cloze && !a.contains_cloze_replacement() {
                    a.add_missing_field_replacement(first_remaining_field_name, is_cloze);
                }
                *template_target = a.template_to_string();
            }
        };

        // Update main templates
        for (idx, (q_opt, a_opt)) in parsed.iter_mut().enumerate() {
            q_update_fields(q_opt, &mut self.templates[idx].config.q_format);

            a_update_fields(a_opt, &mut self.templates[idx].config.a_format);
        }

        // Update browser templates, if they exist
        for (idx, (q_browser_opt, a_browser_opt)) in parsed_browser.iter_mut().enumerate() {
            q_update_fields(
                q_browser_opt,
                &mut self.templates[idx].config.q_format_browser,
            );

            a_update_fields(
                a_browser_opt,
                &mut self.templates[idx].config.a_format_browser,
            );
        }
    }

    fn parsed_templates(&self) -> Vec<(Option<ParsedTemplate>, Option<ParsedTemplate>)> {
        self.templates
            .iter()
            .map(|t| (t.parsed_question(), t.parsed_answer()))
            .collect()
    }
    fn parsed_browser_templates(&self) -> Vec<(Option<ParsedTemplate>, Option<ParsedTemplate>)> {
        self.templates
            .iter()
            .map(|t| {
                (
                    t.parsed_question_format_for_browser(),
                    t.parsed_answer_format_for_browser(),
                )
            })
            .collect()
    }

    fn fix_field_names(&mut self) -> Result<()> {
        self.fields.iter_mut().try_for_each(NoteField::fix_name)
    }

    fn fix_template_names(&mut self) -> Result<()> {
        self.templates
            .iter_mut()
            .try_for_each(CardTemplate::fix_name)
    }

    /// Find the field index of the provided field name.
    pub(crate) fn get_field_ord(&self, field_name: &str) -> Option<usize> {
        let field_name = UniCase::new(field_name);
        self.fields
            .iter()
            .enumerate()
            .filter_map(|(idx, f)| {
                if UniCase::new(&f.name) == field_name {
                    Some(idx)
                } else {
                    None
                }
            })
            .next()
    }

    pub(crate) fn is_cloze(&self) -> bool {
        matches!(self.config.kind(), NotetypeKind::Cloze)
    }

    /// Return all clozable fields. A field is clozable when it belongs to a
    /// cloze notetype and a 'cloze' filter is applied to it in the
    /// template.
    pub(crate) fn cloze_fields(&self) -> HashSet<usize> {
        if !self.is_cloze() {
            HashSet::new()
        } else if let Some((Some(front), _)) = self.parsed_templates().first() {
            front
                .all_referenced_cloze_field_names()
                .iter()
                .filter_map(|name| self.get_field_ord(name))
                .collect()
        } else {
            HashSet::new()
        }
    }

    pub(crate) fn gather_media_names(&self, inserter: &mut impl FnMut(String)) {
        for name in extract_underscored_css_imports(&self.config.css) {
            inserter(name.to_string());
        }
        for template in &self.templates {
            for template_side in [&template.config.q_format, &template.config.a_format] {
                for name in extract_underscored_references(template_side) {
                    inserter(name.to_string());
                }
            }
        }
    }
}

/// True if the slice is empty or either template of the first tuple doesn't
/// have a cloze field.
fn missing_cloze_filter(
    parsed_templates: &[(Option<ParsedTemplate>, Option<ParsedTemplate>)],
) -> bool {
    parsed_templates
        .first()
        .map_or(true, |t| !has_cloze(&t.0) || !has_cloze(&t.1))
}

/// True if the template is non-empty and has a cloze field.
fn has_cloze(template: &Option<ParsedTemplate>) -> bool {
    template
        .as_ref()
        .is_some_and(|t| !t.all_referenced_cloze_field_names().is_empty())
}

impl From<Notetype> for NotetypeProto {
    fn from(nt: Notetype) -> Self {
        NotetypeProto {
            id: nt.id.0,
            name: nt.name,
            mtime_secs: nt.mtime_secs.0,
            usn: nt.usn.0,
            config: Some(nt.config),
            fields: nt.fields.into_iter().map(Into::into).collect(),
            templates: nt.templates.into_iter().map(Into::into).collect(),
        }
    }
}

impl Collection {
    pub(crate) fn ensure_notetype_name_unique(
        &self,
        notetype: &mut Notetype,
        usn: Usn,
    ) -> Result<()> {
        loop {
            match self.storage.get_notetype_id(&notetype.name)? {
                Some(id) if id == notetype.id => {
                    break;
                }
                None => break,
                _ => (),
            }
            notetype.name += "+";
            notetype.set_modified(usn);
        }

        Ok(())
    }

    /// Caller must set notetype as modified if appropriate.
    pub(crate) fn add_notetype_inner(
        &mut self,
        notetype: &mut Notetype,
        usn: Usn,
        skip_checks: bool,
    ) -> Result<()> {
        notetype.prepare_for_update(None, skip_checks)?;
        self.ensure_notetype_name_unique(notetype, usn)?;
        self.add_notetype_undoable(notetype)?;
        self.set_current_notetype_id(notetype.id)
    }

    /// - Caller must set notetype as modified if appropriate.
    /// - This only supports undo when an existing notetype is passed in.
    pub(crate) fn add_or_update_notetype_with_existing_id_inner(
        &mut self,
        notetype: &mut Notetype,
        original: Option<Notetype>,
        usn: Usn,
        skip_checks: bool,
    ) -> Result<()> {
        let normalize = self.get_config_bool(BoolKey::NormalizeNoteText);
        notetype.prepare_for_update(original.as_ref(), skip_checks)?;
        self.ensure_notetype_name_unique(notetype, usn)?;

        if let Some(original) = original {
            self.update_notes_for_changed_fields(
                notetype,
                original.fields.len(),
                original.config.sort_field_idx,
                normalize,
            )?;
            self.update_cards_for_changed_templates(notetype, &original.templates)?;
            self.update_notetype_undoable(notetype, original)?;
        } else {
            // adding with existing id for old undo code, bypass undo
            self.state.notetype_cache.remove(&notetype.id);
            self.storage
                .add_or_update_notetype_with_existing_id(notetype)?;
        }

        Ok(())
    }

    pub(crate) fn remove_notetype_inner(&mut self, ntid: NotetypeId) -> Result<()> {
        let notetype = if let Some(notetype) = self.storage.get_notetype(ntid)? {
            notetype
        } else {
            // already removed
            return Ok(());
        };

        // remove associated cards/notes
        let usn = self.usn()?;
        let note_ids = self.search_notes_unordered(ntid)?;
        self.remove_notes_inner(&note_ids, usn)?;

        // remove notetype
        self.set_schema_modified()?;
        self.state.notetype_cache.remove(&ntid);
        self.clear_aux_config_for_notetype(ntid)?;
        self.remove_notetype_only_undoable(notetype)?;

        // update last-used notetype
        let all = self.storage.get_all_notetype_names()?;
        if all.is_empty() {
            let mut nt = all_stock_notetypes(&self.tr).remove(0);
            self.add_notetype_inner(&mut nt, self.usn()?, true)?;
            self.set_current_notetype_id(nt.id)
        } else {
            self.set_current_notetype_id(all[0].0)
        }
    }
}

// Tests
//---------------------------------------

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn update_templates_after_removing_crucial_fields() {
        // Normal Test (all front fields removed)
        let mut nt_norm = Notetype::default();
        nt_norm.add_field("baz"); // Fields "foo" and "bar" were removed
        nt_norm.fields[0].ord = Some(2);

        nt_norm.add_template("Card 1", "front {{foo}}", "back {{bar}}");
        nt_norm.templates[0].ord = Some(0);
        let mut parsed = nt_norm.parsed_templates();
        let mut parsed_browser = nt_norm.parsed_browser_templates();

        let mut field_map: HashMap<String, Option<String>> = HashMap::new();
        field_map.insert("foo".to_owned(), None);
        field_map.insert("bar".to_owned(), None);

        nt_norm.update_templates_for_renamed_and_removed_fields(
            field_map,
            &mut parsed,
            &mut parsed_browser,
        );
        assert_eq!(nt_norm.templates[0].config.q_format, "front {{baz}}");
        assert_eq!(nt_norm.templates[0].config.a_format, "back ");

        // Cloze Test 1/2 (front and back cloze fields removed)
        let mut nt_cloze = Notetype {
            config: Notetype::new_cloze_config(),
            ..Default::default()
        };
        nt_cloze.add_field("baz"); // Fields "foo" and "bar" were removed
        nt_cloze.fields[0].ord = Some(2);

        nt_cloze.add_template("Card 1", "front {{cloze:foo}}", "back {{cloze:bar}}");
        nt_cloze.templates[0].ord = Some(0);
        let mut parsed = nt_cloze.parsed_templates();

        let mut field_map: HashMap<String, Option<String>> = HashMap::new();
        field_map.insert("foo".to_owned(), None);
        field_map.insert("bar".to_owned(), None);

        nt_cloze.update_templates_for_renamed_and_removed_fields(
            field_map,
            &mut parsed,
            &mut parsed_browser,
        );
        assert_eq!(nt_cloze.templates[0].config.q_format, "front {{cloze:baz}}");
        assert_eq!(nt_cloze.templates[0].config.a_format, "back {{cloze:baz}}");

        // Cloze Test 2/2 (only back cloze field is removed)
        let mut nt_cloze = Notetype {
            config: Notetype::new_cloze_config(),
            ..Default::default()
        };
        nt_cloze.add_field("foo");
        nt_cloze.fields[0].ord = Some(0);
        nt_cloze.add_field("baz");
        nt_cloze.fields[1].ord = Some(2);
        // ^ only field "bar" was removed

        nt_cloze.add_template("Card 1", "front {{cloze:foo}}", "back {{cloze:bar}}");
        nt_cloze.templates[0].ord = Some(0);
        let mut parsed = nt_cloze.parsed_templates();

        let mut field_map: HashMap<String, Option<String>> = HashMap::new();
        field_map.insert("bar".to_owned(), None);

        nt_cloze.update_templates_for_renamed_and_removed_fields(
            field_map,
            &mut parsed,
            &mut parsed_browser,
        );
        assert_eq!(nt_cloze.templates[0].config.q_format, "front {{cloze:foo}}");
        assert_eq!(nt_cloze.templates[0].config.a_format, "back {{cloze:foo}}");
    }
}
