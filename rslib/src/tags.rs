// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub use crate::backend_proto::TagConfig;
use crate::{
    backend_proto::{Tag as TagProto, TagTreeNode},
    collection::Collection,
    define_newtype,
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

define_newtype!(TagID, i64);

#[derive(Debug, Clone)]
pub struct Tag {
    pub id: TagID,
    pub name: String,
    pub usn: Usn,
    pub config: TagConfig,
}

impl Ord for Tag {
    fn cmp(&self, other: &Self) -> Ordering {
        self.name.cmp(&other.name)
    }
}

impl PartialOrd for Tag {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl PartialEq for Tag {
    fn eq(&self, other: &Self) -> bool {
        self.name == other.name
    }
}

impl Eq for Tag {}

impl Default for Tag {
    fn default() -> Self {
        Tag {
            id: TagID(0),
            name: "".to_string(),
            usn: Usn(-1),
            config: Default::default(),
        }
    }
}

impl From<Tag> for TagProto {
    fn from(t: Tag) -> Self {
        TagProto {
            id: t.id.0,
            name: t.name,
            usn: t.usn.0,
            config: Some(t.config),
        }
    }
}

impl From<TagProto> for Tag {
    fn from(t: TagProto) -> Self {
        Tag {
            id: TagID(t.id),
            name: t.name,
            usn: Usn(t.usn),
            config: t.config.unwrap(),
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

pub(crate) fn human_tag_name_to_native(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    for comp in name.split("::") {
        out.push_str(&normalized_tag_name_component(comp));
        out.push('\x1f');
    }
    out.trim_end_matches('\x1f').into()
}

pub(crate) fn native_tag_name_to_human(name: &str) -> String {
    name.replace('\x1f', "::")
}

fn fill_missing_tags(tags: Vec<Tag>) -> Vec<Tag> {
    let mut filled_tags: HashMap<String, Tag> = HashMap::new();
    for tag in tags.into_iter() {
        let name = tag.name.to_owned();
        let split: Vec<&str> = (&tag.name).split("::").collect();
        for i in 0..split.len() - 1 {
            let comp = split[0..i + 1].join("::");
            let t = Tag {
                name: comp.to_owned(),
                ..Default::default()
            };
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
                    tag_id: tag.id.0,
                    name: (*split_name.last().unwrap()).into(),
                    children: vec![],
                    level: parent.level + 1,
                    collapsed: tag.config.browser_collapsed,
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
    pub fn all_tags(&self) -> Result<Vec<Tag>> {
        self.storage
            .all_tags()?
            .into_iter()
            .map(|t| {
                Ok(Tag {
                    name: native_tag_name_to_human(&t.name),
                    ..t
                })
            })
            .collect()
    }

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

        let mut tags: Vec<_> = tags.iter().flat_map(|t| split_tags(t)).collect();
        for tag in &mut tags {
            let t = self.register_tag(Tag {
                name: tag.to_string(),
                usn,
                ..Default::default()
            })?;
            if t.0.is_empty() {
                continue;
            }
            added |= t.1;
            seen.insert(UniCase::new(t.0));
        }

        // exit early if no non-empty tags
        if seen.is_empty() {
            return Ok((vec![], added));
        }

        // return the sorted, canonified tags
        let mut tags = seen.into_iter().collect::<Vec<_>>();
        tags.sort_unstable();
        let tags: Vec<_> = tags
            .into_iter()
            .map(|s| s.into_inner().to_string())
            .collect();

        Ok((tags, added))
    }

    pub(crate) fn register_tag<'a>(&self, tag: Tag) -> Result<(Cow<'a, str>, bool)> {
        let native_name = human_tag_name_to_native(&tag.name);
        if native_name.is_empty() {
            return Ok(("".into(), false));
        }
        if let Some(preferred) = self.storage.preferred_tag_case(&native_name)? {
            Ok((native_tag_name_to_human(&preferred).into(), false))
        } else {
            let mut t = Tag {
                name: native_name.clone(),
                ..tag
            };
            self.storage.register_tag(&mut t)?;
            Ok((native_tag_name_to_human(&native_name).into(), true))
        }
    }

    pub(crate) fn register_tags(&self, tags: &str, usn: Usn, clear_first: bool) -> Result<bool> {
        let mut changed = false;
        if clear_first {
            self.storage.clear_tags()?;
        }
        for tag in split_tags(tags) {
            let t = self.register_tag(Tag {
                name: tag.to_string(),
                usn,
                ..Default::default()
            })?;
            changed |= t.1;
        }
        Ok(changed)
    }

    pub(crate) fn update_tag(&self, tag: &Tag) -> Result<()> {
        let native_name = human_tag_name_to_native(&tag.name);
        self.storage.update_tag(&Tag {
            id: tag.id,
            name: native_name,
            usn: tag.usn,
            config: tag.config.clone(),
        })
    }

    pub(crate) fn clear_tag(&self, tag: &str) -> Result<()> {
        let native_name = human_tag_name_to_native(tag);
        self.storage.clear_tag(&native_name)
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

        // note.tags is in human form
        note.tags = vec!["foo::bar".into()];
        col.update_note(&mut note)?;
        assert_eq!(&note.tags, &["foo::bar"]);

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

        Ok(())
    }
}
