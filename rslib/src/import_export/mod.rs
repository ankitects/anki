// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod gather;
mod insert;
pub mod package;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ImportProgress {
    Collection,
    Media(usize),
    MediaCheck(usize),
    Notes(usize),
}
