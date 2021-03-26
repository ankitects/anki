// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Env vars that control behaviour:
//! - FTL_SRC can be pointed at /ftl/BUILD.bazel to tell the script where the translatinos
//! in the source tree can be found. If not set (when building from cargo), the script
//! will look in the parent folders instead.
//! - RSLIB_FTL_ROOT should be set to the l10n.toml file inside the core translation repo.
//! - EXTRA_FTL_ROOT should be set to the l10n.toml file inside the qt translation repo.
//! - If NO_QT_TEMPLATES is set, EXTRA_FTL_ROOT can be pointed at a l10n.toml file in a separate
//! location, to include files from there. In this case, the standard Qt templates will not
//! be included from the source tree.

use std::path::Path;
use std::{collections::HashMap, env};
use std::{fs, path::PathBuf};

pub type TranslationsByFile = HashMap<String, String>;
pub type TranslationsByLang = HashMap<String, TranslationsByFile>;

/// Read the contents of the FTL files into a TranslationMap structure.
pub fn get_ftl_data() -> TranslationsByLang {
    let mut map = TranslationsByLang::default();
    let include_qt = include_local_qt_templates();

    // English templates first
    let ftl_base = source_tree_root();
    add_folder(&mut map, &ftl_base.join("core"), "templates");
    if include_qt {
        add_folder(&mut map, &ftl_base.join("qt"), "templates");
    }

    // Core translations provided?
    if let Some(path) = core_ftl_root() {
        add_translation_root(&mut map, &path, true);
    }

    // Extra templates/translations provided?
    if let Some(path) = extra_ftl_root() {
        add_translation_root(&mut map, &path, include_qt);
    }

    map
}

/// For each .ftl file in the provided folder, add it to the translation map.
fn add_folder(map: &mut TranslationsByLang, folder: &Path, lang: &str) {
    let map_entry = map.entry(lang.to_string()).or_default();
    for entry in fs::read_dir(&folder).unwrap() {
        let entry = entry.unwrap();
        let fname = entry.file_name().to_string_lossy().to_string();
        if !fname.ends_with(".ftl") {
            continue;
        }
        let module = fname.trim_end_matches(".ftl").replace("-", "_");
        let text = fs::read_to_string(&entry.path()).unwrap();
        assert!(
            text.ends_with('\n'),
            "file was missing final newline: {:?}",
            entry
        );
        map_entry.entry(module).or_default().push_str(&text);
    }
}

/// For each language folder in `root`, add the ftl files stored inside.
/// If ignore_templates is true, the templates/ folder will be ignored, on the
/// assumption the templates were extracted from the source tree.
fn add_translation_root(map: &mut TranslationsByLang, root: &Path, ignore_templates: bool) {
    for entry in fs::read_dir(root).unwrap() {
        let entry = entry.unwrap();
        let lang = entry.file_name().to_string_lossy().to_string();
        if ignore_templates && lang == "templates" {
            continue;
        }
        add_folder(map, &entry.path(), &lang);
    }
}

/// In a standard build, the ftl/qt folder is used as the source
/// of truth for @extra_ftl, making it easier to add new strings.
/// If the Qt templates are not desired, the NO_QT_TEMPLATES env
/// var can be set to skip them.
fn include_local_qt_templates() -> bool {
    env::var("NO_QT_TEMPLATES").is_err()
}

fn source_tree_root() -> PathBuf {
    if let Ok(srcfile) = env::var("FTL_SRC") {
        let mut path = PathBuf::from(srcfile);
        path.pop();
        path
    } else {
        PathBuf::from("../../ftl")
    }
}

fn core_ftl_root() -> Option<PathBuf> {
    std::env::var("RSLIB_FTL_ROOT")
        .ok()
        .map(first_folder_next_to_l10n_file)
}

fn extra_ftl_root() -> Option<PathBuf> {
    std::env::var("EXTRA_FTL_ROOT")
        .ok()
        .map(first_folder_next_to_l10n_file)
}

fn first_folder_next_to_l10n_file(l10n_path: String) -> PathBuf {
    // drop the filename
    let mut path = PathBuf::from(&l10n_path);
    path.pop();
    // iterate over the folder
    for entry in path.read_dir().unwrap() {
        let entry = entry.unwrap();
        if entry.metadata().unwrap().is_dir() {
            // return the first folder we find
            return entry.path();
        }
    }
    panic!("no folder found in {}", l10n_path);
}
