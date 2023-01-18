// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use crate::action::BuildAction;
use crate::input::space_separated;
use crate::input::BuildInput;
use crate::inputs;

pub struct RunCommand<'a> {
    // Will be automatically included as a dependency
    pub command: &'static str,
    // Arguments to the script, eg `$in $out` or `$in > $out`.
    pub args: &'a str,
    pub inputs: HashMap<&'static str, BuildInput>,
    pub outputs: HashMap<&'static str, Vec<&'a str>>,
}

impl BuildAction for RunCommand<'_> {
    fn command(&self) -> &str {
        "$cmd $args"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        // Because we've defined a generic rule instead of making one for a specific use
        // case, we need to manually intepolate variables in the user-provided
        // args.
        let mut args = self.args.to_string();
        for (key, inputs) in &self.inputs {
            let files = build.expand_inputs(inputs);
            build.add_inputs("", inputs);
            if !key.is_empty() {
                args = args.replace(&format!("${key}"), &space_separated(files));
            }
        }
        for (key, outputs) in &self.outputs {
            if !key.is_empty() {
                let outputs = outputs.iter().map(|o| {
                    if !o.starts_with("$builddir/") {
                        format!("$builddir/{o}")
                    } else {
                        (*o).into()
                    }
                });
                args = args.replace(&format!("${key}"), &space_separated(outputs));
            }
        }

        build.add_inputs("cmd", inputs![self.command]);
        build.add_variable("args", args);
        for outputs in self.outputs.values() {
            build.add_outputs("", outputs);
        }
    }
}
