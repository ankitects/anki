// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::collection::Collection;
use crate::err::Result;
use crate::types::Usn;
use std::{borrow::Cow, collections::HashSet};
use unicase::UniCase;

fn split_tags(tags: &str) -> impl Iterator<Item = &str> {
    tags.split(|c| c == ' ' || c == '\u{3000}')
        .filter(|tag| !tag.is_empty())
}

impl Collection {
    /// Given a space-separated list of tags, fix case, ordering and duplicates.
    /// Returns true if any new tags were added.
    pub(crate) fn canonify_tags(&self, tags: &str, usn: Usn) -> Result<(String, bool)> {
        let mut tagset = HashSet::new();
        let mut added = false;

        for tag in split_tags(tags) {
            let tag = self.register_tag(tag, usn)?;
            if matches!(tag, Cow::Borrowed(_)) {
                added = true;
            }
            tagset.insert(UniCase::new(tag));
        }

        if tagset.is_empty() {
            return Ok(("".into(), added));
        }

        let mut tags = tagset.into_iter().collect::<Vec<_>>();
        tags.sort_unstable();

        let tags: Vec<_> = tags.into_iter().map(|s| s.into_inner()).collect();

        Ok((format!(" {} ", tags.join(" ")), added))
    }

    pub(crate) fn register_tag<'a>(&self, tag: &'a str, usn: Usn) -> Result<Cow<'a, str>> {
        if let Some(preferred) = self.storage.preferred_tag_case(tag)? {
            Ok(preferred.into())
        } else {
            self.storage.register_tag(tag, usn)?;
            Ok(tag.into())
        }
    }

    pub(crate) fn register_tags(&self, tags: &str, usn: Usn, clear_first: bool) -> Result<bool> {
        let mut changed = false;
        if clear_first {
            self.storage.clear_tags()?;
        }
        for tag in split_tags(tags) {
            let tag = self.register_tag(tag, usn)?;
            if matches!(tag, Cow::Borrowed(_)) {
                changed = true;
            }
        }
        Ok(changed)
    }
}
