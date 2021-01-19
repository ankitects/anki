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

fn normalize_tag_name(name: &str) -> Cow<str> {
    if name
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
    }
}

fn immediate_parent_name_unicase(tag_name: UniCase<&str>) -> Option<UniCase<&str>> {
    tag_name.rsplitn(2, '\x1f').nth(1).map(UniCase::new)
}

fn immediate_parent_name_str(tag_name: &str) -> Option<&str> {
    tag_name.rsplitn(2, "::").nth(1)
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
        let tags = self.storage.all_tags()?;
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
            let mut tag = Tag::new(tag.to_string(), usn);
            added |= self.register_tag(&mut tag)?;
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

    /// Adjust tag casing to match any existing parents, and register it if it's not already
    /// in the tags list. True if the tag was added and not already in tag list.
    /// In the case the tag is already registered, tag will be mutated to match the existing
    /// name.
    pub(crate) fn register_tag(&self, tag: &mut Tag) -> Result<bool> {
        let normalized_name = normalize_tag_name(&tag.name);
        if normalized_name.is_empty() {
            // this should not be possible
            return Err(AnkiError::invalid_input("blank tag"));
        }
        if let Some(existing_tag) = self.storage.get_tag(&normalized_name)? {
            tag.name = existing_tag.name;
            Ok(false)
        } else {
            if let Some(new_name) = self.adjusted_case_for_parents(&normalized_name)? {
                tag.name = new_name;
            } else if let Cow::Owned(new_name) = normalized_name {
                tag.name = new_name;
            }
            self.storage.register_tag(&tag)?;
            Ok(true)
        }
    }

    /// If parent tag(s) exist and differ in case, return a rewritten tag.
    fn adjusted_case_for_parents(&self, tag: &str) -> Result<Option<String>> {
        if let Some(parent_tag) = self.first_existing_parent_tag(&tag)? {
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

    pub fn clear_unused_tags(&self) -> Result<()> {
        let collapsed: HashSet<_> = self.storage.collapsed_tags()?.into_iter().collect();
        self.storage.clear_tags()?;
        let usn = self.usn()?;
        for name in self.storage.all_tags_in_notes()? {
            let name = normalize_tag_name(&name).into();
            self.storage.register_tag(&Tag {
                collapsed: collapsed.contains(&name),
                name,
                usn,
            })?;
        }

        Ok(())
    }

    pub(crate) fn set_tag_collapsed(&self, name: &str, collapsed: bool) -> Result<()> {
        let mut name = name;
        let tag;
        if self.storage.get_tag(name)?.is_none() {
            // tag is missing, register it
            tag = Tag::new(name.to_string(), self.usn()?);
            self.storage.register_tag(&tag)?;
            name = &tag.name;
        }
        self.storage.set_tag_collapsed(name, collapsed)
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
        col.storage.clear_tags()?;
        for name in vec!["a", "a::b", "A::b::c"] {
            col.register_tag(&mut Tag::new(name.to_string(), Usn(0)))?;
        }
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

        // children should match the case of their parents
        col.storage.clear_tags()?;
        *(&mut note.tags[0]) = "FOO".into();
        *(&mut note.tags[1]) = "foo::BAR".into();
        *(&mut note.tags[2]) = "foo::bar::baz".into();
        col.update_note(&mut note)?;
        assert_eq!(note.tags, vec!["FOO", "FOO::BAR", "FOO::BAR::baz"]);

        Ok(())
    }

    #[test]
    fn clearing() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.tags.push("one".into());
        note.tags.push("two".into());
        col.add_note(&mut note, DeckID(1))?;

        col.set_tag_collapsed("two", true)?;
        col.clear_unused_tags()?;
        assert_eq!(col.storage.get_tag("one")?.unwrap().collapsed, false);
        assert_eq!(col.storage.get_tag("two")?.unwrap().collapsed, true);

        Ok(())
    }
}
