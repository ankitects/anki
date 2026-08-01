// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::iter::Peekable;

use anki_proto::tags::TagTreeNode;
use unicase::UniCase;

use super::immediate_parent_name_unicase;
use super::Tag;
use crate::prelude::*;

impl Collection {
    pub fn tag_tree(&mut self) -> Result<TagTreeNode> {
        let tags = self.storage.all_tags()?;
        let tree = tags_to_tree(tags);

        Ok(tree)
    }

    pub fn set_tag_collapsed(&mut self, tag: &str, collapsed: bool) -> Result<OpOutput<()>> {
        self.transact(Op::SkipUndo, |col| {
            col.set_tag_collapsed_inner(tag, collapsed, col.usn()?)
        })
    }
}

impl Collection {
    fn set_tag_collapsed_inner(&mut self, name: &str, collapsed: bool, usn: Usn) -> Result<()> {
        self.register_tag_string(name.into(), usn)?;
        if let Some(mut tag) = self.storage.get_tag(name)? {
            let original = tag.clone();
            tag.expanded = !collapsed;
            self.update_tag_inner(&mut tag, original, usn)?;
        }
        Ok(())
    }

    fn update_tag_inner(&mut self, tag: &mut Tag, original: Tag, usn: Usn) -> Result<()> {
        tag.set_modified(usn);
        self.update_tag_undoable(tag, original)
    }
}

/// Append any missing parents. Caller must sort afterwards.
fn add_missing_parents(tags: &mut Vec<Tag>) {
    let mut all_names: HashSet<UniCase<&str>> = HashSet::new();
    let mut missing = vec![];
    for tag in &*tags {
        add_tag_and_missing_parents(&mut all_names, &mut missing, UniCase::new(&tag.name))
    }
    let mut missing: Vec<_> = missing
        .into_iter()
        .map(|n| Tag::new(n.to_string(), Usn(0)))
        .collect();
    tags.append(&mut missing);
}

fn tags_to_tree(mut tags: Vec<Tag>) -> TagTreeNode {
    add_missing_parents(&mut tags);
    for tag in &mut tags {
        tag.name = tag.name.replace("::", "\x1f");
    }
    tags.sort_unstable_by(|a, b| UniCase::new(&a.name).cmp(&UniCase::new(&b.name)));
    let mut top = TagTreeNode::default();
    let mut it = tags.into_iter().peekable();
    add_child_nodes(&mut it, &mut top);

    top
}

fn add_child_nodes(tags: &mut Peekable<impl Iterator<Item = Tag>>, parent: &mut TagTreeNode) {
    while let Some(tag) = tags.peek() {
        let split_name: Vec<_> = tag.name.split('\x1f').collect();
        match split_name.len() as u32 {
            l if l <= parent.level => {
                // next item is at a higher level
                return;
            }
            l if l == parent.level + 1 => {
                // next item is an immediate descendent of parent
                parent.children.push(TagTreeNode {
                    name: (*split_name.last().unwrap()).into(),
                    children: vec![],
                    level: parent.level + 1,
                    collapsed: !tag.expanded,
                });
                tags.next();
            }
            _ => {
                // next item is at a lower level
                if let Some(last_child) = parent.children.last_mut() {
                    add_child_nodes(tags, last_child)
                } else {
                    // immediate parent is missing
                    tags.next();
                }
            }
        }
    }
}

/// For the given tag, check if immediate parent exists. If so, add
/// tag and return.
/// If the immediate parent is missing, check and add any missing parents.
/// This should ensure that if an immediate parent is found, all ancestors
/// are guaranteed to already exist.
fn add_tag_and_missing_parents<'a, 'b>(
    all: &'a mut HashSet<UniCase<&'b str>>,
    missing: &'a mut Vec<UniCase<&'b str>>,
    tag_name: UniCase<&'b str>,
) {
    if let Some(parent) = immediate_parent_name_unicase(tag_name) {
        if !all.contains(&parent) {
            missing.push(parent);
            add_tag_and_missing_parents(all, missing, parent);
        }
    }
    // finally, add provided tag
    all.insert(tag_name);
}

#[cfg(test)]
mod test {
    use super::*;

    fn node(name: &str, level: u32, children: Vec<TagTreeNode>) -> TagTreeNode {
        TagTreeNode {
            name: name.into(),
            level,
            children,
            collapsed: level != 0,
        }
    }

    fn leaf(name: &str, level: u32) -> TagTreeNode {
        node(name, level, vec![])
    }

    #[test]
    fn tree() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("foo::bar::a".into());
        note.tags.push("foo::bar::b".into());
        col.add_note(&mut note, DeckId(1))?;

        // missing parents are added
        assert_eq!(
            col.tag_tree()?,
            node(
                "",
                0,
                vec![node(
                    "foo",
                    1,
                    vec![node("bar", 2, vec![leaf("a", 3), leaf("b", 3)])]
                )]
            )
        );

        // differing case should result in only one parent case being added -
        // the first one
        col.storage.clear_all_tags()?;
        note.tags[0] = "foo::BAR::a".into();
        note.tags[1] = "FOO::bar::b".into();
        col.update_note(&mut note)?;
        assert_eq!(
            col.tag_tree()?,
            node(
                "",
                0,
                vec![node(
                    "foo",
                    1,
                    vec![node("BAR", 2, vec![leaf("a", 3), leaf("b", 3)])]
                )]
            )
        );

        // things should work even if the immediate parent is not missing
        col.storage.clear_all_tags()?;
        note.tags[0] = "foo::bar::baz".into();
        note.tags[1] = "foo::bar::baz::quux".into();
        col.update_note(&mut note)?;
        assert_eq!(
            col.tag_tree()?,
            node(
                "",
                0,
                vec![node(
                    "foo",
                    1,
                    vec![node("bar", 2, vec![node("baz", 3, vec![leaf("quux", 4)])])]
                )]
            )
        );

        // numbers have a smaller ascii number than ':', so a naive sort on
        // '::' would result in one::two being nested under one1.
        col.storage.clear_all_tags()?;
        note.tags[0] = "one".into();
        note.tags[1] = "one1".into();
        note.tags.push("one::two".into());
        col.update_note(&mut note)?;
        assert_eq!(
            col.tag_tree()?,
            node(
                "",
                0,
                vec![node("one", 1, vec![leaf("two", 2)]), leaf("one1", 1)]
            )
        );

        // children should match the case of their parents
        col.storage.clear_all_tags()?;
        note.tags[0] = "FOO".into();
        note.tags[1] = "foo::BAR".into();
        note.tags[2] = "foo::bar::baz".into();
        col.update_note(&mut note)?;
        assert_eq!(note.tags, vec!["FOO", "FOO::BAR", "FOO::BAR::baz"]);

        Ok(())
    }
}
