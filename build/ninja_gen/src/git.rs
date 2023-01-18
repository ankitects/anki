// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use itertools::Itertools;

use super::*;
use crate::action::BuildAction;
use crate::input::BuildInput;

pub struct SyncSubmodule {
    pub path: &'static str,
}

impl BuildAction for SyncSubmodule {
    fn command(&self) -> &str {
        "git -c protocol.file.allow=always submodule update --init $path"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        if let Some(head) = locate_git_head() {
            build.add_inputs("", head);
        } else {
            println!("Warning, .git/HEAD not found; submodules may be stale");
        }
        build.add_variable("path", self.path);
        build.add_output_stamp(format!("git/{}", self.path));
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        build.pool("git", 1);
        Ok(())
    }

    fn concurrency_pool(&self) -> Option<&'static str> {
        Some("git")
    }
}

/// We check the mtime of .git/HEAD to detect when we should sync submodules.
/// If this repo is a submodule of another project, .git/HEAD will not exist,
/// and we fall back on .git/modules/*/HEAD in a parent folder instead.
fn locate_git_head() -> Option<BuildInput> {
    let standard_path = Utf8Path::new(".git/HEAD");
    if standard_path.exists() {
        return Some(inputs![standard_path.to_string()]);
    }

    let mut folder = Utf8Path::new(".").canonicalize_utf8().unwrap();
    loop {
        let path = folder.join(".git").join("modules");
        if path.exists() {
            let heads = path
                .read_dir_utf8()
                .unwrap()
                .filter_map(|p| {
                    let head = p.unwrap().path().join("HEAD");
                    if head.exists() {
                        Some(head.as_str().replace(':', "$:"))
                    } else {
                        None
                    }
                })
                .collect_vec();
            return Some(inputs![heads]);
        }
        if let Some(parent) = folder.parent() {
            folder = parent.to_owned();
        } else {
            return None;
        }
    }
}
