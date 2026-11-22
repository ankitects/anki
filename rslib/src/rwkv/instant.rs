// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! A thin wrapper around the RWKV engine that serves RWKV-Instant recall
//! predictions with the bundled SM98 model.

use std::io;
use std::io::Read;
use std::io::Write;

use super::ReviewInput;
use super::RwkvInference;
use super::RwkvWarmUpSnapshot;
use super::StatePrecision;

/// The show-time instant-only small model (SM98) from the JSchoreels/anki
/// fork: trained only to predict the next answer at the moment a card is
/// shown. It has no trained forgetting curve.
static SM98_WEIGHTS: &[u8] = include_bytes!("sm98.weights.bin");

/// Curve outputs are unused by the instant-only model, but the engine still
/// needs a target retention and maximum interval.
const UNUSED_TARGET_RETENTION: f32 = 0.9;
const UNUSED_MAX_INTERVAL_DAYS: u32 = 36_500;

pub(crate) struct InstantModel {
    engine: RwkvInference,
}

impl InstantModel {
    pub(crate) fn load() -> io::Result<Self> {
        let engine = RwkvInference::load_from_bytes(
            SM98_WEIGHTS,
            UNUSED_TARGET_RETENTION,
            UNUSED_MAX_INTERVAL_DAYS,
            StatePrecision::F32,
        )?
        // SM98 was trained without the deleted-card signal.
        .with_leak_free_inputs()?;
        Ok(Self { engine })
    }

    /// Feed answered reviews (oldest first) into the recurrent state.
    pub(crate) fn replay(&mut self, reviews: Vec<ReviewInput>) -> io::Result<()> {
        if reviews.is_empty() {
            return Ok(());
        }
        self.engine.warm_up_reviews(reviews, false).map(|_| ())
    }

    /// Probability of recall if each card were shown now. Queries do not
    /// change the state.
    pub(crate) fn predict(&mut self, queries: Vec<ReviewInput>) -> io::Result<Vec<f32>> {
        if queries.is_empty() {
            return Ok(vec![]);
        }
        self.engine
            .predict_retrievability_many_from_warm_up(queries)
    }

    /// Identifies the model weights, so saved state is only restored into
    /// the model that produced it.
    pub(crate) fn fingerprint() -> [u8; 32] {
        *blake3::hash(SM98_WEIGHTS).as_bytes()
    }

    /// Writes the recurrent state and feature caches.
    pub(crate) fn write_state(&self, out: &mut impl Write) -> io::Result<()> {
        write_blob(out, &self.engine.cache_state())?;
        let snapshot = self.engine.warm_up_snapshot();
        for states in [
            &snapshot.card_states,
            &snapshot.note_states,
            &snapshot.deck_states,
            &snapshot.preset_states,
        ] {
            write_u64(out, states.len() as u64)?;
            for (id, state) in states {
                out.write_all(&id.to_le_bytes())?;
                write_blob(out, state)?;
            }
        }
        match &snapshot.global_state {
            Some(state) => {
                out.write_all(&[1])?;
                write_blob(out, state)
            }
            None => out.write_all(&[0]),
        }
    }

    /// Restores state written by [Self::write_state].
    pub(crate) fn read_state(&mut self, input: &mut impl Read) -> io::Result<()> {
        let cache = read_blob(input)?;
        let mut maps: Vec<Vec<(i64, Vec<u8>)>> = Vec::with_capacity(4);
        for _ in 0..4 {
            let count = read_u64(input)?;
            let mut states = Vec::with_capacity(count.min(1 << 20) as usize);
            for _ in 0..count {
                states.push((read_i64(input)?, read_blob(input)?));
            }
            maps.push(states);
        }
        let mut has_global = [0u8];
        input.read_exact(&mut has_global)?;
        let global_state = match has_global[0] {
            0 => None,
            1 => Some(read_blob(input)?),
            _ => return Err(invalid("bad RWKV global state flag")),
        };
        let mut maps = maps.into_iter();
        let snapshot = RwkvWarmUpSnapshot {
            card_states: maps.next().unwrap(),
            note_states: maps.next().unwrap(),
            deck_states: maps.next().unwrap(),
            preset_states: maps.next().unwrap(),
            global_state,
        };
        self.engine.restore_cache_state(&cache)?;
        self.engine.restore_warm_up_snapshot(snapshot)
    }
}

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

pub(crate) fn write_u64(out: &mut impl Write, value: u64) -> io::Result<()> {
    out.write_all(&value.to_le_bytes())
}

pub(crate) fn read_u64(input: &mut impl Read) -> io::Result<u64> {
    let mut bytes = [0; 8];
    input.read_exact(&mut bytes)?;
    Ok(u64::from_le_bytes(bytes))
}

pub(crate) fn read_i64(input: &mut impl Read) -> io::Result<i64> {
    read_u64(input).map(|value| value as i64)
}

fn write_blob(out: &mut impl Write, bytes: &[u8]) -> io::Result<()> {
    write_u64(out, bytes.len() as u64)?;
    out.write_all(bytes)
}

fn read_blob(input: &mut impl Read) -> io::Result<Vec<u8>> {
    let len = read_u64(input)?;
    if len > 1 << 32 {
        return Err(invalid("RWKV state blob too large"));
    }
    let mut bytes = vec![0; len as usize];
    input.read_exact(&mut bytes)?;
    Ok(bytes)
}
