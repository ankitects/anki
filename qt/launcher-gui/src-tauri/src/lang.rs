// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::RwLock;

use anyhow::anyhow;
use anyhow::Result;
use phf::phf_map;
use phf::phf_ordered_map;
use phf::phf_set;
use tauri::Manager;
use tauri::Runtime;

pub type I18n = anki_i18n::I18n<anki_i18n::Launcher>;
pub type Tr = RwLock<Option<I18n>>;

pub trait I18nExt<R: Runtime> {
    fn setup_tr(&self, locales: &[&str]);
    fn tr(&self) -> Result<I18n>;
}

impl<R: Runtime, T: Manager<R>> I18nExt<R> for T {
    fn setup_tr(&self, locales: &[&str]) {
        *self.state::<Tr>().write().expect("tr lock was poisoned!") = Some(I18n::new(locales));
    }

    fn tr(&self) -> Result<I18n> {
        let tr_state = self.state::<Tr>();
        let guard = tr_state.read().expect("tr lock was poisoned!");
        guard
            .clone()
            .ok_or_else(|| anyhow!("tr was not initialised!"))
    }
}

pub const LANGS: phf::OrderedMap<&'static str, &'static str> = phf_ordered_map! {
    // "af-ZA" => "Afrikaans",
    // "ms-MY" => "Bahasa Melayu",
    // "ca-ES" => "Català",
    // "da-DK" => "Dansk",
    // "de-DE" => "Deutsch",
    // "et-EE" => "Eesti",
    "en-US" => "English (United States)",
    // "en-GB" => "English (United Kingdom)",
    // "es-ES" => "Español",
    // "eo-UY" => "Esperanto",
    // "eu-ES" => "Euskara",
    "fr-FR" => "Français",
    // "gl-ES" => "Galego",
    // "hr-HR" => "Hrvatski",
    // "it-IT" => "Italiano",
    // "jbo-EN" => "lo jbobau",
    // "oc-FR" => "Lenga d'òc",
    // "kk-KZ" => "Қазақша",
    // "hu-HU" => "Magyar",
    // "nl-NL" => "Nederlands",
    // "nb-NO" => "Norsk",
    // "pl-PL" => "Polski",
    // "pt-BR" => "Português Brasileiro",
    // "pt-PT" => "Português",
    // "ro-RO" => "Română",
    // "sk-SK" => "Slovenčina",
    // "sl-SI" => "Slovenščina",
    // "fi-FI" => "Suomi",
    // "sv-SE" => "Svenska",
    // "vi-VN" => "Tiếng Việt",
    // "tr-TR" => "Türkçe",
    // "zh-CN" => "简体中文",
    "ja-JP" => "日本語",
    // "zh-TW" => "繁體中文",
    // "ko-KR" => "한국어",
    // "cs-CZ" => "Čeština",
    // "el-GR" => "Ελληνικά",
    // "bg-BG" => "Български",
    // "mn-MN" => "Монгол хэл",
    // "ru-RU" => "Pусский язык",
    // "sr-SP" => "Српски",
    // "uk-UA" => "Українська мова",
    // "hy-AM" => "Հայերեն",
    // "he-IL" => "עִבְרִית",
    // "yi" => "ייִדיש",
    "ar-SA" => "العربية",
    // "fa-IR" => "فارسی",
    // "th-TH" => "ภาษาไทย",
    // "la-LA" => "Latin",
    // "ga-IE" => "Gaeilge",
    // "be-BY" => "Беларуская мова",
    // "or-OR" => "ଓଡ଼ିଆ",
    // "tl" => "Filipino",
    // "ug" => "ئۇيغۇر",
    // "uz-UZ" => "Oʻzbekcha",
};

pub const LANGS_DEFAULT_REGION: phf::Map<&str, &str> = phf_map! {
    "af" => "af-ZA",
    "ar" => "ar-SA",
    "be" => "be-BY",
    "bg" => "bg-BG",
    "ca" => "ca-ES",
    "cs" => "cs-CZ",
    "da" => "da-DK",
    "de" => "de-DE",
    "el" => "el-GR",
    "en" => "en-US",
    "eo" => "eo-UY",
    "es" => "es-ES",
    "et" => "et-EE",
    "eu" => "eu-ES",
    "fa" => "fa-IR",
    "fi" => "fi-FI",
    "fr" => "fr-FR",
    "gl" => "gl-ES",
    "he" => "he-IL",
    "hr" => "hr-HR",
    "hu" => "hu-HU",
    "hy" => "hy-AM",
    "it" => "it-IT",
    "ja" => "ja-JP",
    "jbo" => "jbo-EN",
    "kk" => "kk-KZ",
    "ko" => "ko-KR",
    "la" => "la-LA",
    "mn" => "mn-MN",
    "ms" => "ms-MY",
    "nl" => "nl-NL",
    "nb" => "nb-NL",
    "no" => "nb-NL",
    "oc" => "oc-FR",
    "or" => "or-OR",
    "pl" => "pl-PL",
    "pt" => "pt-PT",
    "ro" => "ro-RO",
    "ru" => "ru-RU",
    "sk" => "sk-SK",
    "sl" => "sl-SI",
    "sr" => "sr-SP",
    "sv" => "sv-SE",
    "th" => "th-TH",
    "tr" => "tr-TR",
    "uk" => "uk-UA",
    "uz" => "uz-UZ",
    "vi" => "vi-VN",
    "yi" => "yi",
};

pub const LANGS_WITH_REGIONS: phf::Set<&str> = phf_set![
    "en-GB", "ga-IE", "hy-AM", "nb-NO", "nn-NO", "pt-BR", "pt-PT", "sv-SE", "zh-CN", "zh-TW"
];
