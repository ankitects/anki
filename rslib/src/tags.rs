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
use std::{borrow::Cow, collections::HashSet, iter::Peekable};
use unicase::UniCase;

#[derive(Debug, Clone, PartialEq)]
pub struct Tag {
    pub name: String,
    pub usn: Usn,
    pub collapsed: bool,
}

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

fn immediate_parent_name(tag_name: UniCase<&str>) -> Option<UniCase<&str>> {
    tag_name.rsplitn(2, '\x1f').nth(1).map(UniCase::new)
}

/// For the given tag, check if immediate parent exists. If so, add
/// tag and return.
/// If the immediate parent is missing, check and add any missing parents.
/// This should ensure that if an immediate parent is found, all ancestors
/// are guaranteed to already exist.
fn add_self_and_missing_parents<'a, 'b>(
    all: &'a mut HashSet<UniCase<&'b str>>,
    missing: &'a mut Vec<UniCase<&'b str>>,
    tag_name: UniCase<&'b str>,
) {
    if let Some(parent) = immediate_parent_name(tag_name) {
        if !all.contains(&parent) {
            missing.push(parent);
            add_self_and_missing_parents(all, missing, parent);
        }
    }
    // finally, add self
    all.insert(tag_name);
}

/// Append any missing parents. Caller must sort afterwards.
fn add_missing_parents(tags: &mut Vec<Tag>) {
    let mut all_names: HashSet<UniCase<&str>> = HashSet::new();
    let mut missing = vec![];
    for tag in &*tags {
        add_self_and_missing_parents(&mut all_names, &mut missing, UniCase::new(&tag.name))
    }
    let mut missing: Vec<_> = missing
        .into_iter()
        .map(|n| Tag::new(n.to_string(), Usn(0)))
        .collect();
    tags.append(&mut missing);
}

fn tags_to_tree(mut tags: Vec<Tag>) -> TagTreeNode {
    for tag in &mut tags {
        tag.name = tag.name.replace("::", "\x1f");
    }
    add_missing_parents(&mut tags);
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
        note.tags.push("foo::bar::a".into());
        note.tags.push("foo::bar::b".into());
        col.add_note(&mut note, DeckID(1))?;

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
        col.storage.clear_tags()?;
        *(&mut note.tags[0]) = "foo::BAR::a".into();
        *(&mut note.tags[1]) = "FOO::bar::b".into();
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
        col.storage.clear_tags()?;
        *(&mut note.tags[0]) = "foo::bar::baz".into();
        *(&mut note.tags[1]) = "foo::bar::baz::quux".into();
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
        col.storage.clear_tags()?;
        *(&mut note.tags[0]) = "one".into();
        *(&mut note.tags[1]) = "one1".into();
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

        Ok(())
    }
}
