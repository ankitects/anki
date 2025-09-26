// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod generated;
mod generated_launcher;

use std::borrow::Cow;
use std::marker::PhantomData;
use std::sync::Arc;
use std::sync::Mutex;

use fluent::types::FluentNumber;
use fluent::FluentArgs;
use fluent::FluentResource;
use fluent::FluentValue;
use fluent_bundle::bundle::FluentBundle as FluentBundleOrig;
use num_format::Locale;
use serde::Serialize;
use unic_langid::LanguageIdentifier;

type FluentBundle<T> = FluentBundleOrig<T, intl_memoizer::concurrent::IntlLangMemoizer>;

pub use fluent::fluent_args as tr_args;

pub use crate::generated::All;
pub use crate::generated_launcher::Launcher;

pub trait Number: Into<FluentNumber> {
    fn round(self) -> Self;
}
impl Number for i32 {
    #[inline]
    fn round(self) -> Self {
        self
    }
}
impl Number for i64 {
    #[inline]
    fn round(self) -> Self {
        self
    }
}
impl Number for u32 {
    #[inline]
    fn round(self) -> Self {
        self
    }
}
impl Number for f32 {
    // round to 2 decimal places
    #[inline]
    fn round(self) -> Self {
        (self * 100.0).round() / 100.0
    }
}
impl Number for u64 {
    #[inline]
    fn round(self) -> Self {
        self
    }
}
impl Number for usize {
    #[inline]
    fn round(self) -> Self {
        self
    }
}

fn remapped_lang_name(lang: &LanguageIdentifier) -> &str {
    let region = lang.region.as_ref().map(|v| v.as_str());
    match lang.language.as_str() {
        "en" => {
            match region {
                Some("GB") | Some("AU") => "en-GB",
                // go directly to fallback
                _ => "templates",
            }
        }
        "zh" => match region {
            Some("TW") | Some("HK") => "zh-TW",
            _ => "zh-CN",
        },
        "pt" => {
            if let Some("PT") = region {
                "pt-PT"
            } else {
                "pt-BR"
            }
        }
        "ga" => "ga-IE",
        "hy" => "hy-AM",
        "nb" => "nb-NO",
        "sv" => "sv-SE",
        other => other,
    }
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
) -> Option<FluentBundle<FluentResource>> {
    let res = FluentResource::try_new(text.into())
        .map_err(|e| {
            println!("Unable to parse translations file: {e:?}");
        })
        .ok()?;

    let mut bundle: FluentBundle<FluentResource> = FluentBundle::new_concurrent(locales.to_vec());
    bundle
        .add_resource(res)
        .map_err(|e| {
            println!("Duplicate key detected in translation file: {e:?}");
        })
        .ok()?;

    if !extra_text.is_empty() {
        match FluentResource::try_new(extra_text) {
            Ok(res) => bundle.add_resource_overriding(res),
            Err((_res, e)) => println!("Unable to parse translations file: {e:?}"),
        }
    }

    // add numeric formatter
    set_bundle_formatter_for_langs(&mut bundle, locales);

    Some(bundle)
}

/// Get a bundle that includes any filesystem overrides.
fn get_bundle_with_extra(
    text: &str,
    lang: Option<LanguageIdentifier>,
) -> Option<FluentBundle<FluentResource>> {
    let mut extra_text = "".into();
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

    get_bundle(text, extra_text, &locales)
}

pub trait Translations {
    const STRINGS: &phf::Map<&str, &phf::Map<&str, &str>>;
    const KEYS_BY_MODULE: &[&[&str]];
}

#[derive(Clone)]
pub struct I18n<P: Translations = All> {
    inner: Arc<Mutex<I18nInner>>,
    _translations_type: std::marker::PhantomData<P>,
}

impl<P: Translations> I18n<P> {
    fn get_key(module_idx: usize, translation_idx: usize) -> &'static str {
        P::KEYS_BY_MODULE
            .get(module_idx)
            .and_then(|translations| translations.get(translation_idx))
            .cloned()
            .unwrap_or("invalid-module-or-translation-index")
    }

    fn get_modules(langs: &[LanguageIdentifier], desired_modules: &[String]) -> Vec<String> {
        langs
            .iter()
            .cloned()
            .map(|lang| {
                let mut buf = String::new();
                let lang_name = remapped_lang_name(&lang);
                if let Some(strings) = P::STRINGS.get(lang_name) {
                    if desired_modules.is_empty() {
                        // empty list, provide all modules
                        for value in strings.values() {
                            buf.push_str(value)
                        }
                    } else {
                        for module_name in desired_modules {
                            if let Some(text) = strings.get(module_name.as_str()) {
                                buf.push_str(text);
                            }
                        }
                    }
                }
                buf
            })
            .collect()
    }

    /// This temporarily behaves like the older code; in the future we could
    /// either access each &str separately, or load them on demand.
    fn ftl_localized_text(lang: &LanguageIdentifier) -> Option<String> {
        let lang = remapped_lang_name(lang);
        if let Some(module) = P::STRINGS.get(lang) {
            let mut text = String::new();
            for module_text in module.values() {
                text.push_str(module_text)
            }
            Some(text)
        } else {
            None
        }
    }

    pub fn template_only() -> Self {
        Self::new::<&str>(&[])
    }

    pub fn new<S: AsRef<str>>(locale_codes: &[S]) -> Self {
        let mut input_langs = vec![];
        let mut bundles = Vec::with_capacity(locale_codes.len() + 1);

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
            if let Some(text) = Self::ftl_localized_text(&lang).or_else(|| {
                // when testing, allow missing translations
                if cfg!(test) {
                    Some(String::new())
                } else {
                    None
                }
            }) {
                if let Some(bundle) = get_bundle_with_extra(&text, Some(lang.clone())) {
                    bundles.push(bundle);
                    output_langs.push(lang);
                } else {
                    println!("Failed to create bundle for {:?}", lang.language)
                }
            }
        }

        // add English templates
        let template_lang = "en-US".parse().unwrap();
        let template_text = Self::ftl_localized_text(&template_lang).unwrap();
        let template_bundle = get_bundle_with_extra(&template_text, None).unwrap();
        bundles.push(template_bundle);
        output_langs.push(template_lang);

        if locale_codes.is_empty() || cfg!(test) {
            // disable isolation characters in test mode
            for bundle in &mut bundles {
                bundle.set_use_isolating(false);
            }
        }

        Self {
            inner: Arc::new(Mutex::new(I18nInner {
                bundles,
                langs: output_langs,
            })),
            _translations_type: PhantomData,
        }
    }

    pub fn translate_via_index(
        &self,
        module_index: usize,
        message_index: usize,
        args: FluentArgs,
    ) -> String {
        let key = Self::get_key(module_index, message_index);
        self.translate(key, Some(args)).into()
    }

    fn translate<'a>(&'a self, key: &str, args: Option<FluentArgs>) -> Cow<'a, str> {
        for bundle in &self.inner.lock().unwrap().bundles {
            let msg = match bundle.get_message(key) {
                Some(msg) => msg,
                // not translated in this bundle
                None => continue,
            };

            let pat = match msg.value() {
                Some(val) => val,
                // empty value
                None => continue,
            };

            let mut errs = vec![];
            let out = bundle.format_pattern(pat, args.as_ref(), &mut errs);
            if !errs.is_empty() {
                println!("Error(s) in translation '{key}': {errs:?}");
            }
            // clone so we can discard args
            return out.to_string().into();
        }

        // return the key name if it was missing
        key.to_string().into()
    }

    /// Return text from configured locales for use with the JS Fluent
    /// implementation.
    pub fn resources_for_js(&self, desired_modules: &[String]) -> ResourcesForJavascript {
        let inner = self.inner.lock().unwrap();
        let resources = Self::get_modules(&inner.langs, desired_modules);
        ResourcesForJavascript {
            langs: inner.langs.iter().map(ToString::to_string).collect(),
            resources,
        }
    }
}

struct I18nInner {
    // bundles in preferred language order, with template English as the
    // last element
    bundles: Vec<FluentBundle<FluentResource>>,
    langs: Vec<LanguageIdentifier>,
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
    val: &FluentValue,
    _intl: &intl_memoizer::concurrent::IntlLangMemoizer,
) -> Option<String> {
    format_number_values(val, Some(","))
}

fn format_decimal_with_period(
    val: &FluentValue,
    _intl: &intl_memoizer::concurrent::IntlLangMemoizer,
) -> Option<String> {
    format_number_values(val, None)
}

#[inline]
fn format_number_values(val: &FluentValue, alt_separator: Option<&'static str>) -> Option<String> {
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
    resources: Vec<String>,
}

pub fn without_unicode_isolation(s: &str) -> String {
    s.replace(['\u{2068}', '\u{2069}'], "")
}

#[cfg(test)]
mod test {
    use unic_langid::langid;

    use super::*;

    #[test]
    fn numbers() {
        assert!(!want_comma_as_decimal_separator(&[langid!("en-US")]));
        assert!(want_comma_as_decimal_separator(&[langid!("pl-PL")]));
    }

    #[test]
    fn decimal_rounding() {
        let tr = I18n::new(&["en"]);

        assert_eq!(tr.browsing_cards_deleted(1.001), "1 card deleted.");
        assert_eq!(tr.browsing_cards_deleted(1.01), "1.01 cards deleted.");
    }

    #[test]
    fn i18n() {
        // English template
        let tr = I18n::<All>::new(&["zz"]);
        assert_eq!(tr.translate("valid-key", None), "a valid key");
        assert_eq!(tr.translate("invalid-key", None), "invalid-key");

        assert_eq!(
            tr.translate("two-args-key", Some(tr_args!["one"=>1.1, "two"=>"2"])),
            "two args: 1.1 and 2"
        );

        assert_eq!(
            tr.translate("plural", Some(tr_args!["hats"=>1.0])),
            "You have 1 hat."
        );
        assert_eq!(
            tr.translate("plural", Some(tr_args!["hats"=>1.1])),
            "You have 1.1 hats."
        );
        assert_eq!(
            tr.translate("plural", Some(tr_args!["hats"=>3])),
            "You have 3 hats."
        );

        // Another language
        let tr = I18n::<All>::new(&["ja_JP"]);
        assert_eq!(tr.translate("valid-key", None), "キー");
        assert_eq!(tr.translate("only-in-english", None), "not translated");
        assert_eq!(tr.translate("invalid-key", None), "invalid-key");

        assert_eq!(
            tr.translate("two-args-key", Some(tr_args!["one"=>1, "two"=>"2"])),
            "1と2"
        );

        // Decimal separator
        let tr = I18n::<All>::new(&["pl-PL"]);
        // Polish will use a comma if the string is translated
        assert_eq!(
            tr.translate("one-arg-key", Some(tr_args!["one"=>2.07])),
            "fake Polish 2,07"
        );

        // but if it falls back on English, it will use an English separator
        assert_eq!(
            tr.translate("two-args-key", Some(tr_args!["one"=>1, "two"=>2.07])),
            "two args: 1 and 2.07"
        );
    }
}
