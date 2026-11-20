// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::card_answer::Rating;
use anki_proto::scheduler::CardAnswer;

use super::*;
use crate::config::BoolKey;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::states::FilteredState;
use crate::services::SchedulerService;
use crate::tests::open_fs_test_collection;
use crate::tests::CardAdder;

fn enable_rwkv(col: &mut Collection) -> Result<()> {
    enable_rwkv_with_model(col, "../qt/aqt/rwkv_inference/sm56/weights.f32.bin")
}

fn enable_rwkv_with_model(col: &mut Collection, path: &str) -> Result<()> {
    col.update_default_deck_config(|config| config.rwkv_review_instant_order_enabled = true);
    let model = OfflineModel {
        path: Path::new(env!("CARGO_MANIFEST_DIR")).join(path),
        precision: StatePrecision::Int8,
    };
    let mut runtime = RwkvOfflineRuntime::load(model)?;
    col.rwkv_offline_sync_history(&mut runtime)?;
    col.state.rwkv_offline = Some(Box::new(runtime));
    Ok(())
}

fn answer(col: &mut Collection, card_id: CardId, rating: Rating) -> Result<RevlogReviewKind> {
    answer_with_kind(col, card_id, rating, None)
}

fn answer_with_kind(
    col: &mut Collection,
    card_id: CardId,
    rating: Rating,
    rwkv_review_kind: Option<u32>,
) -> Result<RevlogReviewKind> {
    let states = col.get_scheduling_states(card_id)?;
    let next = match rating {
        Rating::Again => states.again,
        Rating::Hard => states.hard,
        Rating::Good => states.good,
        Rating::Easy => states.easy,
    };
    let _ = SchedulerService::answer_card(
        col,
        CardAnswer {
            card_id: card_id.0,
            current_state: Some(states.current.into()),
            new_state: Some(next.into()),
            rating: rating as i32,
            answered_at_millis: TimestampMillis::now().0,
            rwkv_review_kind,
            ..Default::default()
        },
    )?;
    Ok(col
        .storage
        .get_revlog_entries_for_card(card_id)?
        .into_iter()
        .max_by_key(|entry| entry.id.0)
        .unwrap()
        .review_kind)
}

#[test]
fn normal_review_with_a_future_due_date_uses_review_kind() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-early-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "10", None)?;
    assert!(col.storage.get_card(card)?.unwrap().due > col.timing_today()?.days_elapsed as i32);
    let CardState::Normal(NormalState::Review(review)) = col.get_scheduling_states(card)?.current
    else {
        panic!("expected normal review");
    };
    // Anki normalizes the scheduling state of a normal-deck card to today.
    assert_eq!(review.elapsed_days, review.scheduled_days);
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Review
    );
    Ok(())
}

#[test]
fn rescheduled_filtered_review_uses_filtered_kind() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "0", None)?;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    assert!(matches!(
        col.get_scheduling_states(card)?.current,
        CardState::Filtered(FilteredState::Rescheduling(_))
    ));
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Filtered
    );
    Ok(())
}

#[test]
fn graduating_filtered_new_card_uses_filtered_kind() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-new-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    assert!(matches!(
        col.get_scheduling_states(card)?.current,
        CardState::Filtered(FilteredState::Rescheduling(_))
    ));
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Easy)?,
        RevlogReviewKind::Filtered
    );
    Ok(())
}

#[test]
fn filtered_learning_without_memory_remains_a_counted_learning_answer() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-learning-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Again)?,
        RevlogReviewKind::Learning
    );
    let history = col.rwkv_historical_review_inputs(Default::default())?;
    assert_eq!(history.reviews.len(), 1);
    assert_eq!(history.reviews[0].review_kind, 0);
    Ok(())
}

#[test]
fn filtered_learning_with_memory_uses_filtered_kind() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-fsrs-kind");
    col.set_config_bool(BoolKey::Fsrs, true, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Again)?,
        RevlogReviewKind::Filtered
    );
    let history = col.rwkv_historical_review_inputs(Default::default())?;
    assert_eq!(history.reviews.len(), 1);
    assert_eq!(history.reviews[0].review_kind, 3);
    Ok(())
}

#[test]
fn preview_answer_is_excluded_from_model_history() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-preview-kind");
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "0", None)?;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = false;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Again)?,
        RevlogReviewKind::Filtered
    );
    assert!(col
        .rwkv_historical_review_inputs(Default::default())?
        .reviews
        .is_empty());
    Ok(())
}

#[test]
fn curve_scores_filtered_new_card_with_filtered_context() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-curve");
    let card = CardAdder::new().add(&mut col)[0].id;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    enable_rwkv_with_model(
        &mut col,
        "../qt/aqt/rwkv_inference/RWKV_trained_on_5000_10000.bin",
    )?;
    let state = col.get_scheduling_states(card)?.current;
    assert!(matches!(
        state,
        CardState::Filtered(FilteredState::Rescheduling(_))
    ));
    let timing = col.timing_today()?;
    let row = col
        .rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
            card_ids: vec![card.0],
            include_new_cards: true,
            ..Default::default()
        })?
        .rows
        .pop()
        .expect("filtered new card must be scoreable");
    let wrong_input = query_input(&row);
    let mut expected_input = wrong_input.clone();
    expected_input.card_type = Some(4);
    expected_input.current_elapsed_days = None;
    expected_input.current_elapsed_seconds = None;
    let runtime = col.state.rwkv_offline.as_mut().unwrap();
    let (expected, _) = runtime.inference.curve_from_warm_up(expected_input)?;
    let (wrong, _) = runtime.inference.curve_from_warm_up(wrong_input)?;
    assert!(expected.iter().any(Option::is_some));
    assert_ne!(
        expected, wrong,
        "the actual study context must affect the model"
    );
    let actual = col
        .rwkv_offline_curve(card, &state, [0.9; 4], true, &timing)
        .unwrap();
    assert_eq!(actual, expected);
    Ok(())
}

#[test]
fn curve_uses_the_scheduler_elapsed_time_for_a_learning_step() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-learning-curve");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Again)?,
        RevlogReviewKind::Learning
    );

    // Simulate a learning due time that no longer reflects the last answer.
    let mut edited = col.storage.get_card(card)?.unwrap();
    edited.due -= 30;
    let _ = col.update_cards_maybe_undoable(vec![edited], true)?;
    let state = col.get_scheduling_states(card)?.current;
    let CardState::Normal(NormalState::Learning(learning)) = state else {
        panic!("expected learning state");
    };
    let timing = col.timing_today()?;
    let row = col
        .rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
            card_ids: vec![card.0],
            include_new_cards: true,
            ..Default::default()
        })?
        .rows
        .pop()
        .expect("learning card must be scoreable");
    let wrong_input = query_input(&row);
    assert_ne!(
        wrong_input.current_elapsed_seconds,
        Some(i64::from(learning.elapsed_secs))
    );
    let mut expected_input = wrong_input.clone();
    expected_input.current_elapsed_seconds = Some(i64::from(learning.elapsed_secs));
    let runtime = col.state.rwkv_offline.as_mut().unwrap();
    let (expected, _) = runtime.inference.curve_from_warm_up(expected_input)?;
    let (wrong, _) = runtime.inference.curve_from_warm_up(wrong_input)?;
    assert_ne!(expected, wrong);
    let actual = col
        .rwkv_offline_curve(card, &state, [0.9; 4], true, &timing)
        .unwrap();
    assert_eq!(actual, expected);
    Ok(())
}

#[test]
fn ordinary_filtered_review_does_not_make_next_review_synthetic() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-filtered-repeat-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "0", None)?;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer_with_kind(&mut col, card, Rating::Good, Some(3))?,
        RevlogReviewKind::Filtered
    );
    assert!(matches!(
        col.get_scheduling_states(card)?.current,
        CardState::Normal(NormalState::Review(_))
    ));
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Review
    );
    Ok(())
}

#[test]
fn synthetic_filtered_repeats_chain_but_undo_redo_clears_the_marker() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-synthetic-repeat-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "0", None)?;
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Review
    );
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Filtered
    );
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Filtered
    );
    col.undo()?;
    col.redo()?;
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Review
    );
    Ok(())
}

#[test]
fn undo_of_another_card_keeps_the_synthetic_repeat_marker() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-other-undo-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    let other = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card, other], "0", None)?;
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Review
    );
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Filtered
    );
    assert_eq!(
        answer(&mut col, other, Rating::Good)?,
        RevlogReviewKind::Review
    );
    col.undo()?;
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Filtered
    );
    Ok(())
}

#[test]
fn explicit_answer_kind_is_preserved() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-explicit-kind");
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "0", None)?;
    enable_rwkv(&mut col)?;
    assert_eq!(
        answer_with_kind(&mut col, card, Rating::Good, Some(2))?,
        RevlogReviewKind::Relearning
    );
    Ok(())
}

#[test]
fn disabled_rwkv_keeps_ankis_filtered_review_kind() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-disabled-kind");
    col.set_config_bool(BoolKey::Fsrs, false, false)?;
    let card = CardAdder::new().add(&mut col)[0].id;
    col.set_due_date(&[card], "0", None)?;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = true;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    enable_rwkv(&mut col)?;
    col.update_default_deck_config(|config| config.rwkv_review_instant_order_enabled = false);
    assert_eq!(
        answer(&mut col, card, Rating::Good)?,
        RevlogReviewKind::Review
    );
    Ok(())
}
