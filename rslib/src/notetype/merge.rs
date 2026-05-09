// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardTemplate;
use crate::notetype::NoteField;
use crate::prelude::*;

impl Notetype {
    /// Inserts not yet existing fields ands templates from `other`.
    pub(crate) fn merge(&mut self, other: &Self) {
        self.merge_fields(other);
        if !self.is_cloze() {
            self.merge_templates(other);
        }
    }

    pub(crate) fn merge_all<'a>(&mut self, others: impl IntoIterator<Item = &'a Self>) {
        for other in others {
            self.merge(other);
        }
    }

    /// Inserts not yet existing fields from `other`.
    fn merge_fields(&mut self, other: &Self) {
        for (index, field) in other.fields.iter().enumerate() {
            match self.find_field(field) {
                Some(i) if i == index => (),
                Some(i) => self.fields.swap(i, index),
                None => {
                    let mut missing = field.clone();
                    missing.ord.take();
                    self.fields.insert(index, missing);
                }
            }
        }
    }

    fn find_field(&self, like: &NoteField) -> Option<usize> {
        self.fields
            .iter()
            .enumerate()
            .find_map(|(i, f)| f.is_match(like).then_some(i))
    }

    /// Inserts not yet existing templates from `other`.
    fn merge_templates(&mut self, other: &Self) {
        for (index, template) in other.templates.iter().enumerate() {
            match self.find_template(template) {
                Some(i) if i == index => (),
                Some(i) => self.templates.swap(i, index),
                None => {
                    let mut missing = template.clone();
                    missing.ord.take();
                    self.templates.insert(index, missing);
                }
            }
        }
    }

    fn find_template(&self, like: &CardTemplate) -> Option<usize> {
        self.templates
            .iter()
            .enumerate()
            .find_map(|(i, t)| t.is_match(like).then_some(i))
    }
}

impl NoteField {
    /// True if both ids are identical, but not [None], or at least one id is
    /// [None] and the names are identical.
    pub(crate) fn is_match(&self, other: &Self) -> bool {
        if let (Some(id), Some(other_id)) = (self.config.id, other.config.id) {
            id == other_id
        } else {
            self.name == other.name
        }
    }
}

impl CardTemplate {
    /// True if both ids are identical, but not [None], or at least one id is
    /// [None] and the names are identical.
    pub(crate) fn is_match(&self, other: &Self) -> bool {
        if let (Some(id), Some(other_id)) = (self.config.id, other.config.id) {
            id == other_id
        } else {
            self.name == other.name
        }
    }
}

#[cfg(test)]
mod test {
    use itertools::assert_equal;

    use super::*;
    use crate::notetype::stock;

    impl Notetype {
        fn field_ids(&self) -> impl Iterator<Item = Option<i64>> + '_ {
            self.fields.iter().map(|field| field.config.id)
        }

        fn template_ids(&self) -> impl Iterator<Item = Option<i64>> + '_ {
            self.templates.iter().map(|template| template.config.id)
        }
    }

    #[test]
    fn merge_new_fields() {
        let mut basic = stock::basic(&I18n::template_only());
        let mut other = basic.clone();
        other.add_field("with id");
        other.add_field("without id");
        other.fields[3].config.id.take();
        basic.merge(&other);
        assert_equal(basic.field_ids(), other.field_ids());
        assert_equal(basic.field_names(), other.field_names());
    }

    #[test]
    fn skip_merging_field_with_existing_id() {
        let mut basic = stock::basic(&I18n::template_only());
        let mut other = basic.clone();
        other.fields[1].name = String::from("renamed");
        basic.merge(&other);
        assert_equal(basic.field_ids(), other.field_ids());
        assert_equal(basic.field_names(), ["Front", "Back"].iter());
    }

    #[test]
    fn align_field_order() {
        let mut basic = stock::basic(&I18n::template_only());
        let mut other = basic.clone();
        other.fields.swap(0, 1);
        basic.merge(&other);
        assert_equal(basic.field_ids(), other.field_ids());
        assert_equal(basic.field_names(), other.field_names());
    }

    #[test]
    fn merge_new_templates() {
        let mut basic = stock::basic(&I18n::template_only());
        let mut other = basic.clone();
        other.add_template("with id", "", "");
        other.add_template("without id", "", "");
        other.templates[2].config.id.take();
        basic.merge(&other);
        assert_equal(basic.template_ids(), other.template_ids());
        assert_equal(basic.template_names(), other.template_names());
    }

    #[test]
    fn skip_merging_template_with_existing_id() {
        let mut basic = stock::basic(&I18n::template_only());
        let mut other = basic.clone();
        other.templates[0].name = String::from("renamed");
        basic.merge(&other);
        assert_equal(basic.template_ids(), other.template_ids());
        assert_equal(basic.template_names(), std::iter::once("Card 1"));
    }

    #[test]
    fn align_template_order() {
        let mut basic_rev = stock::basic_forward_reverse(&I18n::template_only());
        let mut other = basic_rev.clone();
        other.templates.swap(0, 1);
        basic_rev.merge(&other);
        assert_equal(basic_rev.template_ids(), other.template_ids());
        assert_equal(basic_rev.template_names(), other.template_names());
    }
}
