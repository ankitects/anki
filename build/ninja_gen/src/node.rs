// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;

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
            url: "https://nodejs.org/dist/v18.12.1/node-v18.12.1-linux-x64.tar.xz",
            sha256: "4481a34bf32ddb9a9ff9540338539401320e8c3628af39929b4211ea3552a19e",
        },
        Platform::LinuxArm => OnlineArchive {
            url: "https://nodejs.org/dist/v18.12.1/node-v18.12.1-linux-arm64.tar.xz",
            sha256: "3904869935b7ecc51130b4b86486d2356539a174d11c9181180cab649f32cd2a",
        },
        Platform::MacX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v18.12.1/node-v18.12.1-darwin-x64.tar.xz",
            sha256: "6c88d462550a024661e74e9377371d7e023321a652eafb3d14d58a866e6ac002",
        },
        Platform::MacArm => OnlineArchive {
            url: "https://nodejs.org/dist/v18.12.1/node-v18.12.1-darwin-arm64.tar.xz",
            sha256: "17f2e25d207d36d6b0964845062160d9ed16207c08d09af33b9a2fd046c5896f",
        },
        Platform::WindowsX64 => OnlineArchive {
            url: "https://nodejs.org/dist/v18.12.1/node-v18.12.1-win-x64.zip",
            sha256: "5478a5a2dce2803ae22327a9f8ae8494c1dec4a4beca5bbf897027380aecf4c7",
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
        build.add_inputs("", inputs![":extract:node"]);
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
    download_and_extract(
        build,
        "node",
        archive,
        hashmap! {
            "bin" => vec![if cfg!(windows) { "node.exe" } else { "bin/node" }],
            "npm" => vec![if cfg!(windows) { "npm.cmd " } else { "bin/npm" }]
        },
    )?;

    let node_binary = match std::env::var("NODE_BINARY") {
        Ok(path) => {
            assert!(
                Utf8Path::new(&path).is_absolute(),
                "NODE_BINARY must be absolute"
            );
            path.into()
        }
        Err(_) => {
            inputs![":extract:node:bin"]
        }
    };
    let node_binary = build.expand_inputs(node_binary);
    build.variable("node_binary", &node_binary[0]);

    build.add("yarn", YarnSetup {})?;

    for binary in binary_exports {
        data_exports.insert(
            *binary,
            vec![format!(".bin/{}", with_cmd_ext(binary)).into()],
        );
    }
    build.add(
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
        build.add_inputs("node_bin", inputs!["$node_binary"]);
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
        --fail-on-warnings --threshold warning --use-new-transformation"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("svelte-check", inputs![":node_modules:svelte-check"]);
        build.add_inputs("tsconfig", &self.tsconfig);
        build.add_inputs("", &self.inputs);
        build.add_inputs("", inputs!["yarn.lock"]);
        let hash = simple_hash(&self.tsconfig);
        build.add_output_stamp(format!("tests/svelte-check.{hash}"));
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
        build.add_inputs("", inputs!["yarn.lock"]);
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
        build.add_inputs("sql_format", inputs!["ts/sql_format/sql_format.ts"]);
        build.add_inputs("in", &self.inputs);
        let mode = if self.check_only { "check" } else { "fix" };
        build.add_variable("mode", mode);
        build.add_output_stamp(format!("tests/sql_format.{mode}"));
    }
}

pub struct GenTypescriptProto {
    pub protos: BuildInput,
    /// .js and .d.ts will be added to it
    pub output_stem: &'static str,
}

impl BuildAction for GenTypescriptProto {
    fn command(&self) -> &str {
        "$pbjs --target=static-module --wrap=default --force-number --force-message --out=$static $in && $
         $pbjs --target=json-module --wrap=default --force-number --force-message --out=$js $in && $
         $pbts --out=$dts $static && $
         rm $static"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("pbjs", inputs![":node_modules:pbjs"]);
        build.add_inputs("pbts", inputs![":node_modules:pbts"]);
        build.add_inputs("in", &self.protos);
        build.add_inputs("", inputs!["yarn.lock"]);

        let stem = self.output_stem;
        build.add_variable("static", format!("$builddir/{stem}_static.js"));
        build.add_outputs("js", vec![format!("{stem}.js")]);
        build.add_outputs("dts", vec![format!("{stem}.d.ts")]);
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
