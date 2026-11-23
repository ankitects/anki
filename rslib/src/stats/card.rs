// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::fsrs::memory_state::fsrs_current_retrievability_for_state;
use crate::scheduler::fsrs::memory_state::fsrs_item_for_memory_state;
use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_params;
use crate::scheduler::timing::is_unix_epoch_timestamp;

impl Collection {
    pub fn card_stats(&mut self, cid: CardId) -> Result<anki_proto::stats::CardStatsResponse> {
        let mut card = self.storage.get_card(cid)?.or_not_found(cid)?;
        let note = self
            .storage
            .get_note(card.note_id)?
            .or_not_found(card.note_id)?;
        let nt = self
            .get_notetype(note.notetype_id)?
            .or_not_found(note.notetype_id)?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .or_not_found(card.deck_id)?;
        let revlog = self.storage.get_revlog_entries_for_card(card.id)?;

        let (average_secs, total_secs) = average_and_total_secs_strings(&revlog);
        let timing = self.timing_today()?;
        let fsrs_enabled = self.fsrs_enabled();

        let last_review_time = if let Some(last_review_time) = card.last_review_time {
            last_review_time
        } else {
            let mut new_card = card.clone();
            let last_review_time = self
                .storage
                .time_of_last_review(card.id)?
                .unwrap_or_default();

            new_card.last_review_time = Some(last_review_time);

            self.storage.update_card(&new_card)?;
            last_review_time
        };

        let seconds_elapsed = timing.now.elapsed_secs_since_clamped(last_review_time);

        let original_deck = if card.original_deck_id == DeckId(0) {
            deck.clone()
        } else {
            self.storage
                .get_deck(card.original_deck_id)?
                .or_not_found(card.original_deck_id)?
        };
        if fsrs_enabled && card.ctype != CardType::New && card.memory_state.is_none() {
            let computed = self.compute_memory_state(card.id)?;
            card.memory_state = computed.state.map(Into::into);
            card.desired_retention = Some(computed.desired_retention);
            card.decay = Some(computed.decay);
        }

        let fsrs_preset = self.fsrs_preset_for_card(&card)?;

        let fsrs_retrievability =
            card.memory_state
                .zip(Some(seconds_elapsed))
                .map(|(state, seconds)| {
                    fsrs_current_retrievability_for_state(
                        &fsrs_preset.params,
                        state,
                        seconds as f32 / 86_400.0,
                    )
                });
        Ok(anki_proto::stats::CardStatsResponse {
            card_id: card.id.into(),
            note_id: card.note_id.into(),
            deck: deck.human_name(),
            added: card.id.as_secs().0,
            first_review: revlog
                .iter()
                .find(|entry| entry.has_rating())
                .map(|entry| entry.id.as_secs().0),
            // last_review_time is not used to ensure cram revlogs are included.
            latest_review: revlog
                .iter()
                .rfind(|entry| entry.has_rating())
                .map(|entry| entry.id.as_secs().0),
            due_date: self.due_date(&card)?,
            due_position: self.position(&card),
            interval: card.interval,
            ease: card.ease_factor as u32,
            reviews: card.reps,
            lapses: card.lapses,
            average_secs,
            total_secs,
            card_type: nt.get_template(card.template_idx)?.name.clone(),
            notetype: nt.name.clone(),
            revlog: self.stats_revlog_entries_with_memory_state(&card, last_review_time, revlog)?,
            memory_state: card.memory_state.map(Into::into),
            fsrs_retrievability: fsrs_retrievability.transpose()?,
            custom_data: card.custom_data,
            fsrs_params: fsrs_preset.params,
            preset: fsrs_preset.name,
            original_deck: if original_deck != deck {
                Some(original_deck.human_name())
            } else {
                None
            },
            desired_retention: card.desired_retention,
            fsrs_enabled,
            extra_rows: vec![],
            rwkv_retry_pending: false,
        })
    }

    pub fn get_review_logs(&mut self, cid: CardId) -> Result<anki_proto::stats::ReviewLogs> {
        let revlogs = self.storage.get_revlog_entries_for_card(cid)?;
        Ok(anki_proto::stats::ReviewLogs {
            entries: revlogs.iter().rev().map(stats_revlog_entry).collect(),
        })
    }

    fn due_date(&mut self, card: &Card) -> Result<Option<i64>> {
        Ok(match card.ctype {
            CardType::New => None,
            CardType::Review | CardType::Learn | CardType::Relearn => {
                let due = if card.original_due != 0 {
                    card.original_due
                } else {
                    card.due
                };
                if !is_unix_epoch_timestamp(due) {
                    let days_remaining = due - (self.timing_today()?.days_elapsed as i32);
                    let mut due_timestamp = TimestampSecs::now();
                    due_timestamp.0 += (days_remaining as i64) * 86_400;
                    Some(due_timestamp.0)
                } else {
                    Some(due as i64)
                }
            }
        })
    }

    fn position(&mut self, card: &Card) -> Option<i32> {
        if let Some(original_pos) = card.original_position {
            return Some(original_pos as i32);
        }
        match card.ctype {
            CardType::New => Some(card.due),
            _ => None,
        }
    }

    fn stats_revlog_entries_with_memory_state(
        self: &mut Collection,
        card: &Card,
        last_review_time: TimestampSecs,
        revlog: Vec<RevlogEntry>,
    ) -> Result<Vec<anki_proto::stats::card_stats_response::StatsRevlogEntry>> {
        let fsrs_preset = self.fsrs_preset_for_card(card)?;
        let historical_retention = fsrs_preset.historical_retention;
        let params = &fsrs_preset.params;
        let fsrs = fsrs_preset.fsrs()?;
        let next_day_at = self.timing_today()?.next_day_at;
        let ignore_before = fsrs_preset.ignore_revlogs_before_ms()?;

        let mut result = Vec::new();
        if let Some(item) = fsrs_item_for_memory_state(
            &fsrs,
            params,
            revlog.clone(),
            next_day_at,
            historical_retention,
            ignore_before,
        )? {
            let memory_states = fsrs.historical_memory_states(item.item, item.starting_state)?;
            let mut revlog_index = 0;
            for entry in revlog {
                let mut stats_entry = stats_revlog_entry(&entry);
                let memory_state: Option<FsrsMemoryState> = if revlog_index >= memory_states.len() {
                    // The removed revlog is in the end of the revlog, so we use the last memory
                    // state
                    Some(fsrs_memory_state_for_params(
                        params,
                        memory_states[memory_states.len() - 1],
                    )?)
                } else if entry.id == item.filtered_revlogs[revlog_index].id {
                    revlog_index += 1;
                    Some(fsrs_memory_state_for_params(
                        params,
                        memory_states[revlog_index - 1],
                    )?)
                } else if revlog_index == 0 {
                    // The removed revlog is in the start of the revlog, so we don't have a memory
                    // state for it
                    None
                } else {
                    // The removed revlog is in the middle of the revlog, so we use the memory
                    // state for the previous revlog entry
                    Some(fsrs_memory_state_for_params(
                        params,
                        memory_states[revlog_index],
                    )?)
                };
                stats_entry.memory_state = memory_state.map(|s| s.into());
                result.push(stats_entry);
            }
            Ok(with_current_memory_state_on_latest_review(
                card.memory_state,
                last_review_time,
                result.into_iter().rev().collect(),
            ))
        } else {
            Ok(with_current_memory_state_on_latest_review(
                card.memory_state,
                last_review_time,
                revlog.iter().rev().map(stats_revlog_entry).collect(),
            ))
        }
    }
}

fn with_current_memory_state_on_latest_review(
    memory_state: Option<FsrsMemoryState>,
    last_review_time: TimestampSecs,
    mut entries: Vec<anki_proto::stats::card_stats_response::StatsRevlogEntry>,
) -> Vec<anki_proto::stats::card_stats_response::StatsRevlogEntry> {
    if let Some(memory_state) = memory_state {
        if let Some(entry) = entries
            .iter_mut()
            .find(|entry| entry.button_chosen > 0 && entry.time == last_review_time.0)
        {
            entry.memory_state = Some(memory_state.into());
        }
    }
    entries
}

fn average_and_total_secs_strings(revlog: &[RevlogEntry]) -> (f32, f32) {
    let normal_answer_count = revlog.iter().filter(|r| r.has_rating()).count();
    let total_secs: f32 = revlog
        .iter()
        .map(|entry| (entry.taken_millis as f32) / 1000.0)
        .sum();
    if normal_answer_count == 0 || total_secs == 0.0 {
        (0.0, 0.0)
    } else {
        (total_secs / normal_answer_count as f32, total_secs)
    }
}

fn stats_revlog_entry(
    entry: &RevlogEntry,
) -> anki_proto::stats::card_stats_response::StatsRevlogEntry {
    anki_proto::stats::card_stats_response::StatsRevlogEntry {
        time: entry.id.as_secs().0,
        review_kind: entry.review_kind.into(),
        button_chosen: entry.button_chosen as u32,
        interval: entry.interval_secs(),
        ease: entry.ease_factor,
        taken_secs: entry.taken_millis as f32 / 1000.,
        memory_state: None,
        last_interval: entry.last_interval_secs(),
    }
}

#[cfg(test)]
mod test {
    use anki_proto::deck_config::deck_configs_for_update::current_deck::Limits;
    use anki_proto::deck_config::UpdateDeckConfigsMode;

    use super::*;
    use crate::card::FsrsMemoryState;
    use crate::deckconfig::FsrsVersion;
    use crate::deckconfig::UpdateDeckConfigsRequest;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::scheduler::fsrs::memory_state::fsrs_current_retrievability_for_state;
    use crate::scheduler::fsrs::preset::AddonFsrsPreset;
    use crate::scheduler::fsrs::preset::AddonFsrsVersion;
    use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
    use crate::scheduler::fsrs::preset::FsrsPresetRule;
    use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;
    use crate::search::SortMode;

    fn fsrs7_params_for_retrievability_test() -> Vec<f32> {
        vec![
            0.4843, 3.0562, 10.9946, 32.7202, 5.6296, 0.5900, 3.1230, 2.4679, 0.2733, 1.4895,
            0.4868, 0.0010, 0.8082, 0.1723, 0.6389, 1.5767, 0.8918, 0.3341, 3.5942, 0.3455, 0.0022,
            0.2834, 2.6418, 0.5604, 1.3042, 2.5054, 0.9376, 0.0611, 0.0830, 0.6339, 0.9846, 0.2485,
            0.6014, 0.0545,
        ]
    }

    fn set_selected_fsrs7_params(col: &mut Collection, params: Vec<f32>) -> Result<()> {
        let output = col.get_deck_configs_for_update(DeckId(1))?;
        let mut input = UpdateDeckConfigsRequest {
            target_deck_id: DeckId(1),
            configs: output
                .all_config
                .into_iter()
                .map(|c| c.config.unwrap().into())
                .collect(),
            removed_config_ids: vec![],
            mode: UpdateDeckConfigsMode::Normal,
            card_state_customizer: String::new(),
            limits: Limits::default(),
            new_cards_ignore_review_limit: false,
            apply_all_parent_limits: false,
            fsrs: true,
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        input.configs[0].inner.fsrs_version = FsrsVersion::Seven as i32;
        input.configs[0].inner.fsrs_params_7 = params;
        col.update_deck_configs(input)?;
        Ok(())
    }

    fn test_collection() -> Result<(Collection, CardId)> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        Ok((col, cid))
    }

    #[test]
    fn stats() -> Result<()> {
        let (mut col, cid) = test_collection()?;
        let _report = col.card_stats(cid)?;

        Ok(())
    }

    #[test]
    fn stats_preserve_pending_review_with_missing_memory_state() -> Result<()> {
        use crate::scheduler::answering::CardAnswer;
        use crate::scheduler::answering::Rating;

        for fsrs_enabled in [false, true] {
            for skip_scheduling_states in [false, true] {
                let (mut col, cid) = test_collection()?;
                set_selected_fsrs7_params(&mut col, fsrs7_params_for_retrievability_test())?;
                col.answer_easy();
                let mut stored = col.storage.get_card(cid)?.unwrap();
                stored.clear_fsrs_data();
                stored.due = col.timing_today()?.days_elapsed as i32;
                col.storage.update_card(&stored)?;
                col.set_config_bool(BoolKey::Fsrs, fsrs_enabled, false)?;
                col.clear_study_queues();

                let queued = col.get_queued_cards(1, false, skip_scheduling_states)?;
                let queued = queued.cards.first().unwrap();
                assert_eq!(queued.card.id, cid);
                assert_eq!(queued.states.is_none(), skip_scheduling_states);
                assert_eq!(
                    queued.card.memory_state.is_some(),
                    fsrs_enabled && !skip_scheduling_states
                );
                let states = match queued.states.clone() {
                    Some(states) => states,
                    None => {
                        col.get_scheduling_states_with_desired_retention_override(cid, Some(0.95))?
                    }
                };

                let report = col.card_stats(cid)?;
                assert_eq!(report.memory_state.is_some(), fsrs_enabled);
                assert_eq!(report.fsrs_retrievability.is_some(), fsrs_enabled);
                assert_eq!(col.storage.get_card(cid)?.unwrap(), stored);

                col.answer_card(&mut CardAnswer {
                    card_id: cid,
                    current_state: states.current,
                    new_state: states.good,
                    rating: Rating::Good,
                    answered_at: TimestampMillis::now(),
                    milliseconds_taken: 0,
                    custom_data: None,
                    desired_retention_override: skip_scheduling_states.then_some(0.95),
                    desired_retention_override_is_final: false,
                    rwkv_s90: None,
                    rwkv_retrievability: None,
                    rwkv_review_kind: None,
                    from_queue: true,
                })?;
                let answered = col.storage.get_card(cid)?.unwrap();
                assert_eq!(answered.reps, stored.reps + 1);
                assert_eq!(answered.memory_state.is_some(), fsrs_enabled);
            }
        }
        Ok(())
    }

    #[test]
    fn stats_calculate_memory_state_if_not_present() -> Result<()> {
        let (mut col, cid) = test_collection()?;

        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        // review the card as easy
        col.grade_now(anki_proto::scheduler::GradeNowRequest {
            card_ids: vec![cid.into()],
            rating: anki_proto::scheduler::card_answer::Rating::Easy as i32,
            card_options: vec![],
        })?;
        let mut card = col.storage.get_card(cid)?.unwrap();
        assert!(card.memory_state.is_some());

        card.clear_fsrs_data();
        col.storage.update_card(&card)?;

        let card = col.storage.get_card(cid)?.unwrap();
        assert!(card.memory_state.is_none());

        let report = col.card_stats(cid)?;
        let card = col.storage.get_card(cid)?.unwrap();

        assert!(report.memory_state.is_some());
        // Don't modify the card. See https://github.com/ankitects/anki/issues/5635
        assert!(card.memory_state.is_none());

        // Toggle FSRS off
        let deck_configs = col.get_deck_configs_for_update(DeckId(1))?;
        col.update_deck_configs(UpdateDeckConfigsRequest {
            target_deck_id: DeckId(1),
            configs: deck_configs
                .all_config
                .into_iter()
                .map(|config| config.config.unwrap().into())
                .collect(),
            removed_config_ids: vec![],
            mode: UpdateDeckConfigsMode::Normal,
            card_state_customizer: String::new(),
            limits: Limits::default(),
            new_cards_ignore_review_limit: false,
            apply_all_parent_limits: false,
            fsrs: false,
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            review_fuzz_config: Default::default(),
            fsrs_reschedule: false,
            fsrs_health_check: true,
        })?;

        // Dont report memory_state while SM2 is enabled
        let report = col.card_stats(cid)?;
        assert!(report.memory_state.is_none());

        Ok(())
    }

    #[test]
    fn card_stats_retrievability_uses_selected_model_curve() -> Result<()> {
        let mut col = Collection::new();
        let params = fsrs7_params_for_retrievability_test();
        set_selected_fsrs7_params(&mut col, params.clone())?;

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let mut card = col.storage.get_card(cid)?.unwrap();
        let stability = 42.0;
        let elapsed_days = 120.0;
        let timing = col.timing_today()?;
        let state = FsrsMemoryState {
            stability,
            stability_internal: stability,
            stability_fast: Some(17.0),
            difficulty: 8.0,
        };
        card.memory_state = Some(state);
        card.last_review_time = Some(timing.now.adding_secs(-(elapsed_days as i64) * 86_400));
        card.decay = Some(params[23]);
        col.storage.update_card(&card)?;

        let report = col.card_stats(cid)?;
        let expected = fsrs_current_retrievability_for_state(&params, state, elapsed_days)?;
        assert_eq!(
            report.fsrs_retrievability.map(|v| format!("{v:.6}")),
            Some(format!("{expected:.6}"))
        );
        Ok(())
    }

    #[test]
    fn card_stats_uses_card_level_fsrs_preset() -> Result<()> {
        let mut col = Collection::new();
        let params = fsrs7_params_for_retrievability_test();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:card-info".into(),
                    name: "Card Info Dynamic".into(),
                    fsrs_version: AddonFsrsVersion::Seven,
                    params: params.clone(),
                    desired_retention: 0.81,
                    historical_retention: 0.9,
                    ignore_revlogs_before_date: String::new(),
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "deck:Default".into(),
                    preset_id: "addon:test:card-info".into(),
                }],
                simulator_rules: Vec::new(),
            },
        )?;

        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let report = col.card_stats(cid)?;
        assert_eq!(report.preset, "Card Info Dynamic");
        assert_eq!(report.fsrs_params, params);
        Ok(())
    }

    #[test]
    fn card_stats_latest_revlog_uses_current_memory_state() -> Result<()> {
        let mut col = Collection::new();
        let params = fsrs7_params_for_retrievability_test();
        set_selected_fsrs7_params(&mut col, params.clone())?;

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let last_review_time = TimestampSecs::now();
        let stability = 0.0101;
        let stability_internal = 0.0733;
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.memory_state = Some(FsrsMemoryState {
            stability,
            stability_internal,
            stability_fast: None,
            difficulty: 9.168,
        });
        card.last_review_time = Some(last_review_time);
        card.decay = Some(params[23]);
        col.storage.update_card(&card)?;
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(last_review_time.0 * 1000),
                cid,
                usn: Usn(0),
                button_chosen: 3,
                review_kind: RevlogReviewKind::Learning,
                ..Default::default()
            },
            false,
        )?;

        let report = col.card_stats(cid)?;
        let latest = report.revlog[0].memory_state.as_ref().unwrap();
        assert!((latest.stability - stability).abs() < 0.0001);
        assert!((latest.stability_internal.unwrap() - stability_internal).abs() < 0.0001);
        Ok(())
    }

    #[test]
    fn memory_metrics_preserve_order_missing_cards_and_null_state() -> Result<()> {
        let (mut col, cid) = test_collection()?;
        let before = col.storage.get_card(cid)?.unwrap();
        assert!(col.card_memory_metrics(&[], true)?.is_empty());
        let values = col.card_memory_metrics(&[cid, CardId(1), cid], true)?;
        assert_eq!(values.len(), 2);
        assert_eq!(values[0], values[1]);
        assert_eq!(values[0].card_id, cid.0);
        assert!(values[0].memory_state.is_none());
        assert!(values[0].desired_retention.is_none());
        assert!(values[0].fsrs_retrievability.is_none());
        assert_eq!(col.storage.get_card(cid)?.unwrap(), before);
        Ok(())
    }

    #[test]
    fn memory_metrics_match_full_stats_with_fsrs7_overlay_and_large_batch() -> Result<()> {
        let (mut col, cid) = test_collection()?;
        let params = fsrs7_params_for_retrievability_test();
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:metrics".into(),
                    name: "Metrics".into(),
                    fsrs_version: AddonFsrsVersion::Seven,
                    params,
                    desired_retention: 0.81,
                    historical_retention: 0.9,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "deck:Default".into(),
                    preset_id: "addon:metrics".into(),
                }],
                simulator_rules: vec![],
            },
        )?;
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.ctype = CardType::Review;
        card.memory_state = Some(FsrsMemoryState {
            stability: 42.0,
            stability_internal: 30.0,
            stability_fast: Some(2.5),
            difficulty: 8.0,
        });
        card.desired_retention = Some(0.93);
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-172_801));
        col.storage.update_card(&card)?;
        // Exercise the shared batch resolver, including duplicate inputs.
        let values = col.card_memory_metrics(&vec![cid; 150], true)?;
        let full = col.card_stats(cid)?;
        assert_eq!(values.len(), 150);
        for value in values {
            assert_eq!(value.memory_state, full.memory_state);
            assert_eq!(value.desired_retention, Some(0.93));
            assert!(
                (value.fsrs_retrievability.unwrap() - full.fsrs_retrievability.unwrap()).abs()
                    < 0.00001
            );
        }
        let stored = col.card_memory_metrics(&[cid], false)?.remove(0);
        assert_eq!(stored.memory_state, full.memory_state);
        assert!(stored.fsrs_retrievability.is_none());
        assert_eq!(col.storage.get_card(cid)?.unwrap(), card);
        Ok(())
    }

    #[test]
    fn memory_metrics_preserve_missing_state_for_legacy_initialization() -> Result<()> {
        let (mut col, cid) = test_collection()?;
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        col.grade_now(anki_proto::scheduler::GradeNowRequest {
            card_ids: vec![cid.into()],
            rating: anki_proto::scheduler::card_answer::Rating::Good as i32,
            card_options: vec![],
        })?;
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.memory_state = None;
        card.last_review_time = None;
        col.storage.update_card(&card)?;
        let metrics = col.card_memory_metrics(&[cid], true)?.remove(0);
        assert!(metrics.memory_state.is_none());
        assert!(metrics.fsrs_retrievability.is_none());
        assert_eq!(col.storage.get_card(cid)?.unwrap(), card);
        // The original endpoint still initializes state and includes history.
        let full = col.card_stats(cid)?;
        assert!(full.memory_state.is_some());
        assert_eq!(full.revlog.len(), 1);
        Ok(())
    }

    #[test]
    fn memory_metrics_use_last_review_fallback_without_writing_it() -> Result<()> {
        let (mut col, cid) = test_collection()?;
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        col.grade_now(anki_proto::scheduler::GradeNowRequest {
            card_ids: vec![cid.into()],
            rating: anki_proto::scheduler::card_answer::Rating::Good as i32,
            card_options: vec![],
        })?;
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.last_review_time = None;
        col.storage.update_card(&card)?;
        let metrics = col.card_memory_metrics(&[cid], true)?.remove(0);
        assert_eq!(col.storage.get_card(cid)?.unwrap(), card);
        let full = col.card_stats(cid)?;
        assert_eq!(metrics.memory_state, full.memory_state);
        assert_eq!(metrics.desired_retention, full.desired_retention);
        assert!(
            (metrics.fsrs_retrievability.unwrap() - full.fsrs_retrievability.unwrap()).abs()
                < 0.00001
        );
        assert_eq!(full.revlog.len(), 1);
        Ok(())
    }
}
