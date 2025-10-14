// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::env;
use std::fmt::Write;
use std::path::PathBuf;

use anki_io::write_file_if_changed;
use anki_proto_gen::get_services;
use anki_proto_gen::CollectionService;
use anyhow::Context;
use anyhow::Result;
use inflections::Inflect;
use prost_reflect::DescriptorPool;

pub fn write_rust_interface(pool: &DescriptorPool) -> Result<()> {
    let mut buf = String::new();
    buf.push_str("use prost::Message; use anyhow::Context; use anyhow::anyhow;");

    let (services, _) = get_services(pool);
    if let Some(s) = services
        .into_iter()
        .find(|s| s.name.starts_with("Launcher"))
    {
        render_service(&s, &mut buf);
    }

    let buf = format_code(buf)?;
    let out_dir = env::var("OUT_DIR").unwrap();
    let path = PathBuf::from(out_dir).join("rpc.rs");
    write_file_if_changed(path, buf).context("write file")?;

    Ok(())
}

fn render_service(service: &CollectionService, buf: &mut impl Write) {
    buf.write_str(
        r#"
pub(crate) async fn handle_rpc<R: ::tauri::Runtime>(
    app: ::tauri::AppHandle<R>,
    window: ::tauri::WebviewWindow<R>,
    req: ::tauri::http::Request<Vec<u8>>,
) -> ::anyhow::Result<Vec<u8>> {
    let method = &req.uri().path()[1..];
    println!("{}: {method}", window.url().unwrap());
    match method {
"#,
    )
    .unwrap();

    for method in &service.trait_methods {
        let method_name = method.name.to_snake_case();
        let handler_method_name = format!("crate::commands::{method_name}");
        let method_name_ts = method_name.to_camel_case();

        let output_map = if method.output().is_some() {
            Cow::from(format!(
                ".map(|o: {}| o.encode_to_vec())",
                method.output_type().unwrap()
            ))
        } else {
            Cow::from(".map(|()| Vec::new())")
        };

        let handler_call = if method.input().is_some() {
            let input_type = method.input_type().unwrap();
            format!(
                r##"
            let input = ::{input_type}::decode(req.body().as_slice())
                .with_context(|| "failed to decode protobuf for {method_name_ts}")?;
            {handler_method_name}(app, window, input)
"##
            )
        } else {
            format!(
                r#"
            {handler_method_name}(app, window)
"#
            )
        };

        if let Some(comments) = method.comments.as_deref() {
            writeln!(
                buf,
                r#"
            /*
               {comments}
             */
            "#
            )
            .unwrap();
        }

        writeln!(
            buf,
            r#"
        "{method_name_ts}" => {{
            {handler_call}
                .await
                {output_map}
        }}
"#
        )
        .unwrap();
    }
    buf.write_str(
        r#"
        _ => Err(anyhow!("{method} not implemented"))?,
    }
    .with_context(|| format!("{method} rpc call failed"))
}
        "#,
    )
    .unwrap();
}

trait MethodHelpers {
    fn input_type(&self) -> Option<String>;
    fn output_type(&self) -> Option<String>;
}

impl MethodHelpers for anki_proto_gen::Method {
    fn input_type(&self) -> Option<String> {
        self.input().map(|t| rust_type(t.full_name()))
    }

    fn output_type(&self) -> Option<String> {
        self.output().map(|t| rust_type(t.full_name()))
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

fn format_code(code: String) -> Result<String> {
    let syntax_tree = syn::parse_file(&code)?;
    Ok(prettyplease::unparse(&syntax_tree))
}
