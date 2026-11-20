// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::card_answer::Rating;
use anki_proto::scheduler::CardAnswer;
use anki_proto::scheduler::GetQueuedCardsRequest;
use tempfile::TempDir;

use super::*;
use crate::services::SchedulerService;
use crate::tests::open_fs_test_collection;
use crate::tests::CardAdder;
use crate::tests::DeckAdder;

fn offline_review_collection(
    model_path: &str,
    interval: u32,
    approximate: bool,
    reviews: u32,
    extra_new: usize,
    subdeck: bool,
) -> Result<(TempDir, Collection)> {
    let (mut col, dir) = open_fs_test_collection("rwkv-refresh");
    col.update_default_deck_config(|config| {
        config.rwkv_review_enabled = true;
        config.rwkv_review_instant_order_enabled = true;
        config.rwkv_review_refresh_interval = interval;
        config.rwkv_review_candidate_refresh_enabled = approximate;
        config.rwkv_review_batch_size = 64;
        config.rwkv_review_minimum_reviews_per_day = reviews;
        config.review_order = ReviewCardOrder::RetrievabilityAscending as i32;
        config.new_mix =
            anki_proto::deck_config::deck_config::config::ReviewMix::AfterReviews as i32;
    });
    let deck_id = if subdeck {
        let deck = DeckAdder::new("Default::Child").add(&mut col);
        col.set_current_deck(deck.id)?;
        deck.id
    } else {
        DeckId(1)
    };
    let cards: Vec<_> = (0..reviews)
        .map(|_| CardAdder::new().deck(deck_id).add(&mut col)[0].id)
        .collect();
    col.set_due_date(&cards, "0", None)?;
    for _ in 0..extra_new {
        CardAdder::new().deck(deck_id).add(&mut col);
    }
    let model = OfflineModel {
        path: Path::new(env!("CARGO_MANIFEST_DIR")).join(model_path),
        precision: StatePrecision::Int8,
    };
    let mut runtime = RwkvOfflineRuntime::load(model)?;
    col.rwkv_offline_sync_history(&mut runtime)?;
    col.state.rwkv_offline = Some(Box::new(runtime));
    Ok((dir, col))
}

fn queued_for_review(col: &mut Collection) -> Result<anki_proto::scheduler::QueuedCards> {
    SchedulerService::get_queued_cards(
        col,
        GetQueuedCardsRequest {
            fetch_limit: 1,
            ..Default::default()
        },
    )
}

fn answer_queued_review(
    col: &mut Collection,
    queue: &anki_proto::scheduler::QueuedCards,
) -> Result<()> {
    let card = queue.cards.first().unwrap();
    let states = card.states.clone().unwrap();
    let _ = SchedulerService::answer_card(
        col,
        CardAnswer {
            card_id: card.card.as_ref().unwrap().id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good as i32,
            answered_at_millis: TimestampMillis::now().0,
            milliseconds_taken: 0,
            ..Default::default()
        },
    )?;
    Ok(())
}

#[test]
fn synced_retention_rules_refresh_live_offline_scores() -> Result<()> {
    let (_dir, mut col) = offline_review_collection(
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
        1,
        false,
        3,
        0,
        false,
    )?;
    let _ = queued_for_review(&mut col)?;
    assert!(!col.state.rwkv_offline.as_ref().unwrap().scopes.is_empty());
    col.set_config(
        crate::scheduler::addon_desired_retention::CONFIG_KEY,
        &serde_json::json!({
            "version": 1,
            "rules": [{"query": "deck:Default", "desired_retention": 0.75}]
        }),
    )?;
    assert!(col.state.rwkv_offline.as_ref().unwrap().scopes.is_empty());
    let _ = queued_for_review(&mut col)?;
    let scope = &col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)];
    assert_eq!(scope.cards.len(), 3);
    assert!(scope
        .cards
        .iter()
        .all(|card| card.target_retention == Some(0.75)));
    Ok(())
}

#[test]
fn synced_retention_rules_keep_the_configured_queue_refresh_interval() -> Result<()> {
    let (_dir, mut col) = offline_review_collection(
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
        3,
        false,
        8,
        0,
        false,
    )?;
    col.set_config(
        crate::scheduler::addon_desired_retention::CONFIG_KEY,
        &serde_json::json!({
            "version": 1,
            "rules": [{"query": "deck:Default", "desired_retention": 0.99}]
        }),
    )?;
    let queue = queued_for_review(&mut col)?;
    assert!(!queue.cards.is_empty());
    answer_queued_review(&mut col, &queue)?;
    let _ = queued_for_review(&mut col)?;
    let runtime = col.state.rwkv_offline.as_ref().unwrap();
    let scope = &runtime.scopes[&DeckId(1)];
    assert_eq!(scope.answered.len(), 1);
    assert!(scope.generation < runtime.generation);
    Ok(())
}

#[test]
fn synced_retention_search_refreshes_after_a_note_changes() -> Result<()> {
    let (_dir, mut col) = offline_review_collection(
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
        3,
        false,
        3,
        0,
        false,
    )?;
    let ids = col.search_cards("deck:Default", SortMode::NoOrder)?;
    let cards = col.all_cards_for_ids(&ids, false)?;
    let (card_id, note_id) = (cards[0].id, cards[0].note_id);
    col.set_config(
        crate::scheduler::addon_desired_retention::CONFIG_KEY,
        &serde_json::json!({
            "version": 1,
            "rules": [{"query": "tag:priority", "desired_retention": 0.99}]
        }),
    )?;
    let _ = queued_for_review(&mut col)?;
    let prior = &col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)];
    assert_ne!(
        prior
            .cards
            .iter()
            .find(|card| card.card_id == card_id)
            .unwrap()
            .target_retention,
        Some(0.99)
    );
    col.add_tags_to_notes(&[note_id], "priority")?;
    let _ = queued_for_review(&mut col)?;
    let updated = &col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)];
    assert_eq!(
        updated
            .cards
            .iter()
            .find(|card| card.card_id == card_id)
            .unwrap()
            .target_retention,
        Some(0.99)
    );
    Ok(())
}

#[test]
fn offline_review_queue_refreshes_at_the_configured_interval() -> Result<()> {
    for model in [
        "../qt/aqt/rwkv_inference/RWKV_trained_on_5000_10000.bin",
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
    ] {
        for interval in [1, 3] {
            for approximate in [false, true] {
                let (_dir, mut col) =
                    offline_review_collection(model, interval, approximate, 8, 0, false)?;
                let mut queue = queued_for_review(&mut col)?;
                assert_eq!(queue.review_count, 8);
                let initial = col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)]
                    .cards
                    .clone();
                let mut answered_ids = HashSet::new();
                for answered in 1..=interval {
                    answered_ids.insert(CardId(queue.cards[0].card.as_ref().unwrap().id));
                    answer_queued_review(&mut col, &queue)?;
                    queue = queued_for_review(&mut col)?;
                    let runtime = col.state.rwkv_offline.as_ref().unwrap();
                    let scope = &runtime.scopes[&DeckId(1)];
                    assert_eq!(queue.review_count, 8 - answered);
                    if answered < interval {
                        assert_eq!(scope.answered.len(), answered as usize);
                        assert!(scope.generation < runtime.generation);
                    } else {
                        assert!(
                            scope.answered.is_empty(),
                            "queue was not refreshed at answer {answered}"
                        );
                        assert_eq!(scope.generation, runtime.generation);
                        assert!(
                            scope.cards.iter().any(|card| {
                                !answered_ids.contains(&card.card_id)
                                    && initial.iter().any(|old| {
                                        old.card_id == card.card_id
                                            && (old.retrievability - card.retrievability).abs()
                                                > 0.0001
                                    })
                            }),
                            "refresh did not update any unanswered card's recall"
                        );
                    }
                }
            }
        }
    }
    Ok(())
}

#[test]
fn offline_deck_counts_do_not_reuse_a_partial_queue_refresh() -> Result<()> {
    for (interval, approximate) in [(1, true), (3, false)] {
        let (_dir, mut col) = offline_review_collection(
            "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
            interval,
            approximate,
            8,
            0,
            false,
        )?;
        let queue = queued_for_review(&mut col)?;
        answer_queued_review(&mut col, &queue)?;
        // An unrelated event may also cause the reviewer to rebuild its queue.
        col.clear_study_queues();
        let _ = queued_for_review(&mut col)?;
        let runtime = col.state.rwkv_offline.as_ref().unwrap();
        let scope = &runtime.scopes[&DeckId(1)];
        assert_eq!(scope.generation, runtime.generation);
        assert!(scope.fully_computed_generation < runtime.generation);

        let _ = col.deck_tree(Some(TimestampSecs::now()))?;
        let runtime = col.state.rwkv_offline.as_ref().unwrap();
        let scope = &runtime.scopes[&DeckId(1)];
        assert_eq!(scope.fully_computed_generation, runtime.generation);
    }
    Ok(())
}

#[test]
fn offline_review_queue_refreshes_when_reviews_run_out_before_the_interval() -> Result<()> {
    for extra_new in [0, 2] {
        for approximate in [false, true] {
            let (_dir, mut col) = offline_review_collection(
                "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
                20,
                approximate,
                8,
                extra_new,
                false,
            )?;
            let mut queue = queued_for_review(&mut col)?;
            for _ in 0..8 {
                answer_queued_review(&mut col, &queue)?;
                queue = queued_for_review(&mut col)?;
            }
            assert_eq!(queue.review_count, 0);
            assert_eq!(queue.new_count, extra_new as u32);
            let runtime = col.state.rwkv_offline.as_ref().unwrap();
            let scope = &runtime.scopes[&DeckId(1)];
            assert_eq!(scope.generation, runtime.generation);
            assert!(scope.answered.is_empty());
        }
    }
    Ok(())
}

#[test]
fn offline_subdeck_uses_its_shared_scopes_refresh_interval() -> Result<()> {
    let (_dir, mut col) = offline_review_collection(
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
        2,
        false,
        8,
        0,
        true,
    )?;
    let mut queue = queued_for_review(&mut col)?;
    for _ in 0..2 {
        answer_queued_review(&mut col, &queue)?;
        queue = queued_for_review(&mut col)?;
    }
    let runtime = col.state.rwkv_offline.as_ref().unwrap();
    let scope = &runtime.scopes[&DeckId(1)];
    assert_eq!(scope.generation, runtime.generation);
    assert!(scope.answered.is_empty());
    Ok(())
}

#[test]
fn offline_answer_catches_up_when_the_fast_append_cannot_run() -> Result<()> {
    let (_dir, mut col) = offline_review_collection(
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
        20,
        false,
        8,
        0,
        false,
    )?;
    let mut queue = queued_for_review(&mut col)?;
    answer_queued_review(&mut col, &queue)?;
    queue = queued_for_review(&mut col)?;
    let runtime = col.state.rwkv_offline.as_mut().unwrap();
    let before = runtime.identity.as_ref().unwrap().review_count;
    // A separate collection change makes the cheap append unsafe.
    runtime.checked_at_mod = Some(TimestampMillis(0));
    answer_queued_review(&mut col, &queue)?;
    let runtime = col.state.rwkv_offline.as_ref().unwrap();
    assert_eq!(runtime.identity.as_ref().unwrap().review_count, before + 1);
    Ok(())
}

#[test]
fn offline_deck_counts_rescore_cards_outside_the_approximate_batch() -> Result<()> {
    let (_dir, mut col) = offline_review_collection(
        "../qt/aqt/rwkv_inference/sm56/weights.f32.bin",
        1,
        true,
        80,
        0,
        false,
    )?;
    let queue = queued_for_review(&mut col)?;
    let answered_id = CardId(queue.cards[0].card.as_ref().unwrap().id);
    let original = col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)]
        .cards
        .clone();
    answer_queued_review(&mut col, &queue)?;
    let _ = queued_for_review(&mut col)?;
    let partial = &col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)].cards;
    let untouched: Vec<_> = partial
        .iter()
        .filter(|card| card.card_id != answered_id)
        .filter_map(|card| {
            original
                .iter()
                .find(|old| {
                    old.card_id == card.card_id && old.retrievability == card.retrievability
                })
                .copied()
        })
        .collect();
    assert!(untouched.len() >= 15);

    let _ = col.deck_tree(Some(TimestampSecs::now()))?;
    let refreshed = &col.state.rwkv_offline.as_ref().unwrap().scopes[&DeckId(1)].cards;
    assert!(untouched.iter().any(|old| refreshed.iter().any(|card| {
        card.card_id == old.card_id && (card.retrievability - old.retrievability).abs() > 0.0001
    })));
    Ok(())
}
