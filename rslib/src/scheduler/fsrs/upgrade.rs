// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::FSRS;

use super::memory_state::ComputeMemoryProgress;
use super::repair::migration_history_cutoff;
use super::repair::set_preset_retention_and_decay;
use crate::card::CardType;
use crate::prelude::*;
use crate::search::SearchNode;
use crate::storage::comma_separated_ids;

/// Offline replay must leave already-synced cards clean: an older client may
/// have answered one within the same modification-timestamp second.
#[derive(Clone, Copy)]
pub(crate) enum ReplaySync {
    Preserve,
    Upload,
}

impl ReplaySync {
    pub(super) fn card_usn(self, original: Usn, pending: Usn) -> Usn {
        match self {
            Self::Preserve => original,
            Self::Upload => pending,
        }
    }
}

impl Collection {
    pub(crate) fn refresh_fsrs_states(&mut self, sync: ReplaySync) -> Result<()> {
        self.upgrade_empty_fsrs_presets(sync)?;
        self.repair_incomplete_fsrs7_states(sync)?;
        Ok(())
    }
    /// Empty presets follow the current defaults. Upgrade their cards and pin
    /// the chosen defaults atomically, before exposing them to R consumers.
    /// Explicit legacy parameter arrays continue to select their old model.
    pub(crate) fn upgrade_empty_fsrs_presets(&mut self, sync: ReplaySync) -> Result<()> {
        if self.server {
            return Ok(());
        }
        let mut configs: Vec<_> = self
            .storage
            .all_deck_config()?
            .into_iter()
            .filter(DeckConfig::has_empty_fsrs_params)
            .filter_map(|config| migration_history_cutoff(&config).map(|cutoff| (config, cutoff)))
            .collect();
        if configs.is_empty() {
            return Ok(());
        }
        configs.sort_by_key(|(config, _)| config.id);
        self.transact_no_undo_after_sync(|col| {
            let decks = col.storage.get_decks_map()?;
            let usn = col.usn()?;
            let fsrs_enabled = col.get_config_bool(BoolKey::Fsrs);
            let should_upgrade = |card: &Card| {
                card.ctype != CardType::New && (fsrs_enabled || card.memory_state.is_some())
            };
            let fsrs = FSRS::new(&[])?;
            let mut progress = col.new_progress_handler::<ComputeMemoryProgress>();
            for (mut config, cutoff) in configs {
                let deck_ids: Vec<_> = decks
                    .values()
                    .filter(|deck| deck.config_id() == Some(config.id))
                    .map(|deck| deck.id)
                    .collect();
                if !deck_ids.is_empty() {
                    let search = SearchNode::DeckIdsWithoutChildren(comma_separated_ids(&deck_ids));
                    let cards = col.all_cards_for_search(search.clone())?;
                    progress.update(false, |p| p.total_cards += cards.len() as u32)?;
                    let mut states = col.replay_fsrs_states(
                        &fsrs,
                        search,
                        cards
                            .iter()
                            .filter(|card| should_upgrade(card))
                            .map(|card| card.id),
                        &config,
                        cutoff,
                        &mut progress,
                    )?;
                    for mut card in cards {
                        if should_upgrade(&card) {
                            let original = card.clone();
                            if let Some(state) = states.remove(&card.id) {
                                card.memory_state = Some(state);
                            } else {
                                card.set_memory_state(
                                    &fsrs,
                                    None,
                                    config.inner.historical_retention,
                                )?;
                            }
                            set_preset_retention_and_decay(
                                &mut card,
                                &decks,
                                &config,
                                &fsrs::DEFAULT_PARAMETERS,
                            );
                            card.usn = sync.card_usn(original.usn, usn);
                            col.update_card_undoable(&mut card, original)?;
                        }
                        progress.update(true, |p| p.current_cards += 1)?;
                    }
                }
                let original = config.clone();
                config.inner.fsrs_params_7 = fsrs::DEFAULT_PARAMETERS.to_vec();
                config.usn = usn;
                col.update_deck_config_inner(&mut config, original, None)?;
                progress.check_cancelled()?;
            }
            Ok(())
        })
    }
}

#[cfg(test)]
mod tests {
    use anki_io::new_tempfile;
    use fsrs::FSRSItem;
    use fsrs::FSRSReview;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::FsrsMemoryState;
    use crate::collection::CollectionBuilder;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::tests::DeckAdder;
    use crate::tests::NoteAdder;

    fn empty_default_preset(col: &mut Collection) -> Result<()> {
        let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        config.clear_fsrs_params();
        col.add_or_update_deck_config(&mut config)
    }

    fn legacy_card(col: &mut Collection, deck: DeckId) -> Result<Card> {
        let note = NoteAdder::basic(col).deck(deck).add(col);
        let mut card = col.storage.all_cards_of_note(note.id)?.remove(0);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = 100;
        card.interval = 20;
        card.reps = 2;
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.usn = Usn(0);
        col.storage.update_card(&card)?;
        Ok(card)
    }

    #[test]
    fn opening_upgrades_empty_presets_and_replays_fractional_history_without_rescheduling(
    ) -> Result<()> {
        let file = new_tempfile()?;
        let mut col = CollectionBuilder::new(file.path()).build()?;
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        empty_default_preset(&mut col)?;
        let card = legacy_card(&mut col, DeckId(1))?;
        for (index, seconds) in [1_700_000_000i64, 1_700_000_600].into_iter().enumerate() {
            col.storage.add_revlog_entry(
                &RevlogEntry {
                    id: RevlogId(seconds * 1000),
                    cid: card.id,
                    button_chosen: 3,
                    interval: if index == 0 { -600 } else { 20 },
                    review_kind: RevlogReviewKind::Learning,
                    ..Default::default()
                },
                false,
            )?;
        }
        col.close(None)?;

        let col = CollectionBuilder::new(file.path()).build()?;
        let upgraded = col.storage.get_card(card.id)?.unwrap();
        let config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        assert_eq!(config.inner.fsrs_params_7, fsrs::DEFAULT_PARAMETERS);
        assert_eq!(config.usn, Usn(-1));
        assert_eq!(
            (upgraded.due, upgraded.interval, upgraded.queue),
            (100, 20, CardQueue::Review)
        );
        let model = FSRS::new(&[])?;
        let expected = model.memory_state(
            FSRSItem {
                reviews: vec![
                    FSRSReview {
                        delta_t: 0.0,
                        rating: 3,
                    },
                    FSRSReview {
                        delta_t: 600.0 / 86_400.0,
                        rating: 3,
                    },
                ],
            },
            None,
        )?;
        let state = upgraded.memory_state.unwrap();
        assert!((state.stability_internal - expected.stability).abs() < 0.0001);
        assert!((state.stability_fast.unwrap() - expected.stability_fast).abs() < 0.0001);
        assert!((state.stability - model.s90(expected)).abs() < 0.0001);
        assert!(
            (model.current_retrievability(state.into(), 10.0)
                - model.current_retrievability(expected, 10.0))
            .abs()
                < 0.0001
        );
        assert_eq!(upgraded.usn, card.usn);
        assert_eq!(upgraded.mtime, card.mtime);
        let modified = col.storage.get_collection_timestamps()?.collection_change;
        col.close(None)?;

        let col = CollectionBuilder::new(file.path()).build()?;
        assert_eq!(col.storage.get_card(card.id)?.unwrap(), upgraded);
        assert_eq!(
            col.storage.get_collection_timestamps()?.collection_change,
            modified
        );
        Ok(())
    }

    #[test]
    fn upgrade_handles_filtered_cards_without_history_and_preserves_explicit_models() -> Result<()>
    {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        empty_default_preset(&mut col)?;
        let filtered = DeckAdder::new("Filtered").filtered(true).add(&mut col);
        let mut card = legacy_card(&mut col, DeckId(1))?;
        card.original_deck_id = card.deck_id;
        card.original_due = card.due;
        card.deck_id = filtered.id;
        card.due = -1;
        card.queue = CardQueue::Suspended;
        col.storage.update_card(&card)?;
        let explicit = DeckAdder::new("Explicit FSRS6")
            .with_config(|config| {
                config.inner.fsrs_params_6 = fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec()
            })
            .add(&mut col);
        let explicit_card = legacy_card(&mut col, explicit.id)?;
        let explicit_config = col
            .get_deck_config(explicit.config_id().unwrap(), false)?
            .unwrap();
        let new_note = NoteAdder::basic(&mut col).add(&mut col);
        let new_card = col.storage.all_cards_of_note(new_note.id)?.remove(0);

        col.upgrade_empty_fsrs_presets(ReplaySync::Preserve)?;

        let upgraded = col.storage.get_card(card.id)?.unwrap();
        assert_eq!(
            (
                upgraded.deck_id,
                upgraded.original_deck_id,
                upgraded.due,
                upgraded.original_due,
                upgraded.interval,
                upgraded.queue
            ),
            (filtered.id, DeckId(1), -1, 100, 20, CardQueue::Suspended)
        );
        let model = FSRS::new(&[])?;
        let expected = model.memory_state_from_sm2(card.ease_factor(), 20.0, 0.9)?;
        let state = upgraded.memory_state.unwrap();
        assert!((state.stability_internal - expected.stability).abs() < 0.0001);
        assert!((state.stability_fast.unwrap() - expected.stability_fast).abs() < 0.0001);
        assert_eq!(
            col.storage.get_card(explicit_card.id)?.unwrap(),
            explicit_card
        );
        assert_eq!(
            col.get_deck_config(explicit_config.id, false)?.unwrap(),
            explicit_config
        );
        assert_eq!(col.storage.get_card(new_card.id)?.unwrap(), new_card);
        Ok(())
    }

    #[test]
    fn invalid_cutoff_defers_only_its_preset_until_corrected() -> Result<()> {
        let file = new_tempfile()?;
        let mut col = CollectionBuilder::new(file.path()).build()?;
        empty_default_preset(&mut col)?;
        let invalid = DeckAdder::new("Invalid cutoff")
            .with_config(|config| {
                config.name = "Invalid cutoff".into();
                config.inner.ignore_revlogs_before_date = "2025-02-30".into();
            })
            .add(&mut col)
            .config_id()
            .unwrap();
        col.close(None)?;
        let mut col = CollectionBuilder::new(file.path()).build()?;
        assert_eq!(
            col.get_deck_config(DeckConfigId(1), false)?
                .unwrap()
                .inner
                .fsrs_params_7,
            fsrs::DEFAULT_PARAMETERS
        );
        let mut config = col.get_deck_config(invalid, false)?.unwrap();
        assert!(config.has_empty_fsrs_params());
        let diagnostics = col.check_database()?.to_i18n_strings(&col.tr);
        assert!(diagnostics
            .iter()
            .any(|message| message.contains("Invalid cutoff")));
        config.inner.ignore_revlogs_before_date.clear();
        col.add_or_update_deck_config(&mut config)?;
        col.upgrade_empty_fsrs_presets(ReplaySync::Preserve)?;
        assert_eq!(
            col.get_deck_config(invalid, false)?
                .unwrap()
                .inner
                .fsrs_params_7,
            fsrs::DEFAULT_PARAMETERS
        );
        Ok(())
    }

    #[test]
    fn upgrade_remains_pending_when_the_sync_server_clock_is_ahead() -> Result<()> {
        let mut col = Collection::new();
        empty_default_preset(&mut col)?;
        let server_time = TimestampMillis(TimestampMillis::now().0 + 60_000);
        col.storage.set_last_sync(server_time)?;
        col.storage.set_modified_time(server_time)?;

        col.upgrade_empty_fsrs_presets(ReplaySync::Preserve)?;

        assert!(col
            .storage
            .get_collection_timestamps()?
            .collection_changed_since_sync());
        assert_eq!(
            col.sync_status_offline()?,
            anki_proto::sync::sync_status_response::Required::NormalSync
        );
        Ok(())
    }

    #[test]
    fn server_and_sync_validation_preserve_empty_presets() -> Result<()> {
        let file = new_tempfile()?;
        let mut col = CollectionBuilder::new(file.path()).build()?;
        empty_default_preset(&mut col)?;
        col.close(None)?;
        for server in [false, true] {
            let mut builder = CollectionBuilder::new(file.path());
            if server {
                builder.set_server(true);
            } else {
                builder.set_skip_fsrs_defaults_upgrade();
            }
            let col = builder.build()?;
            assert!(col
                .get_deck_config(DeckConfigId(1), false)?
                .unwrap()
                .has_empty_fsrs_params());
            col.close(None)?;
        }
        Ok(())
    }
}
