// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Extract references from all Rust, Python, TS, Svelte and Designer files in
/// the given roots, convert them to ftl names case and write them as a json to
/// the target file.
/// First argument is the target file name, following are source roots.
fn main() {
    let args: Vec<String> = std::env::args().collect();
    anki_i18n_helpers::garbage_collection::write_ftl_json(&args[2..], &args[1]);
}
