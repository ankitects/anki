// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use regex::{Captures, Regex};
use std::{borrow::Cow, collections::HashSet};

use super::{join_tags, split_tags};
use crate::prelude::*;
pub(crate) struct PrefixReplacer {
    regex: Regex,
    seen_tags: HashSet<String>,
}

/// Helper to match any of the provided space-separated tags in a space-
/// separated list of tags, and replace the prefix.
///
/// Tracks seen tags during replacement, so the tag list can be updated as well.
impl PrefixReplacer {
    pub fn new(space_separated_tags: &str) -> Result<Self> {
        // convert "fo*o bar" into "fo\*o|bar"
        let tags: Vec<_> = split_tags(space_separated_tags)
            .map(regex::escape)
            .collect();
        let tags = tags.join("|");

        let regex = Regex::new(&format!(
            r#"(?ix)
            # start of string, or a space
            (?:^|\ )
            # 1: the tag prefix
            (
                {}
            )
            (?:
                # 2: an optional child separator
                (::)
                # or a space/end of string the end of the string
                |\ |$
            )
        "#,
            tags
        ))?;

        Ok(Self {
            regex,
            seen_tags: HashSet::new(),
        })
    }

    pub fn is_match(&self, space_separated_tags: &str) -> bool {
        self.regex.is_match(space_separated_tags)
    }

    pub fn replace(&mut self, space_separated_tags: &str, replacement: &str) -> String {
        let tags: Vec<_> = split_tags(space_separated_tags)
            .map(|tag| {
                self.regex
                    .replace(tag, |caps: &Captures| {
                        // if we captured the child separator, add it to the replacement
                        if caps.get(2).is_some() {
                            Cow::Owned(format!("{}::", replacement))
                        } else {
                            Cow::Borrowed(replacement)
                        }
                    })
                    .to_string()
            })
            .collect();

        for tag in &tags {
            // sadly HashSet doesn't have an entry API at the moment
            if !self.seen_tags.contains(tag) {
                self.seen_tags.insert(tag.clone());
            }
        }

        join_tags(tags.as_slice())
    }

    pub fn into_seen_tags(self) -> HashSet<String> {
        self.seen_tags
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn regex() -> Result<()> {
        let re = PrefixReplacer::new("one two")?;
        assert_eq!(re.is_match(" foo "), false);
        assert_eq!(re.is_match(" foo one "), true);
        assert_eq!(re.is_match(" two foo "), true);

        let mut re = PrefixReplacer::new("foo")?;
        assert_eq!(re.is_match("foo"), true);
        assert_eq!(re.is_match(" foo "), true);
        assert_eq!(re.is_match(" bar foo baz "), true);
        assert_eq!(re.is_match(" bar FOO baz "), true);
        assert_eq!(re.is_match(" bar foof baz "), false);
        assert_eq!(re.is_match(" barfoo "), false);

        let mut as_xxx = |text| re.replace(text, "xxx");

        assert_eq!(&as_xxx(" baz FOO "), " baz xxx ");
        assert_eq!(&as_xxx(" x foo::bar x "), " x xxx::bar x ");
        assert_eq!(
            &as_xxx(" x foo::bar bar::foo x "),
            " x xxx::bar bar::foo x "
        );
        assert_eq!(
            &as_xxx(" x foo::bar foo::bar::baz x "),
            " x xxx::bar xxx::bar::baz x "
        );

        Ok(())
    }
}
