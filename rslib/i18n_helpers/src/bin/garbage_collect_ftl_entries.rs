// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Delete every entry in the ftl files that is not mentioned in another message
/// or a given json.
/// First argument is the root of the json files, following are the roots of the
/// ftl files.
fn main() {
    let args: Vec<String> = std::env::args().collect();
    anki_i18n_helpers::garbage_collection::garbage_collect_ftl_entries(&args[2..], &args[1]);
}
