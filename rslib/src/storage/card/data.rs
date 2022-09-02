// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use rusqlite::{
    types::{FromSql, FromSqlError, ToSqlOutput, ValueRef},
    ToSql,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;

use crate::{prelude::*, serde::default_on_invalid};

/// Helper for serdeing the card data column.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
#[serde(default)]
pub(crate) struct CardData {
    #[serde(
        skip_serializing_if = "Option::is_none",
        rename = "pos",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) original_position: Option<u32>,
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
            custom_data: card.custom_data.clone(),
        }
    }

    pub(crate) fn from_str(s: &str) -> Self {
        serde_json::from_str(s).unwrap_or_default()
    }
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

impl ToSql for CardData {
    fn to_sql(&self) -> Result<ToSqlOutput<'_>, rusqlite::Error> {
        Ok(ToSqlOutput::Owned(
            serde_json::to_string(self).unwrap().into(),
        ))
    }
}

/// Serialize the JSON `data` for a card.
pub(crate) fn card_data_string(card: &Card) -> String {
    serde_json::to_string(&CardData::from_card(card)).unwrap()
}

fn meta_is_empty(s: &str) -> bool {
    matches!(s, "" | "{}")
}

fn validate_custom_data(json_str: &str) -> Result<()> {
    if !meta_is_empty(json_str) {
        let object: HashMap<&str, Value> = serde_json::from_str(json_str)
            .map_err(|e| AnkiError::invalid_input(format!("custom data not an object: {e}")))?;
        if object.keys().any(|k| k.as_bytes().len() > 8) {
            return Err(AnkiError::invalid_input(
                "custom data keys must be <= 8 bytes",
            ));
        }
        if json_str.len() > 100 {
            return Err(AnkiError::invalid_input(
                "serialized custom data must be under 100 bytes",
            ));
        }
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
}
