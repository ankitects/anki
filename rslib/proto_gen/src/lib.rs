// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Some helpers for code generation in external crates, that ensure indexes
//! match.

use std::collections::HashMap;
use std::env;
use std::path::PathBuf;

use anki_io::read_to_string;
use anki_io::write_file_if_changed;
use anki_io::ToUtf8Path;
use anyhow::Result;
use camino::Utf8Path;
use inflections::Inflect;
use itertools::Either;
use itertools::Itertools;
use once_cell::sync::Lazy;
use prost_reflect::DescriptorPool;
use prost_reflect::MessageDescriptor;
use prost_reflect::MethodDescriptor;
use prost_reflect::ServiceDescriptor;
use regex::Captures;
use regex::Regex;
use walkdir::WalkDir;

/// We look for ExampleService and BackedExampleService, both of which are
/// expected to exist (but may be empty).
///
/// - If a method is listed in BackendExampleService and not in ExampleService,
/// that method is only available with a Backend.
/// - If a method is listed in both services, you can provide separate
/// implementations for each of the traits.
/// - If a method is listed only in ExampleService, a forwarding method on
/// Backend is automatically implemented. This bypasses the trait and implements
/// directly on Backend.
///
/// It's important that service and method indices are the same for
/// client-generated code, so the client code should use the .index fields
/// of Service and Method provided by get_services(), and not
/// .enumerate() or .proto.index()
///
/// Client code will want to ignore CollectionServices, and focus on
/// BackendServices.
pub fn get_services(pool: &DescriptorPool) -> (Vec<CollectionService>, Vec<BackendService>) {
    // split services into backend and collection
    let (mut col_services, mut backend_services): (Vec<_>, Vec<_>) =
        pool.services().partition_map(|service| {
            if service.name().starts_with("Backend") {
                Either::Right(BackendService::from_proto(service))
            } else {
                Either::Left(CollectionService::from_proto(service))
            }
        });
    // frontend.proto is only in col_services
    assert_eq!(col_services.len(), backend_services.len());
    // copy collection methods into backend services if they don't have one with
    // a matching name
    for service in &mut backend_services {
        // locate associated collection service
        let Some(col_service) = col_services
            .iter()
            .find(|cs| cs.name == service.name.trim_start_matches("Backend"))
        else {
            panic!("missing associated service: {}", service.name)
        };

        // add any methods that don't exist in backend trait methods to the delegating
        // methods
        service.delegating_methods = col_service
            .trait_methods
            .iter()
            .filter(|m| service.trait_methods.iter().all(|bm| bm.name != m.name))
            .map(|method| Method {
                index: method.index + service.trait_methods.len(),
                ..method.clone()
            })
            .collect();
    }
    // fill comments in
    let comments = MethodComments::from_pool(pool);
    for service in &mut col_services {
        for method in &mut service.trait_methods {
            method.comments = comments.get_for_method(&method.proto);
        }
    }
    for service in &mut backend_services {
        for method in &mut service.trait_methods {
            method.comments = comments.get_for_method(&method.proto);
        }
        for method in &mut service.delegating_methods {
            method.comments = comments.get_for_method(&method.proto);
        }
    }

    (col_services, backend_services)
}

#[derive(Debug)]
pub struct CollectionService {
    pub name: String,
    pub index: usize,
    pub trait_methods: Vec<Method>,
    pub proto: ServiceDescriptor,
}

#[derive(Debug)]
pub struct BackendService {
    pub name: String,
    pub index: usize,
    pub trait_methods: Vec<Method>,
    pub delegating_methods: Vec<Method>,
    pub proto: ServiceDescriptor,
}

#[derive(Debug, Clone)]
pub struct Method {
    pub name: String,
    pub index: usize,
    pub comments: Option<String>,
    pub proto: MethodDescriptor,
}

impl CollectionService {
    pub fn from_proto(service: prost_reflect::ServiceDescriptor) -> Self {
        CollectionService {
            name: service.name().to_string(),
            index: service.index(),
            trait_methods: service.methods().map(Method::from_proto).collect(),
            proto: service,
        }
    }
}

impl BackendService {
    pub fn from_proto(service: prost_reflect::ServiceDescriptor) -> Self {
        BackendService {
            name: service.name().to_string(),
            index: service.index(),
            trait_methods: service.methods().map(Method::from_proto).collect(),
            proto: service,
            // filled in later
            delegating_methods: vec![],
        }
    }

    pub fn all_methods(&self) -> impl Iterator<Item = &Method> {
        self.trait_methods
            .iter()
            .chain(self.delegating_methods.iter())
    }
}

impl Method {
    pub fn from_proto(method: prost_reflect::MethodDescriptor) -> Self {
        Method {
            name: method.name().to_snake_case(),
            index: method.index(),
            proto: method,
            // filled in later
            comments: None,
        }
    }

    /// The input type, if not empty.
    pub fn input(&self) -> Option<MessageDescriptor> {
        msg_if_not_empty(self.proto.input())
    }

    /// The output type, if not empty.
    pub fn output(&self) -> Option<MessageDescriptor> {
        msg_if_not_empty(self.proto.output())
    }
}

fn msg_if_not_empty(msg: MessageDescriptor) -> Option<MessageDescriptor> {
    if msg.full_name() == "anki.generic.Empty" {
        None
    } else {
        Some(msg)
    }
}

#[derive(Debug)]
struct MethodComments<'a> {
    // package name -> method path -> comment
    by_package_and_path: HashMap<&'a str, HashMap<Vec<i32>, String>>,
}

impl<'a> MethodComments<'a> {
    pub fn from_pool(pool: &'a DescriptorPool) -> MethodComments<'a> {
        let mut by_package_and_path = HashMap::new();
        for file in pool.file_descriptor_protos() {
            let path_map = file
                .source_code_info
                .as_ref()
                .unwrap()
                .location
                .iter()
                .map(|l| (l.path.clone(), l.leading_comments().trim().to_string()))
                .collect();
            by_package_and_path.insert(file.package(), path_map);
        }
        Self {
            by_package_and_path,
        }
    }

    pub fn get_for_method(&self, method: &MethodDescriptor) -> Option<String> {
        self.by_package_and_path
            .get(method.parent_file().package_name())
            .and_then(|by_path| by_path.get(method.path()))
            .and_then(|s| if s.is_empty() { None } else { Some(s.into()) })
    }
}

pub fn add_must_use_annotations<P, E>(
    out_dir: &PathBuf,
    should_process_path: P,
    is_empty: E,
) -> Result<()>
where
    P: Fn(&Utf8Path) -> bool,
    E: Fn(&Utf8Path, &str) -> bool,
{
    for file in WalkDir::new(out_dir).into_iter() {
        let file = file?;
        let path = file.path().utf8()?;
        if path.file_name().unwrap().ends_with(".rs") && should_process_path(path) {
            add_must_use_annotations_to_file(path, &is_empty)?;
        }
    }
    Ok(())
}

pub fn add_must_use_annotations_to_file<E>(path: &Utf8Path, is_empty: E) -> Result<()>
where
    E: Fn(&Utf8Path, &str) -> bool,
{
    static MESSAGE_OR_ENUM_RE: Lazy<Regex> =
        Lazy::new(|| Regex::new(r"pub (struct|enum) ([[:alnum:]]+?)\s").unwrap());
    let contents = read_to_string(path)?;
    let contents = MESSAGE_OR_ENUM_RE.replace_all(&contents, |caps: &Captures| {
        let is_enum = caps.get(1).unwrap().as_str() == "enum";
        let name = caps.get(2).unwrap().as_str();
        if is_enum || !is_empty(path, name) {
            format!("#[must_use]\n{}", caps.get(0).unwrap().as_str())
        } else {
            caps.get(0).unwrap().as_str().to_string()
        }
    });
    write_file_if_changed(path, contents.as_ref())?;
    Ok(())
}

/// Given a generated prost filename and a struct name, try to determine whether
/// the message has 0 fields.
///
/// This is unfortunately rather circuitous, as Prost doesn't allow us to easily
/// alter the code generation with access to the associated proto descriptor. So
/// we need to infer the full proto path based on the filename and the Rust type
/// name, which we can only do for top-level elements. For any nested messages
/// we can't find, we assume they must be used.
pub fn determine_if_message_is_empty(pool: &DescriptorPool, path: &Utf8Path, name: &str) -> bool {
    let package = path.file_stem().unwrap();
    let full_name = format!("{package}.{name}");
    if let Some(msg) = pool.get_message_by_name(&full_name) {
        msg.fields().count() == 0
    } else {
        false
    }
}

/// - When building via a local checkout, the path defined in .cargo/config
/// - When building via cargo install or a third-party crate,
///   OUT_DIR/../../anki_descriptors.bin (so it can be seen by the rslib crate)
pub fn descriptors_path() -> PathBuf {
    if let Ok(path) = env::var("DESCRIPTORS_BIN") {
        PathBuf::from(path)
    } else {
        PathBuf::from(env::var("OUT_DIR").unwrap()).join("../../anki_descriptors.bin")
    }
}
