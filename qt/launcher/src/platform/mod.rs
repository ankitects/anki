// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(unix)]
mod unix;

#[cfg(target_os = "macos")]
mod mac;

#[cfg(target_os = "windows")]
mod windows;

#[cfg(target_os = "macos")]
pub use mac::*;
#[cfg(all(unix, not(target_os = "macos")))]
pub use unix::*;
#[cfg(target_os = "windows")]
pub use windows::*;
