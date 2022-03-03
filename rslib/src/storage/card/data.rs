// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::{
    types::{FromSql, FromSqlError, ToSqlOutput, ValueRef},
    ToSql,
};
use serde_derive::{Deserialize, Serialize};

use crate::{prelude::*, serde::default_on_invalid};

/// Helper for serdeing the card data column.
#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
#[serde(default)]
pub(super) struct CardData {
    #[serde(
        skip_serializing_if = "Option::is_none",
        rename = "pos",
        deserialize_with = "default_on_invalid"
    )]
    pub(crate) original_position: Option<u32>,
}

impl CardData {
    pub(super) fn from_card(card: &Card) -> Self {
        Self {
            original_position: card.original_position,
        }
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

/// Extract original position from JSON `data`.
pub(crate) fn original_position_from_card_data(card_data: &str) -> Option<u32> {
    let data: CardData = serde_json::from_str(card_data).unwrap_or_default();
    data.original_position
}
