// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::fmt::Write as WriteFmt;
use std::io::BufWriter;
use std::io::Write;
use std::path::Path;

use anki_io::create_dir_all;
use anki_io::create_file;
use anyhow::Result;
use inflections::Inflect;
use prost_reflect::DescriptorPool;
use prost_reflect::MethodDescriptor;
use prost_reflect::ServiceDescriptor;

use crate::utils::Comments;

pub(crate) fn write_ts_interface(pool: &DescriptorPool) -> Result<()> {
    let root = Path::new("../../out/ts/lib/anki");
    create_dir_all(root)?;

    for service in pool.services() {
        if service.name() == "AnkidroidService" {
            continue;
        }
        let service_name = service.name().replace("Service", "").to_snake_case();
        let comments = Comments::from_file(service.parent_file().file_descriptor_proto());

        write_dts_file(root, &service_name, &service, &comments)?;
        write_js_file(root, &service_name, &service, &comments)?;
    }

    Ok(())
}

fn write_dts_file(
    root: &Path,
    service_name: &str,
    service: &ServiceDescriptor,
    comments: &Comments,
) -> Result<()> {
    let output_path = root.join(format!("{service_name}_service.d.ts"));
    let mut out = BufWriter::new(create_file(output_path)?);
    write_dts_header(&mut out)?;

    let mut referenced_packages = HashSet::new();
    let mut method_text = String::new();
    for method in service.methods() {
        let method = MethodDetails::from_descriptor(&method, comments);
        record_referenced_type(&mut referenced_packages, &method.input_type)?;
        record_referenced_type(&mut referenced_packages, &method.output_type)?;
        write_dts_method(&method, &mut method_text)?;
    }

    write_imports(referenced_packages, &mut out)?;
    write!(out, "{}", method_text)?;
    Ok(())
}

fn write_dts_header(out: &mut impl std::io::Write) -> Result<()> {
    out.write_all(
        br#"// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; https://www.gnu.org/licenses/agpl.html

import type { PlainMessage } from "@bufbuild/protobuf";
import type { PostProtoOptions } from "../post";
"#,
    )?;
    Ok(())
}

fn write_imports(referenced_packages: HashSet<String>, out: &mut impl Write) -> Result<()> {
    for package in referenced_packages {
        writeln!(
            out,
            "import * as {} from \"./{}_pb\";",
            package,
            package.to_snake_case()
        )?;
    }
    Ok(())
}

fn write_dts_method(
    MethodDetails {
        method_name,
        input_type,
        output_type,
        comments,
    }: &MethodDetails,
    out: &mut String,
) -> Result<()> {
    let comments = format_comments(comments);
    writeln!(
        out,
        r#"{comments}export declare function {method_name}(input: PlainMessage<{input_type}>, options?: PostProtoOptions): Promise<{output_type}>;"#
    )?;
    Ok(())
}

fn write_js_file(
    root: &Path,
    service_name: &str,
    service: &ServiceDescriptor,
    comments: &Comments,
) -> Result<()> {
    let output_path = root.join(format!("{service_name}_service.js"));
    let mut out = BufWriter::new(create_file(output_path)?);
    write_js_header(&mut out)?;

    let mut referenced_packages = HashSet::new();
    let mut method_text = String::new();
    for method in service.methods() {
        let method = MethodDetails::from_descriptor(&method, comments);
        record_referenced_type(&mut referenced_packages, &method.input_type)?;
        record_referenced_type(&mut referenced_packages, &method.output_type)?;
        write_js_method(&method, &mut method_text)?;
    }

    write_imports(referenced_packages, &mut out)?;
    write!(out, "{}", method_text)?;
    Ok(())
}

fn write_js_header(out: &mut impl std::io::Write) -> Result<()> {
    out.write_all(
        br#"// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; https://www.gnu.org/licenses/agpl.html

import { postProto } from "../post";
"#,
    )?;
    Ok(())
}

fn write_js_method(
    MethodDetails {
        method_name,
        input_type,
        output_type,
        ..
    }: &MethodDetails,
    out: &mut String,
) -> Result<()> {
    write!(
        out,
        r#"export async function {method_name}(input, options = {{}}) {{
    return await postProto("{method_name}", new {input_type}(input), {output_type}, options);
}}
"#
    )?;
    Ok(())
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
    fn from_descriptor(method: &MethodDescriptor, comments: &Comments) -> MethodDetails {
        let name = method.name().to_camel_case();
        let input_type = full_name_to_imported_reference(method.input().full_name());
        let output_type = full_name_to_imported_reference(method.output().full_name());
        let comments = comments.get_for_path(method.path());
        Self {
            method_name: name,
            input_type,
            output_type,
            comments: comments.map(ToString::to_string),
        }
    }
}

fn record_referenced_type(
    referenced_packages: &mut HashSet<String>,
    type_name: &str,
) -> Result<()> {
    referenced_packages.insert(type_name.split('.').next().unwrap().to_string());
    Ok(())
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
