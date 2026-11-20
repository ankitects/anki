// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Byte layouts of a paired runtime: the release model (curve, intervals,
//! S90, answer time) and an instant model (the show-time instant-only model,
//! stage 2, the show-time retrain or the instant-only model: instant recall
//! and button probabilities) replay the same history, so every serialized
//! state holds one state of each, release first.

use std::io;

use super::api::*;

/// One stream state of each model.
const PAIRED_STATE_MAGIC: &[u8] = b"ARWKVPAIRSTATE1";
/// The runtime caches (feature state and curves) of both models.
pub(crate) const PAIRED_CACHE_MAGIC: &[u8] = b"ARWKVPAIRCACHE1";
/// Store chunks of the instant model's state delta start at this index, so
/// both deltas of a segment live in the same table and are pruned together.
pub(crate) const INSTANT_CHUNK_BASE: i64 = 1 << 32;

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

pub(crate) fn paired_len(release_len: usize, instant_len: usize) -> usize {
    PAIRED_STATE_MAGIC.len() + 4 + release_len + instant_len
}

pub(crate) fn write_paired_header(out: &mut impl io::Write, release_len: usize) -> io::Result<()> {
    out.write_all(PAIRED_STATE_MAGIC)?;
    let len = u32::try_from(release_len).map_err(|_| invalid("RWKV state is too large"))?;
    out.write_all(&len.to_le_bytes())
}

pub fn join_state(release: &[u8], instant: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(paired_len(release.len(), instant.len()));
    write_paired_header(&mut out, release.len()).expect("writing to a Vec should not fail");
    out.extend_from_slice(release);
    out.extend_from_slice(instant);
    out
}

pub fn split_state(bytes: &[u8]) -> io::Result<(&[u8], &[u8])> {
    let rest = bytes
        .strip_prefix(PAIRED_STATE_MAGIC)
        .ok_or_else(|| invalid("RWKV state belongs to a single model"))?;
    if rest.len() < 4 {
        return Err(invalid("truncated paired RWKV state"));
    }
    let len = u32::from_le_bytes(rest[..4].try_into().unwrap()) as usize;
    let rest = &rest[4..];
    if rest.len() < len {
        return Err(invalid("truncated paired RWKV state"));
    }
    Ok(rest.split_at(len))
}

pub fn join_cache(release: &[u8], instant: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(PAIRED_CACHE_MAGIC.len() + 8 + release.len() + instant.len());
    out.extend_from_slice(PAIRED_CACHE_MAGIC);
    out.extend_from_slice(&(release.len() as u64).to_le_bytes());
    out.extend_from_slice(release);
    out.extend_from_slice(instant);
    out
}

pub fn split_cache(bytes: &[u8]) -> io::Result<(&[u8], &[u8])> {
    let rest = bytes
        .strip_prefix(PAIRED_CACHE_MAGIC)
        .ok_or_else(|| invalid("RWKV runtime cache belongs to a single model"))?;
    if rest.len() < 8 {
        return Err(invalid("truncated paired RWKV runtime cache"));
    }
    let len = usize::try_from(u64::from_le_bytes(rest[..8].try_into().unwrap()))
        .map_err(|_| invalid("truncated paired RWKV runtime cache"))?;
    let rest = &rest[8..];
    if rest.len() < len {
        return Err(invalid("truncated paired RWKV runtime cache"));
    }
    Ok(rest.split_at(len))
}

type OptionalPair = (Option<Vec<u8>>, Option<Vec<u8>>);

fn split_optional(bytes: Option<&[u8]>) -> io::Result<OptionalPair> {
    Ok(match bytes {
        Some(bytes) => {
            let (release, instant) = split_state(bytes)?;
            (Some(release.to_vec()), Some(instant.to_vec()))
        }
        None => (None, None),
    })
}

fn join_optional(
    release: Option<Vec<u8>>,
    instant: Option<Vec<u8>>,
) -> io::Result<Option<Vec<u8>>> {
    match (release, instant) {
        (Some(release), Some(instant)) => Ok(Some(join_state(&release, &instant))),
        (None, None) => Ok(None),
        _ => Err(invalid("RWKV paired models hold different streams")),
    }
}

pub fn split_review_state(
    state: &ReviewState<'_>,
) -> io::Result<(ReviewStateOwned, ReviewStateOwned)> {
    split_owned_parts([
        state.card,
        state.deck,
        state.note,
        state.preset,
        state.global,
    ])
}

pub fn split_owned_state(
    state: &ReviewStateOwned,
) -> io::Result<(ReviewStateOwned, ReviewStateOwned)> {
    split_owned_parts([
        state.card.as_deref(),
        state.deck.as_deref(),
        state.note.as_deref(),
        state.preset.as_deref(),
        state.global.as_deref(),
    ])
}

fn split_owned_parts(
    parts: [Option<&[u8]>; 5],
) -> io::Result<(ReviewStateOwned, ReviewStateOwned)> {
    let [card, deck, note, preset, global] = parts;
    let (card_r, card_i) = split_optional(card)?;
    let (deck_r, deck_i) = split_optional(deck)?;
    let (note_r, note_i) = split_optional(note)?;
    let (preset_r, preset_i) = split_optional(preset)?;
    let (global_r, global_i) = split_optional(global)?;
    Ok((
        ReviewStateOwned {
            card: card_r,
            deck: deck_r,
            note: note_r,
            preset: preset_r,
            global: global_r,
        },
        ReviewStateOwned {
            card: card_i,
            deck: deck_i,
            note: note_i,
            preset: preset_i,
            global: global_i,
        },
    ))
}

pub fn join_owned_state(
    release: ReviewStateOwned,
    instant: ReviewStateOwned,
) -> io::Result<ReviewStateOwned> {
    Ok(ReviewStateOwned {
        card: join_optional(release.card, instant.card)?,
        deck: join_optional(release.deck, instant.deck)?,
        note: join_optional(release.note, instant.note)?,
        preset: join_optional(release.preset, instant.preset)?,
        global: join_optional(release.global, instant.global)?,
    })
}

pub fn as_review_state(state: &ReviewStateOwned) -> ReviewState<'_> {
    ReviewState {
        card: state.card.as_deref(),
        deck: state.deck.as_deref(),
        note: state.note.as_deref(),
        preset: state.preset.as_deref(),
        global: state.global.as_deref(),
    }
}

type StateMap = Vec<(i64, Vec<u8>)>;

fn split_map(states: &[(i64, Vec<u8>)]) -> io::Result<(StateMap, StateMap)> {
    let mut release = Vec::with_capacity(states.len());
    let mut instant = Vec::with_capacity(states.len());
    for (identity, bytes) in states {
        let (r, i) = split_state(bytes)?;
        release.push((*identity, r.to_vec()));
        instant.push((*identity, i.to_vec()));
    }
    Ok((release, instant))
}

fn join_map(release: StateMap, instant: StateMap) -> io::Result<StateMap> {
    let mut instant: std::collections::HashMap<i64, Vec<u8>> = instant.into_iter().collect();
    let joined = release
        .into_iter()
        .map(|(identity, release)| {
            let instant = instant
                .remove(&identity)
                .ok_or_else(|| invalid("RWKV paired models hold different streams"))?;
            Ok((identity, join_state(&release, &instant)))
        })
        .collect::<io::Result<Vec<_>>>()?;
    if !instant.is_empty() {
        return Err(invalid("RWKV paired models hold different streams"));
    }
    Ok(joined)
}

pub fn split_warm_up_snapshot(
    snapshot: &RwkvWarmUpSnapshot,
) -> io::Result<(RwkvWarmUpSnapshot, RwkvWarmUpSnapshot)> {
    let (card_r, card_i) = split_map(&snapshot.card_states)?;
    let (note_r, note_i) = split_map(&snapshot.note_states)?;
    let (deck_r, deck_i) = split_map(&snapshot.deck_states)?;
    let (preset_r, preset_i) = split_map(&snapshot.preset_states)?;
    let (global_r, global_i) = split_optional(snapshot.global_state.as_deref())?;
    Ok((
        RwkvWarmUpSnapshot {
            card_states: card_r,
            note_states: note_r,
            deck_states: deck_r,
            preset_states: preset_r,
            global_state: global_r,
        },
        RwkvWarmUpSnapshot {
            card_states: card_i,
            note_states: note_i,
            deck_states: deck_i,
            preset_states: preset_i,
            global_state: global_i,
        },
    ))
}

pub fn join_warm_up_snapshot(
    release: RwkvWarmUpSnapshot,
    instant: RwkvWarmUpSnapshot,
) -> io::Result<RwkvWarmUpSnapshot> {
    Ok(RwkvWarmUpSnapshot {
        card_states: join_map(release.card_states, instant.card_states)?,
        note_states: join_map(release.note_states, instant.note_states)?,
        deck_states: join_map(release.deck_states, instant.deck_states)?,
        preset_states: join_map(release.preset_states, instant.preset_states)?,
        global_state: join_optional(release.global_state, instant.global_state)?,
    })
}

/// Splits a simulation snapshot; `want_instant` selects the half returned.
pub fn simulation_snapshot_half(
    snapshot: RwkvWorkloadSimulationSnapshot,
    want_instant: bool,
) -> io::Result<RwkvWorkloadSimulationSnapshot> {
    let pick = |states: &[(i64, Vec<u8>)]| -> io::Result<StateMap> {
        let (release, instant) = split_map(states)?;
        Ok(if want_instant { instant } else { release })
    };
    let (release_global, instant_global) = split_optional(snapshot.global_state.as_deref())?;
    let global_state = if want_instant {
        instant_global
    } else {
        release_global
    };
    let runtime_state = match snapshot.runtime_state.as_deref() {
        Some(bytes) => {
            let (release, instant) = split_cache(bytes)?;
            Some(if want_instant { instant } else { release }.to_vec())
        }
        None => None,
    };
    Ok(RwkvWorkloadSimulationSnapshot {
        card_states: pick(&snapshot.card_states)?,
        note_states: pick(&snapshot.note_states)?,
        deck_states: pick(&snapshot.deck_states)?,
        preset_states: pick(&snapshot.preset_states)?,
        global_state,
        runtime_state,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn paired_bytes_round_trip_and_reject_single_states() {
        let joined = join_state(b"release", b"instant!");
        assert_eq!(
            split_state(&joined).unwrap(),
            (&b"release"[..], &b"instant!"[..])
        );
        assert!(split_state(b"ARWKVMODSTATE1").is_err());
        assert!(split_state(&joined[..joined.len() - 9]).is_err());
        let cache = join_cache(b"a", b"bc");
        assert_eq!(split_cache(&cache).unwrap(), (&b"a"[..], &b"bc"[..]));
        assert!(split_cache(b"ARWKVPROCSTATE4").is_err());
        assert!(join_map(vec![(1, vec![1])], vec![(2, vec![2])]).is_err());
    }
}
