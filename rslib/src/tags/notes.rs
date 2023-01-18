// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use unicase::UniCase;

use super::split_tags;
use crate::prelude::*;
use crate::search::SearchNode;

impl Collection {
    pub(crate) fn all_tags_in_deck(&mut self, deck_id: DeckId) -> Result<HashSet<UniCase<String>>> {
        let guard = self.search_notes_into_table(SearchNode::DeckIdWithChildren(deck_id))?;
        let mut all_tags: HashSet<UniCase<String>> = HashSet::new();
        guard
            .col
            .storage
            .for_each_note_tag_in_searched_notes(|tags| {
                for tag in split_tags(tags) {
                    // A benchmark on a large deck indicates that nothing is gained by using a Cow
                    // and skipping an allocation in the duplicate case, and
                    // this approach is simpler.
                    all_tags.insert(UniCase::new(tag.to_string()));
                }
            })?;
        Ok(all_tags)
    }
}
