// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;

use crate::action::BuildAction;
use crate::cargo::CargoInstall;
use crate::input::space_separated;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;

pub struct CompileSassWithGrass {
    pub input: BuildInput,
    pub output: &'static str,
    pub deps: BuildInput,
    pub load_paths: Vec<&'static str>,
}

impl BuildAction for CompileSassWithGrass {
    fn command(&self) -> &str {
        "$grass $args -s compressed $in -- $out"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        let args = space_separated(self.load_paths.iter().map(|path| format!("-I {path}")));

        build.add_inputs("grass", inputs![":grass"]);
        build.add_inputs("in", &self.input);
        build.add_inputs("", &self.deps);
        build.add_variable("args", args);
        build.add_outputs("out", vec![self.output]);
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        build.add_action(
            "grass",
            CargoInstall {
                binary_name: "grass",
                args: "grass --version 0.11.2",
            },
        )?;
        Ok(())
    }
}
