// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fluent::{FluentArgs, FluentBundle, FluentResource};
use log::{error, warn};
use std::borrow::Cow;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use unic_langid::LanguageIdentifier;

pub use crate::backend_proto::StringsGroup;
pub use fluent::fluent_args as tr_args;

/// Helper for creating args with &strs
#[macro_export]
macro_rules! tr_strs {
    ( $($key:expr => $value:expr),* ) => {
        {
            let mut args: fluent::FluentArgs = fluent::FluentArgs::new();
            $(
                args.insert($key, $value.to_string().into());
            )*
            args
        }
    };
}
use std::collections::HashMap;
pub use tr_strs;

/// The folder containing ftl files for the provided language.
/// If a fully qualified folder exists (eg, en_GB), return that.
/// Otherwise, try the language alone (eg en).
/// If neither folder exists, return None.
fn lang_folder(lang: LanguageIdentifier, ftl_folder: &Path) -> Option<PathBuf> {
    if let Some(region) = lang.get_region() {
        let path = ftl_folder.join(format!("{}_{}", lang.get_language(), region));
        if fs::metadata(&path).is_ok() {
            return Some(path);
        }
    }
    let path = ftl_folder.join(lang.get_language());
    if fs::metadata(&path).is_ok() {
        Some(path)
    } else {
        None
    }
}

/// Get the fallback/English resource text for the given group.
/// These are embedded in the binary.
fn ftl_fallback_for_group(group: StringsGroup) -> String {
    match group {
        StringsGroup::Other => "",
        StringsGroup::Test => include_str!("../../tests/support/test.ftl"),
        StringsGroup::MediaCheck => include_str!("media-check.ftl"),
        StringsGroup::CardTemplates => include_str!("card-template-rendering.ftl"),
        StringsGroup::Sync => include_str!("sync.ftl"),
        StringsGroup::Network => include_str!("network.ftl"),
        StringsGroup::Statistics => include_str!("statistics.ftl"),
        StringsGroup::Filtering => include_str!("filtering.ftl"),
    }
    .to_string()
}

/// Get the resource text for the given group in the given language folder.
/// If the file can't be read, returns None.
fn localized_ftl_for_group(group: StringsGroup, lang_ftl_folder: &Path) -> Option<String> {
    let path = lang_ftl_folder.join(match group {
        StringsGroup::Other => "",
        StringsGroup::Test => "test.ftl",
        StringsGroup::MediaCheck => "media-check.ftl",
        StringsGroup::CardTemplates => "card-template-rendering.ftl",
        StringsGroup::Sync => "sync.ftl",
        StringsGroup::Network => "network.ftl",
        StringsGroup::Statistics => "statistics.ftl",
        StringsGroup::Filtering => "filtering.ftl",
    });
    fs::read_to_string(&path)
        .map_err(|e| {
            warn!("Unable to read translation file: {:?}: {}", path, e);
        })
        .ok()
}

/// Parse resource text into an AST for inclusion in a bundle.
/// Returns None if the text contains errors.
fn get_bundle(
    text: String,
    locales: &[LanguageIdentifier],
) -> Option<FluentBundle<FluentResource>> {
    let res = FluentResource::try_new(text)
        .map_err(|e| {
            error!("Unable to parse translations file: {:?}", e);
        })
        .ok()?;

    let mut bundle: FluentBundle<FluentResource> = FluentBundle::new(locales);
    bundle
        .add_resource(res)
        .map_err(|e| {
            error!("Duplicate key detected in translation file: {:?}", e);
        })
        .ok()?;

    Some(bundle)
}

#[derive(Clone)]
pub struct I18n {
    inner: Arc<Mutex<I18nInner>>,
}

impl I18n {
    pub fn new<S: AsRef<str>, P: Into<PathBuf>>(locale_codes: &[S], ftl_folder: P) -> Self {
        let mut langs = vec![];
        let mut supported = vec![];
        let ftl_folder = ftl_folder.into();
        for code in locale_codes {
            if let Ok(lang) = code.as_ref().parse::<LanguageIdentifier>() {
                langs.push(lang.clone());
                if let Some(path) = lang_folder(lang, &ftl_folder) {
                    supported.push(path);
                }
            }
        }
        // add fallback date/time
        langs.push("en_US".parse().unwrap());

        Self {
            inner: Arc::new(Mutex::new(I18nInner {
                langs,
                available_ftl_folders: supported,
                cache: Default::default(),
            })),
        }
    }

    pub fn get(&self, group: StringsGroup) -> Arc<I18nCategory> {
        self.inner.lock().unwrap().get(group)
    }
}

struct I18nInner {
    // all preferred languages of the user, used for date/time processing
    langs: Vec<LanguageIdentifier>,
    // the available ftl folder subset of the user's preferred languages
    available_ftl_folders: Vec<PathBuf>,
    cache: HashMap<StringsGroup, Arc<I18nCategory>>,
}

impl I18nInner {
    pub fn get(&mut self, group: StringsGroup) -> Arc<I18nCategory> {
        let langs = &self.langs;
        let avail = &self.available_ftl_folders;

        self.cache
            .entry(group)
            .or_insert_with(|| Arc::new(I18nCategory::new(langs, avail, group)))
            .clone()
    }
}

pub struct I18nCategory {
    // bundles in preferred language order, with fallback English as the
    // last element
    bundles: Vec<FluentBundle<FluentResource>>,
}

impl I18nCategory {
    pub fn new(langs: &[LanguageIdentifier], preferred: &[PathBuf], group: StringsGroup) -> Self {
        let mut bundles = Vec::with_capacity(preferred.len() + 1);
        for ftl_folder in preferred {
            if let Some(text) = localized_ftl_for_group(group, ftl_folder) {
                if let Some(mut bundle) = get_bundle(text, langs) {
                    if cfg!(test) {
                        bundle.set_use_isolating(false);
                    }
                    bundles.push(bundle);
                } else {
                    error!("Failed to create bundle for {:?} {:?}", ftl_folder, group);
                }
            }
        }

        let mut fallback_bundle = get_bundle(ftl_fallback_for_group(group), langs).unwrap();
        if cfg!(test) {
            fallback_bundle.set_use_isolating(false);
        }

        bundles.push(fallback_bundle);

        Self { bundles }
    }

    /// Get translation with zero arguments.
    pub fn tr(&self, key: &str) -> Cow<str> {
        self.tr_(key, None)
    }

    /// Get translation with one or more arguments.
    pub fn trn(&self, key: &str, args: FluentArgs) -> String {
        self.tr_(key, Some(args)).into()
    }

    fn tr_<'a>(&'a self, key: &str, args: Option<FluentArgs>) -> Cow<'a, str> {
        for bundle in &self.bundles {
            let msg = match bundle.get_message(key) {
                Some(msg) => msg,
                // not translated in this bundle
                None => continue,
            };

            let pat = match msg.value {
                Some(val) => val,
                // empty value
                None => continue,
            };

            let mut errs = vec![];
            let out = bundle.format_pattern(pat, args.as_ref(), &mut errs);
            if !errs.is_empty() {
                error!("Error(s) in translation '{}': {:?}", key, errs);
            }
            // clone so we can discard args
            return out.to_string().into();
        }

        format!("Missing translation key: {}", key).into()
    }
}

#[cfg(test)]
mod test {
    use crate::i18n::StringsGroup;
    use crate::i18n::{tr_args, I18n};
    use std::path::PathBuf;

    #[test]
    fn i18n() {
        // English fallback
        let i18n = I18n::new(&["zz"], "../../tests/support");
        let cat = i18n.get(StringsGroup::Test);
        assert_eq!(cat.tr("valid-key"), "a valid key");
        assert_eq!(
            cat.tr("invalid-key"),
            "Missing translation key: invalid-key"
        );

        assert_eq!(
            cat.trn("two-args-key", tr_args!["one"=>1, "two"=>"2"]),
            "two args: 1 and 2"
        );

        // commented out to avoid scary warning during unit tests
        //        assert_eq!(
        //            cat.trn("two-args-key", tr_args!["one"=>"testing error reporting"]),
        //            "two args: testing error reporting and {$two}"
        //        );

        assert_eq!(cat.trn("plural", tr_args!["hats"=>1]), "You have 1 hat.");
        assert_eq!(cat.trn("plural", tr_args!["hats"=>3]), "You have 3 hats.");

        // Other language
        let mut d = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        d.push("tests/support");
        let i18n = I18n::new(&["ja_JP"], d);
        let cat = i18n.get(StringsGroup::Test);
        assert_eq!(cat.tr("valid-key"), "キー");
        assert_eq!(cat.tr("only-in-english"), "not translated");
        assert_eq!(
            cat.tr("invalid-key"),
            "Missing translation key: invalid-key"
        );

        assert_eq!(
            cat.trn("two-args-key", tr_args!["one"=>1, "two"=>"2"]),
            "1と2"
        );
    }
}
