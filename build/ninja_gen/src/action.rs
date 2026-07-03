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
        std::any::type_name::<Self>()
            .split("::")
            .last()
            .unwrap()
            .split('<')
            .next()
            .unwrap()
    }
}

#[cfg(test)]
trait TestBuildAction {}

#[cfg(test)]
impl<T: TestBuildAction + ?Sized> BuildAction for T {
    fn command(&self) -> &str {
        "test"
    }
    fn files(&mut self, _build: &mut impl FilesHandle) {}
}

#[allow(dead_code, unused_variables)]
#[test]
fn should_strip_regions_in_type_name() {
    struct Bare;
    impl TestBuildAction for Bare {}
    assert_eq!(Bare {}.name(), "Bare");

    struct WithLifeTime<'a>(&'a str);
    impl TestBuildAction for WithLifeTime<'_> {}
    assert_eq!(WithLifeTime("test").name(), "WithLifeTime");

    struct WithMultiLifeTime<'a, 'b>(&'a str, &'b str);
    impl TestBuildAction for WithMultiLifeTime<'_, '_> {}
    assert_eq!(
        WithMultiLifeTime("test", "test").name(),
        "WithMultiLifeTime"
    );

    struct WithGeneric<T>(T);
    impl<T> TestBuildAction for WithGeneric<T> {}
    assert_eq!(WithGeneric(3).name(), "WithGeneric");
}
