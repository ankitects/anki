// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    action::BuildAction,
    build::FilesHandle,
    cargo::{CargoBuild, RustOutput},
    glob, inputs, Build, Result,
};

pub struct ConfigureBuild {}

impl BuildAction for ConfigureBuild {
    fn command(&self) -> &str {
        "$cmd && ninja -f $builddir/build.ninja -t cleandead"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("cmd", inputs![":build:configure"]);
        // reconfigure when env changes
        build.add_inputs("", inputs!["$builddir/env"]);
        build.add_outputs("", ["build.ninja"])
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        build.add(
            "build:configure",
            CargoBuild {
                inputs: inputs![glob!["build/**/*"]],
                outputs: &[RustOutput::Binary("configure")],
                target: None,
                // we ensure runner is up to date, but don't declare it as output,
                // as ninja will try to clean up stale outputs, and that fails on
                // Windows. The ninja wrapper script should ensure the runner is up to
                // date anyway, but advanced users can invoke ninja directly to save
                // the ~80+ms it takes cargo to check that the runner is up to date.
                extra_args: "-p configure -p runner",
                release_override: Some(false),
            },
        )?;
        Ok(())
    }
}
