// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use regex::Regex;

use crate::prelude::*;

impl Collection {
    pub fn complete_tag(&self, input: &str, limit: usize) -> Result<Vec<String>> {
        let filters: Vec<_> = input
            .split("::")
            .map(component_to_regex)
            .collect::<Result<_, _>>()?;
        let mut tags = vec![];
        let mut priority = vec![];
        self.storage.get_tags_by_predicate(|tag| {
            if priority.len() + tags.len() <= limit {
                match filters_match(&filters, tag) {
                    Some(true) => priority.push(tag.to_string()),
                    Some(_) => tags.push(tag.to_string()),
                    _ => {}
                }
            }
            // we only need the tag name
            false
        })?;
        priority.append(&mut tags);
        Ok(priority)
    }
}

fn component_to_regex(component: &str) -> Result<Regex> {
    Regex::new(&format!("(?i){}", regex::escape(component))).map_err(Into::into)
}

/// Returns None if tag wasn't a match, otherwise whether it was a consecutive prefix match
fn filters_match(filters: &[Regex], tag: &str) -> Option<bool> {
    let mut remaining_tag_components = tag.split("::");
    let mut is_prefix = true;
    'outer: for filter in filters {
        loop {
            if let Some(component) = remaining_tag_components.next() {
                if let Some(m) = filter.find(component) {
                    is_prefix &= m.start() == 0;
                    continue 'outer;
                } else {
                    is_prefix = false;
                }
            } else {
                return None;
            }
        }
    }
    Some(is_prefix)
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn matching() -> Result<()> {
        let filters = &[component_to_regex("b")?];
        assert!(filters_match(filters, "ABC"));
        assert!(filters_match(filters, "ABC::def"));
        assert!(filters_match(filters, "def::abc"));
        assert!(!filters_match(filters, "def"));

        let filters = &[component_to_regex("b")?, component_to_regex("E")?];
        assert!(!filters_match(filters, "ABC"));
        assert!(filters_match(filters, "ABC::def"));
        assert!(!filters_match(filters, "def::abc"));
        assert!(!filters_match(filters, "def"));

        let filters = &[
            component_to_regex("a")?,
            component_to_regex("c")?,
            component_to_regex("e")?,
        ];
        assert!(!filters_match(filters, "ace"));
        assert!(!filters_match(filters, "a::c"));
        assert!(!filters_match(filters, "c::e"));
        assert!(filters_match(filters, "a::c::e"));
        assert!(filters_match(filters, "a::b::c::d::e"));
        assert!(filters_match(filters, "1::a::b::c::d::e::f"));

        Ok(())
    }
}
