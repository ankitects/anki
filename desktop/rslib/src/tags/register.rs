// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashSet;

use unicase::UniCase;

use super::immediate_parent_name_str;
use super::is_tag_separator;
use super::split_tags;
use super::Tag;
use crate::prelude::*;
use crate::text::normalize_to_nfc;
use crate::types::Usn;

impl Collection {
    /// Given a list of tags, fix case, ordering and duplicates.
    /// Returns true if any new tags were added.
    /// Each tag is split on spaces, so if you have a &str, you
    /// can pass that in as a one-element vec.
    pub(crate) fn canonify_tags(
        &mut self,
        tags: Vec<String>,
        usn: Usn,
    ) -> Result<(Vec<String>, bool)> {
        self.canonify_tags_inner(tags, usn, true)
    }

    pub(crate) fn canonify_tags_without_registering(
        &mut self,
        tags: Vec<String>,
        usn: Usn,
    ) -> Result<Vec<String>> {
        self.canonify_tags_inner(tags, usn, false)
            .map(|(tags, _)| tags)
    }

    /// Like [canonify_tags()], but doesn't save new tags. As a consequence, new
    /// parents are not canonified.
    fn canonify_tags_inner(
        &mut self,
        tags: Vec<String>,
        usn: Usn,
        register: bool,
    ) -> Result<(Vec<String>, bool)> {
        let mut seen = HashSet::new();
        let mut added = false;

        let tags: Vec<_> = tags.iter().flat_map(|t| split_tags(t)).collect();
        for tag in tags {
            let mut tag = Tag::new(tag.to_string(), usn);
            if register {
                added |= self.register_tag(&mut tag)?;
            } else {
                self.prepare_tag_for_registering(&mut tag)?;
            }
            seen.insert(UniCase::new(tag.name));
        }

        // exit early if no non-empty tags
        if seen.is_empty() {
            return Ok((vec![], added));
        }

        // return the sorted, canonified tags
        let mut tags = seen.into_iter().collect::<Vec<_>>();
        tags.sort_unstable();
        let tags: Vec<_> = tags.into_iter().map(|s| s.into_inner()).collect();

        Ok((tags, added))
    }

    /// Returns true if any cards were added to the tag list.
    pub(crate) fn canonified_tags_as_vec(
        &mut self,
        tags: &str,
        usn: Usn,
    ) -> Result<Vec<UniCase<String>>> {
        let mut out_tags = vec![];

        for tag in split_tags(tags) {
            let mut tag = Tag::new(tag.to_string(), usn);
            self.register_tag(&mut tag)?;
            out_tags.push(UniCase::new(tag.name));
        }

        Ok(out_tags)
    }

    /// Adjust tag casing to match any existing parents, and register it if it's
    /// not already in the tags list. True if the tag was added and not
    /// already in tag list. In the case the tag is already registered, tag
    /// will be mutated to match the existing name.
    pub(crate) fn register_tag(&mut self, tag: &mut Tag) -> Result<bool> {
        let is_new = self.prepare_tag_for_registering(tag)?;
        if is_new {
            self.register_tag_undoable(tag)?;
        }
        Ok(is_new)
    }

    /// Create a tag object, normalize text, and match parents/existing case if
    /// available. True if tag is new.
    pub(super) fn prepare_tag_for_registering(&self, tag: &mut Tag) -> Result<bool> {
        let normalized_name = normalize_tag_name(&tag.name)?;
        if let Some(existing_tag) = self.storage.get_tag(&normalized_name)? {
            tag.name = existing_tag.name;
            Ok(false)
        } else {
            if let Some(new_name) = self.adjusted_case_for_parents(&normalized_name)? {
                tag.name = new_name;
            } else if let Cow::Owned(new_name) = normalized_name {
                tag.name = new_name;
            }
            Ok(true)
        }
    }

    pub(super) fn register_tag_string(&mut self, tag: String, usn: Usn) -> Result<bool> {
        let mut tag = Tag::new(tag, usn);
        self.register_tag(&mut tag)
    }
}

impl Collection {
    /// If parent tag(s) exist and differ in case, return a rewritten tag.
    pub(super) fn adjusted_case_for_parents(&self, tag: &str) -> Result<Option<String>> {
        if let Some(parent_tag) = self.first_existing_parent_tag(tag)? {
            let child_split: Vec<_> = tag.split("::").collect();
            let parent_count = parent_tag.matches("::").count() + 1;
            Ok(Some(format!(
                "{}::{}",
                parent_tag,
                &child_split[parent_count..].join("::")
            )))
        } else {
            Ok(None)
        }
    }

    fn first_existing_parent_tag(&self, mut tag: &str) -> Result<Option<String>> {
        while let Some(parent_name) = immediate_parent_name_str(tag) {
            if let Some(parent_tag) = self.storage.preferred_tag_case(parent_name)? {
                return Ok(Some(parent_tag));
            }
            tag = parent_name;
        }

        Ok(None)
    }
}

fn invalid_char_for_tag(c: char) -> bool {
    c.is_ascii_control() || is_tag_separator(c)
}

fn normalized_tag_name_component(comp: &str) -> Cow<'_, str> {
    let mut out = normalize_to_nfc(comp);
    if out.contains(invalid_char_for_tag) {
        out = out.replace(invalid_char_for_tag, "").into();
    }
    let trimmed = out.trim();
    if trimmed.is_empty() {
        "blank".to_string().into()
    } else if trimmed.len() != out.len() {
        trimmed.to_string().into()
    } else {
        out
    }
}

pub(super) fn normalize_tag_name(name: &str) -> Result<Cow<'_, str>> {
    let normalized_name: Cow<str> = if name
        .split("::")
        .any(|comp| matches!(normalized_tag_name_component(comp), Cow::Owned(_)))
    {
        let comps: Vec<_> = name
            .split("::")
            .map(normalized_tag_name_component)
            .collect::<Vec<_>>();
        comps.join("::").into()
    } else {
        // no changes required
        name.into()
    };
    if normalized_name.is_empty() {
        // this should not be possible
        invalid_input!("blank tag");
    } else {
        Ok(normalized_name)
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::decks::DeckId;

    #[test]
    fn tags() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let tags: String = col.storage.db_scalar("select tags from notes")?;
        assert_eq!(tags, "");

        // first instance wins in case of duplicates
        note.tags = vec!["foo".into(), "FOO".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["foo"]);
        let tags: String = col.storage.db_scalar("select tags from notes")?;
        assert_eq!(tags, " foo ");

        // existing case is used if in DB
        note.tags = vec!["FOO".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["foo"]);
        assert_eq!(tags, " foo ");

        // tags are normalized to nfc
        note.tags = vec!["\u{fa47}".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["\u{6f22}"]);

        // if code incorrectly adds a space to a tag, it gets split
        note.tags = vec!["one two".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["one", "two"]);

        // blanks should be handled
        note.tags = vec![
            "".into(),
            "foo".into(),
            " ".into(),
            "::".into(),
            "foo::".into(),
        ];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["blank::blank", "foo", "foo::blank"]);

        Ok(())
    }
}
