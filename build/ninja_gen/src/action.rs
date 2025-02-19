// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;

use crate::build::FilesHandle;
use crate::Build;

pub trait BuildAction {
    /// Command line to invoke for each build statement.
    fn command(&self) -> &str;

    /// Declare the input files and variables, and output files.
    fn files(&mut self, build: &mut impl FilesHandle);

    /// If true, this action will not trigger a rebuild of dependent targets if
    /// the output files are unchanged. This corresponds to Ninja's "restat"
    /// argument.
    fn check_output_timestamps(&self) -> bool {
        false
    }

    /// True if this rule generates build.ninja
    fn generator(&self) -> bool {
        false
    }

    /// Called on first action invocation; can be used to inject other build
    /// actions to perform initial setup.
    #[allow(unused_variables)]
    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        Ok(())
    }

    fn concurrency_pool(&self) -> Option<&'static str> {
        None
    }

    fn bypass_runner(&self) -> bool {
        false
    }

    fn hide_success(&self) -> bool {
        true
    }

    fn hide_progress(&self) -> bool {
        false
    }

    fn name(&self) -> &'static str {
        std::any::type_name::<Self>().split("::").last().unwrap()
    }
}
