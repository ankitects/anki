// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::log::{error, Logger};
use fluent::{concurrent::FluentBundle, FluentArgs, FluentResource, FluentValue};
use num_format::Locale;
use serde::Serialize;
use std::borrow::Cow;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use unic_langid::LanguageIdentifier;

include!(concat!(env!("OUT_DIR"), "/fluent_keys.rs"));

pub use crate::fluent_proto::FluentString as TR;
pub use fluent::fluent_args as tr_args;

/// Helper for creating args with &strs
#[macro_export]
macro_rules! tr_strs {
    ( $($key:expr => $value:expr),* ) => {
        {
            let mut args: fluent::FluentArgs = fluent::FluentArgs::new();
            $(
                args.add($key, $value.to_string().into());
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
fn lang_folder(lang: &Option<LanguageIdentifier>, ftl_root_folder: &Path) -> Option<PathBuf> {
    if let Some(lang) = lang {
        if let Some(region) = lang.region {
            let path = ftl_root_folder.join(format!("{}_{}", lang.language, region));
            if fs::metadata(&path).is_ok() {
                return Some(path);
            }
        }
        let path = ftl_root_folder.join(lang.language.to_string());
        if fs::metadata(&path).is_ok() {
            Some(path)
        } else {
            None
        }
    } else {
        // fallback folder
        let path = ftl_root_folder.join("templates");
        if fs::metadata(&path).is_ok() {
            Some(path)
        } else {
            None
        }
    }
}

#[cfg(feature = "translations")]
macro_rules! ftl_path {
    ( $fname: expr ) => {
        include_str!(concat!(env!("OUT_DIR"), "/", $fname))
    };
}

#[cfg(not(feature = "translations"))]
macro_rules! ftl_path {
    ( "template.ftl" ) => {
        include_str!(concat!(env!("OUT_DIR"), "/template.ftl"))
    };
    ( $fname: expr ) => {
        "" // translations not included
    };
}

/// Get the template/English resource text.
fn ftl_template_text() -> &'static str {
    ftl_path!("template.ftl")
}

fn ftl_localized_text(lang: &LanguageIdentifier) -> Option<&'static str> {
    let region = match &lang.region {
        Some(region) => Some(region.as_str()),
        None => None,
    };
    Some(match lang.language.as_str() {
        "en" => {
            match region {
                Some("GB") | Some("AU") => ftl_path!("en-GB.ftl"),
                // use fallback language instead
                _ => return None,
            }
        }
        "zh" => match region {
            Some("TW") | Some("HK") => ftl_path!("zh-TW.ftl"),
            _ => ftl_path!("zh-CN.ftl"),
        },
        "pt" => {
            if let Some("PT") = region {
                ftl_path!("pt-PT.ftl")
            } else {
                ftl_path!("pt-BR.ftl")
            }
        }
        "ga" => ftl_path!("ga-IE.ftl"),
        "hy" => ftl_path!("hy-AM.ftl"),
        "nb" => ftl_path!("nb-NO.ftl"),
        "sv" => ftl_path!("sv-SE.ftl"),
        "jbo" => ftl_path!("jbo.ftl"),
        "kab" => ftl_path!("kab.ftl"),
        "af" => ftl_path!("af.ftl"),
        "ar" => ftl_path!("ar.ftl"),
        "bg" => ftl_path!("bg.ftl"),
        "ca" => ftl_path!("ca.ftl"),
        "cs" => ftl_path!("cs.ftl"),
        "da" => ftl_path!("da.ftl"),
        "de" => ftl_path!("de.ftl"),
        "el" => ftl_path!("el.ftl"),
        "eo" => ftl_path!("eo.ftl"),
        "es" => ftl_path!("es.ftl"),
        "et" => ftl_path!("et.ftl"),
        "eu" => ftl_path!("eu.ftl"),
        "fa" => ftl_path!("fa.ftl"),
        "fi" => ftl_path!("fi.ftl"),
        "fr" => ftl_path!("fr.ftl"),
        "gl" => ftl_path!("gl.ftl"),
        "he" => ftl_path!("he.ftl"),
        "hr" => ftl_path!("hr.ftl"),
        "hu" => ftl_path!("hu.ftl"),
        "it" => ftl_path!("it.ftl"),
        "ja" => ftl_path!("ja.ftl"),
        "ko" => ftl_path!("ko.ftl"),
        "la" => ftl_path!("la.ftl"),
        "mn" => ftl_path!("mn.ftl"),
        "mr" => ftl_path!("mr.ftl"),
        "ms" => ftl_path!("ms.ftl"),
        "nl" => ftl_path!("nl.ftl"),
        "oc" => ftl_path!("oc.ftl"),
        "pl" => ftl_path!("pl.ftl"),
        "ro" => ftl_path!("ro.ftl"),
        "ru" => ftl_path!("ru.ftl"),
        "sk" => ftl_path!("sk.ftl"),
        "sl" => ftl_path!("sl.ftl"),
        "sr" => ftl_path!("sr.ftl"),
        "th" => ftl_path!("th.ftl"),
        "tr" => ftl_path!("tr.ftl"),
        "uk" => ftl_path!("uk.ftl"),
        "vi" => ftl_path!("vi.ftl"),
        _ => return None,
    })
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

/// Some sample text for testing purposes.
fn test_en_text() -> &'static str {
    "
valid-key = a valid key
only-in-english = not translated
two-args-key = two args: {$one} and {$two}
plural = You have {$hats ->
    [one]   1 hat
   *[other] {$hats} hats
}.
"
}

fn test_jp_text() -> &'static str {
    "
valid-key = キー
two-args-key = {$one}と{$two}    
"
}

fn test_pl_text() -> &'static str {
    "
one-arg-key = fake Polish {$one}
"
}

/// Parse resource text into an AST for inclusion in a bundle.
/// Returns None if text contains errors.
/// extra_text may contain resources loaded from the filesystem
/// at runtime. If it contains errors, they will not prevent a
/// bundle from being returned.
fn get_bundle(
    text: &str,
    extra_text: String,
    locales: &[LanguageIdentifier],
    log: &Logger,
) -> Option<FluentBundle<FluentResource>> {
    let res = FluentResource::try_new(text.into())
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
    text: &str,
    lang: Option<LanguageIdentifier>,
    ftl_root_folder: &Path,
    log: &Logger,
) -> Option<FluentBundle<FluentResource>> {
    let mut extra_text = if let Some(path) = lang_folder(&lang, &ftl_root_folder) {
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

    if cfg!(test) {
        // inject some test strings in test mode
        match &lang {
            None => {
                extra_text += test_en_text();
            }
            Some(lang) if lang.language == "ja" => {
                extra_text += test_jp_text();
            }
            Some(lang) if lang.language == "pl" => {
                extra_text += test_pl_text();
            }
            _ => {}
        }
    }

    let mut locales = if let Some(lang) = lang {
        vec![lang]
    } else {
        vec![]
    };
    locales.push("en-US".parse().unwrap());

    get_bundle(text, extra_text, &locales, log)
}

#[derive(Clone)]
pub struct I18n {
    inner: Arc<Mutex<I18nInner>>,
    log: Logger,
}

impl I18n {
    pub fn new<S: AsRef<str>, P: Into<PathBuf>>(
        locale_codes: &[S],
        ftl_root_folder: P,
        log: Logger,
    ) -> Self {
        let ftl_root_folder = ftl_root_folder.into();
        let mut input_langs = vec![];
        let mut bundles = Vec::with_capacity(locale_codes.len() + 1);
        let mut resource_text = vec![];

        for code in locale_codes {
            let code = code.as_ref();
            if let Ok(lang) = code.parse::<LanguageIdentifier>() {
                input_langs.push(lang.clone());
                if lang.language == "en" {
                    // if English was listed, any further preferences are skipped,
                    // as the template has 100% coverage, and we need to ensure
                    // it is tried prior to any other langs.
                    break;
                }
            }
        }

        let mut output_langs = vec![];
        for lang in input_langs {
            // if the language is bundled in the binary
            if let Some(text) = ftl_localized_text(&lang) {
                if let Some(bundle) =
                    get_bundle_with_extra(text, Some(lang.clone()), &ftl_root_folder, &log)
                {
                    resource_text.push(text);
                    bundles.push(bundle);
                    output_langs.push(lang);
                } else {
                    error!(log, "Failed to create bundle for {:?}", lang.language)
                }
            }
        }

        // add English templates
        let template_text = ftl_template_text();
        let template_lang = "en-US".parse().unwrap();
        let template_bundle =
            get_bundle_with_extra(template_text, None, &ftl_root_folder, &log).unwrap();
        resource_text.push(template_text);
        bundles.push(template_bundle);
        output_langs.push(template_lang);

        Self {
            inner: Arc::new(Mutex::new(I18nInner {
                bundles,
                langs: output_langs,
                resource_text,
            })),
            log,
        }
    }

    /// Get translation with zero arguments.
    pub fn tr(&self, key: TR) -> Cow<str> {
        let key = FLUENT_KEYS[key as usize];
        self.tr_(key, None)
    }

    /// Get translation with one or more arguments.
    pub fn trn(&self, key: TR, args: FluentArgs) -> String {
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

    /// Return text from configured locales for use with the JS Fluent implementation.
    pub fn resources_for_js(&self) -> ResourcesForJavascript {
        let inner = self.inner.lock().unwrap();
        ResourcesForJavascript {
            langs: inner.langs.iter().map(ToString::to_string).collect(),
            resources: inner.resource_text.clone(),
        }
    }
}

struct I18nInner {
    // bundles in preferred language order, with template English as the
    // last element
    bundles: Vec<FluentBundle<FluentResource>>,
    langs: Vec<LanguageIdentifier>,
    resource_text: Vec<&'static str>,
}

// Simple number formatting implementation

fn set_bundle_formatter_for_langs<T>(bundle: &mut FluentBundle<T>, langs: &[LanguageIdentifier]) {
    let formatter = if want_comma_as_decimal_separator(langs) {
        format_decimal_with_comma
    } else {
        format_decimal_with_period
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
    if let Some(region) = lang.region {
        let code = format!("{}_{}", lang.language, region);
        if let Ok(locale) = Locale::from_name(code) {
            return Some(locale);
        }
    }
    // try the language alone
    Locale::from_name(lang.language.as_str()).ok()
}

fn want_comma_as_decimal_separator(langs: &[LanguageIdentifier]) -> bool {
    let separator = if let Some(locale) = first_available_num_format_locale(langs) {
        locale.decimal()
    } else {
        "."
    };

    separator == ","
}

fn format_decimal_with_comma(
    val: &fluent::FluentValue,
    _intl: &intl_memoizer::concurrent::IntlLangMemoizer,
) -> Option<String> {
    format_number_values(val, Some(","))
}

fn format_decimal_with_period(
    val: &fluent::FluentValue,
    _intl: &intl_memoizer::concurrent::IntlLangMemoizer,
) -> Option<String> {
    format_number_values(val, None)
}

#[inline]
fn format_number_values(
    val: &fluent::FluentValue,
    alt_separator: Option<&'static str>,
) -> Option<String> {
    match val {
        FluentValue::Number(num) => {
            // create a string with desired maximum digits
            let max_frac_digits = 2;
            let with_max_precision = format!(
                "{number:.precision$}",
                number = num.value,
                precision = max_frac_digits
            );

            // remove any excess trailing zeros
            let mut val: Cow<str> = with_max_precision.trim_end_matches('0').into();

            // adding back any required to meet minimum_fraction_digits
            if let Some(minfd) = num.options.minimum_fraction_digits {
                let pos = val.find('.').expect("expected . in formatted string");
                let frac_num = val.len() - pos - 1;
                let zeros_needed = minfd - frac_num;
                if zeros_needed > 0 {
                    val = format!("{}{}", val, "0".repeat(zeros_needed)).into();
                }
            }

            // lop off any trailing '.'
            let result = val.trim_end_matches('.');

            if let Some(sep) = alt_separator {
                Some(result.replace('.', sep))
            } else {
                Some(result.to_string())
            }
        }
        _ => None,
    }
}

#[derive(Serialize)]
pub struct ResourcesForJavascript {
    langs: Vec<String>,
    resources: Vec<&'static str>,
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::log;
    use std::path::PathBuf;
    use unic_langid::langid;

    #[test]
    fn numbers() {
        assert_eq!(want_comma_as_decimal_separator(&[langid!("en-US")]), false);
        assert_eq!(want_comma_as_decimal_separator(&[langid!("pl-PL")]), true);
    }

    #[test]
    fn i18n() {
        let ftl_dir = PathBuf::from(std::env::var("TEST_SRCDIR").unwrap());
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
        // Polish will use a comma if the string is translated
        assert_eq!(
            i18n.tr_("one-arg-key", Some(tr_args!["one"=>2.07])),
            "fake Polish 2,07"
        );

        // but if it falls back on English, it will use an English separator
        assert_eq!(
            i18n.tr_("two-args-key", Some(tr_args!["one"=>1, "two"=>2.07])),
            "two args: 1 and 2.07"
        );
    }
}
