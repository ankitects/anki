// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::card_answer::Rating;
use anki_proto::scheduler::CardAnswer;

use super::*;
use crate::services::SchedulerService;
use crate::tests::CardAdder;

fn answer(col: &mut Collection, card_id: CardId, rating: Rating, s90: Option<f32>) -> Result<()> {
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
            rwkv_s90: s90,
            ..Default::default()
        },
    )?;
    Ok(())
}

#[test]
fn preview_answer_does_not_apply_curve_stability() -> Result<()> {
    let mut col = Collection::new();
    col.set_config_bool(BoolKey::Fsrs, true, false)?;
    let card_id = CardAdder::new().add(&mut col)[0].id;
    answer(&mut col, card_id, Rating::Easy, None)?;
    let mut deck = Deck::new_filtered();
    deck.filtered_mut()?.reschedule = false;
    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card_id}");
    col.add_or_update_deck(&mut deck)?;
    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
    assert!(matches!(
        col.get_scheduling_states(card_id)?.current,
        CardState::Filtered(FilteredState::Preview(_))
    ));
    let before = col
        .storage
        .get_card(card_id)?
        .unwrap()
        .memory_state
        .unwrap();

    assert!(answer(&mut col, card_id, Rating::Good, Some(-1.0)).is_err());
    assert_eq!(
        col.storage.get_card(card_id)?.unwrap().memory_state,
        Some(before)
    );
    // Desktop's reviewer can send Curve S90 metadata even for a preview.
    answer(&mut col, card_id, Rating::Good, Some(12.5))?;
    assert_eq!(
        col.storage.get_card(card_id)?.unwrap().memory_state,
        Some(before)
    );

    // A normal answer after leaving the preview still applies the S90.
    answer(&mut col, card_id, Rating::Easy, Some(12.5))?;
    assert!(
        (col.storage
            .get_card(card_id)?
            .unwrap()
            .memory_state
            .unwrap()
            .stability
            - 12.5)
            .abs()
            < 0.001
    );
    Ok(())
}

#[test]
fn preview_answer_does_not_create_memory_for_a_new_card() -> Result<()> {
    let mut col = Collection::new();
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
