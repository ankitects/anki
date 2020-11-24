use std::path::PathBuf;
use std::{env, fmt::Write};

struct CustomGenerator {}

fn write_method_enum(buf: &mut String, service: &prost_build::Service) {
    buf.push_str(
        r#"
use num_enum::TryFromPrimitive;
#[derive(PartialEq,TryFromPrimitive)]
#[repr(u32)]
pub enum BackendMethod {
"#,
    );
    for (idx, method) in service.methods.iter().enumerate() {
        writeln!(buf, "    {} = {},", method.proto_name, idx + 1).unwrap();
    }
    buf.push_str("}\n\n");
}

fn write_method_trait(buf: &mut String, service: &prost_build::Service) {
    buf.push_str(
        r#"
use prost::Message;
pub type BackendResult<T> = std::result::Result<T, crate::err::AnkiError>;
pub trait BackendService {
    fn run_command_bytes2_inner(&self, method: u32, input: &[u8]) -> std::result::Result<Vec<u8>, crate::err::AnkiError> {
        match method {
"#,
    );

    for (idx, method) in service.methods.iter().enumerate() {
        write!(
            buf,
            concat!("            ",
            "{idx} => {{ let input = {input_type}::decode(input)?;\n",
            "let output = self.{rust_method}(input)?;\n",
            "let mut out_bytes = Vec::new(); output.encode(&mut out_bytes)?; Ok(out_bytes) }}, "),
            idx = idx + 1,
            input_type = method.input_type,
            rust_method = method.name
        )
        .unwrap();
    }
    buf.push_str(
        r#"
            _ => Err(crate::err::AnkiError::invalid_input("invalid command")),
        }
    }
"#,
    );

    for method in &service.methods {
        write!(
            buf,
            concat!(
                "    fn {method_name}(&self, input: {input_type}) -> ",
                "BackendResult<{output_type}>;\n"
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
        write_method_enum(buf, &service);
        write_method_trait(buf, &service);
    }
}

fn service_generator() -> Box<dyn prost_build::ServiceGenerator> {
    Box::new(CustomGenerator {})
}

pub fn write_backend_proto_rs() {
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let backend_proto;
    let proto_dir;
    if let Ok(proto) = env::var("BACKEND_PROTO") {
        backend_proto = PathBuf::from(proto);
        proto_dir = backend_proto.parent().unwrap().to_owned();
    } else {
        backend_proto = PathBuf::from("backend.proto");
        proto_dir = PathBuf::from(".");
    }
    let fluent_proto = out_dir.join("fluent.proto");

    let mut config = prost_build::Config::new();
    config
        .out_dir(&out_dir)
        .service_generator(service_generator())
        .compile_protos(&[&backend_proto, &fluent_proto], &[&proto_dir, &out_dir])
        .unwrap();
}
