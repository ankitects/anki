// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// Include auto-generated content

#![allow(clippy::all)]

#[cfg(not(launcher))]
include!(concat!(env!("OUT_DIR"), "/strings.rs"));

#[cfg(launcher)]
include!(concat!(env!("OUT_DIR"), "/strings_launcher.rs"));
