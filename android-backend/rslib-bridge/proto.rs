// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    io::{BufWriter, Write},
    path::{Path, PathBuf},
    process::Command,
};

use anki_io::{create_dir_all, create_file};
use anki_process::CommandExt;
use anki_proto_gen::{BackendService, Method};
use anyhow::Result;
use inflections::Inflect;
use prost_reflect::{FieldDescriptor, Kind, MessageDescriptor};

fn out_dir() -> PathBuf {
    PathBuf::from(std::env::var("GENERATED_BACKEND_DIR").unwrap())
}

pub(crate) fn write_kotlin_interface(services: &[BackendService]) -> Result<()> {
    let out_dir = out_dir();
    if out_dir.exists() {
        std::fs::remove_dir_all(&out_dir)?;
    }
    create_dir_all(&out_dir)?;
    let output_path = out_dir.join("GeneratedBackend.kt");
    create_dir_all(output_path.parent().unwrap())?;
    let mut out = BufWriter::new(create_file(output_path)?);
    write_header(&mut out)?;

    for service in services {
        for method in service.all_methods() {
            render_method(service, method, &mut out);
        }
    }

    writeln!(&mut out, "}}\n")?;

    build_kotlin_protos(&out_dir)?;

    Ok(())
}

fn render_method(service: &BackendService, method: &Method, out: &mut impl Write) {
    let method_name = method.name.to_camel_case();
    let input = method.proto.input();
    let output = method.proto.output();
    let service_idx = service.index;
    let method_idx = method.index;
    let comments = format_comments(&method.comments);

    // raw bytes
    write!(
        out,
        r#"{comments}    fun {method_name}Raw(input: ByteArray): ByteArray {{
        return runMethodRaw({service_idx}, {method_idx}, input)
    }}
"#
    )
    .unwrap();

    // (possibly destructured) message
    let (input_params, input_assign) = maybe_destructured_input(&input);
    let output_constructor = output.full_name();
    let (output_msg_or_single_field, output_type) = maybe_destructured_output(&output);

    write!(
        out,
        r#"{comments}
    open fun {method_name}({input_params}): {output_type} {{{input_assign}
        val outputData = {method_name}Raw(input.toByteArray())
        val output = {output_constructor}.parseFrom(outputData)
        return {output_msg_or_single_field}
    }}

"#
    )
    .unwrap();
}

fn format_comments(path: &Option<String>) -> String {
    path.as_ref()
        .map(|c| format!("/** {c} */\n"))
        .unwrap_or_default()
}

/// If any of the following apply to the input type:
/// - it has a single field
/// - its name ends in Request
/// - it has any optional fields
///
/// ...then destructuring will be skipped, and the method will take the input
/// message directly. Returns (params_line, assignment_lines)
// Returns the string with 4 spaces of identation.
fn maybe_destructured_input(input: &MessageDescriptor) -> (String, String) {
    if (input.name().ends_with("Request") || input.fields().len() < 2)
        && input.oneofs().next().is_none()
    {
        // destructure
        let method_args = build_method_arguments(input);
        let input_type = full_name_to_kotlin_class(input.full_name());
        let input_message_args = build_input_message_arguments(input);
        let assignment =format!("\n        val builder = {input_type}.newBuilder(){input_message_args}\n        val input = builder.build() ",) ;
        (method_args, assignment)
    } else {
        // no destructure
        let params = format!("input: {}", full_name_to_kotlin_class(input.full_name()));
        let assignment = String::new();
        (params, assignment)
    }
}

fn build_method_arguments(input: &MessageDescriptor) -> String {
    let fields = input.fields();
    let mut args = vec![];
    for field in fields {
        let arg = format!(
            "{}: {}",
            field_name_to_kotlin_arg(&field),
            kotlin_type(&field).replace("List<", "Iterable<")
        );
        args.push(arg);
    }
    args.join(", ")
}

// Starts each instruction with a new line and 8 spaces of identation
fn build_input_message_arguments(input: &MessageDescriptor) -> String {
    input
        .fields()
        .map(|field| {
            let name = field_name_to_kotlin_arg(&field);
            let pascal_name = field.name().to_pascal_case();
            let op = if field.is_list() {
                "addAll"
            } else if field.is_map() {
                "putAll"
            } else {
                "set"
            };
            let setter = format!("\n        builder.{op}{pascal_name}({name})");
            if field.field_descriptor_proto().proto3_optional() {
                format!("if ({name} != null) {{\n    {setter}\n      }}")
            } else {
                setter
            }
        })
        .collect::<Vec<_>>()
        .join("")
}

// If output type has a single field and is not an enum, we return its single
// field value directly. Returns (expr, type), where expr is 'output' or
// 'output.<only_field>'.
fn maybe_destructured_output(output: &MessageDescriptor) -> (String, String) {
    let first_field = output.fields().next();
    if output.full_name() == "anki.generic.Empty" {
        return ("".into(), "Unit".into());
    }

    if output.fields().len() == 1 && !matches!(first_field.as_ref().unwrap().kind(), Kind::Enum(_))
    {
        let field = first_field.unwrap();
        (
            format!("output.{}", field_name_to_kotlin(&field)),
            kotlin_type(&field),
        )
    } else {
        (
            "output".into(),
            full_name_to_kotlin_class(output.full_name()),
        )
    }
}

fn kotlin_type(field: &FieldDescriptor) -> String {
    let kind = match field.kind() {
        Kind::Int32 | Kind::Fixed32 | Kind::Sfixed32 | Kind::Sint32 => "Int".into(),
        Kind::Uint32 => "Int".into(),
        Kind::Int64 | Kind::Sint64 | Kind::Sfixed64 => "Long".into(),
        Kind::Uint64 | Kind::Fixed64 => "Long".into(),
        Kind::Float => "Float".into(),
        Kind::Double => "Double".into(),
        Kind::Bool => "Boolean".into(),
        Kind::String => "String".into(),
        Kind::Bytes => "com.google.protobuf.ByteString".into(),
        Kind::Message(msg) => full_name_to_kotlin_class(msg.full_name()),
        Kind::Enum(en) => full_name_to_kotlin_class(en.full_name()),
    };
    if field.is_list() {
        format!("List<{kind}>")
    } else if field.is_map() {
        let map_kind = field.kind();
        let map_kind = map_kind.as_message().unwrap();
        let map_kv: Vec<_> = map_kind.fields().map(|f| kotlin_type(&f)).collect();
        format!("Map<{},{}>", map_kv[0], map_kv[1])
    } else {
        kind
    }
}

fn field_name_to_kotlin(field: &FieldDescriptor) -> String {
    let name = field.name().to_camel_case();
    if field.is_list() {
        format!("{name}List")
    } else if name == "val" {
        "`val`".into()
    } else {
        name
    }
}

fn field_name_to_kotlin_arg(field: &FieldDescriptor) -> String {
    let name = field.name().to_camel_case();
    if name == "val" {
        "`val`".into()
    } else {
        name
    }
}

fn full_name_to_kotlin_class(name: &str) -> String {
    name.into()
}

fn write_header(out: &mut impl Write) -> Result<()> {
    out.write_all(
        br#"/* Auto-generated from the .proto files in AnkiDroidBackend. */

@file:Suppress("NAME_SHADOWING", "UNUSED_VARIABLE")
        
package anki.backend;

import com.google.protobuf.InvalidProtocolBufferException;

import net.ankiweb.rsdroid.BackendException;

public abstract class GeneratedBackend {

    @Throws(BackendException::class)
    protected abstract fun runMethodRaw(service: Int, method: Int, input: ByteArray): ByteArray;

"#,
    )?;
    Ok(())
}

/// Invoke protoc to build java/kotlin code from .proto files
fn build_kotlin_protos(out_dir: &Path) -> Result<()> {
    let dir = out_dir.display();
    let proto_files: Vec<_> = glob::glob("../anki/proto/anki/*")?
        .map(|p| p.unwrap())
        .collect();
    let mut command = Command::new(std::env::var("PROTOC").unwrap());
    command
        .args([
            format!("--kotlin_out=lite:{dir}"),
            format!("--java_out=lite:{dir}"),
        ])
        .args(["-I", "../anki/proto"])
        .args(proto_files);
    command.ensure_success()?;

    Ok(())
}
