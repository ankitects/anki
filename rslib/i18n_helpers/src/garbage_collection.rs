// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashSet, fs, io::BufReader};

use fluent_syntax::{ast, parser};
use lazy_static::lazy_static;
use regex::Regex;
use serde_json;
use walkdir::{DirEntry, WalkDir};

use crate::serialize;

/// Extract references from all Rust, Python, TS, Svelte and Designer files in
/// the `roots`, convert them to kebab case and write them as a json to the
/// target file.
pub fn extract_ftl_references(roots: &[&str], target: &str) {
    let mut refs = HashSet::new();
    for root in roots {
        for entry in WalkDir::new(root) {
            let entry = entry.expect("failed to visit dir");
            if entry.file_type().is_file() {
                extract_references_from_file(&mut refs, &entry);
            }
        }
    }
    serde_json::to_writer(
        fs::File::create(target).expect("failed to create file"),
        &refs,
    )
    .expect("failed to write file");
}

/// Delete every entry in `ftl_root` that is not mentioned in any json in
/// `json_root`.
pub fn remove_unused_ftl_messages(ftl_root: &str, json_root: &str) {
    let mut used_ftls = HashSet::new();
    for entry in WalkDir::new(json_root) {
        let entry = entry.expect("failed to visit dir");
        if entry.file_type().is_file()
            && entry
                .file_name()
                .to_str()
                .expect("non-unicode filename")
                .ends_with(".json")
        {
            let buffer = BufReader::new(fs::File::open(entry.path()).expect("failed to open file"));
            let refs: Vec<String> = serde_json::from_reader(buffer).expect("failed to parse json");
            used_ftls.extend(refs);
        }
    }
    for entry in WalkDir::new(ftl_root) {
        let entry = entry.expect("failed to visit dir");
        if entry.file_type().is_file()
            && entry
                .file_name()
                .to_str()
                .expect("non-unicode filename")
                .ends_with(".ftl")
        {
            let ftl = fs::read_to_string(entry.path()).expect("failed to open file");
            let mut ast = parser::parse(ftl.as_str()).expect("failed to parse ftl");
            extract_nested_ftl_messages(&mut &ast, &mut used_ftls);
            let num_entries = ast.body.len();
            ast.body = ast
                .body
                .into_iter()
                .filter(|entry| match entry {
                    ast::Entry::Message(msg) => used_ftls.contains(msg.id.name),
                    _ => true,
                })
                .collect();
            if ast.body.len() < num_entries {
                fs::write(entry.path(), serialize::serialize(&ast)).expect("failed to write file");
            }
        }
    }
}

fn extract_nested_ftl_messages(ast: &ast::Resource<&str>, messages: &mut HashSet<String>) {
    for entry in &ast.body {
        if let ast::Entry::Message(ast::Message {
            value: Some(pattern),
            ..
        }) = entry
        {
            extract_messages_from_pattern(pattern, messages);
        }
    }
}

fn extract_messages_from_pattern(pattern: &ast::Pattern<&str>, messages: &mut HashSet<String>) {
    for elem in &pattern.elements {
        if let ast::PatternElement::Placeable { expression } = elem {
            extract_messages_from_expression(expression, messages);
        }
    }
}

fn extract_messages_from_expression(
    expression: &ast::Expression<&str>,
    messages: &mut HashSet<String>,
) {
    match expression {
        ast::Expression::Select { selector, variants } => {
            extract_messages_from_inline_expression(selector, messages);
            for variant in variants {
                extract_messages_from_pattern(&variant.value, messages);
            }
        }
        ast::Expression::Inline(expression) => {
            extract_messages_from_inline_expression(expression, messages)
        }
    }
}

fn extract_messages_from_inline_expression(
    expression: &ast::InlineExpression<&str>,
    messages: &mut HashSet<String>,
) {
    match expression {
        ast::InlineExpression::MessageReference { id, .. } => {
            messages.insert(id.name.to_string());
        }
        ast::InlineExpression::Placeable { expression } => {
            extract_messages_from_expression(expression, messages)
        }
        _ => (),
    }
}

fn extract_references_from_file(refs: &mut HashSet<String>, entry: &DirEntry) {
    lazy_static! {
        static ref RUST_STYLE_TR: Regex = Regex::new(r"\Wtr\s*\.([0-9a-z_]+)\W").unwrap();
        static ref TS_STYLE_TR: Regex = Regex::new(r"\Wtr2?\.([0-9A-Za-z_]+)\W").unwrap();
        static ref DESIGNER_STYLE_TR: Regex = Regex::new(r"<string>([0-9a-z_]+)</string>").unwrap();
    }

    let file_name = entry.file_name().to_str().expect("non-unicode filename");

    let (regex, case_conversion): (&Regex, fn(&str) -> String) =
        if file_name.ends_with(".rs") || file_name.ends_with(".py") {
            (&RUST_STYLE_TR, snake_to_kebab_case)
        } else if file_name.ends_with(".ts") || file_name.ends_with(".svelte") {
            (&TS_STYLE_TR, camel_to_kebab_case)
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

    #[ignore]
    #[test]
    fn garbage_collection() {
        extract_ftl_references(
            &["../../pylib", "../../qt", "../../rslib", "../../ts"],
            "refs.json",
        );
        remove_unused_ftl_messages("../../ftl/core", "refs.json");
    }

    #[ignore]
    #[test]
    fn formatting() {
        for entry in WalkDir::new("../../ftl/core") {
            let entry = entry.expect("failed to visit dir");
            if entry.file_type().is_file()
                && entry
                    .file_name()
                    .to_str()
                    .expect("non-unicode filename")
                    .ends_with(".ftl")
            {
                let ftl = fs::read_to_string(entry.path()).expect("failed to open file");
                let ast = parser::parse(ftl.as_str()).expect("failed to parse ftl");
                fs::write(entry.path(), serialize::serialize(&ast)).expect("failed to write file");
            }
        }
    }

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
