// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;

use anyhow::Result;
use itertools::Itertools;

use super::*;
use crate::action::BuildAction;
use crate::archives::download_and_extract;
use crate::archives::OnlineArchive;
use crate::archives::Platform;
use crate::hash::simple_hash;
use crate::input::space_separated;
use crate::input::BuildInput;

pub fn node_archive(platform: Platform) -> OnlineArchive {
    match platform {
        Platform::LinuxX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-linux-x64.tar.xz",
            sha256: "822780369d0ea309e7d218e41debbd1a03f8cdf354ebf8a4420e89f39cc2e612",
        },
        Platform::LinuxArm => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-linux-arm64.tar.xz",
            sha256: "f943abd348d2b8ff8754ca912c118a20301eb6a0014cc4cdea86cff021fde8e6",
        },
        Platform::MacX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-darwin-x64.tar.xz",
            sha256: "d4b4ab81ebf1f7aab09714f834992f27270ad0079600da00c8110f8950ca6c5a",
        },
        Platform::MacArm => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-darwin-arm64.tar.xz",
            sha256: "f18a7438723d48417f5e9be211a2f3c0520ffbf8e02703469e5153137ca0f328",
        },
        Platform::WindowsX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v20.11.0/node-v20.11.0-win-x64.zip",
            sha256: "893115cd92ad27bf178802f15247115e93c0ef0c753b93dca96439240d64feb5",
        },
    }
}

pub struct YarnSetup {}

impl BuildAction for YarnSetup {
    fn command(&self) -> &str {
        if cfg!(windows) {
            "corepack.cmd enable yarn"
        } else {
            "corepack enable yarn"
        }
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("", inputs![":node_binary"]);
        build.add_outputs_ext(
            "bin",
            vec![if cfg!(windows) {
                "extracted/node/yarn.cmd"
            } else {
                "extracted/node/bin/yarn"
            }],
            true,
        );
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}
pub struct YarnInstall<'a> {
    pub package_json_and_lock: BuildInput,
    pub exports: HashMap<&'a str, Vec<Cow<'a, str>>>,
}

impl BuildAction for YarnInstall<'_> {
    fn command(&self) -> &str {
        "$runner yarn $yarn $out"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("", &self.package_json_and_lock);
        build.add_inputs("yarn", inputs![":yarn:bin"]);
        build.add_outputs("out", vec!["node_modules/.marker"]);
        for (key, value) in &self.exports {
            let outputs: Vec<_> = value.iter().map(|o| format!("node_modules/{o}")).collect();
            build.add_outputs_ext(*key, outputs, true);
        }
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}

fn with_cmd_ext(bin: &str) -> Cow<str> {
    if cfg!(windows) {
        format!("{bin}.cmd").into()
    } else {
        bin.into()
    }
}

pub fn setup_node(
    build: &mut Build,
    archive: OnlineArchive,
    binary_exports: &[&'static str],
    mut data_exports: HashMap<&str, Vec<Cow<str>>>,
) -> Result<()> {
    let node_binary = match std::env::var("NODE_BINARY") {
        Ok(path) => {
            assert!(
                Utf8Path::new(&path).is_absolute(),
                "NODE_BINARY must be absolute"
            );
            path.into()
        }
        Err(_) => {
            download_and_extract(
                build,
                "node",
                archive,
                hashmap! {
                    "bin" => vec![if cfg!(windows) { "node.exe" } else { "bin/node" }],
                    "npm" => vec![if cfg!(windows) { "npm.cmd " } else { "bin/npm" }]
                },
            )?;
            inputs![":extract:node:bin"]
        }
    };
    build.add_dependency("node_binary", node_binary);

    match std::env::var("YARN_BINARY") {
        Ok(path) => {
            assert!(
                Utf8Path::new(&path).is_absolute(),
                "YARN_BINARY must be absolute"
            );
            build.add_dependency("yarn:bin", inputs![path]);
        }
        Err(_) => {
            build.add_action("yarn", YarnSetup {})?;
        }
    };

    for binary in binary_exports {
        data_exports.insert(
            *binary,
            vec![format!(".bin/{}", with_cmd_ext(binary)).into()],
        );
    }
    build.add_action(
        "node_modules",
        YarnInstall {
            package_json_and_lock: inputs!["yarn.lock", "package.json"],
            exports: data_exports,
        },
    )?;
    Ok(())
}

pub struct EsbuildScript<'a> {
    pub script: BuildInput,
    pub entrypoint: BuildInput,
    pub deps: BuildInput,
    /// .js will be appended, and any extra extensions
    pub output_stem: &'a str,
    /// eg ['.css', '.html']
    pub extra_exts: &'a [&'a str],
}

impl BuildAction for EsbuildScript<'_> {
    fn command(&self) -> &str {
        "$node_bin $script $entrypoint $out"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("node_bin", inputs![":node_binary"]);
        build.add_inputs("script", &self.script);
        build.add_inputs("entrypoint", &self.entrypoint);
        build.add_inputs("", inputs!["yarn.lock", ":node_modules", &self.deps]);
        build.add_inputs("", inputs!["out/env"]);
        let stem = self.output_stem;
        let mut outs = vec![format!("{stem}.js")];
        outs.extend(self.extra_exts.iter().map(|ext| format!("{stem}.{ext}")));
        build.add_outputs("out", outs);
    }
}

pub struct DPrint {
    pub inputs: BuildInput,
    pub check_only: bool,
}

impl BuildAction for DPrint {
    fn command(&self) -> &str {
        "$dprint $mode"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("dprint", inputs![":node_modules:dprint"]);
        build.add_inputs("", &self.inputs);
        let mode = if self.check_only { "check" } else { "fmt" };
        build.add_variable("mode", mode);
        build.add_output_stamp(format!("tests/dprint.{mode}"));
    }
}

pub struct SvelteCheck {
    pub tsconfig: BuildInput,
    pub inputs: BuildInput,
}

impl BuildAction for SvelteCheck {
    fn command(&self) -> &str {
        "$svelte-check --tsconfig $tsconfig $
        --fail-on-warnings --threshold warning $
        --compiler-warnings $compiler_warnings"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("svelte-check", inputs![":node_modules:svelte-check"]);
        build.add_inputs("tsconfig", &self.tsconfig);
        build.add_inputs("", &self.inputs);
        build.add_inputs("", inputs!["yarn.lock"]);
        build.add_variable(
            "compiler_warnings",
            [
                "a11y-click-events-have-key-events",
                "a11y-no-noninteractive-tabindex",
                "a11y-no-static-element-interactions",
            ]
            .iter()
            .map(|warning| format!("{}$:ignore", warning))
            .collect::<Vec<_>>()
            .join(","),
        );
        let hash = simple_hash(&self.tsconfig);
        build.add_output_stamp(format!("tests/svelte-check.{hash}"));
    }

    fn hide_last_line(&self) -> bool {
        true
    }
}

pub struct TypescriptCheck {
    pub tsconfig: BuildInput,
    pub inputs: BuildInput,
}

impl BuildAction for TypescriptCheck {
    fn command(&self) -> &str {
        "$tsc --noEmit -p $tsconfig"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("tsc", inputs![":node_modules:tsc"]);
        build.add_inputs("tsconfig", &self.tsconfig);
        build.add_inputs("", &self.inputs);
        build.add_inputs("", inputs!["yarn.lock"]);
        let hash = simple_hash(&self.tsconfig);
        build.add_output_stamp(format!("tests/typescript.{hash}"));
    }
}

pub struct Eslint<'a> {
    pub folder: &'a str,
    pub inputs: BuildInput,
    pub eslint_rc: BuildInput,
    pub fix: bool,
}

impl BuildAction for Eslint<'_> {
    fn command(&self) -> &str {
        "$eslint --max-warnings=0 -c $eslint_rc $fix $folder"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("eslint", inputs![":node_modules:eslint"]);
        build.add_inputs("eslint_rc", &self.eslint_rc);
        build.add_inputs("in", &self.inputs);
        build.add_inputs("", inputs!["yarn.lock", "ts/tsconfig.json"]);
        build.add_variable("fix", if self.fix { "--fix" } else { "" });
        build.add_variable("folder", self.folder);
        let hash = simple_hash(self.folder);
        let kind = if self.fix { "fix" } else { "check" };
        build.add_output_stamp(format!("tests/eslint.{kind}.{hash}"));
    }
}

pub struct JestTest<'a> {
    pub folder: &'a str,
    pub deps: BuildInput,
    pub jest_rc: BuildInput,
    pub jsdom: bool,
}

impl BuildAction for JestTest<'_> {
    fn command(&self) -> &str {
        "$jest --config $config $env $folder"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("jest", inputs![":node_modules:jest"]);
        build.add_inputs("", &self.deps);
        build.add_inputs("config", &self.jest_rc);
        build.add_variable("env", if self.jsdom { "--env=jsdom" } else { "" });
        build.add_variable("folder", self.folder);
        let hash = simple_hash(self.folder);
        build.add_output_stamp(format!("tests/jest.{hash}"));
    }

    fn hide_last_line(&self) -> bool {
        true
    }
}

pub struct SqlFormat {
    pub inputs: BuildInput,
    pub check_only: bool,
}

impl BuildAction for SqlFormat {
    fn command(&self) -> &str {
        "$tsx $sql_format $mode $in"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("tsx", inputs![":node_modules:tsx"]);
        build.add_inputs("sql_format", inputs!["ts/tools/sql_format.ts"]);
        build.add_inputs("in", &self.inputs);
        let mode = if self.check_only { "check" } else { "fix" };
        build.add_variable("mode", mode);
        build.add_output_stamp(format!("tests/sql_format.{mode}"));
    }
}

pub struct GenTypescriptProto<'a> {
    pub protos: BuildInput,
    pub include_dirs: &'a [&'a str],
    /// Automatically created.
    pub out_dir: &'a str,
    /// Can be used to adjust the output js/dts files to point to out_dir.
    pub out_path_transform: fn(&str) -> String,
    /// Script to apply modifications to the generated files.
    pub ts_transform_script: &'static str,
}

impl BuildAction for GenTypescriptProto<'_> {
    fn command(&self) -> &str {
        "$protoc $includes $in \
        --plugin $gen-es --es_out $out_dir && \
        $tsx $transform_script $out_dir"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        let proto_files = build.expand_inputs(&self.protos);
        let output_files: Vec<_> = proto_files
            .iter()
            .flat_map(|f| {
                let js_path = f.replace(".proto", "_pb.js");
                let dts_path = f.replace(".proto", "_pb.d.ts");
                [
                    (self.out_path_transform)(&js_path),
                    (self.out_path_transform)(&dts_path),
                ]
            })
            .collect();

        build.create_dir_all("out_dir", self.out_dir);
        build.add_variable(
            "includes",
            self.include_dirs
                .iter()
                .map(|d| format!("-I {d}"))
                .join(" "),
        );
        build.add_inputs("protoc", inputs![":protoc_binary"]);
        build.add_inputs("gen-es", inputs![":node_modules:protoc-gen-es"]);
        build.add_inputs_vec("in", proto_files);
        build.add_inputs("", inputs!["yarn.lock"]);
        build.add_inputs("tsx", inputs![":node_modules:tsx"]);
        build.add_inputs("transform_script", inputs![self.ts_transform_script]);

        build.add_outputs("", output_files);
    }
}

pub struct CompileSass<'a> {
    pub input: BuildInput,
    pub output: &'a str,
    pub deps: BuildInput,
    pub load_paths: Vec<&'a str>,
}

impl BuildAction for CompileSass<'_> {
    fn command(&self) -> &str {
        "$sass -s compressed $args $in -- $out"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("sass", inputs![":node_modules:sass"]);
        build.add_inputs("in", &self.input);
        build.add_inputs("", &self.deps);

        let args = space_separated(self.load_paths.iter().map(|path| format!("-I {path}")));
        build.add_variable("args", args);

        build.add_outputs("out", vec![self.output]);
    }
}

/// Usually we rely on esbuild to transpile our .ts files on the fly, but when
/// we want generated code to be able to import a .ts file, we need to use
/// typescript to generate .js/.d.ts files, or types can't be looked up, and
/// esbuild can't find the file to bundle.
pub struct CompileTypescript<'a> {
    pub ts_files: BuildInput,
    /// Automatically created.
    pub out_dir: &'a str,
    /// Can be used to adjust the output js/dts files to point to out_dir.
    pub out_path_transform: fn(&str) -> String,
}

impl BuildAction for CompileTypescript<'_> {
    fn command(&self) -> &str {
        "$tsc $in --outDir $out_dir -d --skipLibCheck --types node"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("tsc", inputs![":node_modules:tsc"]);
        build.add_inputs("in", &self.ts_files);
        build.add_inputs("", inputs!["yarn.lock"]);

        let ts_files = build.expand_inputs(&self.ts_files);
        let output_files: Vec<_> = ts_files
            .iter()
            .flat_map(|f| {
                let js_path = f.replace(".ts", ".js");
                let dts_path = f.replace(".ts", ".d.ts");
                [
                    (self.out_path_transform)(&js_path),
                    (self.out_path_transform)(&dts_path),
                ]
            })
            .collect();

        build.create_dir_all("out_dir", self.out_dir);
        build.add_outputs("", output_files);
    }
}
