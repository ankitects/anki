// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashSet, fs, io::BufReader, iter::FromIterator};

use fluent_syntax::{ast, parser};
use lazy_static::lazy_static;
use regex::Regex;
use serde_json;
use walkdir::{DirEntry, WalkDir};

use crate::serialize;

/// Extract references from all Rust, Python, TS, Svelte, Swift and Designer files in
/// the `roots`, convert them to kebab case and write them as a json to the
/// target file.
pub fn extract_ftl_references<S1: AsRef<str>, S2: AsRef<str>>(roots: &[S1], target: S2) {
    let mut refs = HashSet::new();
    for root in roots {
        for_files_with_ending(root.as_ref(), "", |entry| {
            extract_references_from_file(&mut refs, &entry)
        })
    }
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
pub fn remove_unused_ftl_messages<S: AsRef<str>>(ftl_root: S, json_root: S) {
    let mut used_ftls = HashSet::new();
    import_used_messages(json_root.as_ref(), &mut used_ftls);
    extract_nested_messages_and_terms(ftl_root.as_ref(), &mut used_ftls);
    strip_unused_ftl_messages_and_terms(ftl_root.as_ref(), &used_ftls);
}

fn for_files_with_ending(root: &str, file_ending: &str, mut op: impl FnMut(DirEntry)) {
    for res in WalkDir::new(root) {
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

fn import_used_messages(json_root: &str, used_ftls: &mut HashSet<String>) {
    for_files_with_ending(json_root, ".json", |entry| {
        let buffer = BufReader::new(fs::File::open(entry.path()).expect("failed to open file"));
        let refs: Vec<String> = serde_json::from_reader(buffer).expect("failed to parse json");
        used_ftls.extend(refs);
    })
}

fn extract_nested_messages_and_terms(ftl_root: &str, used_ftls: &mut HashSet<String>) {
    lazy_static! {
        static ref REFERENCE: Regex = Regex::new(r"\{\s*-?([-0-9a-z]+)\s*\}").unwrap();
    }

    for_files_with_ending(ftl_root, ".ftl", |entry| {
        let source = fs::read_to_string(entry.path()).expect("file not readable");
        for caps in REFERENCE.captures_iter(&source) {
            used_ftls.insert(caps[1].to_string());
        }
    })
}

fn strip_unused_ftl_messages_and_terms(ftl_root: &str, used_ftls: &HashSet<String>) {
    for_files_with_ending(ftl_root, ".ftl", |entry| {
        let ftl = fs::read_to_string(entry.path()).expect("failed to open file");
        let mut ast = parser::parse(ftl.as_str()).expect("failed to parse ftl");
        let num_entries = ast.body.len();

        ast.body = ast
            .body
            .into_iter()
            .filter(|entry| match entry {
                ast::Entry::Message(msg) => used_ftls.contains(msg.id.name),
                ast::Entry::Term(term) => used_ftls.contains(term.id.name),
                _ => true,
            })
            .collect();

        if ast.body.len() < num_entries {
            fs::write(entry.path(), serialize::serialize(&ast)).expect("failed to write file");
        }
    });
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
