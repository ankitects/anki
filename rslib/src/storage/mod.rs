// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod card;
mod collection_timestamps;
mod config;
mod dbcheck;
mod deck;
mod deckconfig;
mod graves;
mod note;
mod notetype;
mod revlog;
mod sqlite;
mod sync;
mod sync_check;
mod tag;
mod upgrades;

use std::fmt::Write;

pub(crate) use sqlite::SqliteStorage;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SchemaVersion {
    V11,
    V18,
}

impl SchemaVersion {
    pub(super) fn has_journal_mode_delete(self) -> bool {
        self == Self::V11
    }
}

/// Write a list of IDs as '(x,y,...)' into the provided string.
pub fn ids_to_string<D, I>(buf: &mut String, ids: I)
where
    D: std::fmt::Display,
    I: IntoIterator<Item = D>,
{
    buf.push('(');
    write_comma_separated_ids(buf, ids);
    buf.push(')');
}

/// Write a list of Ids as 'x,y,...' into the provided string.
pub(crate) fn write_comma_separated_ids<D, I>(buf: &mut String, ids: I)
where
    D: std::fmt::Display,
    I: IntoIterator<Item = D>,
{
    let mut trailing_sep = false;
    for id in ids {
        write!(buf, "{id},").unwrap();
        trailing_sep = true;
    }
    if trailing_sep {
        buf.pop();
    }
}

pub(crate) fn comma_separated_ids<T>(ids: &[T]) -> String
where
    T: std::fmt::Display,
{
    let mut buf = String::new();
    write_comma_separated_ids(&mut buf, ids);

    buf
}

#[cfg(test)]
mod test {
    use super::ids_to_string;

    #[test]
    fn ids_string() {
        let mut s = String::new();
        ids_to_string(&mut s, [0; 0]);
        assert_eq!(s, "()");
        s.clear();
        ids_to_string(&mut s, [7]);
        assert_eq!(s, "(7)");
        s.clear();
        ids_to_string(&mut s, [7, 6]);
        assert_eq!(s, "(7,6)");
        s.clear();
        ids_to_string(&mut s, [7, 6, 5]);
        assert_eq!(s, "(7,6,5)");
        s.clear();
    }
}
