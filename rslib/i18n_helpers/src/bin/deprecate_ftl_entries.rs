// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Deprecate unused ftl files by moving them to the bottom and adding a
/// deprecation warning.
/// First argument is the root of the ftl files, second one
/// is the root of the json files.
fn main() {
    let args: Vec<String> = std::env::args().collect();
    anki_i18n_helpers::garbage_collection::deprecate_ftl_entries(&args[1], &args[2]);
}
