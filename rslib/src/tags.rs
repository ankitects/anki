// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto::{Tag as TagProto, TagTreeNode},
    collection::Collection,
    err::{AnkiError, Result},
    notes::{NoteID, TransformNoteOutput},
    text::{normalize_to_nfc, to_re},
    types::Usn,
};

use regex::{NoExpand, Regex, Replacer};
use std::cmp::Ordering;
use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    iter::Peekable,
};
use unicase::UniCase;

#[derive(Debug, Clone)]
pub struct Tag {
    pub name: String,
    pub usn: Usn,
    pub collapsed: bool,
}

impl Ord for Tag {
    fn cmp(&self, other: &Self) -> Ordering {
        UniCase::new(&self.name).cmp(&UniCase::new(&other.name))
    }
}

impl PartialOrd for Tag {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl PartialEq for Tag {
    fn eq(&self, other: &Self) -> bool {
        self.cmp(other) == Ordering::Equal
    }
}

impl Eq for Tag {}

impl From<Tag> for TagProto {
    fn from(t: Tag) -> Self {
        TagProto {
            name: t.name,
            usn: t.usn.0,
            collapsed: t.collapsed,
        }
    }
}

impl From<TagProto> for Tag {
    fn from(t: TagProto) -> Self {
        Tag {
            name: t.name,
            usn: Usn(t.usn),
            collapsed: t.collapsed,
        }
    }
}

impl Tag {
    pub fn new(name: String, usn: Usn) -> Self {
        Tag {
            name,
            usn,
            collapsed: false,
        }
    }
}

pub(crate) fn split_tags(tags: &str) -> impl Iterator<Item = &str> {
    tags.split(is_tag_separator).filter(|tag| !tag.is_empty())
}

pub(crate) fn join_tags(tags: &[String]) -> String {
    if tags.is_empty() {
        "".into()
    } else {
        format!(" {} ", tags.join(" "))
    }
}

fn is_tag_separator(c: char) -> bool {
    c == ' ' || c == '\u{3000}'
}

fn invalid_char_for_tag(c: char) -> bool {
    c.is_ascii_control() || is_tag_separator(c) || c == '"'
}

fn normalized_tag_name_component(comp: &str) -> Cow<str> {
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

fn normalize_tag_name(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    for comp in name.split("::") {
        out.push_str(&normalized_tag_name_component(comp));
        out.push_str("::");
    }
    out.trim_end_matches("::").into()
}

fn fill_missing_tags(tags: Vec<Tag>) -> Vec<Tag> {
    let mut filled_tags: HashMap<String, Tag> = HashMap::with_capacity(tags.len());
    for tag in tags.into_iter() {
        let name = tag.name.to_owned();
        let split: Vec<&str> = (&tag.name).split("::").collect();
        for i in 0..split.len() - 1 {
            let comp = split[0..i + 1].join("::");
            let t = Tag::new(comp.to_owned(), Usn(-1));
            if filled_tags.get(&comp).is_none() {
                filled_tags.insert(comp, t);
            }
        }
        if filled_tags.get(&name).is_none() {
            filled_tags.insert(name, tag);
        }
    }
    let mut tags: Vec<Tag> = filled_tags.values().map(|t| (*t).clone()).collect();
    tags.sort_unstable();

    tags
}

fn tags_to_tree(tags: Vec<Tag>) -> TagTreeNode {
    let tags = fill_missing_tags(tags);
    let mut top = TagTreeNode::default();
    let mut it = tags.into_iter().peekable();
    add_child_nodes(&mut it, &mut top);

    top
}

fn add_child_nodes(tags: &mut Peekable<impl Iterator<Item = Tag>>, parent: &mut TagTreeNode) {
    while let Some(tag) = tags.peek() {
        let split_name: Vec<_> = tag.name.split("::").collect();
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
                    collapsed: tag.collapsed,
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

impl Collection {
    pub fn tag_tree(&mut self) -> Result<TagTreeNode> {
        let tags = self.storage.all_tags_sorted()?;
        let tree = tags_to_tree(tags);

        Ok(tree)
    }

    /// Given a list of tags, fix case, ordering and duplicates.
    /// Returns true if any new tags were added.
    pub(crate) fn canonify_tags(&self, tags: Vec<String>, usn: Usn) -> Result<(Vec<String>, bool)> {
        let mut seen = HashSet::new();
        let mut added = false;

        let tags: Vec<_> = tags.iter().flat_map(|t| split_tags(t)).collect();
        for tag in tags {
            let t = self.register_tag(Tag::new(tag.to_string(), usn))?;
            if t.0.name.is_empty() {
                continue;
            }
            added |= t.1;
            seen.insert(UniCase::new(t.0.name));
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

    /// Register tag if it doesn't exist.
    /// Returns a tuple of the tag with its name normalized and a boolean indicating if it was added.
    pub(crate) fn register_tag(&self, tag: Tag) -> Result<(Tag, bool)> {
        let normalized_name = normalize_tag_name(&tag.name);
        let mut t = Tag {
            name: normalized_name.clone(),
            ..tag
        };
        if normalized_name.is_empty() {
            return Ok((t, false));
        }
        if let Some(preferred) = self.storage.preferred_tag_case(&normalized_name)? {
            t.name = preferred;
            Ok((t, false))
        } else {
            self.storage.register_tag(&t)?;
            Ok((t, true))
        }
    }

    pub(crate) fn register_tags(&self, tags: &str, usn: Usn, clear_first: bool) -> Result<bool> {
        let mut changed = false;
        if clear_first {
            self.storage.clear_tags()?;
        }
        for tag in split_tags(tags) {
            let t = self.register_tag(Tag::new(tag.to_string(), usn))?;
            changed |= t.1;
        }
        Ok(changed)
    }

    pub(crate) fn set_tag_collapsed(&self, name: &str, collapsed: bool) -> Result<()> {
        if self.storage.get_tag(name)?.is_none() {
            // tag is missing, register it
            self.register_tag(Tag::new(name.to_string(), self.usn()?))?;
        }
        self.storage.set_tag_collapsed(name, collapsed)
    }

    /// Update collapse state of existing tags and register tags in old_tags that are parents of those tags
    pub(crate) fn update_tags_collapse(&self, old_tags: Vec<Tag>) -> Result<()> {
        let new_tags = self.storage.all_tags_sorted()?;
        for old in old_tags.into_iter() {
            for new in new_tags.iter() {
                if new.name == old.name {
                    self.storage.set_tag_collapsed(&new.name, old.collapsed)?;
                    break;
                } else if new.name.starts_with(&old.name) {
                    self.set_tag_collapsed(&old.name, old.collapsed)?;
                }
            }
        }
        Ok(())
    }

    fn replace_tags_for_notes_inner<R: Replacer>(
        &mut self,
        nids: &[NoteID],
        tags: &[Regex],
        mut repl: R,
    ) -> Result<usize> {
        self.transact(None, |col| {
            col.transform_notes(nids, |note, _nt| {
                let mut changed = false;
                for re in tags {
                    if note.replace_tags(re, repl.by_ref()) {
                        changed = true;
                    }
                }

                Ok(TransformNoteOutput {
                    changed,
                    generate_cards: false,
                    mark_modified: true,
                })
            })
        })
    }

    /// Apply the provided list of regular expressions to note tags,
    /// saving any modified notes.
    pub fn replace_tags_for_notes(
        &mut self,
        nids: &[NoteID],
        tags: &str,
        repl: &str,
        regex: bool,
    ) -> Result<usize> {
        // generate regexps
        let tags = split_tags(tags)
            .map(|tag| {
                let tag = if regex { tag.into() } else { to_re(tag) };
                Regex::new(&format!("(?i)^{}(::.*)?", tag))
                    .map_err(|_| AnkiError::invalid_input("invalid regex"))
            })
            .collect::<Result<Vec<Regex>>>()?;
        if !regex {
            self.replace_tags_for_notes_inner(nids, &tags, NoExpand(repl))
        } else {
            self.replace_tags_for_notes_inner(nids, &tags, repl)
        }
    }

    pub fn add_tags_for_notes(&mut self, nids: &[NoteID], tags: &str) -> Result<usize> {
        let tags: Vec<_> = split_tags(tags).collect();
        let matcher = regex::RegexSet::new(
            tags.iter()
                .map(|s| regex::escape(s))
                .map(|s| format!("(?i)^{}$", s)),
        )
        .map_err(|_| AnkiError::invalid_input("invalid regex"))?;

        self.transact(None, |col| {
            col.transform_notes(nids, |note, _nt| {
                let mut need_to_add = true;
                let mut match_count = 0;
                for tag in &note.tags {
                    if matcher.is_match(tag) {
                        match_count += 1;
                    }
                    if match_count == tags.len() {
                        need_to_add = false;
                        break;
                    }
                }

                if need_to_add {
                    note.tags.extend(tags.iter().map(|&s| s.to_string()))
                }

                Ok(TransformNoteOutput {
                    changed: need_to_add,
                    generate_cards: false,
                    mark_modified: true,
                })
            })
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, decks::DeckID};

    #[test]
    fn tags() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckID(1))?;

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

        Ok(())
    }

    #[test]
    fn bulk() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("test".into());
        col.add_note(&mut note, DeckID(1))?;

        col.replace_tags_for_notes(&[note.id], "foo test", "bar", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.replace_tags_for_notes(&[note.id], "b.r", "baz", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "bar");

        col.replace_tags_for_notes(&[note.id], "b*r", "baz", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "baz");

        col.replace_tags_for_notes(&[note.id], "b.r", "baz", true)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.tags[0], "baz");

        let cnt = col.add_tags_for_notes(&[note.id], "cee aye")?;
        assert_eq!(cnt, 1);
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["aye", "baz", "cee"]);

        // if all tags already on note, it doesn't get updated
        let cnt = col.add_tags_for_notes(&[note.id], "cee aye")?;
        assert_eq!(cnt, 0);

        // empty replacement deletes tag
        col.replace_tags_for_notes(&[note.id], "b.* .*ye", "", true)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["cee"]);

        let mut note = col.storage.get_note(note.id)?.unwrap();
        note.tags = vec![
            "foo::bar".into(),
            "foo::bar::foo".into(),
            "bar::foo".into(),
            "bar::foo::bar".into(),
        ];
        col.update_note(&mut note)?;
        col.replace_tags_for_notes(&[note.id], "bar::foo", "foo::bar", false)?;
        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(&note.tags, &["foo::bar", "foo::bar::bar", "foo::bar::foo",]);

        // tag children are also cleared when clearing their parent
        col.register_tags("a a::b a::b::c", Usn(-1), true)?;
        col.storage.clear_tag("a")?;
        assert_eq!(col.storage.all_tags()?, vec![]);

        Ok(())
    }

    fn node(name: &str, level: u32, children: Vec<TagTreeNode>) -> TagTreeNode {
        TagTreeNode {
            name: name.into(),
            level,
            children,
            ..Default::default()
        }
    }

    fn leaf(name: &str, level: u32) -> TagTreeNode {
        node(name, level, vec![])
    }

    #[test]
    fn tree() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("foo::a".into());
        note.tags.push("foo::b".into());
        col.add_note(&mut note, DeckID(1))?;

        // missing parents are added
        assert_eq!(
            col.tag_tree()?,
            node(
                "",
                0,
                vec![node("foo", 1, vec![leaf("a", 2), leaf("b", 2)])]
            )
        );

        // differing case should result in only one parent case being added -
        // the first one
        *(&mut note.tags[1]) = "FOO::b".into();
        col.storage.clear_tags()?;
        col.update_note(&mut note)?;
        assert_eq!(
            col.tag_tree()?,
            node(
                "",
                0,
                vec![node("foo", 1, vec![leaf("a", 2), leaf("b", 2)])]
            )
        );

        Ok(())
    }
}
