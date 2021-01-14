// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod card;
mod config;
mod deck;
mod deckconf;
mod graves;
mod note;
mod notetype;
mod revlog;
mod sqlite;
mod sync;
mod sync_check;
mod tag;
mod upgrades;

pub(crate) use sqlite::SqliteStorage;
pub(crate) use sync::open_and_check_sqlite_file;

use std::fmt::Write;

// Write a list of IDs as '(x,y,...)' into the provided string.
pub(crate) fn ids_to_string<T>(buf: &mut String, ids: &[T])
where
    T: std::fmt::Display,
{
    buf.push('(');
    if !ids.is_empty() {
        for id in ids.iter().skip(1) {
            write!(buf, "{},", id).unwrap();
        }
        write!(buf, "{}", ids[0]).unwrap();
    }
    buf.push(')');
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
