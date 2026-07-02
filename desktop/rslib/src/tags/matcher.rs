// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashSet;

use regex::Captures;
use regex::Regex;

use super::join_tags;
use super::split_tags;
use crate::prelude::*;
pub(crate) struct TagMatcher {
    regex: Regex,
    new_tags: HashSet<String>,
}

/// Helper to match any of the provided space-separated tags in a space-
/// separated list of tags, and replace the prefix.
///
/// Tracks seen tags during replacement, so the tag list can be updated as well.
impl TagMatcher {
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
                {tags}
            )
            (?:
                # 2: an optional child separator
                (::)
                # or a space/end of string the end of the string
                |\ |$
            )
        "#
        ))?;

        Ok(Self {
            regex,
            new_tags: HashSet::new(),
        })
    }

    pub fn is_match(&self, space_separated_tags: &str) -> bool {
        self.regex.is_match(space_separated_tags)
    }

    pub fn replace(&mut self, space_separated_tags: &str, replacement: &str) -> String {
        let tags: Vec<_> = split_tags(space_separated_tags)
            .map(|tag| {
                let out = self.regex.replace(tag, |caps: &Captures| {
                    // if we captured the child separator, add it to the replacement
                    if caps.get(2).is_some() {
                        Cow::Owned(format!("{replacement}::"))
                    } else {
                        Cow::Borrowed(replacement)
                    }
                });
                if let Cow::Owned(out) = out {
                    if !self.new_tags.contains(&out) {
                        self.new_tags.insert(out.clone());
                    }
                    out
                } else {
                    out.to_string()
                }
            })
            .collect();

        join_tags(tags.as_slice())
    }

    /// The `replacement` function should return the text to use as a
    /// replacement.
    pub fn replace_with_fn<F>(&mut self, space_separated_tags: &str, replacer: F) -> String
    where
        F: Fn(&str) -> String,
    {
        let tags: Vec<_> = split_tags(space_separated_tags)
            .map(|tag| {
                let out = self.regex.replace(tag, |caps: &Captures| {
                    let replacement = replacer(caps.get(1).unwrap().as_str());
                    // if we captured the child separator, add it to the replacement
                    if caps.get(2).is_some() {
                        format!("{replacement}::")
                    } else {
                        replacement
                    }
                });
                if let Cow::Owned(out) = out {
                    if !self.new_tags.contains(&out) {
                        self.new_tags.insert(out.clone());
                    }
                    out
                } else {
                    out.to_string()
                }
            })
            .collect();

        join_tags(tags.as_slice())
    }

    /// Remove any matching tags. Does not update seen_tags.
    pub fn remove(&mut self, space_separated_tags: &str) -> String {
        let tags: Vec<_> = split_tags(space_separated_tags)
            .filter(|&tag| !self.is_match(tag))
            .map(ToString::to_string)
            .collect();

        join_tags(tags.as_slice())
    }

    /// Returns all replaced values that were used, so they can be registered
    /// into the tag list.
    pub fn into_new_tags(self) -> HashSet<String> {
        self.new_tags
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn regex() -> Result<()> {
        let re = TagMatcher::new("one two")?;
        assert!(!re.is_match(" foo "));
        assert!(re.is_match(" foo one "));
        assert!(re.is_match(" two foo "));

        let mut re = TagMatcher::new("foo")?;
        assert!(re.is_match("foo"));
        assert!(re.is_match(" foo "));
        assert!(re.is_match(" bar foo baz "));
        assert!(re.is_match(" bar FOO baz "));
        assert!(!re.is_match(" bar foof baz "));
        assert!(!re.is_match(" barfoo "));

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
