// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{env, fmt::Write, path::PathBuf};

struct CustomGenerator {}

fn write_method_trait(buf: &mut String, service: &prost_build::Service) {
    buf.push_str(
        r#"
pub trait Service {
    fn run_method(&self, method: u32, input: &[u8]) -> Result<Vec<u8>> {
        match method {
"#,
    );
    for (idx, method) in service.methods.iter().enumerate() {
        write!(
            buf,
            concat!("            ",
            "{idx} => {{ let input = super::{input_type}::decode(input)?;\n",
            "let output = self.{rust_method}(input)?;\n",
            "let mut out_bytes = Vec::new(); output.encode(&mut out_bytes)?; Ok(out_bytes) }}, "),
            idx = idx,
            input_type = method.input_type,
            rust_method = method.name
        )
        .unwrap();
    }
    buf.push_str(
        r#"
            _ => Err(crate::error::AnkiError::invalid_input("invalid command")),
        }
    }
"#,
    );

    for method in &service.methods {
        write!(
            buf,
            concat!(
                "    fn {method_name}(&self, input: super::{input_type}) -> ",
                "Result<super::{output_type}>;\n"
            ),
            method_name = method.name,
            input_type = method.input_type,
            output_type = method.output_type
        )
        .unwrap();
    }
    buf.push_str("}\n");
}

impl prost_build::ServiceGenerator for CustomGenerator {
    fn generate(&mut self, service: prost_build::Service, buf: &mut String) {
        write!(
            buf,
            "pub mod {name}_service {{
                use prost::Message;
                use crate::error::Result;
                ",
            name = service.name.replace("Service", "").to_ascii_lowercase()
        )
        .unwrap();
        write_method_trait(buf, &service);
        buf.push('}');
    }
}

fn service_generator() -> Box<dyn prost_build::ServiceGenerator> {
    Box::new(CustomGenerator {})
}

pub fn write_backend_proto_rs() {
    let proto_dir = if let Ok(proto) = env::var("PROTO_TOP") {
        PathBuf::from(proto).parent().unwrap().to_owned()
    } else {
        PathBuf::from("../proto")
    };

    let subfolders = &["anki"];
    let mut paths = vec![];
    for subfolder in subfolders {
        for entry in proto_dir.join(subfolder).read_dir().unwrap() {
            let entry = entry.unwrap();
            let path = entry.path();
            if path
                .file_name()
                .unwrap()
                .to_str()
                .unwrap()
                .ends_with(".proto")
            {
                println!("cargo:rerun-if-changed={}", path.to_str().unwrap());
                paths.push(path);
            }
        }
    }

    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let mut config = prost_build::Config::new();
    config
        .out_dir(&out_dir)
        .service_generator(service_generator())
        .type_attribute(
            "Deck.Filtered.SearchTerm.Order",
            "#[derive(strum::EnumIter)]",
        )
        .type_attribute(
            "HelpPageLinkRequest.HelpPage",
            "#[derive(strum::EnumIter)]",
        )
        .compile_protos(paths.as_slice(), &[proto_dir])
        .unwrap();
}
