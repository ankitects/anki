// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::assert_ne;
use std::collections::HashMap;
use std::println;

use camino::Utf8PathBuf;
use clap::Args;
use fluent_syntax::ast::Entry;

use crate::string;

#[derive(Args)]
pub struct CopyOrMoveArgs {
    /// The folder which contains the different languages as subfolders, e.g.
    /// ftl/core-repo/core
    src_lang_folder: Utf8PathBuf,
    dst_lang_folder: Utf8PathBuf,
    /// E.g. 'actions-run'. File will be inferred from the prefix.
    src_key: String,
    /// If not specified, the key & file will be the same as the source key.
    dst_key: Option<String>,
}

#[derive(Debug, Eq, PartialEq)]
pub(super) enum CopyOrMove {
    Copy,
    Move,
}

pub(super) fn copy_or_move(mode: CopyOrMove, args: CopyOrMoveArgs) -> anyhow::Result<()> {
    let old_key = &args.src_key;
    let new_key = args.dst_key.as_ref().unwrap_or(old_key);
    let src_ftl_file = string::ftl_file_from_key(old_key);
    let dst_ftl_file = string::ftl_file_from_key(new_key);
    let mut entries: HashMap<&str, Entry<String>> = HashMap::new();

    // Fetch source strings
    let src_langs = string::all_langs(&args.src_lang_folder)?;
    for lang in &src_langs {
        let ftl_path = lang.join(&src_ftl_file);
        if !ftl_path.exists() {
            continue;
        }

        let entry = string::get_entry(&ftl_path, old_key);
        if let Some(entry) = entry {
            entries.insert(lang.file_name().unwrap(), entry);
        } else {
            // the key might be missing from some languages, but it should not be missing
            // from the template
            assert_ne!(lang, "templates");
        }
    }

    // Apply to destination
    let dst_langs = string::all_langs(&args.dst_lang_folder)?;
    for lang in &dst_langs {
        let ftl_path = lang.join(&dst_ftl_file);
        if !ftl_path.exists() {
            continue;
        }

        if let Some(entry) = entries.get(lang.file_name().unwrap()) {
            println!("Updating {ftl_path}");
            string::write_entry(&ftl_path, new_key, entry.clone())?;
        }
    }

    if let Some(template_dir) = string::additional_template_folder(&args.dst_lang_folder) {
        // Our templates are also stored in the source tree, and need to be updated too.
        let ftl_path = template_dir.join(&dst_ftl_file);
        println!("Updating {ftl_path}");
        string::write_entry(
            &ftl_path,
            new_key,
            entries.get("templates").unwrap().clone(),
        )?;
    }

    if mode == CopyOrMove::Move {
        // Delete the old key
        for lang in &src_langs {
            let ftl_path = lang.join(&src_ftl_file);
            if !ftl_path.exists() {
                continue;
            }

            if string::delete_entry(&ftl_path, old_key)? {
                println!("Deleted entry from {ftl_path}");
            }
        }
        if let Some(template_dir) = string::additional_template_folder(&args.src_lang_folder) {
            let ftl_path = template_dir.join(&src_ftl_file);
            if string::delete_entry(&ftl_path, old_key)? {
                println!("Deleted entry from {ftl_path}");
            }
        }
    }

    Ok(())
}
