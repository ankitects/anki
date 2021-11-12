// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Delete every entry in the ftl files that is not mentioned in another message
/// or a given json.
/// First argument is the root of the ftl files, second one is the root of the
/// json files.
fn main() {
    let args: Vec<String> = std::env::args().collect();
    anki_i18n_helpers::garbage_collection::remove_unused_ftl_messages(&args[1], &args[2]);
}
