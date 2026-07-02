use anki_io::{copy_file, create_dir_all};
use anki_io::{create_file, read_file};
use anki_process::CommandExt;
use anyhow::Result;
use camino::{Utf8Path, Utf8PathBuf};
use std::collections::HashSet;
use std::env;
use std::env::consts::OS;
use std::fs;
use std::io::{self, Write};
use std::path::Path;
use std::process::Command;
use streaming_iterator::StreamingIterator;
use tree_sitter::{Parser, Query, QueryCursor};

const ANDROID_OUT_DIR: &str = "rsdroid/build/generated/jniLibs";
const ROBOLECTRIC_OUT_DIR: &str = "rsdroid-testing/build/generated/jniLibs";

fn main() -> Result<()> {
    if env::var("RUNNING_FROM_BUILD_SCRIPT").is_ok() {
        return Ok(());
    }
    let ndk_path = Utf8PathBuf::from(env::var("ANDROID_NDK_HOME").unwrap_or_default());
    if !ndk_path.exists() {
        panic!("error: ANDROID_NDK_HOME must point to your NDK installation.");
    }

    build_web_artifacts()?;
    build_android_jni()?;
    build_robolectric_jni()?;
    run_gradle()?;

    println!();
    println!("*** Build complete.");

    Ok(())
}

fn run_gradle() -> Result<()> {
    if env::var("RUNNING_FROM_GRADLE").is_err() {
        println!("*** Running gradle");
        let mut cmd = if cfg!(windows) {
            let mut cmd = Command::new("cmd");
            cmd.args(["/c", "gradlew.bat"]);
            cmd
        } else {
            Command::new("./gradlew")
        };
        cmd.env("RUNNING_FROM_BUILD_SCRIPT", "1")
            .args(["assembleRelease", "rsdroid-testing:build"])
            .ensure_success()?;
    }
    Ok(())
}

fn build_web_artifacts() -> Result<()> {
    println!("*** Building desktop web components");
    let artifacts_dir = Path::new("rsdroid/build/generated/anki_artifacts/backend");
    let mut cmd = if cfg!(windows) {
        let mut cmd = Command::new("cmd");
        cmd.args(["/c", "tools\\ninja.bat"]);
        cmd
    } else {
        Command::new("./ninja")
    };

    cmd.current_dir("anki")
        .args([
            "extract:protoc",
            "css:_root-vars",
            "ts:reviewer:reviewer.js",
            "ts:reviewer:reviewer.css",
            "ts:reviewer:reviewer_extras_bundle.js",
            "ts:reviewer:reviewer_extras.css",
            "ts:mathjax",
            "qt:aqt:data:web:js:vendor:mathjax",
            "node_modules:jquery",
            "sveltekit",
        ])
        .ensure_success()?;

    copy_dir_all("anki/out/sveltekit", artifacts_dir.join("sveltekit"))?;
    // directories starting with `_` are ignored by default:
    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/tools/aapt/AaptAssets.cpp;l=63;drc=835dfe50a73c6f6de581aaa143c333af79bcca4d
    let new_svelte_app_path = artifacts_dir.join("sveltekit/app");
    if new_svelte_app_path.exists() {
        fs::remove_dir_all(&new_svelte_app_path)?;
    }
    fs::rename(artifacts_dir.join("sveltekit/_app"), &new_svelte_app_path)?;

    create_dir_all(artifacts_dir.join("js"))?;
    create_dir_all(artifacts_dir.join("css"))?;

    let query = String::from_utf8(
        read_file(Path::new("build_rust/tree_sitter_queries/ts_imports.scm")).unwrap(),
    )
    .unwrap();
    let mut ts_funcs = HashSet::new();
    let mut ts_code = vec![];
    ts_code.append(&mut ts_all_scripts(Path::new("anki/ts")).unwrap());
    // Also include scripts inside of Svelte files.
    ts_code.append(&mut svelte_all_scripts(Path::new("anki/ts")).unwrap());
    for script_code in ts_code {
        // Get all of the imported backend funcs from the code.
        ts_funcs.extend(ts_imported_funcs(script_code, &query).unwrap());
    }

    if let Ok(mut funcs_file) = create_file(artifacts_dir.join("ts_funcs.txt")) {
        let mut content = ts_funcs
            .iter()
            .map(|name| name.to_string())
            .collect::<Vec<String>>();
        // Make sure the file ends with a newline.
        content.push(String::new());
        content.dedup();
        funcs_file.write_all(content.join("\n").as_bytes()).unwrap();
    }

    copy_file(
        "anki/out/ts/reviewer/reviewer_extras_bundle.js",
        artifacts_dir.join("js/reviewer_extras_bundle.js"),
    )?;
    copy_file(
        "anki/out/ts/reviewer/reviewer_extras.css",
        artifacts_dir.join("css/reviewer_extras.css"),
    )?;
    copy_file(
        "anki/out/ts/reviewer/reviewer.js",
        artifacts_dir.join("js/reviewer.js"),
    )?;
    copy_file(
        "anki/out/ts/reviewer/reviewer.css",
        artifacts_dir.join("css/reviewer.css"),
    )?;

    copy_dir_all(
        "anki/out/qt/_aqt/data/web/js/vendor/mathjax",
        artifacts_dir.join("js/vendor/mathjax"),
    )?;
    copy_file(
        "anki/out/ts/mathjax/mathjax.js",
        artifacts_dir.join("js/mathjax.js"),
    )?;
    copy_file(
        "anki/out/node_modules/jquery/dist/jquery.min.js",
        artifacts_dir.join("js/jquery.min.js"),
    )?;
    copy_file(
        "anki/out/ts/lib/sass/_root-vars.css",
        artifacts_dir.join("css/root-vars.css"),
    )?;
    copy_file(
        "anki/cargo/licenses.json",
        artifacts_dir.join("licenses-cargo.json"),
    )?;
    copy_file(
        "anki/ts/licenses.json",
        artifacts_dir.join("licenses-ts.json"),
    )?;
    Ok(())
}

fn build_android_jni() -> Result<()> {
    println!("*** Building Android JNI library + backend interface");
    let jni_dir = Path::new(ANDROID_OUT_DIR);
    if jni_dir.exists() {
        std::fs::remove_dir_all(jni_dir)?;
    }
    create_dir_all(jni_dir)?;

    let all_archs = env::var("ALL_ARCHS").is_ok();
    let ndk_targets = add_android_rust_targets(all_archs)?;
    let (is_release, _release_dir) = check_release(false);

    // Also listed in Cargo.toml for dependabot tracking
    Command::run("cargo install cargo-ndk@4.1.2")?;

    let mut command = Command::new("cargo");
    command
        // build products go into separate folder so they don't trigger recompile
        // of robolectric/desktop code
        .env("CARGO_TARGET_DIR", "target")
        .env("STRINGS_JSON", env!("STRINGS_JSON_ANKIDROID"))
        .arg("ndk")
        .arg("-o")
        .arg(jni_dir)
        .args(ndk_targets)
        .args(["build", "-p", "rsdroid"]);
    if is_release {
        command.arg("--release");
    }
    command.env("RUSTFLAGS", "-C link-args=-Wl,-z,max-page-size=16384");
    command.ensure_success()?;

    Ok(())
}

// is_release, release/debug dir
// windows robolectric is forced to release, as debug builds fail with an error
fn check_release(force_release_on_windows: bool) -> (bool, &'static str) {
    if env::var("RELEASE").is_ok() || (force_release_on_windows && cfg!(windows)) {
        (true, "release")
    } else {
        (false, "debug")
    }
}

/// Returns target list to pass to cargo ndk
fn add_android_rust_targets(all_archs: bool) -> Result<&'static [&'static str]> {
    Ok(if all_archs {
        add_rust_targets(&[
            "armv7-linux-androideabi",
            "i686-linux-android",
            "aarch64-linux-android",
            "x86_64-linux-android",
        ])?;
        &[
            "-t",
            "armv7-linux-androideabi",
            "-t",
            "i686-linux-android",
            "-t",
            "aarch64-linux-android",
            "-t",
            "x86_64-linux-android",
        ]
    } else if cfg!(all(target_os = "macos", target_arch = "aarch64")) {
        add_rust_targets(&["aarch64-linux-android"])?;
        &["-t", "arm64-v8a"]
    } else {
        add_rust_targets(&["x86_64-linux-android"])?;
        &["-t", "x86_64"]
    })
}

fn add_rust_targets(targets: &[&str]) -> Result<()> {
    Command::new("rustup")
        .args(["target", "add"])
        .args(targets)
        .ensure_success()?;
    Ok(())
}

fn build_robolectric_jni() -> Result<()> {
    println!("*** Building Robolectric JNI library");
    let jni_dir = Path::new(ROBOLECTRIC_OUT_DIR);
    if jni_dir.exists() {
        std::fs::remove_dir_all(jni_dir)?;
    }
    create_dir_all(jni_dir)?;

    let all_archs = env::var("ALL_ARCHS").is_ok();
    let (is_release, release_dir) = check_release(true);
    let target_root = Utf8Path::new("anki/out/rust");
    let file_in_target =
        |platform: &str, fname: &str| target_root.join(platform).join(release_dir).join(fname);

    if all_archs {
        if cfg!(not(target_os = "macos")) {
            panic!("Must be on macOS to do a multi-arch build.");
        }

        let mac_targets = &["x86_64-apple-darwin", "aarch64-apple-darwin"];
        add_rust_targets(mac_targets)?;
        for target in mac_targets {
            build_rsdroid(is_release, target, target_root)?;
        }
        Command::new("lipo")
            .arg("-create")
            .args(&[
                file_in_target("x86_64-apple-darwin", "librsdroid.dylib"),
                file_in_target("aarch64-apple-darwin", "librsdroid.dylib"),
            ])
            .arg("-output")
            .arg(jni_dir.join("librsdroid.dylib"))
            .ensure_success()?;

        let linux_targets = &["x86_64-unknown-linux-gnu"];
        add_rust_targets(linux_targets)?;
        build_rsdroid(is_release, linux_targets[0], target_root)?;
        copy_file(
            file_in_target(linux_targets[0], "librsdroid.so"),
            jni_dir.join("librsdroid.so"),
        )?;

        let windows_targets = &["x86_64-pc-windows-gnu"];
        add_rust_targets(windows_targets)?;
        build_rsdroid(is_release, windows_targets[0], target_root)?;
        copy_file(
            file_in_target(windows_targets[0], "rsdroid.dll"),
            jni_dir.join("rsdroid.dll"),
        )?;
    } else {
        // Just build for current architecture
        build_rsdroid(is_release, "", target_root)?;
        let mut found_one = false;
        for fname in ["librsdroid.so", "librsdroid.dylib", "rsdroid.dll"] {
            let file = target_root.join(release_dir).join(fname);
            if Path::new(&file).exists() {
                found_one = true;
                copy_file(&file, jni_dir.join(fname))?;
            }
        }
        assert!(
            found_one,
            "expected to find at least one robolectric library"
        );
    }

    Ok(())
}

fn build_rsdroid(is_release: bool, target_arch: &str, target_dir: &Utf8Path) -> Result<()> {
    let mut command = Command::new("cargo");
    command
        // Robolectric build cache can be shared with desktop, as it's the same arch
        .env("CARGO_TARGET_DIR", target_dir)
        .env("STRINGS_JSON", env!("STRINGS_JSON_ANKIDROID"))
        .args(["build", "-p", "rsdroid"]);
    if is_release {
        command.arg("--release");
    }
    if !target_arch.is_empty() {
        command.args(["--target", target_arch]);
    }
    if OS == "macos" && target_arch == "x86_64-unknown-linux-gnu" {
        command.env("CC", "x86_64-unknown-linux-gnu-gcc").env(
            "CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER",
            "x86_64-unknown-linux-gnu-gcc",
        );
    }
    command.ensure_success()?;
    Ok(())
}

// Copies a directory and all of its files and subdirectories
fn copy_dir_all(src: impl AsRef<Path>, dst: impl AsRef<Path>) -> io::Result<()> {
    fs::create_dir_all(&dst)?;
    for entry in fs::read_dir(src)? {
        let entry = entry?;
        let file_type = entry.file_type()?;
        if file_type.is_dir() {
            copy_dir_all(entry.path(), dst.as_ref().join(entry.file_name()))?;
        } else {
            fs::copy(entry.path(), dst.as_ref().join(entry.file_name()))?;
        }
    }
    Ok(())
}

fn ts_all_scripts(dir: &Path) -> Result<Vec<String>> {
    let ts_scripts = fs::read_dir(dir).unwrap();
    let mut script_code = vec![];

    for script in ts_scripts {
        let file_path = script.as_ref().unwrap().path();
        let file_name = file_path.file_name().unwrap().to_string_lossy();

        if file_path.is_dir() && !file_name.starts_with(".") {
            script_code.append(&mut ts_all_scripts(file_path.as_path()).unwrap());
        } else if let Some(extension) = file_path.extension() {
            if extension == "ts" {
                script_code.push(
                    String::from_utf8(read_file(Path::new(file_path.to_str().unwrap())).unwrap())
                        .unwrap(),
                );
            }
        }
    }

    Ok(script_code)
}

fn svelte_all_scripts(dir: &Path) -> Result<Vec<String>> {
    let mut parser = Parser::new();
    parser
        .set_language(&tree_sitter_svelte_ng::LANGUAGE.into())
        .unwrap();
    let svelte_files = fs::read_dir(dir).unwrap();
    let mut script_code = vec![];

    for file in svelte_files {
        let file_path = file.as_ref().unwrap().path();
        let file_name = file_path.file_name().unwrap().to_string_lossy();

        if file_path.is_dir() && !file_name.starts_with(".") {
            script_code.append(&mut svelte_all_scripts(file_path.as_path()).unwrap());
        } else if let Some(extension) = file_path.extension() {
            if extension == "svelte" {
                let svelte_code = &read_file(file_path.clone()).unwrap();
                let tree = parser.parse(svelte_code, None).unwrap();
                let root_node = tree.root_node();
                let mut root_cursor = root_node.walk();

                // Always expect scripts in Svelte files to be a child of the root node.
                for script_node in root_node.children(&mut root_cursor) {
                    if script_node.kind() != "script_element" {
                        continue;
                    }

                    let code = script_node
                        .child(1)
                        .unwrap()
                        .utf8_text(svelte_code)
                        .unwrap()
                        .to_string();
                    script_code.push(code.clone());
                }
            }
        }
    }

    Ok(script_code)
}

fn ts_imported_funcs(code: String, query: &str) -> Result<Vec<String>> {
    let mut parser = Parser::new();
    let code_bytes = code.as_bytes();
    parser
        .set_language(&tree_sitter_typescript::LANGUAGE_TYPESCRIPT.into())
        .unwrap();
    let tree = parser.parse(code_bytes, None).unwrap();
    let mut query_cursor = QueryCursor::new();
    let query = Query::new(&tree_sitter_typescript::LANGUAGE_TYPESCRIPT.into(), query).unwrap();

    let mut imports = vec![];
    let mut matches = query_cursor.matches(&query, tree.root_node(), code_bytes);
    while let Some(item) = matches.next() {
        let captures = item.captures;
        // The name of an import. E.g. "importDone" if the import line is:
        // "import { importDone } from "@generated/backend";
        // Should always be the first captured field.
        let name = captures
            .first()
            .unwrap()
            .node
            .utf8_text(code_bytes)
            .unwrap();
        // The source of an import. E.g. "@generated/backend" if the import line is:
        // "import { importDone } from "@generated/backend";
        // Should always be the second captured field.
        let src = captures.get(1).unwrap().node.utf8_text(code_bytes).unwrap();

        if src == "\"@generated/backend\"" {
            imports.push(name.to_string());
        }
    }

    Ok(imports)
}
