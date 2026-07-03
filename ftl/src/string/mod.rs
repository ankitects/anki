// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod copy;
mod transform;

use std::fs;
use std::path::Path;

use anki_io::read_to_string;
use anki_io::write_file_if_changed;
use anki_io::ToUtf8PathBuf;
use anyhow::anyhow;
use anyhow::Context;
use anyhow::Result;
use camino::Utf8Component;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Subcommand;
use copy::CopyOrMoveArgs;
use fluent_syntax::ast::Entry;
use fluent_syntax::ast::Resource;
use fluent_syntax::parser;
use itertools::Itertools;

use crate::serialize;
use crate::string::copy::copy_or_move;
use crate::string::copy::CopyOrMove;
use crate::string::transform::transform;
use crate::string::transform::TransformArgs;

#[derive(Subcommand)]
pub enum StringCommand {
    /// Copy a key from one ftl file to another, including all its
    /// translations. Source and destination should be e.g.
    /// ftl/core-repo/core.
    Copy(CopyOrMoveArgs),
    /// Move a key from one ftl file to another, including all its
    /// translations. Source and destination should be e.g.
    /// ftl/core-repo/core.
    Move(CopyOrMoveArgs),
    /// Apply a regex find&replace to the template and translations.
    Transform(TransformArgs),
}

pub fn string_operation(args: StringCommand) -> anyhow::Result<()> {
    match args {
        StringCommand::Copy(args) => copy_or_move(CopyOrMove::Copy, args),
        StringCommand::Move(args) => copy_or_move(CopyOrMove::Move, args),
        StringCommand::Transform(args) => transform(args),
    }
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
        .with_context(|| format!("reading {lang_folder:?}"))?
        .filter_map(Result::ok)
        .map(|e| Ok(e.path().utf8()?))
        .collect()
}

fn ftl_file_from_key(old_key: &str) -> String {
    for prefix in [
        "card-stats",
        "card-template-rendering",
        "card-templates",
        "change-notetype",
        "custom-study",
        "database-check",
        "deck-config",
        "empty-cards",
        "media-check",
        "qt-misc",
    ] {
        if old_key.starts_with(&format!("{prefix}-")) {
            return format!("{prefix}.ftl");
        }
    }

    format!("{}.ftl", old_key.split('-').next().unwrap())
}

fn parse_file(ftl_path: &Utf8Path) -> Result<Resource<String>> {
    let content = read_to_string(ftl_path).unwrap();
    parser::parse(content).map_err(|(_, errs)| {
        anyhow!(
            "while reading {ftl_path}: {}",
            errs.into_iter().map(|err| err.to_string()).join(", ")
        )
    })
}

/// True if changed.
fn serialize_file(path: &Utf8Path, resource: &Resource<String>) -> Result<bool> {
    let mut text = serialize::serialize(resource);
    // escape leading dots
    text = text.replace(" +.", " +{\".\"}");
    // ensure the resulting serialized file is valid by parsing again
    let _ = parser::parse(text.clone()).unwrap();
    // it's ok, write it out
    Ok(write_file_if_changed(path, text)?)
}

fn get_entry(fname: &Utf8Path, key: &str) -> Option<Entry<String>> {
    let resource = parse_file(fname).unwrap();
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

    serialize_file(path, &resource)?;
    Ok(())
}

fn delete_entry(path: &Utf8Path, key: &str) -> Result<bool> {
    let mut resource = parse_file(path)?;
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
    serialize_file(path, &resource)
}
