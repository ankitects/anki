// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fmt::Write;
use std::path::Path;
use std::path::PathBuf;

use anki_io::create_dir_all;
use anki_io::read_file;
use anki_io::write_file_if_changed;
use anyhow::Context;
use anyhow::Result;
use prost_build::ServiceGenerator;
use prost_reflect::DescriptorPool;

pub fn write_backend_proto_rs(descriptors_path: Option<PathBuf>) -> Result<DescriptorPool> {
    set_protoc_path();
    let proto_dir = PathBuf::from("../../proto");
    let paths = gather_proto_paths(&proto_dir)?;
    let out_dir = PathBuf::from(env::var("OUT_DIR").unwrap());
    let tmp_descriptors = out_dir.join("descriptors.tmp");
    prost_build::Config::new()
        .out_dir(&out_dir)
        .file_descriptor_set_path(&tmp_descriptors)
        .service_generator(RustCodeGenerator::boxed())
        .type_attribute(
            "Deck.Filtered.SearchTerm.Order",
            "#[derive(strum::EnumIter)]",
        )
        .type_attribute(
            "Deck.Normal.DayLimit",
            "#[derive(Copy, Eq, serde::Deserialize, serde::Serialize)]",
        )
        .type_attribute("HelpPageLinkRequest.HelpPage", "#[derive(strum::EnumIter)]")
        .type_attribute("CsvMetadata.Delimiter", "#[derive(strum::EnumIter)]")
        .type_attribute(
            "Preferences.BackupLimits",
            "#[derive(Copy, serde::Deserialize, serde::Serialize)]",
        )
        .type_attribute(
            "CsvMetadata.DupeResolution",
            "#[derive(serde::Deserialize, serde::Serialize)]",
        )
        .type_attribute(
            "CsvMetadata.MatchScope",
            "#[derive(serde::Deserialize, serde::Serialize)]",
        )
        .compile_protos(paths.as_slice(), &[proto_dir])
        .context("prost build")?;

    let descriptors = read_file(&tmp_descriptors)?;
    if let Some(descriptors_path) = descriptors_path {
        create_dir_all(
            descriptors_path
                .parent()
                .context("missing parent of descriptor")?,
        )?;
        write_file_if_changed(descriptors_path, &descriptors)?;
    }
    write_service_index(&out_dir, descriptors)
}

fn write_service_index(out_dir: &Path, descriptors: Vec<u8>) -> Result<DescriptorPool> {
    let pool =
        DescriptorPool::decode(descriptors.as_ref()).context("unable to decode descriptors")?;
    let mut buf = String::new();

    writeln!(
        buf,
        "#[derive(num_enum::TryFromPrimitive)]
#[repr(u32)]
pub enum ServiceIndex {{"
    )
    .unwrap();
    for service in pool.services() {
        writeln!(
            buf,
            " {} = {},",
            service.name().replace("Service", ""),
            service.index()
        )
        .unwrap();
    }
    writeln!(buf, "}}").unwrap();

    write_file_if_changed(out_dir.join("service_index.rs"), buf)?;

    Ok(pool)
}

fn gather_proto_paths(proto_dir: &Path) -> Result<Vec<PathBuf>> {
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
    paths.sort();
    Ok(paths)
}

struct RustCodeGenerator {}

impl RustCodeGenerator {
    fn boxed() -> Box<dyn ServiceGenerator> {
        Box::new(Self {})
    }

    fn write_method_trait(&mut self, buf: &mut String, service: &prost_build::Service) {
        buf.push_str(
            r#"
pub trait Service {
    type Error: From<crate::ProtoError>;

    fn run_method(&self, method: u32, input: &[u8]) -> Result<Vec<u8>, Self::Error> {
        match method {
"#,
        );
        for (idx, method) in service.methods.iter().enumerate() {
            write!(
                buf,
                concat!("            ",
                "{idx} => {{ let input = super::{input_type}::decode(input).map_err(crate::ProtoError::from)?;\n",
                "let output = self.{rust_method}(input)?;\n",
                "let mut out_bytes = Vec::new(); output.encode(&mut out_bytes).map_err(crate::ProtoError::from)?; Ok(out_bytes) }}, "),
                idx = idx,
                input_type = method.input_type,
                rust_method = method.name
            )
                .unwrap();
        }
        buf.push_str(
            r#"
            _ => Err(crate::ProtoError::InvalidMethodIndex.into()),
        }
    }
"#,
        );

        for method in &service.methods {
            write!(
                buf,
                concat!(
                    "    fn {method_name}(&self, input: super::{input_type}) -> ",
                    "Result<super::{output_type}, Self::Error>;\n"
                ),
                method_name = method.name,
                input_type = method.input_type,
                output_type = method.output_type
            )
            .unwrap();
        }
        buf.push_str("}\n");
    }
}

impl ServiceGenerator for RustCodeGenerator {
    fn generate(&mut self, service: prost_build::Service, buf: &mut String) {
        write!(
            buf,
            "pub mod {name}_service {{
                use prost::Message;
                ",
            name = service.name.replace("Service", "").to_ascii_lowercase()
        )
        .unwrap();
        self.write_method_trait(buf, &service);
        buf.push('}');
    }
}

/// Set PROTOC to the custom path provided by PROTOC_BINARY, or add .exe to
/// the standard path if on Windows.
fn set_protoc_path() {
    if let Ok(custom_protoc) = env::var("PROTOC_BINARY") {
        env::set_var("PROTOC", custom_protoc);
    } else if let Ok(bundled_protoc) = env::var("PROTOC") {
        if cfg!(windows) && !bundled_protoc.ends_with(".exe") {
            env::set_var("PROTOC", format!("{bundled_protoc}.exe"));
        }
    }
}
