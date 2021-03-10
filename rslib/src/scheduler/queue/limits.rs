// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{Deck, DeckKind};
use crate::deckconf::{DeckConf, DeckConfID};
use std::collections::HashMap;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct RemainingLimits {
    pub review: u32,
    pub new: u32,
}

impl RemainingLimits {
    pub(crate) fn new(deck: &Deck, config: Option<&DeckConf>, today: u32) -> Self {
        if let Some(config) = config {
            let (new_today, rev_today) = deck.new_rev_counts(today);
            RemainingLimits {
                review: ((config.inner.reviews_per_day as i32) - rev_today).max(0) as u32,
                new: ((config.inner.new_per_day as i32) - new_today).max(0) as u32,
            }
        } else {
            RemainingLimits {
                review: std::u32::MAX,
                new: std::u32::MAX,
            }
        }
    }

    fn limit_to_parent(&mut self, parent: RemainingLimits) {
        self.review = self.review.min(parent.review);
        self.new = self.new.min(parent.new);
    }
}

pub(super) fn remaining_limits_capped_to_parents(
    decks: &[Deck],
    config: &HashMap<DeckConfID, DeckConf>,
    today: u32,
) -> Vec<RemainingLimits> {
    let mut limits = get_remaining_limits(decks, config, today);
    cap_limits_to_parents(decks.iter().map(|d| d.name.as_str()), &mut limits);
    limits
}

/// Return the remaining limits for each of the provided decks, in
/// the provided deck order.
fn get_remaining_limits(
    decks: &[Deck],
    config: &HashMap<DeckConfID, DeckConf>,
    today: u32,
) -> Vec<RemainingLimits> {
    decks
        .iter()
        .map(move |deck| {
            // get deck config if not filtered
            let config = if let DeckKind::Normal(normal) = &deck.kind {
                config.get(&DeckConfID(normal.config_id))
            } else {
                None
            };
            RemainingLimits::new(deck, config, today)
        })
        .collect()
}

/// Given a sorted list of deck names and their current limits,
/// cap child limits to their parents.
fn cap_limits_to_parents<'a>(
    names: impl IntoIterator<Item = &'a str>,
    limits: &'a mut Vec<RemainingLimits>,
) {
    let mut parent_limits = vec![];
    let mut last_limit = None;
    let mut last_level = 0;

    names
        .into_iter()
        .zip(limits.iter_mut())
        .for_each(|(name, limits)| {
            let level = name.matches('\x1f').count() + 1;
            if last_limit.is_none() {
                // top-level deck
                last_limit = Some(*limits);
                last_level = level;
            } else {
                // add/remove parent limits if descending/ascending
                let mut target = level;
                while target != last_level {
                    if target < last_level {
                        // current deck is at higher level than previous
                        parent_limits.pop();
                        target += 1;
                    } else {
                        // current deck is at a lower level than previous. this
                        // will push the same remaining counts multiple times if
                        // the deck tree is missing a parent
                        parent_limits.push(last_limit.unwrap());
                        target -= 1;
                    }
                }

                // apply current parent limit
                limits.limit_to_parent(*parent_limits.last().unwrap());
                last_level = level;
                last_limit = Some(*limits);
            }
        })
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn limits() {
        let limits_map = vec![
            (
                "A",
                RemainingLimits {
                    review: 100,
                    new: 20,
                },
            ),
            (
                "A\x1fB",
                RemainingLimits {
                    review: 50,
                    new: 30,
                },
            ),
            (
                "A\x1fC",
                RemainingLimits {
                    review: 10,
                    new: 10,
                },
            ),
            ("A\x1fC\x1fD", RemainingLimits { review: 5, new: 30 }),
            (
                "A\x1fE",
                RemainingLimits {
                    review: 200,
                    new: 100,
                },
            ),
        ];
        let (names, mut limits): (Vec<_>, Vec<_>) = limits_map.into_iter().unzip();
        cap_limits_to_parents(names.into_iter(), &mut limits);
        assert_eq!(
            &limits,
            &[
                RemainingLimits {
                    review: 100,
                    new: 20
                },
                RemainingLimits {
                    review: 50,
                    new: 20
                },
                RemainingLimits {
                    review: 10,
                    new: 10
                },
                RemainingLimits { review: 5, new: 10 },
                RemainingLimits {
                    review: 100,
                    new: 20
                }
            ]
        );

        // missing parents should not break it
        let limits_map = vec![
            (
                "A",
                RemainingLimits {
                    review: 100,
                    new: 20,
                },
            ),
            (
                "A\x1fB\x1fC\x1fD",
                RemainingLimits {
                    review: 50,
                    new: 30,
                },
            ),
            (
                "A\x1fC",
                RemainingLimits {
                    review: 100,
                    new: 100,
                },
            ),
        ];

        let (names, mut limits): (Vec<_>, Vec<_>) = limits_map.into_iter().unzip();
        cap_limits_to_parents(names.into_iter(), &mut limits);
        assert_eq!(
            &limits,
            &[
                RemainingLimits {
                    review: 100,
                    new: 20
                },
                RemainingLimits {
                    review: 50,
                    new: 20
                },
                RemainingLimits {
                    review: 100,
                    new: 20
                },
            ]
        );
    }
}
