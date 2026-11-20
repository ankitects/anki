// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::card_answer::Rating;
use anki_proto::scheduler::CardAnswer;

use super::*;
use crate::config::BoolKey;
use crate::scheduler::states::FilteredState;
use crate::services::SchedulerService;
use crate::tests::open_fs_test_collection;
use crate::tests::CardAdder;

fn answer(col: &mut Collection, card_id: CardId, rating: Rating, s90: Option<f32>) -> Result<()> {
    let states = col.get_scheduling_states(card_id)?;
    let new_state = match rating {
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
            new_state: Some(new_state.into()),
            rating: rating as i32,
            answered_at_millis: TimestampMillis::now().0,
            rwkv_s90: s90,
            ..Default::default()
        },
    )?;
    Ok(())
}

#[test]
fn preview_does_not_apply_offline_or_desktop_curve_stability() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-preview-s90");
    col.set_config_bool(BoolKey::Fsrs, true, false)?;
    let card_id = CardAdder::new().add(&mut col)[0].id;
    answer(&mut col, card_id, Rating::Easy, None)?;
    assert!(col
        .storage
        .get_card(card_id)?
        .unwrap()
        .memory_state
        .is_some());
    col.update_default_deck_config(|config| config.rwkv_review_enabled = true);
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = false;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card_id}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    let before = col
        .storage
        .get_card(card_id)?
        .unwrap()
        .memory_state
        .unwrap();

    let model = OfflineModel {
        path: Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../qt/aqt/rwkv_inference/sm56/weights.f32.bin"),
        precision: StatePrecision::Int8,
    };
    let mut runtime = RwkvOfflineRuntime::load(model)?;
    col.rwkv_offline_sync_history(&mut runtime)?;
    col.state.rwkv_offline = Some(Box::new(runtime));
    assert!(matches!(
        col.get_scheduling_states(card_id)?.current,
        CardState::Filtered(FilteredState::Preview(_))
    ));
    let predicted = col
        .rwkv_offline_curve_s90(card_id, 0)
        .expect("Curve must score the preview");
    assert_ne!(predicted, before.stability);
    answer(&mut col, card_id, Rating::Again, None)?;
    assert_eq!(
        col.storage.get_card(card_id)?.unwrap().memory_state,
        Some(before)
    );

    col.undo()?;
    assert!(answer(&mut col, card_id, Rating::Good, Some(-1.0)).is_err());
    assert_eq!(
        col.storage.get_card(card_id)?.unwrap().memory_state,
        Some(before)
    );
    // Desktop sends the selected S90 explicitly in CardAnswer metadata.
    answer(&mut col, card_id, Rating::Good, Some(12.5))?;
    assert_eq!(
        col.storage.get_card(card_id)?.unwrap().memory_state,
        Some(before)
    );

    // Once the preview has returned to the home deck, a normal answer can
    // still use an explicit Curve S90.
    answer(&mut col, card_id, Rating::Easy, Some(12.5))?;
    let after = col
        .storage
        .get_card(card_id)?
        .unwrap()
        .memory_state
        .unwrap();
    assert!((after.stability - 12.5).abs() < 0.001);
    Ok(())
}

#[test]
fn preview_does_not_create_memory_for_a_new_card() -> Result<()> {
    let (mut col, _dir) = open_fs_test_collection("rwkv-preview-new-s90");
    col.set_config_bool(BoolKey::Fsrs, true, false)?;
    let card_id = CardAdder::new().add(&mut col)[0].id;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = false;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card_id}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    assert!(col
        .storage
        .get_card(card_id)?
        .unwrap()
        .memory_state
        .is_none());
    answer(&mut col, card_id, Rating::Good, Some(12.5))?;
    assert!(col
        .storage
        .get_card(card_id)?
        .unwrap()
        .memory_state
        .is_none());
    Ok(())
}
