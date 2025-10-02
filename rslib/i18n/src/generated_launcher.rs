// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![allow(clippy::all)]

#[derive(Clone)]
pub struct Launcher;

// Include auto-generated content
include!(concat!(env!("OUT_DIR"), "/strings_launcher.rs"));

impl Translations for Launcher {
    const STRINGS: &phf::Map<&str, &phf::Map<&str, &str>> = &_STRINGS;
    const KEYS_BY_MODULE: &[&[&str]] = &_KEYS_BY_MODULE;
}
