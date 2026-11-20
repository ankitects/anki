// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

const SMALL_MODEL: bool = !super::SMALL_MODEL;
include!("engine.rs");
#[cfg(all(test, rwkv_vectors, feature = "rwkv-sm40"))]
#[path = "legacy_tests.rs"]
mod legacy_tests;
#[cfg(all(test, rwkv_vectors, not(feature = "rwkv-sm40")))]
#[path = "sm40_tests.rs"]
mod sm40_tests;
