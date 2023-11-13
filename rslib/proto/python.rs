// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Cursor;
use std::io::Write;
use std::path::Path;

use anki_io::create_dir_all;
use anki_io::write_file_if_changed;
use anki_proto_gen::BackendService;
use anki_proto_gen::Method;
use anyhow::Result;
use inflections::Inflect;
use prost_reflect::FieldDescriptor;
use prost_reflect::Kind;
use prost_reflect::MessageDescriptor;

pub(crate) fn write_python_interface(services: &[BackendService]) -> Result<()> {
    let output_path = Path::new("../../out/pylib/anki/_backend_generated.py");
    create_dir_all(output_path.parent().unwrap())?;
    let mut out = Cursor::new(Vec::new());
    write_header(&mut out)?;

    for service in services {
        if service.name == "BackendAnkidroidService" {
            continue;
        }
        for method in service.all_methods() {
            render_method(service, method, &mut out);
        }
    }
    write_file_if_changed(output_path, out.into_inner())?;

    Ok(())
}

/// Generates text like the following:
///
/// def get_field_names_raw(self, message: bytes) -> bytes:
///     return self._run_command(7, 16, message)
///
/// def get_field_names(self, ntid: int) -> Sequence[str]:
///     message = anki.notetypes_pb2.NotetypeId(ntid=ntid)
///     raw_bytes = self._run_command(7, 16, message.SerializeToString())
///     output = anki.generic_pb2.StringList()
///     output.ParseFromString(raw_bytes)
///     return output.vals
fn render_method(service: &BackendService, method: &Method, out: &mut impl Write) {
    let method_name = method.name.to_snake_case();
    let input = method.proto.input();
    let output = method.proto.output();
    let service_idx = service.index;
    let method_idx = method.index;
    let comments = format_comments(&method.comments);

    // raw bytes
    write!(
        out,
        r#"    def {method_name}_raw(self, message: bytes) -> bytes:
        {comments}return self._run_command({service_idx}, {method_idx}, message)

"#
    )
    .unwrap();

    // (possibly destructured) message
    let (input_params, input_assign) = maybe_destructured_input(&input);
    let output_constructor = full_name_to_python(output.full_name());
    let (output_msg_or_single_field, output_type) = maybe_destructured_output(&output);
    write!(
        out,
        r#"    def {method_name}({input_params}) -> {output_type}:
        {comments}{input_assign}
        raw_bytes = self._run_command({service_idx}, {method_idx}, message.SerializeToString())
        output = {output_constructor}()
        output.ParseFromString(raw_bytes)
        return {output_msg_or_single_field}

"#
    )
    .unwrap();
}

fn format_comments(comments: &Option<String>) -> String {
    comments
        .as_ref()
        .map(|c| {
            format!(
                r#""""{c}"""
        "#
            )
        })
        .unwrap_or_default()
}

/// If any of the following apply to the input type:
/// - it has a single field
/// - its name ends in Request
/// - it has any optional fields
/// ...then destructuring will be skipped, and the method will take the input
/// message directly. Returns (params_line, assignment_lines)
fn maybe_destructured_input(input: &MessageDescriptor) -> (String, String) {
    if (input.name().ends_with("Request") || input.fields().len() < 2)
        && input.oneofs().next().is_none()
    {
        // destructure
        let method_args = build_method_arguments(input);
        let input_type = full_name_to_python(input.full_name());
        let input_message_args = build_input_message_arguments(input);
        let assignment = format!("message = {input_type}({input_message_args})",);
        (method_args, assignment)
    } else {
        // no destructure
        let params = format!("self, message: {}", full_name_to_python(input.full_name()));
        let assignment = String::new();
        (params, assignment)
    }
}

/// e.g. "self, *, note_ids: Sequence[int], new_fields: Sequence[int]"
fn build_method_arguments(input: &MessageDescriptor) -> String {
    let fields = input.fields();
    let mut args = vec!["self".to_string()];
    if fields.len() >= 2 {
        args.push("*".to_string());
    }
    for field in fields {
        let arg = format!("{}: {}", field.name(), python_type(&field, false));
        args.push(arg);
    }
    args.join(", ")
}

/// e.g. "note_ids=note_ids, new_fields=new_fields"
fn build_input_message_arguments(input: &MessageDescriptor) -> String {
    input
        .fields()
        .map(|field| {
            let name = field.name();
            format!("{name}={name}")
        })
        .collect::<Vec<_>>()
        .join(", ")
}

// If output type has a single field and is not an enum, we return its single
// field value directly. Returns (expr, type), where expr is 'output' or
// 'output.<only_field>'.
fn maybe_destructured_output(output: &MessageDescriptor) -> (String, String) {
    let first_field = output.fields().next();
    if output.fields().len() == 1 && !matches!(first_field.as_ref().unwrap().kind(), Kind::Enum(_))
    {
        let field = first_field.unwrap();
        (
            format!("output.{}", field.name()),
            python_type(&field, true),
        )
    } else {
        ("output".into(), full_name_to_python(output.full_name()))
    }
}

/// e.g. uint32 -> int; repeated bool -> Sequence[bool]
fn python_type(field: &FieldDescriptor, output: bool) -> String {
    let kind = match field.kind() {
        Kind::Int32
        | Kind::Int64
        | Kind::Uint32
        | Kind::Uint64
        | Kind::Sint32
        | Kind::Sint64
        | Kind::Fixed32
        | Kind::Fixed64
        | Kind::Sfixed32
        | Kind::Sfixed64 => "int".into(),
        Kind::Float | Kind::Double => "float".into(),
        Kind::Bool => "bool".into(),
        Kind::String => "str".into(),
        Kind::Bytes => "bytes".into(),
        Kind::Message(msg) => full_name_to_python(msg.full_name()),
        Kind::Enum(en) => format!("{}.V", full_name_to_python(en.full_name())),
    };
    if field.is_list() {
        if output {
            format!("Sequence[{}]", kind)
        } else {
            format!("Iterable[{}]", kind)
        }
    } else if field.is_map() {
        let map_kind = field.kind();
        let map_kind = map_kind.as_message().unwrap();
        let map_kv: Vec<_> = map_kind.fields().map(|f| python_type(&f, output)).collect();
        format!("Mapping[{}, {}]", map_kv[0], map_kv[1])
    } else {
        kind
    }
}

// e.g. anki.import_export.ImportResponse ->
// anki.import_export_pb2.ImportResponse
fn full_name_to_python(name: &str) -> String {
    let mut name = name.splitn(3, '.');
    format!(
        "{}.{}_pb2.{}",
        name.next().unwrap(),
        name.next().unwrap(),
        name.next().unwrap()
    )
}

fn write_header(out: &mut impl Write) -> Result<()> {
    out.write_all(
        br#"# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; https://www.gnu.org/licenses/agpl.html
# pylint: skip-file

from __future__ import annotations

"""
THIS FILE IS AUTOMATICALLY GENERATED - DO NOT EDIT.

Please do not access methods on the backend directly - they may be changed
or removed at any time. Instead, please use the methods on the collection
instead. Eg, don't use col.backend.all_deck_config(), instead use
col.decks.all_config()
"""

from typing import *

import anki
import anki.ankiweb_pb2
import anki.backend_pb2
import anki.card_rendering_pb2
import anki.cards_pb2
import anki.collection_pb2
import anki.config_pb2
import anki.deck_config_pb2
import anki.decks_pb2
import anki.i18n_pb2
import anki.image_occlusion_pb2
import anki.import_export_pb2
import anki.links_pb2
import anki.media_pb2
import anki.notes_pb2
import anki.notetypes_pb2
import anki.scheduler_pb2
import anki.search_pb2
import anki.stats_pb2
import anki.sync_pb2
import anki.tags_pb2

class RustBackendGenerated:
    def _run_command(self, service: int, method: int, input: Any) -> bytes:
        raise Exception("not implemented")

"#,
    )?;
    Ok(())
}
