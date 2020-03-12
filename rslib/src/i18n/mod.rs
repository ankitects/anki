// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::log::{error, Logger};
use fluent::{FluentArgs, FluentBundle, FluentResource, FluentValue};
use intl_memoizer::IntlLangMemoizer;
use num_format::Locale;
use std::borrow::Cow;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use unic_langid::LanguageIdentifier;

mod autogen;
use crate::i18n::autogen::FLUENT_KEYS;

pub use fluent::fluent_args as tr_args;

pub use crate::backend_proto::FluentString as FString;

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
pub use tr_strs;

/// The folder containing ftl files for the provided language.
/// If a fully qualified folder exists (eg, en_GB), return that.
/// Otherwise, try the language alone (eg en).
/// If neither folder exists, return None.
fn lang_folder(lang: Option<&LanguageIdentifier>, ftl_folder: &Path) -> Option<PathBuf> {
    if let Some(lang) = lang {
        if let Some(region) = lang.region() {
            let path = ftl_folder.join(format!("{}_{}", lang.language(), region));
            if fs::metadata(&path).is_ok() {
                return Some(path);
            }
        }
        let path = ftl_folder.join(lang.language());
        if fs::metadata(&path).is_ok() {
            Some(path)
        } else {
            None
        }
    } else {
        // fallback folder
        let path = ftl_folder.join("templates");
        if fs::metadata(&path).is_ok() {
            Some(path)
        } else {
            None
        }
    }
}

/// Get the template/English resource text for the given group.
/// These are embedded in the binary.
fn ftl_template_text() -> String {
    include_str!("ftl/template.ftl").to_string()
}

fn ftl_localized_text(lang: &LanguageIdentifier) -> Option<String> {
    Some(
        match lang.language() {
            "en" => {
                match lang.region() {
                    Some("GB") | Some("AU") => include_str!("ftl/en-GB.ftl"),
                    // use fallback language instead
                    _ => return None,
                }
            }
            "zh" => match lang.region() {
                Some("TW") | Some("HK") => include_str!("ftl/zh-TW.ftl"),
                _ => include_str!("ftl/zh-CN.ftl"),
            },
            "pt" => {
                if let Some("PT") = lang.region() {
                    include_str!("ftl/pt-PT.ftl")
                } else {
                    include_str!("ftl/pt-BR.ftl")
                }
            }
            "ga" => include_str!("ftl/ga-IE.ftl"),
            "hy" => include_str!("ftl/hy-AM.ftl"),
            "nb" => include_str!("ftl/nb-NO.ftl"),
            "sv" => include_str!("ftl/sv-SE.ftl"),
            "jbo" => include_str!("ftl/jbo.ftl"),
            "kab" => include_str!("ftl/kab.ftl"),
            "af" => include_str!("ftl/af.ftl"),
            "ar" => include_str!("ftl/ar.ftl"),
            "bg" => include_str!("ftl/bg.ftl"),
            "ca" => include_str!("ftl/ca.ftl"),
            "cs" => include_str!("ftl/cs.ftl"),
            "da" => include_str!("ftl/da.ftl"),
            "de" => include_str!("ftl/de.ftl"),
            "el" => include_str!("ftl/el.ftl"),
            "eo" => include_str!("ftl/eo.ftl"),
            "es" => include_str!("ftl/es.ftl"),
            "et" => include_str!("ftl/et.ftl"),
            "eu" => include_str!("ftl/eu.ftl"),
            "fa" => include_str!("ftl/fa.ftl"),
            "fi" => include_str!("ftl/fi.ftl"),
            "fr" => include_str!("ftl/fr.ftl"),
            "gl" => include_str!("ftl/gl.ftl"),
            "he" => include_str!("ftl/he.ftl"),
            "hr" => include_str!("ftl/hr.ftl"),
            "hu" => include_str!("ftl/hu.ftl"),
            "it" => include_str!("ftl/it.ftl"),
            "ja" => include_str!("ftl/ja.ftl"),
            "ko" => include_str!("ftl/ko.ftl"),
            "la" => include_str!("ftl/la.ftl"),
            "mn" => include_str!("ftl/mn.ftl"),
            "mr" => include_str!("ftl/mr.ftl"),
            "ms" => include_str!("ftl/ms.ftl"),
            "nl" => include_str!("ftl/nl.ftl"),
            "oc" => include_str!("ftl/oc.ftl"),
            "pl" => include_str!("ftl/pl.ftl"),
            "ro" => include_str!("ftl/ro.ftl"),
            "ru" => include_str!("ftl/ru.ftl"),
            "sk" => include_str!("ftl/sk.ftl"),
            "sl" => include_str!("ftl/sl.ftl"),
            "sr" => include_str!("ftl/sr.ftl"),
            "th" => include_str!("ftl/th.ftl"),
            "tr" => include_str!("ftl/tr.ftl"),
            "uk" => include_str!("ftl/uk.ftl"),
            "vi" => include_str!("ftl/vi.ftl"),
            _ => return None,
        }
        .to_string(),
    )
}

/// Return the text from any .ftl files in the given folder.
fn ftl_external_text(folder: &Path) -> Result<String> {
    let mut buf = String::new();
    for entry in fs::read_dir(folder)? {
        let entry = entry?;
        let fname = entry
            .file_name()
            .into_string()
            .unwrap_or_else(|_| "".into());
        if !fname.ends_with(".ftl") {
            continue;
        }
        buf += &fs::read_to_string(entry.path())?
    }

    Ok(buf)
}

/// Parse resource text into an AST for inclusion in a bundle.
/// Returns None if text contains errors.
/// extra_text may contain resources loaded from the filesystem
/// at runtime. If it contains errors, they will not prevent a
/// bundle from being returned.
fn get_bundle(
    text: String,
    extra_text: String,
    locales: &[LanguageIdentifier],
    log: &Logger,
) -> Option<FluentBundle<FluentResource>> {
    let res = FluentResource::try_new(text)
        .map_err(|e| {
            error!(log, "Unable to parse translations file: {:?}", e);
        })
        .ok()?;

    let mut bundle: FluentBundle<FluentResource> = FluentBundle::new(locales);
    bundle
        .add_resource(res)
        .map_err(|e| {
            error!(log, "Duplicate key detected in translation file: {:?}", e);
        })
        .ok()?;

    if !extra_text.is_empty() {
        match FluentResource::try_new(extra_text) {
            Ok(res) => bundle.add_resource_overriding(res),
            Err((_res, e)) => error!(log, "Unable to parse translations file: {:?}", e),
        }
    }

    // disable isolation characters in test mode
    if cfg!(test) {
        bundle.set_use_isolating(false);
    }

    // add numeric formatter
    set_bundle_formatter_for_langs(&mut bundle, locales);

    Some(bundle)
}

/// Get a bundle that includes any filesystem overrides.
fn get_bundle_with_extra(
    text: String,
    lang: Option<&LanguageIdentifier>,
    ftl_folder: &Path,
    locales: &[LanguageIdentifier],
    log: &Logger,
) -> Option<FluentBundle<FluentResource>> {
    let extra_text = if let Some(path) = lang_folder(lang, &ftl_folder) {
        match ftl_external_text(&path) {
            Ok(text) => text,
            Err(e) => {
                error!(log, "Error reading external FTL files: {:?}", e);
                "".into()
            }
        }
    } else {
        "".into()
    };

    get_bundle(text, extra_text, locales, log)
}

#[derive(Clone)]
pub struct I18n {
    inner: Arc<Mutex<I18nInner>>,
    log: Logger,
}

impl I18n {
    pub fn new<S: AsRef<str>, P: Into<PathBuf>>(
        locale_codes: &[S],
        ftl_folder: P,
        log: Logger,
    ) -> Self {
        let ftl_folder = ftl_folder.into();

        let mut langs = vec![];
        let mut bundles = Vec::with_capacity(locale_codes.len() + 1);

        for code in locale_codes {
            let code = code.as_ref();
            if let Ok(lang) = code.parse::<LanguageIdentifier>() {
                langs.push(lang.clone());
            }
        }
        // add fallback date/time
        langs.push("en_US".parse().unwrap());

        for lang in &langs {
            // if the language is bundled in the binary
            if let Some(text) = ftl_localized_text(lang) {
                if let Some(bundle) =
                    get_bundle_with_extra(text, Some(lang), &ftl_folder, &langs, &log)
                {
                    bundles.push(bundle);
                } else {
                    error!(log, "Failed to create bundle for {:?}", lang.language())
                }
            }

            // if English was listed, any further preferences are skipped,
            // as the template has 100% coverage, and we need to ensure
            // it is tried prior to any other langs. But we do keep a file
            // if one was returned, to allow locale English variants to take
            // priority over the template.
            if lang.language() == "en" {
                break;
            }
        }

        // add English templates
        let template_bundle =
            get_bundle_with_extra(ftl_template_text(), None, &ftl_folder, &langs, &log).unwrap();
        bundles.push(template_bundle);

        Self {
            inner: Arc::new(Mutex::new(I18nInner { bundles })),
            log,
        }
    }

    /// Get translation with zero arguments.
    pub fn tr(&self, key: FString) -> Cow<str> {
        let key = FLUENT_KEYS[key as usize];
        self.tr_(key, None)
    }

    /// Get translation with one or more arguments.
    pub fn trn(&self, key: FString, args: FluentArgs) -> String {
        let key = FLUENT_KEYS[key as usize];
        self.tr_(key, Some(args)).into()
    }

    fn tr_<'a>(&'a self, key: &str, args: Option<FluentArgs>) -> Cow<'a, str> {
        for bundle in &self.inner.lock().unwrap().bundles {
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
                error!(self.log, "Error(s) in translation '{}': {:?}", key, errs);
            }
            // clone so we can discard args
            return out.to_string().into();
        }

        // return the key name if it was missing
        key.to_string().into()
    }
}

struct I18nInner {
    // bundles in preferred language order, with template English as the
    // last element
    bundles: Vec<FluentBundle<FluentResource>>,
}

fn set_bundle_formatter_for_langs<T>(bundle: &mut FluentBundle<T>, langs: &[LanguageIdentifier]) {
    let num_formatter = NumberFormatter::new(langs);
    let formatter = move |val: &FluentValue, _intls: &Mutex<IntlLangMemoizer>| -> Option<String> {
        match val {
            FluentValue::Number(n) => {
                let mut num = n.clone();
                num.options.maximum_fraction_digits = Some(2);
                Some(num_formatter.format(num.as_string().to_string()))
            }
            _ => None,
        }
    };

    bundle.set_formatter(Some(formatter));
}

fn first_available_num_format_locale(langs: &[LanguageIdentifier]) -> Option<Locale> {
    for lang in langs {
        if let Some(locale) = num_format_locale(lang) {
            return Some(locale);
        }
    }
    None
}

// try to locate a num_format locale for a given language identifier
fn num_format_locale(lang: &LanguageIdentifier) -> Option<Locale> {
    // region provided?
    if let Some(region) = lang.region() {
        let code = format!("{}_{}", lang.language(), region);
        if let Ok(locale) = Locale::from_name(code) {
            return Some(locale);
        }
    }
    // try the language alone
    Locale::from_name(lang.language()).ok()
}

struct NumberFormatter {
    decimal_separator: &'static str,
}

impl NumberFormatter {
    fn new(langs: &[LanguageIdentifier]) -> Self {
        if let Some(locale) = first_available_num_format_locale(langs) {
            Self {
                decimal_separator: locale.decimal(),
            }
        } else {
            // fallback on English defaults
            Self {
                decimal_separator: ".",
            }
        }
    }

    /// Given a pre-formatted number, change the decimal separator as appropriate.
    fn format(&self, num: String) -> String {
        if self.decimal_separator != "." {
            num.replace(".", self.decimal_separator)
        } else {
            num
        }
    }
}

#[cfg(test)]
mod test {
    use crate::i18n::NumberFormatter;
    use crate::i18n::{tr_args, I18n};
    use crate::log;
    use std::path::PathBuf;
    use unic_langid::langid;

    #[test]
    fn numbers() {
        let fmter = NumberFormatter::new(&[langid!("pl-PL")]);
        assert_eq!(&fmter.format("1.007".to_string()), "1,007");
    }

    #[test]
    fn i18n() {
        let mut ftl_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
        ftl_dir.push("tests/support/ftl");

        let log = log::terminal();

        // English template
        let i18n = I18n::new(&["zz"], &ftl_dir, log.clone());
        assert_eq!(i18n.tr_("valid-key", None), "a valid key");
        assert_eq!(i18n.tr_("invalid-key", None), "invalid-key");

        assert_eq!(
            i18n.tr_("two-args-key", Some(tr_args!["one"=>1.1, "two"=>"2"])),
            "two args: 1.1 and 2"
        );

        assert_eq!(
            i18n.tr_("plural", Some(tr_args!["hats"=>1.0])),
            "You have 1 hat."
        );
        assert_eq!(
            i18n.tr_("plural", Some(tr_args!["hats"=>1.1])),
            "You have 1.1 hats."
        );
        assert_eq!(
            i18n.tr_("plural", Some(tr_args!["hats"=>3])),
            "You have 3 hats."
        );

        // Another language
        let i18n = I18n::new(&["ja_JP"], &ftl_dir, log.clone());
        assert_eq!(i18n.tr_("valid-key", None), "キー");
        assert_eq!(i18n.tr_("only-in-english", None), "not translated");
        assert_eq!(i18n.tr_("invalid-key", None), "invalid-key");

        assert_eq!(
            i18n.tr_("two-args-key", Some(tr_args!["one"=>1, "two"=>"2"])),
            "1と2"
        );

        // Decimal separator
        let i18n = I18n::new(&["pl-PL"], &ftl_dir, log.clone());
        // falls back on English, but with Polish separators
        assert_eq!(
            i18n.tr_("two-args-key", Some(tr_args!["one"=>1, "two"=>2.07])),
            "two args: 1 and 2,07"
        );
    }
}
