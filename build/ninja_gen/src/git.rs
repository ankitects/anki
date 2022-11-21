// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::*;
use crate::action::BuildAction;

pub struct SyncSubmodule {
    pub path: &'static str,
}

impl BuildAction for SyncSubmodule {
    fn command(&self) -> &str {
        "git submodule update --init $path"
    }

    fn files(&mut self, build: &mut impl build::FilesHandle) {
        build.add_inputs("", inputs![".git/HEAD"]);
        build.add_variable("path", self.path);
        build.add_output_stamp(format!("git/{}", self.path));
    }
}
