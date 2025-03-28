// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use unicase::UniCase;

use super::join_tags;
use super::matcher::TagMatcher;
use crate::prelude::*;

impl Collection {
    /// Reparent the provided tags under a new parent.
    ///
    /// Parents of the provided tags are left alone - only the final component
    /// and its children are moved. If a source tag is the parent of the target
    /// tag, it will remain unchanged. If `new_parent` is not provided, tags
    /// will be reparented to the root element. When reparenting tags, any
    /// children they have are reparented as well.
    ///
    /// For example:
    /// - foo,       bar       -> bar::foo
    /// - foo::bar,  baz       -> baz::bar
    /// - foo,       foo::bar  -> no action
    /// - foo::bar,  none      -> bar
    pub fn reparent_tags(
        &mut self,
        tags_to_reparent: &[String],
        new_parent: Option<String>,
    ) -> Result<OpOutput<usize>> {
        self.transact(Op::ReparentTag, |col| {
            col.reparent_tags_inner(tags_to_reparent, new_parent)
        })
    }

    pub fn reparent_tags_inner(
        &mut self,
        tags_to_reparent: &[String],
        new_parent: Option<String>,
    ) -> Result<usize> {
        let usn = self.usn()?;
        let mut matcher = TagMatcher::new(&join_tags(tags_to_reparent))?;
        let old_to_new_names = old_to_new_names(tags_to_reparent, new_parent);
        if old_to_new_names.is_empty() {
            return Ok(0);
        }
        let matched_notes = self
            .storage
            .get_note_tags_by_predicate(|tags| matcher.is_match(tags))?;
        let match_count = matched_notes.len();
        if match_count == 0 {
            // no matches; exit early so we don't clobber the empty tag entries
            return Ok(0);
        }

        // remove old prefixes from the tag list
        for tag in self
            .storage
            .get_tags_by_predicate(|tag| matcher.is_match(tag))?
        {
            self.remove_single_tag_undoable(tag)?;
        }

        // replace tags
        for mut note in matched_notes {
            let original = note.clone();
            note.tags = matcher.replace_with_fn(&note.tags, |cap| {
                old_to_new_names
                    .get(&UniCase::new(cap.to_string()))
                    .unwrap()
                    .clone()
            });
            note.set_modified(usn);
            self.update_note_tags_undoable(&note, original)?;
        }

        // update tag list
        for tag in matcher.into_new_tags() {
            self.register_tag_string(tag, usn)?;
        }

        Ok(match_count)
    }
}

fn old_to_new_names(
    tags_to_reparent: &[String],
    new_parent: Option<String>,
) -> HashMap<UniCase<String>, String> {
    tags_to_reparent
        .iter()
        // generate resulting names and filter out invalid ones
        .flat_map(|source_tag| {
            reparented_name(source_tag, new_parent.as_deref())
                .map(|output_name| (UniCase::new(source_tag.to_owned()), output_name))
        })
        .collect()
}

/// Arguments are expected in 'human' form with a :: separator.
/// Returns None if new parent is a child of the tag to be reparented.
fn reparented_name(existing_name: &str, new_parent: Option<&str>) -> Option<String> {
    let existing_base = existing_name.rsplit("::").next().unwrap();
    let existing_root = existing_name.split("::").next().unwrap();
    if let Some(new_parent) = new_parent {
        let new_parent_root = new_parent.split("::").next().unwrap();
        if new_parent.starts_with(existing_name) && new_parent_root == existing_root {
            // foo onto foo::bar, or foo onto itself -> no-op
            None
        } else {
            // foo::bar onto baz -> baz::bar
            Some(format!("{}::{}", new_parent, existing_base))
        }
    } else {
        // foo::bar onto top level -> bar
        Some(existing_base.into())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    fn alltags(col: &Collection) -> Vec<String> {
        col.storage
            .all_tags()
            .unwrap()
            .into_iter()
            .map(|t| t.name)
            .collect()
    }

    #[test]
    fn dragdrop() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        for tag in &[
            "a",
            "ab",
            "another",
            "parent1::child1::grandchild1",
            "parent1::child1",
            "parent1",
            "parent2",
            "yet::another",
        ] {
            let mut note = nt.new_note();
            note.tags.push(tag.to_string());
            col.add_note(&mut note, DeckId(1))?;
        }

        // two decks with the same base name; they both get mapped
        // to parent1::another
        col.reparent_tags(
            &["another".to_string(), "yet::another".to_string()],
            Some("parent1".to_string()),
        )?;

        assert_eq!(
            alltags(&col),
            &[
                "a",
                "ab",
                "parent1",
                "parent1::another",
                "parent1::child1",
                "parent1::child1::grandchild1",
                "parent2",
            ]
        );

        // child and children moved to parent2
        col.reparent_tags(
            &["parent1::child1".to_string()],
            Some("parent2".to_string()),
        )?;

        assert_eq!(
            alltags(&col),
            &[
                "a",
                "ab",
                "parent1",
                "parent1::another",
                "parent2",
                "parent2::child1",
                "parent2::child1::grandchild1",
            ]
        );

        // empty target reparents to root
        col.reparent_tags(&["parent1::another".to_string()], None)?;

        assert_eq!(
            alltags(&col),
            &[
                "a",
                "ab",
                "another",
                "parent1",
                "parent2",
                "parent2::child1",
                "parent2::child1::grandchild1",
            ]
        );

        // parent1 onto parent1::child1 -> no-op
        col.reparent_tags(
            &["parent1".to_string()],
            Some("parent1::child1".to_string()),
        )?;

        assert_eq!(
            alltags(&col),
            &[
                "a",
                "ab",
                "another",
                "parent1",
                "parent2",
                "parent2::child1",
                "parent2::child1::grandchild1",
            ]
        );

        // tags that are prefixes of the new parent are handled correctly
        col.reparent_tags(&["a".to_string()], Some("ab".to_string()))?;

        assert_eq!(
            alltags(&col),
            &[
                "ab",
                "ab::a",
                "another",
                "parent1",
                "parent2",
                "parent2::child1",
                "parent2::child1::grandchild1",
            ]
        );

        // grandchildren can be reparented under the same root
        col.reparent_tags(
            &["parent2::child1::grandchild1".to_string()],
            Some("parent2".to_string()),
        )?;

        assert_eq!(
            alltags(&col),
            &[
                "ab",
                "ab::a",
                "another",
                "parent1",
                "parent2",
                "parent2::child1",
                "parent2::grandchild1",
            ]
        );

        Ok(())
    }
}
