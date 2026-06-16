// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anyhow::Result;

use crate::action::BuildAction;
use crate::build::FilesHandle;
use crate::hash::simple_hash;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;

struct CogRender<'a> {
    pub inputs: &'a BuildInput,
    pub deps: &'a BuildInput,
    pub check_only: bool,
}

impl BuildAction for CogRender<'_> {
    fn command(&self) -> &str {
        "$cog -I . --markers \"<<[cog ]>> <<[end]>>\" $mode $in"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("", self.deps);
        build.add_inputs("in", self.inputs);
        build.add_inputs("cog", inputs![":pyenv:cog"]);
        build.add_variable(
            "mode",
            if self.check_only {
                "--check --diff"
            } else {
                "-r $in"
            },
        );

        let hash = simple_hash(self.inputs);
        build.add_output_stamp(format!(
            "tests/cog_check.{}.{hash}",
            if self.check_only { "check" } else { "render" }
        ));
    }
}

pub fn cog_render(
    build: &mut Build,
    group: &str,
    inputs: BuildInput,
    deps: BuildInput,
) -> Result<()> {
    build.add_action(
        format!("check:format:cog:{group}"),
        CogRender {
            inputs: &inputs,
            deps: &deps,
            check_only: true,
        },
    )?;
    build.add_action(
        format!("format:cog:{group}"),
        CogRender {
            inputs: &inputs,
            deps: &deps,
            check_only: false,
        },
    )?;
    Ok(())
}
