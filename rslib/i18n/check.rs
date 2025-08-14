// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Check the .ftl files at build time to ensure we don't get runtime load
//! failures.

use fluent::FluentBundle;
use fluent::FluentResource;
use unic_langid::LanguageIdentifier;

use super::gather::TranslationsByLang;

pub fn check(lang_map: &TranslationsByLang) {
    for (lang, files_map) in lang_map {
        for (fname, content) in files_map {
            check_content(lang, fname, content);
        }
    }
}

fn check_content(lang: &str, fname: &str, content: &str) {
    let lang_id: LanguageIdentifier = "en-US".parse().unwrap();
    let resource = FluentResource::try_new(content.into()).unwrap_or_else(|e| {
        panic!("{content}\nUnable to parse {lang}/{fname}: {e:?}");
    });

    let mut bundle: FluentBundle<FluentResource> = FluentBundle::new(vec![lang_id]);
    bundle.add_resource(resource).unwrap_or_else(|e| {
        panic!("{content}\nUnable to bundle - duplicate key? {lang}/{fname}: {e:?}");
    });
}
