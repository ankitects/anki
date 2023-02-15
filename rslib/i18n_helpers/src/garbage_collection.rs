// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::fs;
use std::io::BufReader;
use std::iter::FromIterator;

use fluent_syntax::ast;
use fluent_syntax::ast::Resource;
use fluent_syntax::parser;
use lazy_static::lazy_static;
use regex::Regex;
use serde_json;
use walkdir::DirEntry;
use walkdir::WalkDir;

use crate::serialize;

const DEPCRATION_WARNING: &str =
    "NO NEED TO TRANSLATE. This text is no longer used by Anki, and will be removed in the future.";

/// Extract references from all Rust, Python, TS, Svelte, Swift, Kotlin and
/// Designer files in the `roots`, convert them to kebab case and write them as
/// a json to the target file.
pub fn write_ftl_json<S1: AsRef<str>, S2: AsRef<str>>(roots: &[S1], target: S2) {
    let refs = gather_ftl_references(roots);
    let mut refs = Vec::from_iter(refs);
    refs.sort();
    serde_json::to_writer_pretty(
        fs::File::create(target.as_ref()).expect("failed to create file"),
        &refs,
    )
    .expect("failed to write file");
}

/// Delete every entry in `ftl_root` that is not mentioned in another message
/// or any json in `json_root`.
pub fn garbage_collect_ftl_entries(ftl_roots: &[impl AsRef<str>], json_root: impl AsRef<str>) {
    let used_ftls = get_all_used_messages_and_terms(json_root.as_ref(), ftl_roots);
    strip_unused_ftl_messages_and_terms(ftl_roots, &used_ftls);
}

/// Moves every entry in `ftl_roots` that is not mentioned in another message, a
/// source file or any json in `json_roots` to the bottom of its file below a
/// deprecation warning.
pub fn deprecate_ftl_entries(
    ftl_roots: &[impl AsRef<str>],
    source_roots: &[impl AsRef<str>],
    json_roots: &[impl AsRef<str>],
) {
    let mut used_ftls = gather_ftl_references(source_roots);
    import_messages_from_json(json_roots, &mut used_ftls);
    extract_nested_messages_and_terms(ftl_roots, &mut used_ftls);
    deprecate_unused_ftl_messages_and_terms(ftl_roots, &used_ftls);
}

fn get_all_used_messages_and_terms(
    json_root: &str,
    ftl_roots: &[impl AsRef<str>],
) -> HashSet<String> {
    let mut used_ftls = HashSet::new();
    import_messages_from_json(&[json_root], &mut used_ftls);
    extract_nested_messages_and_terms(ftl_roots, &mut used_ftls);
    used_ftls
}

fn for_files_with_ending(
    roots: &[impl AsRef<str>],
    file_ending: &str,
    mut op: impl FnMut(DirEntry),
) {
    for root in roots {
        for res in WalkDir::new(root.as_ref()) {
            let entry = res.expect("failed to visit dir");
            if entry.file_type().is_file()
                && entry
                    .file_name()
                    .to_str()
                    .expect("non-unicode filename")
                    .ends_with(file_ending)
            {
                op(entry);
            }
        }
    }
}

fn gather_ftl_references(roots: &[impl AsRef<str>]) -> HashSet<String> {
    let mut refs = HashSet::new();
    for_files_with_ending(roots, "", |entry| {
        extract_references_from_file(&mut refs, &entry)
    });
    refs
}

/// Iterates over all .ftl files in `root`, parses them and rewrites the file if
/// `op` decides to return a new AST.
fn rewrite_ftl_files(
    roots: &[impl AsRef<str>],
    mut op: impl FnMut(Resource<&str>) -> Option<Resource<&str>>,
) {
    for_files_with_ending(roots, ".ftl", |entry| {
        let ftl = fs::read_to_string(entry.path()).expect("failed to open file");
        let ast = parser::parse(ftl.as_str()).expect("failed to parse ftl");
        if let Some(ast) = op(ast) {
            fs::write(entry.path(), serialize::serialize(&ast)).expect("failed to write file");
        }
    });
}

fn import_messages_from_json(json_roots: &[impl AsRef<str>], entries: &mut HashSet<String>) {
    for_files_with_ending(json_roots, ".json", |entry| {
        let buffer = BufReader::new(fs::File::open(entry.path()).expect("failed to open file"));
        let refs: Vec<String> = serde_json::from_reader(buffer).expect("failed to parse json");
        entries.extend(refs);
    })
}

fn extract_nested_messages_and_terms(
    ftl_roots: &[impl AsRef<str>],
    used_ftls: &mut HashSet<String>,
) {
    lazy_static! {
        static ref REFERENCE: Regex = Regex::new(r"\{\s*-?([-0-9a-z]+)\s*\}").unwrap();
    }
    for_files_with_ending(ftl_roots, ".ftl", |entry| {
        let source = fs::read_to_string(entry.path()).expect("file not readable");
        for caps in REFERENCE.captures_iter(&source) {
            used_ftls.insert(caps[1].to_string());
        }
    })
}

fn strip_unused_ftl_messages_and_terms(roots: &[impl AsRef<str>], used_ftls: &HashSet<String>) {
    rewrite_ftl_files(roots, |mut ast| {
        let num_entries = ast.body.len();
        ast.body.retain(entry_use_check(used_ftls));
        (ast.body.len() < num_entries).then_some(ast)
    });
}

fn deprecate_unused_ftl_messages_and_terms(roots: &[impl AsRef<str>], used_ftls: &HashSet<String>) {
    rewrite_ftl_files(roots, |ast| {
        let (mut used, mut unused): (Vec<_>, Vec<_>) =
            ast.body.into_iter().partition(entry_use_check(used_ftls));
        if unused.is_empty() {
            None
        } else {
            append_deprecation_warning(&mut used);
            used.append(&mut unused);
            Some(Resource { body: used })
        }
    });
}

fn append_deprecation_warning(entries: &mut Vec<ast::Entry<&str>>) {
    entries.retain(|entry| match entry {
        ast::Entry::GroupComment(ast::Comment { content }) => {
            !matches!(content.first(), Some(&DEPCRATION_WARNING))
        }
        _ => true,
    });
    entries.push(ast::Entry::GroupComment(ast::Comment {
        content: vec![DEPCRATION_WARNING],
    }));
}

fn entry_use_check(used_ftls: &HashSet<String>) -> impl Fn(&ast::Entry<&str>) -> bool + '_ {
    |entry: &ast::Entry<&str>| match entry {
        ast::Entry::Message(msg) => used_ftls.contains(msg.id.name),
        ast::Entry::Term(term) => used_ftls.contains(term.id.name),
        _ => true,
    }
}

fn extract_references_from_file(refs: &mut HashSet<String>, entry: &DirEntry) {
    lazy_static! {
        static ref SNAKECASE_TR: Regex = Regex::new(r"\Wtr\s*\.([0-9a-z_]+)\W").unwrap();
        static ref CAMELCASE_TR: Regex = Regex::new(r"\Wtr2?\.([0-9A-Za-z_]+)\W").unwrap();
        static ref DESIGNER_STYLE_TR: Regex = Regex::new(r"<string>([0-9a-z_]+)</string>").unwrap();
    }

    let file_name = entry.file_name().to_str().expect("non-unicode filename");

    let (regex, case_conversion): (&Regex, fn(&str) -> String) =
        if file_name.ends_with(".rs") || file_name.ends_with(".py") {
            (&SNAKECASE_TR, snake_to_kebab_case)
        } else if file_name.ends_with(".ts")
            || file_name.ends_with(".svelte")
            || file_name.ends_with(".swift")
            || file_name.ends_with(".kt")
        {
            (&CAMELCASE_TR, camel_to_kebab_case)
        } else if file_name.ends_with(".ui") {
            (&DESIGNER_STYLE_TR, snake_to_kebab_case)
        } else {
            return;
        };

    let source = fs::read_to_string(entry.path()).expect("file not readable");
    for caps in regex.captures_iter(&source) {
        refs.insert(case_conversion(&caps[1]));
    }
}

fn snake_to_kebab_case(name: &str) -> String {
    name.replace('_', "-")
}

fn camel_to_kebab_case(name: &str) -> String {
    let mut kebab = String::with_capacity(name.len() + 8);
    for ch in name.chars() {
        if ch.is_ascii_uppercase() || ch == '_' {
            kebab.push('-');
        }
        if ch != '_' {
            kebab.push(ch.to_ascii_lowercase());
        }
    }
    kebab
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn case_conversion() {
        assert_eq!(snake_to_kebab_case("foo"), "foo");
        assert_eq!(snake_to_kebab_case("foo_bar"), "foo-bar");
        assert_eq!(snake_to_kebab_case("foo_123"), "foo-123");
        assert_eq!(snake_to_kebab_case("foo123"), "foo123");

        assert_eq!(camel_to_kebab_case("foo"), "foo");
        assert_eq!(camel_to_kebab_case("fooBar"), "foo-bar");
        assert_eq!(camel_to_kebab_case("foo_123"), "foo-123");
        assert_eq!(camel_to_kebab_case("foo123"), "foo123");
        assert_eq!(camel_to_kebab_case("123foo"), "123foo");
        assert_eq!(camel_to_kebab_case("123Foo"), "123-foo");
    }
}
