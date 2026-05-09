// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! By default, the Qt translations will be included in rslib. EXTRA_FTL_ROOT
//! can be set to an external folder so the mobile clients can use their own
//! translations instead.

use std::collections::HashMap;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

pub type TranslationsByFile = HashMap<String, String>;
pub type TranslationsByLang = HashMap<String, TranslationsByFile>;

/// Read the contents of the FTL files into a TranslationMap structure.
pub fn get_ftl_data() -> TranslationsByLang {
    let mut map = TranslationsByLang::default();

    // English core templates are taken from this repo
    let ftl_base = source_tree_root();
    add_folder(&mut map, &ftl_base.join("core"), "templates");
    // And core translations from submodule
    add_translation_root(&mut map, &ftl_base.join("core-repo/core"), true);

    if let Some(path) = extra_ftl_root() {
        // Mobile client has requested its own extra translations
        add_translation_root(&mut map, &path, false);
    } else {
        // Qt core templates from this repo
        add_folder(&mut map, &ftl_base.join("qt"), "templates");
        // And translations from submodule
        add_translation_root(&mut map, &ftl_base.join("qt-repo/desktop"), true)
    }
    map
}

/// For each .ftl file in the provided folder, add it to the translation map.
fn add_folder(map: &mut TranslationsByLang, folder: &Path, lang: &str) {
    let map_entry = map.entry(lang.to_string()).or_default();
    for entry in fs::read_dir(folder).unwrap() {
        let entry = entry.unwrap();
        let fname = entry.file_name().to_string_lossy().to_string();
        if !fname.ends_with(".ftl") {
            continue;
        }
        let module = fname.trim_end_matches(".ftl").replace('-', "_");
        let text = fs::read_to_string(entry.path()).unwrap();
        assert!(
            text.ends_with('\n'),
            "file was missing final newline: {entry:?}"
        );
        map_entry.entry(module).or_default().push_str(&text);
        println!("cargo:rerun-if-changed={}", entry.path().to_str().unwrap());
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

fn source_tree_root() -> PathBuf {
    PathBuf::from("../../ftl")
}

fn extra_ftl_root() -> Option<PathBuf> {
    std::env::var("EXTRA_FTL_ROOT").ok().map(PathBuf::from)
}
