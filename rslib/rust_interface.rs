// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fmt::Write;
use std::path::PathBuf;

use anki_io::write_file_if_changed;
use anki_proto::codegen::BackendMethod;
use anyhow::Context;
use anyhow::Result;
use inflections::Inflect;
use prost_reflect::DescriptorPool;

pub fn write_rust_interface(pool: &DescriptorPool) -> Result<()> {
    let mut buf = String::new();
    buf.push_str("use crate::error::Result; use prost::Message;");
    let services = pool
        .services()
        .map(RustService::from_proto)
        .collect::<Vec<_>>();
    for service in &services {
        render_service(service, &mut buf);
    }

    render_top_level_run_method(&mut buf, &services, true);
    render_top_level_run_method(&mut buf, &services, false);
    render_method_lookup(&mut buf, &services);

    // println!("{}", &buf);
    let buf = format_code(buf)?;
    // write into OUT_DIR so we can use it in build.rs
    let out_dir = env::var("OUT_DIR").unwrap();
    let path = PathBuf::from(out_dir).join("backend.rs");
    write_file_if_changed(path, buf).context("write file")?;
    Ok(())
}

#[derive(Debug)]
struct RustService {
    name: String,
    methods: Vec<RustMethod>,
}

#[derive(Debug)]
struct RustMethod {
    name: String,
    input_type: Option<String>,
    output_type: Option<String>,
    options: anki_proto::codegen::MethodOptions,
    service_name: String,
}

impl RustMethod {
    /// No text if generic::Empty
    fn text_if_input_not_empty(&self, text: impl Fn(&String) -> String) -> String {
        self.input_type.as_ref().map(text).unwrap_or_default()
    }

    /// No text if generic::Empty
    fn get_input_arg_with_label(&self) -> String {
        self.input_type
            .as_ref()
            .map(|t| format!("input: {}", t))
            .unwrap_or_default()
    }

    /// () if generic::Empty
    fn get_output_type(&self) -> String {
        self.output_type.as_deref().unwrap_or("()").into()
    }

    fn text_if_output_not_empty(&self, text: impl Fn(&String) -> String) -> String {
        self.output_type.as_ref().map(text).unwrap_or_default()
    }

    fn wants_abstract_backend_method(&self) -> bool {
        self.service_name.starts_with("Backend")
            || self.options.backend_method() == BackendMethod::Implement
    }

    fn wants_abstract_collection_method(&self) -> bool {
        !self.service_name.starts_with("Backend")
    }

    fn from_proto(method: prost_reflect::MethodDescriptor) -> Self {
        RustMethod {
            name: method.name().to_snake_case(),
            input_type: rust_type(method.input().full_name()),
            output_type: rust_type(method.output().full_name()),
            options: method.options().transcode_to().unwrap(),
            service_name: method.parent_service().name().to_string(),
        }
    }
}

impl RustService {
    fn from_proto(service: prost_reflect::ServiceDescriptor) -> Self {
        RustService {
            name: service.name().to_string(),
            methods: service.methods().map(RustMethod::from_proto).collect(),
        }
    }
}

fn rust_type(name: &str) -> Option<String> {
    if name == "anki.generic.Empty" {
        return None;
    }
    let Some((head, tail)) = name.rsplit_once( '.') else { panic!() };
    Some(format!(
        "{}::{}",
        head.to_snake_case()
            .replace('.', "::")
            .replace("anki::", "anki_proto::"),
        tail
    ))
}

fn format_code(code: String) -> Result<String> {
    let syntax_tree = syn::parse_file(&code)?;
    Ok(prettyplease::unparse(&syntax_tree))
}

fn render_abstract_collection_method(method: &RustMethod, buf: &mut String) {
    let method_name = &method.name;
    let input_with_label = method.get_input_arg_with_label();
    let output_type = method.get_output_type();
    writeln!(
        buf,
        "fn {method_name}(&mut self, {input_with_label}) -> Result<{output_type}>;"
    )
    .unwrap();
}

fn render_abstract_backend_method(method: &RustMethod, buf: &mut String, _service: &RustService) {
    let method_name = &method.name;
    let input_with_label = method.get_input_arg_with_label();
    let output_type = method.get_output_type();
    writeln!(
        buf,
        "fn {method_name}(&self, {input_with_label}) -> Result<{output_type}>;"
    )
    .unwrap();
}

fn render_delegating_backend_method(method: &RustMethod, buf: &mut String, service: &RustService) {
    let method_name = &method.name;
    let input_with_label = method.get_input_arg_with_label();
    let input = method.text_if_input_not_empty(|_| "input".into());
    let output_type = method.get_output_type();
    let col_service = &service.name;
    writeln!(
        buf,
        "fn {method_name}(&self, {input_with_label}) -> Result<{output_type}> {{
        self.with_col(|col| {col_service}::{method_name}(col, {input})) }}",
    )
    .unwrap();
}

fn render_service(service: &RustService, buf: &mut String) {
    let have_collection = service
        .methods
        .iter()
        .any(|m| m.wants_abstract_collection_method());
    if have_collection {
        render_collection_trait(service, buf);
    }
    if service
        .methods
        .iter()
        .any(|m| m.wants_abstract_backend_method())
    {
        render_backend_trait(service, buf);
    }
    render_delegating_backend_methods(service, buf);
    render_individual_service_run_method(buf, service, true);
    render_individual_service_run_method(buf, service, false);
}

fn render_collection_trait(service: &RustService, buf: &mut String) {
    let name = &service.name;
    writeln!(buf, "pub trait {name} {{").unwrap();
    for method in &service.methods {
        if method.wants_abstract_collection_method() {
            render_abstract_collection_method(method, buf);
        }
    }
    buf.push('}');
}

fn render_backend_trait(service: &RustService, buf: &mut String) {
    let name = if !service.name.starts_with("Backend") {
        format!("Backend{}", service.name)
    } else {
        service.name.clone()
    };
    writeln!(buf, "pub trait {name} {{").unwrap();
    for method in &service.methods {
        if method.wants_abstract_backend_method() {
            render_abstract_backend_method(method, buf, service);
        }
    }
    buf.push('}');
}

fn render_delegating_backend_methods(service: &RustService, buf: &mut String) {
    buf.push_str("impl crate::backend::Backend {");
    for method in &service.methods {
        if method.wants_abstract_backend_method() {
            continue;
        }
        render_delegating_backend_method(method, buf, service);
    }
    buf.push('}');
}

// Matches all service types and delegates to the revelant self.run_foo_method()
fn render_top_level_run_method(buf: &mut String, services: &[RustService], backend: bool) {
    let self_kind = if backend { "&self" } else { "&mut self" };
    let struct_to_impl = if backend {
        "crate::backend::Backend"
    } else {
        "crate::collection::Collection"
    };
    writeln!(buf,
        r#" impl {struct_to_impl} {{
    pub fn run_service_method({self_kind}, service: u32, method: u32, input: &[u8]) -> Result<Vec<u8>, Vec<u8>> {{
        match service {{
"#,
    ).unwrap();
    for (idx, service) in services.iter().enumerate() {
        writeln!(
            buf,
            "{idx} => self.run_{service}_method(method, input),",
            service = service.name.to_snake_case()
        )
        .unwrap();
    }
    buf.push_str(
        r#"
            _ => Err(crate::error::AnkiError::InvalidServiceIndex),
        }
        .map_err(|err| {
                let backend_err = err.into_protobuf(&self.tr);
                let mut bytes = Vec::new();
                backend_err.encode(&mut bytes).unwrap();
                bytes
            })
    } }"#,
    );
}

fn render_individual_service_run_method(buf: &mut String, service: &RustService, backend: bool) {
    let self_kind = if backend { "&self" } else { "&mut self" };
    let struct_to_impl = if backend {
        "crate::backend::Backend"
    } else {
        "crate::collection::Collection"
    };
    let method_qualifier = if backend {
        struct_to_impl
    } else {
        &service.name
    };

    let service_name = &service.name.to_snake_case();
    writeln!(
        buf,
        "#[allow(unused_variables, clippy::match_single_binding)]
        impl {struct_to_impl} {{ pub(crate) fn run_{service_name}_method({self_kind},
        method: u32, input: &[u8]) -> Result<Vec<u8>> {{
        match method {{",
    )
    .unwrap();
    for (idx, method) in service.methods.iter().enumerate() {
        if !backend && !method.wants_abstract_collection_method() {
            continue;
        }
        let decode_input =
            method.text_if_input_not_empty(|kind| format!("let input = {kind}::decode(input)?;"));
        let rust_method = &method.name;
        let input = method.text_if_input_not_empty(|_| "input".into());
        let output_assign = method.text_if_output_not_empty(|_| "let output = ".into());
        let output = if method.output_type.is_none() {
            "Vec::new()"
        } else {
            "{ let mut out_bytes = Vec::new();
            output.encode(&mut out_bytes)?;
            out_bytes }"
        };
        writeln!(
            buf,
            "{idx} => {{ {decode_input}
                {output_assign} {method_qualifier}::{rust_method}(self, {input})?;
                Ok({output}) }},",
        )
        .unwrap();
    }
    buf.push_str(
        r#"
            _ => Err(crate::error::AnkiError::InvalidMethodIndex),
        }
} }
"#,
    );
}

fn render_method_lookup(buf: &mut String, services: &[RustService]) {
    writeln!(
        buf,
        "
pub const METHODS_BY_NAME: phf::Map<&str, (u32, u32)> = phf::phf_map! {{
"
    )
    .unwrap();
    for (sidx, service) in services.iter().enumerate() {
        for (midx, method) in service.methods.iter().enumerate() {
            let name = &method.name;
            writeln!(buf, r#"    "{name}" => ({sidx}, {midx}),"#,).unwrap();
        }
    }
    buf.push_str("};\n");
}
