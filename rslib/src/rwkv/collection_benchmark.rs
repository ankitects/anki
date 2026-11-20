// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::decks::deck::kind_container::Kind;
use anki_proto::decks::deck::KindContainer;
use prost::Message;
use rusqlite::types::Type;
use rusqlite::Connection;

pub(crate) fn deck_config_ids_by_deck(
    connection: &Connection,
) -> rusqlite::Result<HashMap<i64, Option<i64>>> {
    if table_has_column(connection, "decks", "kind")? {
        normalized_deck_config_ids(connection)
    } else {
        legacy_deck_config_ids(connection)
    }
}

fn normalized_deck_config_ids(
    connection: &Connection,
) -> rusqlite::Result<HashMap<i64, Option<i64>>> {
    let mut statement = connection.prepare("select id, kind from decks")?;
    let rows = statement.query_map([], |row| {
        let deck_id = row.get(0)?;
        let kind_blob: Vec<u8> = row.get(1)?;
        let kind = KindContainer::decode(kind_blob.as_slice()).map_err(|error| {
            rusqlite::Error::FromSqlConversionFailure(1, Type::Blob, Box::new(error))
        })?;
        let config_id = match kind.kind {
            Some(Kind::Normal(normal)) => Some(normal.config_id),
            Some(Kind::Filtered(_)) | None => None,
        };
        Ok((deck_id, config_id))
    })?;
    rows.collect()
}

fn legacy_deck_config_ids(connection: &Connection) -> rusqlite::Result<HashMap<i64, Option<i64>>> {
    let decks_json: String =
        connection.query_row("select decks from col limit 1", [], |row| row.get(0))?;
    let decks: serde_json::Value = serde_json::from_str(&decks_json).map_err(|error| {
        rusqlite::Error::FromSqlConversionFailure(0, Type::Text, Box::new(error))
    })?;
    let mut by_deck = HashMap::new();
    if let Some(decks) = decks.as_object() {
        for (id, deck) in decks {
            if let Ok(deck_id) = id.parse::<i64>() {
                let config_id = deck.get("conf").and_then(|value| value.as_i64());
                by_deck.insert(deck_id, config_id);
            }
        }
    }
    Ok(by_deck)
}

fn table_has_column(connection: &Connection, table: &str, column: &str) -> rusqlite::Result<bool> {
    let mut statement = connection.prepare(&format!("pragma table_info({table})"))?;
    let columns = statement.query_map([], |row| row.get::<_, String>(1))?;
    for name in columns {
        if name? == column {
            return Ok(true);
        }
    }
    Ok(false)
}

#[cfg(test)]
mod tests {
    use anki_proto::decks::deck::kind_container::Kind;
    use anki_proto::decks::deck::KindContainer;
    use anki_proto::decks::deck::Normal;
    use prost::Message;
    use rusqlite::params;

    use super::*;

    #[test]
    fn reads_normalized_deck_config_ids() -> rusqlite::Result<()> {
        let connection = Connection::open_in_memory()?;
        connection.execute_batch(
            "create table decks (
                id integer primary key not null,
                kind blob not null
            );",
        )?;
        for deck_id in [100_i64, 200] {
            let kind = KindContainer {
                kind: Some(Kind::Normal(Normal {
                    config_id: 42,
                    ..Default::default()
                })),
            }
            .encode_to_vec();
            connection.execute(
                "insert into decks (id, kind) values (?, ?)",
                params![deck_id, kind],
            )?;
        }

        let ids = deck_config_ids_by_deck(&connection)?;

        assert_eq!(ids.get(&100), Some(&Some(42)));
        assert_eq!(ids.get(&200), Some(&Some(42)));
        Ok(())
    }

    #[test]
    fn reads_legacy_deck_config_ids() -> rusqlite::Result<()> {
        let connection = Connection::open_in_memory()?;
        connection.execute_batch("create table col (decks text not null);")?;
        connection.execute(
            "insert into col (decks) values (?)",
            [r#"{"100":{"conf":42},"200":{"conf":42}}"#],
        )?;

        let ids = deck_config_ids_by_deck(&connection)?;

        assert_eq!(ids.get(&100), Some(&Some(42)));
        assert_eq!(ids.get(&200), Some(&Some(42)));
        Ok(())
    }
}
