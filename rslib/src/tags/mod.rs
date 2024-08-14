// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod bulkadd;
mod complete;
mod findreplace;
mod matcher;
mod notes;
mod register;
mod remove;
mod rename;
mod reparent;
mod service;
mod tree;
pub(crate) mod undo;

use unicase::UniCase;

use crate::prelude::*;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Tag {
    pub name: String,
    pub usn: Usn,
    pub expanded: bool,
}

impl Tag {
    pub fn new(name: String, usn: Usn) -> Self {
        Tag {
            name,
            usn,
            expanded: false,
        }
    }

    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.usn = usn;
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

pub(crate) fn immediate_parent_name_unicase(tag_name: UniCase<&str>) -> Option<UniCase<&str>> {
    tag_name.rsplit_once("::").map(|t| t.0).map(UniCase::new)
}

fn immediate_parent_name_str(tag_name: &str) -> Option<&str> {
    tag_name.rsplit_once("::").map(|t| t.0)
}
