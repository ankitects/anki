// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Experimental deno rules. Not currently used.

use crate::{action::BuildAction, build::FilesHandle, input::BuildInput, inputs};

pub struct DenoBundle<'a> {
    pub input: &'a str,
}

impl BuildAction for DenoBundle<'_> {
    fn command(&self) -> &str {
        "deno bundle -q $in > $out"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("in", inputs![self.input]);
        build.add_outputs("out", vec![self.input.replace(".ts", ".js")]);
    }
}

pub struct DenoTerser<'a> {
    pub input: BuildInput,
    pub args: &'a str,
}

impl BuildAction for DenoTerser<'_> {
    fn command(&self) -> &str {
        "deno run --unstable --allow-read=./,$builddir/ --allow-env npm:terser $args $in > $out"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        let input = build.expand_input(&self.input);
        let output = input.replace(".js", ".min.js");
        build.add_inputs_vec("in", vec![input]);
        build.add_outputs("out", vec![output]);
        build.add_variable("args", self.args);
    }
}

pub struct DenoLint {
    pub inputs: BuildInput,
    pub package: &'static str,
}

impl BuildAction for DenoLint {
    fn command(&self) -> &str {
        "deno lint $in && touch $out"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("in", &self.inputs);
        build.add_outputs("out", vec![format!("tests/deno_lint.{}", self.package)]);
    }
}
