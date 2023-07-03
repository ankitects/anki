// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fmt::Write;
use std::path::PathBuf;

use anki_io::create_dir_all;
use anki_io::write_file_if_changed;
use anyhow::Result;
use inflections::Inflect;
use itertools::Itertools;

use crate::extract::Module;
use crate::extract::Variable;
use crate::extract::VariableKind;

pub fn write_ts_interface(modules: &[Module]) -> Result<()> {
    let mut dts_out = header();
    let mut js_out = header();
    write_translate_method(&mut js_out);
    dts_out.push_str("export declare const funcs: any;\n");

    render_module_map(modules, &mut dts_out, &mut js_out);
    render_methods(modules, &mut dts_out, &mut js_out);

    if let Ok(path) = env::var("STRINGS_JS") {
        let path = PathBuf::from(path);
        create_dir_all(path.parent().unwrap())?;
        write_file_if_changed(path, js_out)?;
    }
    if let Ok(path) = env::var("STRINGS_DTS") {
        let path = PathBuf::from(path);
        create_dir_all(path.parent().unwrap())?;
        write_file_if_changed(path, dts_out)?;
    }

    Ok(())
}

fn render_module_map(modules: &[Module], dts_out: &mut String, js_out: &mut String) {
    dts_out.push_str("export declare enum ModuleName {\n");
    js_out.push_str("export const ModuleName = {};\n");
    for module in modules {
        let name = &module.name;
        let upper = name.to_upper_case();
        writeln!(dts_out, r#"    {upper} = "{name}","#).unwrap();
        writeln!(js_out, r#"ModuleName["{upper}"] = "{name}";"#).unwrap();
    }
    dts_out.push('}');
}

fn render_methods(modules: &[Module], dts_out: &mut String, js_out: &mut String) {
    for module in modules {
        for translation in &module.translations {
            let text = &translation.text;
            let key = &translation.key;
            let func_name = key.replace('-', "_").to_camel_case();
            let arg_types = get_arg_types(&translation.variables);
            let args = get_args(&translation.variables);
            let maybe_args = if translation.variables.is_empty() {
                ""
            } else {
                "args"
            };
            writeln!(
                dts_out,
                "
/** {text} */
export declare function {func_name}({arg_types}): string;",
            )
            .unwrap();
            writeln!(
                js_out,
                r#"
export function {func_name}({maybe_args}) {{
    return translate("{key}", {args})
}}"#,
            )
            .unwrap();
        }
    }
}

fn get_args(variables: &[Variable]) -> String {
    if variables.is_empty() {
        "".into()
    } else if variables
        .iter()
        .all(|v| v.name == typescript_arg_name(&v.name))
    {
        // can use as-is
        "args".into()
    } else {
        let out = variables
            .iter()
            .map(|v| format!("\"{}\": args.{}", v.name, typescript_arg_name(&v.name)))
            .join(", ");
        format!("{{{}}}", out)
    }
}

fn typescript_arg_name(name: &str) -> String {
    let name = name.replace('-', "_").to_camel_case();
    if name == "new" {
        "new_".into()
    } else {
        name
    }
}

fn write_translate_method(buf: &mut String) {
    buf.push_str(
        "
// tslib is responsible for injecting getMessage helper in
export const funcs = {};

function translate(key, args = {}) {
    return funcs.getMessage(key, args) ?? `missing key: ${key}`;
}
",
    );
}

fn header() -> String {
    "// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"
    .to_string()
}

fn get_arg_types(args: &[Variable]) -> String {
    let args = args
        .iter()
        .map(|arg| format!("{}: {}", arg.name.to_camel_case(), arg_kind(&arg.kind)))
        .join(", ");
    if args.is_empty() {
        "".into()
    } else {
        format!("args: {{{args}}}",)
    }
}

fn arg_kind(kind: &VariableKind) -> &str {
    match kind {
        VariableKind::Int | VariableKind::Float => "number",
        VariableKind::String => "string",
        VariableKind::Any => "number | string",
    }
}
