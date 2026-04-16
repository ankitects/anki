// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod action;
pub mod archives;
pub mod build;
pub mod cargo;
pub mod command;
pub mod configure;
pub mod copy;
pub mod git;
pub mod hash;
pub mod input;
pub mod node;
pub mod protobuf;
pub mod python;
pub mod render;
pub mod rsync;
pub mod sass;

pub use build::Build;
pub use camino::Utf8Path;
pub use camino::Utf8PathBuf;
pub use maplit::hashmap;
pub use which::which;

#[macro_export]
macro_rules! inputs {
    ($($param:expr),+ $(,)?) => {
        $crate::input::BuildInput::from(vec![$($crate::input::BuildInput::from($param)),+])
    };
    () => {
        $crate::input::BuildInput::Empty
    };
}

#[macro_export]
macro_rules! glob {
    ($include:expr) => {
        $crate::input::Glob {
            include: $include.into(),
            exclude: None,
        }
    };
    ($include:expr, $exclude:expr) => {
        $crate::input::Glob {
            include: $include.into(),
            exclude: Some($exclude.into()),
        }
    };
}
