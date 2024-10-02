// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fmt::Write;
use std::path::PathBuf;

use anki_io::write_file_if_changed;
use anki_proto_gen::get_services;
use anki_proto_gen::BackendService;
use anki_proto_gen::CollectionService;
use anki_proto_gen::Method;
use anyhow::Context;
use anyhow::Result;
use inflections::Inflect;
use itertools::Itertools;
use prost_reflect::DescriptorPool;

pub fn write_rust_interface(pool: &DescriptorPool) -> Result<()> {
    let mut buf = String::new();
    buf.push_str("use crate::error::Result; use prost::Message;");

    let (col_services, backend_services) = get_services(pool);
    let col_services = col_services
        .into_iter()
        .filter(|s| s.name != "FrontendService")
        .collect_vec();
    let backend_services = backend_services
        .into_iter()
        .filter(|s| s.name != "BackendFrontendService")
        .collect_vec();

    render_collection_services(&col_services, &mut buf)?;
    render_backend_services(&backend_services, &mut buf)?;

    let buf = format_code(buf)?;
    // println!("{}", &buf);
    // panic!();
    let out_dir = env::var("OUT_DIR").unwrap();
    let path = PathBuf::from(out_dir).join("backend.rs");
    write_file_if_changed(path, buf).context("write file")?;
    Ok(())
}

fn render_collection_services(col_services: &[CollectionService], buf: &mut String) -> Result<()> {
    for service in col_services {
        render_collection_trait(service, buf);
        render_individual_service_run_method_for_collection(buf, service);
    }
    render_top_level_run_method(
        col_services.iter().map(|s| (s.index, s.name.as_str())),
        "&mut self",
        "crate::collection::Collection",
        buf,
    );

    Ok(())
}

fn render_backend_services(backend_services: &[BackendService], buf: &mut String) -> Result<()> {
    for service in backend_services {
        render_backend_trait(service, buf);
        render_delegating_backend_methods(service, buf);
        render_individual_service_run_method_for_backend(buf, service);
    }
    render_top_level_run_method(
        backend_services.iter().map(|s| (s.index, s.name.as_str())),
        "&self",
        "crate::backend::Backend",
        buf,
    );

    Ok(())
}

fn format_code(code: String) -> Result<String> {
    let syntax_tree = syn::parse_file(&code)?;
    Ok(prettyplease::unparse(&syntax_tree))
}

fn render_collection_trait(service: &CollectionService, buf: &mut String) {
    let name = &service.name;
    writeln!(buf, "pub trait {name} {{").unwrap();
    for method in &service.trait_methods {
        render_trait_method(method, "&mut self", buf);
    }
    buf.push('}');
}

fn render_trait_method(method: &Method, self_kind: &str, buf: &mut String) {
    let method_name = &method.name;
    let input_with_label = method.get_input_arg_with_label();
    let output_type = method.get_output_type();
    writeln!(
        buf,
        "fn {method_name}({self_kind}, {input_with_label}) -> Result<{output_type}>;"
    )
    .unwrap();
}

fn render_backend_trait(service: &BackendService, buf: &mut String) {
    let name = &service.name;
    writeln!(buf, "pub trait {name} {{").unwrap();
    for method in &service.trait_methods {
        render_trait_method(method, "&self", buf);
    }
    buf.push('}');
}

fn render_delegating_backend_methods(service: &BackendService, buf: &mut String) {
    buf.push_str("impl crate::backend::Backend {");
    for method in &service.delegating_methods {
        render_delegating_backend_method(method, service.name.trim_start_matches("Backend"), buf);
    }
    buf.push('}');
}

fn render_delegating_backend_method(method: &Method, method_qualifier: &str, buf: &mut String) {
    let method_name = &method.name;
    let input_with_label = method.get_input_arg_with_label();
    let input = method.text_if_input_not_empty(|_| "input".into());
    let output_type = method.get_output_type();
    writeln!(
        buf,
        "fn {method_name}(&self, {input_with_label}) -> Result<{output_type}> {{
        self.with_col(|col| {method_qualifier}::{method_name}(col, {input})) }}",
    )
    .unwrap();
}

// Matches all service types and delegates to the revelant self.run_foo_method()
fn render_top_level_run_method<'a>(
    // (index, name)
    services: impl Iterator<Item = (usize, &'a str)>,
    self_kind: &str,
    struct_name: &str,
    buf: &mut String,
) {
    writeln!(buf,
        r#" impl {struct_name} {{
    pub fn run_service_method({self_kind}, service: u32, method: u32, input: &[u8]) -> Result<Vec<u8>, Vec<u8>> {{
        match service {{
"#,
    ).unwrap();
    for (idx, service) in services {
        writeln!(
            buf,
            "{idx} => self.run_{service}_method(method, input),",
            service = service.to_snake_case()
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

fn render_individual_service_run_method_for_collection(
    buf: &mut String,
    service: &CollectionService,
) {
    let service_name = &service.name.to_snake_case();
    writeln!(
        buf,
        "#[allow(unused_variables, clippy::match_single_binding)]
        impl crate::collection::Collection {{ pub(crate) fn run_{service_name}_method(&mut self,
        method: u32, input: &[u8]) -> Result<Vec<u8>> {{
        match method {{",
    )
    .unwrap();
    for method in &service.trait_methods {
        render_method_in_match_expression(method, &service.name, buf);
    }
    buf.push_str(
        r#"
            _ => Err(crate::error::AnkiError::InvalidMethodIndex),
        }
} }
"#,
    );
}

fn render_individual_service_run_method_for_backend(buf: &mut String, service: &BackendService) {
    let service_name = &service.name.to_snake_case();
    writeln!(
        buf,
        "#[allow(unused_variables, clippy::match_single_binding)]
        impl crate::backend::Backend {{ pub(crate) fn run_{service_name}_method(&self,
        method: u32, input: &[u8]) -> Result<Vec<u8>> {{
        match method {{",
    )
    .unwrap();
    for method in &service.trait_methods {
        render_method_in_match_expression(method, &service.name, buf);
    }
    for method in &service.delegating_methods {
        render_method_in_match_expression(method, "crate::backend::Backend", buf);
    }
    buf.push_str(
        r#"
            _ => Err(crate::error::AnkiError::InvalidMethodIndex),
        }
} }
"#,
    );
}

fn render_method_in_match_expression(method: &Method, method_qualifier: &str, buf: &mut String) {
    let decode_input =
        method.text_if_input_not_empty(|kind| format!("let input = {kind}::decode(input)?;"));
    let rust_method = &method.name;
    let input = method.text_if_input_not_empty(|_| "input".into());
    let output_assign = method.text_if_output_not_empty(|_| "let output = ".into());
    let idx = method.index;
    let output = if method.output().is_none() {
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

trait MethodHelpers {
    fn input_type(&self) -> Option<String>;
    fn output_type(&self) -> Option<String>;
    fn text_if_input_not_empty(&self, text: impl Fn(&String) -> String) -> String;
    fn get_input_arg_with_label(&self) -> String;
    fn get_output_type(&self) -> String;
    fn text_if_output_not_empty(&self, text: impl Fn(&String) -> String) -> String;
}

impl MethodHelpers for Method {
    fn input_type(&self) -> Option<String> {
        self.input().map(|t| rust_type(t.full_name()))
    }

    fn output_type(&self) -> Option<String> {
        self.output().map(|t| rust_type(t.full_name()))
    }
    /// No text if generic::Empty
    fn text_if_input_not_empty(&self, text: impl Fn(&String) -> String) -> String {
        self.input_type().as_ref().map(text).unwrap_or_default()
    }

    /// No text if generic::Empty
    fn get_input_arg_with_label(&self) -> String {
        self.input_type()
            .as_ref()
            .map(|t| format!("input: {}", t))
            .unwrap_or_default()
    }

    /// () if generic::Empty
    fn get_output_type(&self) -> String {
        self.output_type().as_deref().unwrap_or("()").into()
    }

    fn text_if_output_not_empty(&self, text: impl Fn(&String) -> String) -> String {
        self.output_type().as_ref().map(text).unwrap_or_default()
    }
}

fn rust_type(name: &str) -> String {
    let Some((head, tail)) = name.rsplit_once('.') else {
        panic!()
    };
    format!(
        "{}::{}",
        head.to_snake_case()
            .replace('.', "::")
            .replace("anki::", "anki_proto::"),
        tail
    )
}
