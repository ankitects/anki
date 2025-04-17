// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use rusqlite::types::FromSql;
use rusqlite::types::FromSqlError;
use rusqlite::types::ValueRef;
use serde::Deserialize;
use serde::Serialize;
use serde_json::Value;

use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::serde::default_on_invalid;

/// Helper for serdeing the card data column.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
#[serde(default)]
pub(crate) struct CardData {
    #[serde(
        rename = "pos",
        skip_serializing_if = "Option::is_none",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) original_position: Option<u32>,
    #[serde(
        rename = "s",
        skip_serializing_if = "Option::is_none",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) fsrs_stability: Option<f32>,
    #[serde(
        rename = "d",
        skip_serializing_if = "Option::is_none",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) fsrs_difficulty: Option<f32>,
    #[serde(
        rename = "dr",
        skip_serializing_if = "Option::is_none",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) fsrs_desired_retention: Option<f32>,
    #[serde(
        rename = "decay",
        skip_serializing_if = "Option::is_none",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) decay: Option<f32>,

    /// A string representation of a JSON object storing optional data
    /// associated with the card, so v3 custom scheduling code can persist
    /// state.
    #[serde(default, rename = "cd", skip_serializing_if = "meta_is_empty")]
    pub(crate) custom_data: String,
}

impl CardData {
    pub(crate) fn from_card(card: &Card) -> Self {
        Self {
            original_position: card.original_position,
            fsrs_stability: card.memory_state.as_ref().map(|m| m.stability),
            fsrs_difficulty: card.memory_state.as_ref().map(|m| m.difficulty),
            fsrs_desired_retention: card.desired_retention,
            decay: card.decay,
            custom_data: card.custom_data.clone(),
        }
    }

    pub(crate) fn from_str(s: &str) -> Self {
        serde_json::from_str(s).unwrap_or_default()
    }

    pub(crate) fn memory_state(&self) -> Option<FsrsMemoryState> {
        if let Some(stability) = self.fsrs_stability {
            if let Some(difficulty) = self.fsrs_difficulty {
                return Some(FsrsMemoryState {
                    stability,
                    difficulty,
                });
            }
        }
        None
    }

    pub(crate) fn convert_to_json(&mut self) -> Result<String> {
        if let Some(v) = &mut self.fsrs_stability {
            round_to_places(v, 3)
        }
        if let Some(v) = &mut self.fsrs_difficulty {
            round_to_places(v, 3)
        }
        if let Some(v) = &mut self.fsrs_desired_retention {
            round_to_places(v, 2)
        }
        if let Some(v) = &mut self.decay {
            round_to_places(v, 3)
        }
        serde_json::to_string(&self).map_err(Into::into)
    }
}

fn round_to_places(value: &mut f32, decimal_places: u32) {
    let factor = 10_f32.powi(decimal_places as i32);
    *value = (*value * factor).round() / factor;
}

impl FromSql for CardData {
    /// Infallible; invalid/missing data results in the default value.
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Text(s) = value {
            Ok(serde_json::from_slice(s).unwrap_or_default())
        } else {
            Ok(Self::default())
        }
    }
}

/// Serialize the JSON `data` for a card.
pub(crate) fn card_data_string(card: &Card) -> String {
    CardData::from_card(card).convert_to_json().unwrap()
}

fn meta_is_empty(s: &str) -> bool {
    matches!(s, "" | "{}")
}

fn validate_custom_data(json_str: &str) -> Result<()> {
    if !meta_is_empty(json_str) {
        let object: HashMap<&str, Value> =
            serde_json::from_str(json_str).or_invalid("custom data not an object")?;
        require!(
            object.keys().all(|k| k.len() <= 8),
            "custom data keys must be <= 8 bytes"
        );
        require!(
            json_str.len() <= 100,
            "serialized custom data must be under 100 bytes"
        );
    }
    Ok(())
}

impl Card {
    pub(crate) fn validate_custom_data(&self) -> Result<()> {
        validate_custom_data(&self.custom_data)
    }
}

#[cfg(test)]
mod test {
    use super::*;
    #[test]
    fn validation() {
        assert!(validate_custom_data("").is_ok());
        assert!(validate_custom_data("{}").is_ok());
        assert!(validate_custom_data(r#"{"foo": 5}"#).is_ok());
        assert!(validate_custom_data(r#"["foo"]"#).is_err());
        assert!(validate_custom_data(r#"{"日": 5}"#).is_ok());
        assert!(validate_custom_data(r#"{"日本語": 5}"#).is_err());
        assert!(validate_custom_data(&format!(r#"{{"foo": "{}"}}"#, "x".repeat(100))).is_err());
    }

    #[test]
    fn compact_floats() {
        let mut data = CardData {
            original_position: None,
            fsrs_stability: Some(123.45678),
            fsrs_difficulty: Some(1.234567),
            fsrs_desired_retention: Some(0.987654),
            decay: Some(0.123456),
            custom_data: "".to_string(),
        };
        assert_eq!(
            data.convert_to_json().unwrap(),
            r#"{"s":123.457,"d":1.235,"dr":0.99,"decay":0.123}"#
        );
    }
}
