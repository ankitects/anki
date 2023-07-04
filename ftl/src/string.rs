// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;
use std::fs;
use std::path::Path;

use anki_io::read_to_string;
use anki_io::write_file;
use anki_io::write_file_if_changed;
use anki_io::ToUtf8PathBuf;
use anyhow::Context;
use anyhow::Result;
use camino::Utf8Component;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Args;
use clap::ValueEnum;
use fluent_syntax::ast::Entry;
use fluent_syntax::parser;

use crate::serialize;

#[derive(Clone, ValueEnum, PartialEq, Eq, Debug)]
pub enum StringOperation {
    Copy,
    Move,
}

#[derive(Args)]
pub struct StringArgs {
    operation: StringOperation,
    /// The folder which contains the different languages as subfolders, e.g.
    /// ftl/core-repo/core
    src_lang_folder: Utf8PathBuf,
    dst_lang_folder: Utf8PathBuf,
    /// E.g. 'actions-run'. File will be inferred from the prefix.
    src_key: String,
    /// If not specified, the key & file will be the same as the source key.
    dst_key: Option<String>,
}

pub fn string_operation(args: StringArgs) -> Result<()> {
    let old_key = &args.src_key;
    let new_key = args.dst_key.as_ref().unwrap_or(old_key);
    let src_ftl_file = ftl_file_from_key(old_key);
    let dst_ftl_file = ftl_file_from_key(new_key);
    let mut entries: HashMap<&str, Entry<String>> = HashMap::new();

    // Fetch source strings
    let src_langs = all_langs(&args.src_lang_folder)?;
    for lang in &src_langs {
        let ftl_path = lang.join(&src_ftl_file);
        if !ftl_path.exists() {
            continue;
        }

        let entry = get_entry(&ftl_path, old_key);
        if let Some(entry) = entry {
            entries.insert(lang.file_name().unwrap(), entry);
        } else {
            // the key might be missing from some languages, but it should not be missing
            // from the template
            assert_ne!(lang, "templates");
        }
    }

    // Apply to destination
    let dst_langs = all_langs(&args.dst_lang_folder)?;
    for lang in &dst_langs {
        let ftl_path = lang.join(&dst_ftl_file);
        if !ftl_path.exists() {
            continue;
        }

        if let Some(entry) = entries.get(lang.file_name().unwrap()) {
            println!("Updating {ftl_path}");
            write_entry(&ftl_path, new_key, entry.clone())?;
        }
    }

    if let Some(template_dir) = additional_template_folder(&args.dst_lang_folder) {
        // Our templates are also stored in the source tree, and need to be updated too.
        let ftl_path = template_dir.join(&dst_ftl_file);
        println!("Updating {ftl_path}");
        write_entry(
            &ftl_path,
            new_key,
            entries.get("templates").unwrap().clone(),
        )?;
    }

    if args.operation == StringOperation::Move {
        // Delete the old key
        for lang in &src_langs {
            let ftl_path = lang.join(&src_ftl_file);
            if !ftl_path.exists() {
                continue;
            }

            if delete_entry(&ftl_path, old_key)? {
                println!("Deleted entry from {ftl_path}");
            }
        }
        if let Some(template_dir) = additional_template_folder(&args.src_lang_folder) {
            let ftl_path = template_dir.join(&src_ftl_file);
            if delete_entry(&ftl_path, old_key)? {
                println!("Deleted entry from {ftl_path}");
            }
        }
    }

    Ok(())
}

fn additional_template_folder(dst_folder: &Utf8Path) -> Option<Utf8PathBuf> {
    // ftl/core-repo/core -> ftl/core
    // ftl/qt-repo/qt -> ftl/qt
    let adjusted_path = Utf8PathBuf::from_iter(
        [Utf8Component::Normal("ftl")]
            .into_iter()
            .chain(dst_folder.components().skip(2)),
    );
    if adjusted_path.exists() {
        Some(adjusted_path)
    } else {
        None
    }
}

fn all_langs(lang_folder: &Utf8Path) -> Result<Vec<Utf8PathBuf>> {
    std::fs::read_dir(lang_folder)
        .with_context(|| format!("reading {:?}", lang_folder))?
        .filter_map(Result::ok)
        .map(|e| Ok(e.path().utf8()?))
        .collect()
}

fn ftl_file_from_key(old_key: &str) -> String {
    format!("{}.ftl", old_key.split('-').next().unwrap())
}

fn get_entry(fname: &Utf8Path, key: &str) -> Option<Entry<String>> {
    let content = fs::read_to_string(fname).unwrap();
    let resource = parser::parse(content).unwrap();
    for entry in resource.body {
        if let Entry::Message(message) = entry {
            if message.id.name == key {
                return Some(Entry::Message(message));
            }
        }
    }

    None
}

fn write_entry(path: &Utf8Path, key: &str, mut entry: Entry<String>) -> Result<()> {
    if let Entry::Message(message) = &mut entry {
        message.id.name = key.to_string();
    }

    let content = if Path::new(path).exists() {
        fs::read_to_string(path).unwrap()
    } else {
        String::new()
    };
    let mut resource = parser::parse(content).unwrap();
    resource.body.push(entry);

    let mut modified = serialize::serialize(&resource);
    // escape leading dots
    modified = modified.replace(" +.", " +{\".\"}");

    // ensure the resulting serialized file is valid by parsing again
    let _ = parser::parse(modified.clone()).unwrap();

    // it's ok, write it out
    Ok(write_file(path, modified)?)
}

fn delete_entry(path: &Utf8Path, key: &str) -> Result<bool> {
    let content = read_to_string(path)?;
    let mut resource = parser::parse(content).unwrap();
    let mut did_change = false;
    resource.body.retain(|entry| {
        !if let Entry::Message(message) = entry {
            if message.id.name == key {
                did_change = true;
                true
            } else {
                false
            }
        } else {
            false
        }
    });

    let mut modified = serialize::serialize(&resource);
    // escape leading dots
    modified = modified.replace(" +.", " +{\".\"}");

    // ensure the resulting serialized file is valid by parsing again
    let _ = parser::parse(modified.clone()).unwrap();

    // it's ok, write it out
    write_file_if_changed(path, modified)?;
    Ok(did_change)
}
