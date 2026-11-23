// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::retrievability::Series;
use anki_proto::stats::graphs_response::Retrievability;

use crate::prelude::TimestampSecs;
use crate::scheduler::timing::SchedTimingToday;
use crate::stats::graphs::eases::percent_to_bin;
use crate::stats::graphs::GraphsContext;

#[derive(Default)]
struct RetrievabilitySeries {
    output: Series,
    scored_cards: usize,
    note_retrievability: std::collections::HashMap<i64, (f32, u32)>,
}

impl RetrievabilitySeries {
    fn record(&mut self, note_id: i64, retrievability: Option<f32>) {
        let entry = self.note_retrievability.entry(note_id).or_insert((0.0, 0));
        entry.1 += 1;

        let Some(retrievability) = retrievability else {
            return;
        };

        *self
            .output
            .retrievability
            .entry(percent_to_bin(retrievability * 100.0, 1))
            .or_default() += 1;
        self.output.sum_by_card += retrievability;
        self.scored_cards += 1;
        entry.0 += retrievability;
    }

    fn finish(mut self) -> (Series, bool) {
        if self.scored_cards != 0 {
            self.output.average = self.output.sum_by_card * 100.0 / self.scored_cards as f32;
        }
        self.output.sum_by_note = self
            .note_retrievability
            .values()
            .map(|(sum, count)| sum / *count as f32)
            .sum();
        (self.output, self.scored_cards != 0)
    }
}

impl GraphsContext {
    /// (SM-2, FSRS)
    pub(super) fn retrievability(&self) -> Retrievability {
        let mut active = RetrievabilitySeries::default();
        let mut fsrs_series = RetrievabilitySeries::default();
        let mut rwkv_series = RetrievabilitySeries::default();
        let timing = SchedTimingToday {
            days_elapsed: self.days_elapsed,
            now: TimestampSecs::now(),
            next_day_at: self.next_day_start,
        };

        for card in &self.cards {
            let rwkv_retrievability = self
                .rwkv_retrievability_scores
                .as_ref()
                .and_then(|scores| scores.get(&card.id))
                .copied();
            let fsrs_retrievability = card.memory_state.and_then(|state| {
                let elapsed_seconds = card.seconds_since_last_review(&timing).unwrap_or_default();
                let preset_id = self.fsrs_preset_by_card.get(&card.id)?;
                let fsrs = self.fsrs_by_preset.get(preset_id)?;
                Some(fsrs.current_retrievability(state.into(), elapsed_seconds as f32 / 86_400.0))
            });

            fsrs_series.record(card.note_id.0, fsrs_retrievability);
            rwkv_series.record(card.note_id.0, rwkv_retrievability);
            active.record(card.note_id.0, rwkv_retrievability.or(fsrs_retrievability));
        }

        let (active, _) = active.finish();
        let (fsrs, has_fsrs) = fsrs_series.finish();
        let (rwkv, has_rwkv) = rwkv_series.finish();

        Retrievability {
            retrievability: active.retrievability,
            average: active.average,
            sum_by_card: active.sum_by_card,
            sum_by_note: active.sum_by_note,
            fsrs: has_fsrs.then_some(fsrs),
            rwkv: has_rwkv.then_some(rwkv),
        }
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use anki_proto::deck_config::deck_configs_for_update::current_deck::Limits;
    use anki_proto::deck_config::UpdateDeckConfigsMode;
    use fsrs::MemoryState;
    use fsrs::FSRS;

    use crate::card::FsrsMemoryState;
    use crate::deckconfig::FsrsVersion;
    use crate::deckconfig::UpdateDeckConfigsRequest;
    use crate::prelude::*;
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

    #[test]
    fn retrievability_graph_uses_selected_model_curve() -> Result<()> {
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
        card.memory_state = Some(FsrsMemoryState {
            stability,
            stability_internal: stability,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(timing.now.adding_secs(-(elapsed_days as i64) * 86_400));
        card.decay = Some(params[23]);
        col.storage.update_card(&card)?;

        let graphs = col.graph_data_for_search("", 365)?;
        let actual = graphs.retrievability.unwrap().average;
        let expected = FSRS::new(&params)?.current_retrievability(
            MemoryState {
                stability,
                difficulty: 5.0,
                stability_fast: stability,
            },
            elapsed_days,
        ) * 100.0;

        assert_eq!(format!("{actual:.3}"), format!("{expected:.3}"));
        Ok(())
    }

    #[test]
    fn retrievability_graph_uses_rwkv_scores_for_matching_search() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];

        let mut card = col.storage.get_card(cid)?.unwrap();
        let timing = col.timing_today()?;
        card.memory_state = Some(FsrsMemoryState {
            stability: 42.0,
            stability_internal: 42.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;
        col.set_rwkv_stats_graph_scores("".into(), HashMap::from([(cid, 0.25)]))?;

        let graphs = col.graph_data_for_search("", 365)?;
        let retrievability = graphs.retrievability.unwrap();
        let fsrs_retrievability = retrievability.fsrs.as_ref().unwrap();
        let rwkv_retrievability = retrievability.rwkv.as_ref().unwrap();

        assert_eq!(format!("{:.1}", retrievability.average), "25.0");
        assert_eq!(retrievability.retrievability.get(&25), Some(&1));
        assert_eq!(format!("{:.1}", rwkv_retrievability.average), "25.0");
        assert_eq!(rwkv_retrievability.retrievability.get(&25), Some(&1));
        assert_eq!(format!("{:.1}", fsrs_retrievability.average), "100.0");
        assert_eq!(fsrs_retrievability.retrievability.get(&99), Some(&1));
        Ok(())
    }

    #[test]
    fn retrievability_graph_keeps_rwkv_scores_isolated_by_search() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];

        let mut card = col.storage.get_card(cid)?.unwrap();
        let timing = col.timing_today()?;
        card.memory_state = Some(FsrsMemoryState {
            stability: 42.0,
            stability_internal: 42.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;

        col.set_rwkv_stats_graph_scores("".into(), HashMap::from([(cid, 0.25)]))?;
        col.set_rwkv_review_queue_scores(DeckId(1), HashMap::from([(cid, 0.57)]))?;
        col.set_rwkv_card_info_score(cid, Some(0.83))?;

        let graphs = col.graph_data_for_search("", 365)?;
        let retrievability = graphs.retrievability.unwrap();
        let rwkv_retrievability = retrievability.rwkv.as_ref().unwrap();
        assert_eq!(format!("{:.1}", retrievability.average), "25.0");
        assert_eq!(format!("{:.1}", rwkv_retrievability.average), "25.0");

        col.set_rwkv_card_info_score(cid, None)?;
        let graphs = col.graph_data_for_search("", 365)?;
        let retrievability = graphs.retrievability.unwrap();
        let rwkv_retrievability = retrievability.rwkv.as_ref().unwrap();
        assert_eq!(format!("{:.1}", retrievability.average), "25.0");
        assert_eq!(format!("{:.1}", rwkv_retrievability.average), "25.0");

        col.set_rwkv_review_queue_scores(DeckId(1), HashMap::new())?;
        let graphs = col.graph_data_for_search("", 365)?;
        let retrievability = graphs.retrievability.unwrap();
        let rwkv_retrievability = retrievability.rwkv.as_ref().unwrap();
        assert_eq!(format!("{:.1}", retrievability.average), "25.0");
        assert_eq!(format!("{:.1}", rwkv_retrievability.average), "25.0");

        col.set_rwkv_stats_graph_scores("deck:other".into(), HashMap::from([(cid, 0.91)]))?;
        let graphs = col.graph_data_for_search("", 365)?;
        let retrievability = graphs.retrievability.unwrap();
        let rwkv_retrievability = retrievability.rwkv.as_ref().unwrap();
        assert_eq!(format!("{:.1}", rwkv_retrievability.average), "25.0");

        let other_scores = col
            .rwkv_stats_graph_scores_for_search(timing.days_elapsed, Some("deck:other"))
            .unwrap();
        assert_eq!(other_scores.get(&cid), Some(&0.91));

        col.set_rwkv_stats_graph_scores("".into(), HashMap::new())?;
        let graphs = col.graph_data_for_search("", 365)?;
        assert!(graphs.retrievability.unwrap().rwkv.is_none());
        assert_eq!(
            col.rwkv_stats_graph_scores_for_search(timing.days_elapsed, Some("deck:other"),)
                .unwrap()
                .get(&cid),
            Some(&0.91)
        );
        Ok(())
    }

    #[test]
    fn retrievability_graph_filters_with_matching_rwkv_search_scores() -> Result<()> {
        let mut col = Collection::new();

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        let mut note2 = nt.new_note();
        col.add_note(&mut note1, DeckId(1))?;
        col.add_note(&mut note2, DeckId(1))?;
        let mut card_ids = col.search_cards("", SortMode::NoOrder)?;
        card_ids.sort();

        let timing = col.timing_today()?;
        for card_id in &card_ids {
            let mut card = col.storage.get_card(*card_id)?.unwrap();
            card.memory_state = Some(FsrsMemoryState {
                stability: 42.0,
                stability_internal: 42.0,
                stability_fast: None,
                difficulty: 5.0,
            });
            card.last_review_time = Some(timing.now);
            col.storage.update_card(&card)?;
        }

        let search = "prop:rwkv:r<0.5";
        col.set_rwkv_stats_graph_scores(
            search.into(),
            HashMap::from([(card_ids[0], 0.25), (card_ids[1], 0.75)]),
        )?;
        col.set_rwkv_stats_graph_scores(
            "deck:other".into(),
            HashMap::from([(card_ids[0], 0.75), (card_ids[1], 0.25)]),
        )?;
        col.set_rwkv_card_info_score(card_ids[0], Some(0.75))?;
        col.set_rwkv_card_info_score(card_ids[1], Some(0.25))?;

        let graphs = col.graph_data_for_search(search, 365)?;
        let rwkv = graphs.retrievability.unwrap().rwkv.unwrap();
        assert_eq!(format!("{:.1}", rwkv.average), "25.0");
        assert_eq!(rwkv.retrievability.get(&25), Some(&1));
        assert_eq!(rwkv.retrievability.get(&75), None);
        Ok(())
    }
}
