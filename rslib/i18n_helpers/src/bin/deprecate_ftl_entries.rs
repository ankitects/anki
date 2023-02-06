// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// Deprecate unused ftl entries by moving them to the bottom of the file and
/// adding a deprecation warning. An entry is considered unused if cannot be
/// found in a source or JSON file.
/// Arguments before `--` are roots of ftl files, arguments after that are
/// source roots. JSON roots must be preceded by `--keep` or `-k`.
fn main() {
    let args = Arguments::new();
    anki_i18n_helpers::garbage_collection::deprecate_ftl_entries(
        &args.ftl_roots,
        &args.source_roots,
        &args.json_roots,
    );
}

#[derive(Default)]
struct Arguments {
    ftl_roots: Vec<String>,
    source_roots: Vec<String>,
    json_roots: Vec<String>,
}

impl Arguments {
    fn new() -> Self {
        let mut args = Self::default();
        let mut past_separator = false;
        let mut keep_flag = false;
        for arg in std::env::args() {
            match arg.as_str() {
                "--" => {
                    past_separator = true;
                }
                "--keep" | "-k" => {
                    keep_flag = true;
                }
                _ if keep_flag => {
                    keep_flag = false;
                    args.json_roots.push(arg)
                }
                _ if past_separator => args.source_roots.push(arg),
                _ => args.ftl_roots.push(arg),
            };
        }

        args
    }
}
