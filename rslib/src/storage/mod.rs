// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod card;
mod collection_timestamps;
mod config;
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
pub(crate) use sync::open_and_check_sqlite_file;

/// Write a list of IDs as '(x,y,...)' into the provided string.
pub(crate) fn ids_to_string<T>(buf: &mut String, ids: &[T])
where
    T: std::fmt::Display,
{
    buf.push('(');
    write_comma_separated_ids(buf, ids);
    buf.push(')');
}

/// Write a list of Ids as 'x,y,...' into the provided string.
pub(crate) fn write_comma_separated_ids<T>(buf: &mut String, ids: &[T])
where
    T: std::fmt::Display,
{
    if !ids.is_empty() {
        for id in ids.iter().skip(1) {
            write!(buf, "{},", id).unwrap();
        }
        write!(buf, "{}", ids[0]).unwrap();
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
        ids_to_string::<u8>(&mut s, &[]);
        assert_eq!(s, "()");
        s.clear();
        ids_to_string(&mut s, &[7]);
        assert_eq!(s, "(7)");
        s.clear();
        ids_to_string(&mut s, &[7, 6]);
        assert_eq!(s, "(6,7)");
        s.clear();
        ids_to_string(&mut s, &[7, 6, 5]);
        assert_eq!(s, "(6,5,7)");
        s.clear();
    }
}
