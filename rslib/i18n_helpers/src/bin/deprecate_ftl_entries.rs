// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Deprecate unused ftl files by moving them to the bottom and adding a
/// deprecation warning.
/// Argument before `--` are roots of ftl files, arguments after that are source
/// roots.
fn main() {
    let (ftl_roots, source_roots) = collect_separated_args();
    anki_i18n_helpers::garbage_collection::deprecate_ftl_entries(
        &ftl_roots,
        &source_roots,
        "ftl/usage",
    );
}

fn collect_separated_args() -> (Vec<String>, Vec<String>) {
    let mut before = Vec::new();
    let mut after = Vec::new();
    let mut past_separator = false;
    for arg in std::env::args() {
        match arg.as_str() {
            "--" => {
                past_separator = true;
            }
            _ if past_separator => after.push(arg),
            _ => before.push(arg),
        };
    }
    (before, after)
}
