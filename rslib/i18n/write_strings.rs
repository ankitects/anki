// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Write strings to a strings.rs file that will be compiled into the binary.

use std::fmt::Write;
use std::fs;
use std::path::PathBuf;

use inflections::Inflect;

use crate::extract::Module;
use crate::extract::Translation;
use crate::extract::VariableKind;
use crate::gather::TranslationsByFile;
use crate::gather::TranslationsByLang;

pub fn write_strings(map: &TranslationsByLang, modules: &[Module], out_fn: &str, tag: &str) {
    let mut buf = String::new();

    // lang->module map
    write_lang_map(map, &mut buf);
    // module name->translations
    write_translations_by_module(map, &mut buf);
    // ordered list of translations by module
    write_translation_key_index(modules, &mut buf);
    // methods to generate messages
    write_methods(modules, &mut buf, tag);

    let dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let path = dir.join(out_fn);
    fs::write(path, buf).unwrap();
}

fn write_methods(modules: &[Module], buf: &mut String, tag: &str) {
    buf.push_str(
        r#"
#[allow(unused_imports)]
use crate::{I18n,Number,Translations};
#[allow(unused_imports)]
use fluent::{FluentValue, FluentArgs};
use std::borrow::Cow;

"#,
    );
    writeln!(buf, "impl I18n<{tag}> {{").unwrap();
    for module in modules {
        for translation in &module.translations {
            let func = translation.key.to_snake_case();
            let key = &translation.key;
            let doc = translation.text.replace('\n', " ");
            let in_args;
            let out_args;
            let var_build;
            if translation.variables.is_empty() {
                in_args = "".to_string();
                out_args = ", None".to_string();
                var_build = "".to_string();
            } else {
                in_args = build_in_args(translation);
                var_build = build_vars(translation);
                out_args = ", Some(args)".to_string();
            }

            writeln!(
                buf,
                r#"
    /// {doc}
    #[inline]
    pub fn {func}<'a>(&'a self{in_args}) -> Cow<'a, str> {{
{var_build}
        self.translate("{key}"{out_args})
    }}"#,
            )
            .unwrap();
        }
    }

    buf.push_str("}\n");
}

fn build_vars(translation: &Translation) -> String {
    if translation.variables.is_empty() {
        "let args = None;\n".into()
    } else {
        let mut buf = String::from(
            r#"
        let mut args = FluentArgs::new();
"#,
        );
        for v in &translation.variables {
            let fluent_name = &v.name;
            let rust_name = v.name.to_snake_case();
            let trailer = match v.kind {
                VariableKind::Any => "",
                VariableKind::Int | VariableKind::Float => ".round().into()",
                VariableKind::String => ".into()",
            };
            writeln!(
                buf,
                r#"        args.set("{fluent_name}", {rust_name}{trailer});"#,
            )
            .unwrap();
        }

        buf
    }
}

fn build_in_args(translation: &Translation) -> String {
    let v: Vec<_> = translation
        .variables
        .iter()
        .map(|var| {
            let kind = match var.kind {
                VariableKind::Int => "impl Number",
                VariableKind::Float => "impl Number",
                VariableKind::String => "impl Into<String>",
                // VariableKind::Any => "&str",
                _ => "impl Into<FluentValue<'a>>",
            };
            format!("{}: {}", var.name.to_snake_case(), kind)
        })
        .collect();
    format!(", {}", v.join(", "))
}

fn write_translation_key_index(modules: &[Module], buf: &mut String) {
    for module in modules {
        writeln!(
            buf,
            "pub(crate) const {key}: [&str; {count}] = [",
            key = module_constant_name(&module.name),
            count = module.translations.len(),
        )
        .unwrap();

        for translation in &module.translations {
            writeln!(buf, r#"    "{key}","#, key = translation.key).unwrap();
        }

        buf.push_str("];\n")
    }

    writeln!(
        buf,
        "pub(crate) const KEYS_BY_MODULE: [&[&str]; {count}] = [",
        count = modules.len(),
    )
    .unwrap();

    for module in modules {
        writeln!(
            buf,
            r#"    &{module_slice},"#,
            module_slice = module_constant_name(&module.name)
        )
        .unwrap();
    }

    buf.push_str("];\n")
}

fn write_lang_map(map: &TranslationsByLang, buf: &mut String) {
    buf.push_str(
        "
pub(crate) const STRINGS: phf::Map<&str, &phf::Map<&str, &str>> = phf::phf_map! {
",
    );

    for lang in map.keys() {
        writeln!(
            buf,
            r#"    "{lang}" => &{constant},"#,
            lang = lang,
            constant = lang_constant_name(lang)
        )
        .unwrap();
    }

    buf.push_str("};\n");
}

fn write_translations_by_module(map: &TranslationsByLang, buf: &mut String) {
    for (lang, modules) in map {
        write_module_map(buf, lang, modules);
    }
}

fn write_module_map(buf: &mut String, lang: &str, modules: &TranslationsByFile) {
    writeln!(
        buf,
        "
pub(crate) const {lang_name}: phf::Map<&str, &str> = phf::phf_map! {{",
        lang_name = lang_constant_name(lang)
    )
    .unwrap();

    for (module, contents) in modules {
        let escaped_contents = escape_unicode_control_chars(contents);
        writeln!(
            buf,
            r###"        "{module}" => r##"{escaped_contents}"##,"###
        )
        .unwrap();
    }

    buf.push_str("};\n");
}

fn escape_unicode_control_chars(input: &str) -> String {
    use regex::Regex;

    static RE: std::sync::OnceLock<Regex> = std::sync::OnceLock::new();
    let re = RE.get_or_init(|| Regex::new(r"[\u{202a}-\u{202e}\u{2066}-\u{2069}]").unwrap());

    re.replace_all(input, |caps: &regex::Captures| {
        let c = caps.get(0).unwrap().as_str().chars().next().unwrap();
        format!("\\u{{{:04x}}}", c as u32)
    })
    .into_owned()
}

fn lang_constant_name(lang: &str) -> String {
    lang.to_ascii_uppercase().replace('-', "_")
}

fn module_constant_name(module: &str) -> String {
    format!("{}_KEYS", module.to_ascii_uppercase())
}
