// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use regex::{NoExpand, Regex, Replacer};

use super::split_tags;
use crate::{notes::TransformNoteOutput, prelude::*};

impl Collection {
    pub fn drag_drop_tags(
        &mut self,
        source_tags: &[String],
        target_tag: Option<String>,
    ) -> Result<()> {
        let source_tags_and_outputs: Vec<_> = source_tags
            .iter()
            // generate resulting names and filter out invalid ones
            .flat_map(|source_tag| {
                if let Some(output_name) = drag_drop_tag_name(source_tag, target_tag.as_deref()) {
                    Some((source_tag, output_name))
                } else {
                    // invalid rename, ignore this tag
                    None
                }
            })
            .collect();

        let regexps_and_replacements = source_tags_and_outputs
            .iter()
            // convert the names into regexps/replacements
            .map(|(tag, output)| {
                regex_matching_tag_and_children_in_single_tag(tag).map(|regex| (regex, output))
            })
            .collect::<Result<Vec<_>>>()?;

        // locate notes that match them
        let mut nids = vec![];
        self.storage.for_each_note_tags(|nid, tags| {
            for tag in split_tags(&tags) {
                for (regex, _) in &regexps_and_replacements {
                    if regex.is_match(&tag) {
                        nids.push(nid);
                        break;
                    }
                }
            }

            Ok(())
        })?;

        if nids.is_empty() {
            return Ok(());
        }

        // update notes
        self.transact_no_undo(|col| {
            // clear the existing original tags
            for (source_tag, _) in &source_tags_and_outputs {
                col.storage.clear_tag_and_children(source_tag)?;
            }

            col.transform_notes(&nids, |note, _nt| {
                let mut changed = false;
                for (re, repl) in &regexps_and_replacements {
                    if note.replace_tags(re, NoExpand(&repl).by_ref()) {
                        changed = true;
                    }
                }

                Ok(TransformNoteOutput {
                    changed,
                    generate_cards: false,
                    mark_modified: true,
                })
            })
        })?;

        Ok(())
    }
}

/// Arguments are expected in 'human' form with an :: separator.
pub(crate) fn drag_drop_tag_name(dragged: &str, dropped: Option<&str>) -> Option<String> {
    let dragged_base = dragged.rsplit("::").next().unwrap();
    if let Some(dropped) = dropped {
        if dropped.starts_with(dragged) {
            // foo onto foo::bar, or foo onto itself -> no-op
            None
        } else {
            // foo::bar onto baz -> baz::bar
            Some(format!("{}::{}", dropped, dragged_base))
        }
    } else {
        // foo::bar onto top level -> bar
        Some(dragged_base.into())
    }
}

/// A regex that will match a string tag that has been split from a list.
fn regex_matching_tag_and_children_in_single_tag(tag: &str) -> Result<Regex> {
    Regex::new(&format!(
        r#"(?ix)
        ^
        {}
        # optional children
        (::.+)?
        $
        "#,
        regex::escape(tag)
    ))
    .map_err(Into::into)
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;

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
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        for tag in &[
            "another",
            "parent1::child1::grandchild1",
            "parent1::child1",
            "parent1",
            "parent2",
            "yet::another",
        ] {
            let mut note = nt.new_note();
            note.tags.push(tag.to_string());
            col.add_note(&mut note, DeckID(1))?;
        }

        // two decks with the same base name; they both get mapped
        // to parent1::another
        col.drag_drop_tags(
            &["another".to_string(), "yet::another".to_string()],
            Some("parent1".to_string()),
        )?;

        assert_eq!(
            alltags(&col),
            &[
                "parent1",
                "parent1::another",
                "parent1::child1",
                "parent1::child1::grandchild1",
                "parent2",
            ]
        );

        // child and children moved to parent2
        col.drag_drop_tags(
            &["parent1::child1".to_string()],
            Some("parent2".to_string()),
        )?;

        assert_eq!(
            alltags(&col),
            &[
                "parent1",
                "parent1::another",
                "parent2",
                "parent2::child1",
                "parent2::child1::grandchild1",
            ]
        );

        // empty target reparents to root
        col.drag_drop_tags(&["parent1::another".to_string()], None)?;

        assert_eq!(
            alltags(&col),
            &[
                "another",
                "parent1",
                "parent2",
                "parent2::child1",
                "parent2::child1::grandchild1",
            ]
        );

        Ok(())
    }
}
