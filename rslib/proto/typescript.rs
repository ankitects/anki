// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::fmt::Write as WriteFmt;
use std::path::Path;

use anki_io::create_dir_all;
use anki_io::write_file_if_changed;
use anki_proto_gen::BackendService;
use anki_proto_gen::Method;
use anyhow::Result;
use inflections::Inflect;
use itertools::Itertools;

pub(crate) fn write_ts_interface(services: &[BackendService]) -> Result<()> {
    let root = Path::new("../../out/ts/lib");
    create_dir_all(root)?;

    let mut dts_out = String::new();
    let mut js_out = String::new();
    let mut referenced_packages = HashSet::new();

    for service in services {
        if service.name == "BackendAnkidroidService" {
            continue;
        }

        for method in service.all_methods() {
            let method = MethodDetails::from_method(method);
            record_referenced_type(&mut referenced_packages, &method.input_type);
            record_referenced_type(&mut referenced_packages, &method.output_type);
            write_dts_method(&method, &mut dts_out);
            write_js_method(&method, &mut js_out);
        }
    }

    let imports = imports(referenced_packages);
    write_file_if_changed(
        root.join("backend.d.ts"),
        format!("{}{}{}", dts_header(), imports, dts_out),
    )?;
    write_file_if_changed(
        root.join("backend.js"),
        format!("{}{}{}", js_header(), imports, js_out),
    )?;

    Ok(())
}

fn dts_header() -> String {
    r#"// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; https://www.gnu.org/licenses/agpl.html

import type { PlainMessage } from "@bufbuild/protobuf";
import type { PostProtoOptions } from "./post";
"#
    .into()
}

fn imports(referenced_packages: HashSet<String>) -> String {
    let mut out = String::new();
    for package in referenced_packages.iter().sorted() {
        writeln!(
            &mut out,
            "import * as {} from \"./anki/{}_pb\";",
            package,
            package.to_snake_case()
        )
        .unwrap();
    }
    out
}

fn write_dts_method(
    MethodDetails {
        method_name,
        input_type,
        output_type,
        comments,
    }: &MethodDetails,
    out: &mut String,
) {
    let comments = format_comments(comments);
    writeln!(
        out,
        r#"{comments}export declare function {method_name}(input: PlainMessage<{input_type}>, options?: PostProtoOptions): Promise<{output_type}>;"#
    ).unwrap()
}

fn js_header() -> String {
    r#"// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; https://www.gnu.org/licenses/agpl.html

import { postProto } from "./post";
"#
    .into()
}

fn write_js_method(
    MethodDetails {
        method_name,
        input_type,
        output_type,
        ..
    }: &MethodDetails,
    out: &mut String,
) {
    write!(
        out,
        r#"export async function {method_name}(input, options = {{}}) {{
    return await postProto("{method_name}", new {input_type}(input), {output_type}, options);
}}
"#
    )
    .unwrap();
}

fn format_comments(comments: &Option<String>) -> String {
    comments
        .as_ref()
        .map(|s| format!("/** {s} */\n"))
        .unwrap_or_default()
}

struct MethodDetails {
    method_name: String,
    input_type: String,
    output_type: String,
    comments: Option<String>,
}

impl MethodDetails {
    fn from_method(method: &Method) -> MethodDetails {
        let name = method.name.to_camel_case();
        let input_type = full_name_to_imported_reference(method.proto.input().full_name());
        let output_type = full_name_to_imported_reference(method.proto.output().full_name());
        let comments = method.comments.clone();
        Self {
            method_name: name,
            input_type,
            output_type,
            comments,
        }
    }
}

fn record_referenced_type(referenced_packages: &mut HashSet<String>, type_name: &str) {
    referenced_packages.insert(type_name.split('.').next().unwrap().to_string());
}

// e.g. anki.import_export.ImportResponse ->
// importExport.ImportResponse
fn full_name_to_imported_reference(name: &str) -> String {
    let mut name = name.splitn(3, '.');
    name.next().unwrap();
    format!(
        "{}.{}",
        name.next().unwrap().to_camel_case(),
        name.next().unwrap()
    )
}
