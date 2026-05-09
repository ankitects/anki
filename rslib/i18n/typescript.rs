// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
    let mut ts_out = header();
    write_imports(&mut ts_out);

    render_module_map(modules, &mut ts_out);
    render_methods(modules, &mut ts_out);

    if let Some(path) = option_env!("STRINGS_TS") {
        let path = PathBuf::from(path);
        create_dir_all(path.parent().unwrap())?;
        write_file_if_changed(path, ts_out)?;
    }

    Ok(())
}

fn render_module_map(modules: &[Module], ts_out: &mut String) {
    ts_out.push_str("export enum ModuleName {\n");
    for module in modules {
        let name = &module.name;
        let upper = name.to_upper_case();
        writeln!(ts_out, r#"    {upper} = "{name}","#).unwrap();
    }
    ts_out.push('}');
}

fn render_methods(modules: &[Module], ts_out: &mut String) {
    for module in modules {
        for translation in &module.translations {
            let text = &translation.text;
            let key = &translation.key;
            let func_name = key.replace('-', "_").to_camel_case();
            let arg_types = get_arg_types(&translation.variables);
            let args = get_args(&translation.variables);
            let maybe_args = if translation.variables.is_empty() {
                "".to_string()
            } else {
                arg_types
            };
            writeln!(
                ts_out,
                r#"
/** {text} */
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
        format!("{{{out}}}")
    }
}

fn typescript_arg_name(name: &str) -> String {
    name.replace('-', "_").to_camel_case()
}

fn write_imports(buf: &mut String) {
    buf.push_str(
        "
import { translate } from './ftl-helpers';
export { firstLanguage, setBundles } from './ftl-helpers';
export { FluentBundle, FluentResource } from '@fluent/bundle';
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
