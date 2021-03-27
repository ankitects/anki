// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Write strings to a strings.rs file that will be compiled into the binary.

use inflections::Inflect;
use std::{fmt::Write, fs, path::PathBuf};

use crate::{
    extract::{Module, Translation},
    gather::{TranslationsByFile, TranslationsByLang},
};

pub fn write_strings(map: &TranslationsByLang, modules: &[Module]) {
    let mut buf = String::new();

    // lang->module map
    write_lang_map(map, &mut buf);
    // module name->translations
    write_translations_by_module(map, &mut buf);
    // ordered list of translations by module
    write_translation_key_index(modules, &mut buf);
    write_legacy_tr_enum(modules, &mut buf);
    // methods to generate messages
    write_methods(modules, &mut buf);

    let dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let path = dir.join("strings.rs");
    fs::write(&path, buf).unwrap();
}

fn write_methods(modules: &[Module], buf: &mut String) {
    buf.push_str(
        r#"
use crate::I18n;
use crate::tr_args;
use fluent::FluentValue;
use std::borrow::Cow;

impl I18n {
"#,
    );
    for module in modules {
        for translation in &module.translations {
            let func = translation.key.to_snake_case();
            let key = &translation.key;
            let doc = translation.text.replace("\n", " ");
            let in_args;
            let out_args;
            if translation.variables.is_empty() {
                in_args = "".to_string();
                out_args = ", None".to_string();
            } else {
                in_args = build_in_args(translation);
                out_args = build_out_args(translation);
            }

            writeln!(
                buf,
                r#"
    /// {doc}
    #[inline]
    pub fn {func}<'a>(&'a self{in_args}) -> Cow<'a, str> {{
        self.tr_("{key}"{out_args})
    }}"#,
                func = func,
                key = key,
                doc = doc,
                in_args = in_args,
                out_args = out_args,
            )
            .unwrap();
        }
    }

    buf.push_str("}\n");
}

fn build_out_args(translation: &Translation) -> String {
    let v: Vec<_> = translation
        .variables
        .iter()
        .map(|var| {
            format!(
                r#""{fluent_name}"=>{rust_name}"#,
                fluent_name = var.name,
                rust_name = var.name.to_snake_case()
            )
        })
        .collect();
    format!(", Some(tr_args![{}])", v.join(", "))
}

fn build_in_args(translation: &Translation) -> String {
    let v: Vec<_> = translation
        .variables
        .iter()
        .map(|var| {
            // let kind = match var.kind {
            //     VariableKind::Int => "i64",
            //     VariableKind::Float => "f64",
            //     VariableKind::String => "&str",
            //     VariableKind::Any => "&str",
            // };
            let kind = "impl Into<FluentValue<'a>>";
            format!("{}: {}", var.name.to_snake_case(), kind)
        })
        .collect();
    format!(", {}", v.join(", "))
}

fn write_legacy_tr_enum(modules: &[Module], buf: &mut String) {
    buf.push_str("pub enum LegacyKey {\n");
    for module in modules {
        for translation in &module.translations {
            let key = translation.key.to_pascal_case();
            let number = module.index * 1000 + translation.index;
            writeln!(buf, r#"    {key} = {number},"#, key = key, number = number).unwrap();
        }
    }

    buf.push_str("}\n");
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
        writeln!(
            buf,
            r###"        "{module}" => r##"{contents}"##,"###,
            module = module,
            contents = contents
        )
        .unwrap();
    }

    buf.push_str("};\n");
}

fn lang_constant_name(lang: &str) -> String {
    lang.to_ascii_uppercase().replace("-", "_")
}

fn module_constant_name(module: &str) -> String {
    format!("{}_KEYS", module.to_ascii_uppercase())
}
