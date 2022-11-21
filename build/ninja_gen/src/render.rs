// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{fmt::Write, fs::read_to_string};

use crate::{archives::with_exe, input::space_separated, Build};

impl Build {
    pub fn render(&self) -> String {
        let mut buf = String::new();

        writeln!(
            &mut buf,
            "# This file is automatically generated by configure.rs. Any edits will be lost.\n"
        )
        .unwrap();

        writeln!(&mut buf, "builddir = {}", self.buildroot.as_str()).unwrap();
        writeln!(
            &mut buf,
            "runner = $builddir/rust/debug/{}",
            with_exe("runner")
        )
        .unwrap();

        for (key, value) in &self.variables {
            writeln!(&mut buf, "{} = {}", key, value).unwrap();
        }
        buf.push('\n');

        for (key, value) in &self.pools {
            writeln!(&mut buf, "pool {}\n  depth = {}", key, value).unwrap();
        }
        buf.push('\n');

        buf.push_str(&self.output_text);

        for (group, targets) in &self.groups {
            let group = group.replace(':', "_");
            writeln!(
                &mut buf,
                "build {group}: phony {}",
                space_separated(targets)
            )
            .unwrap();
            buf.push('\n');
        }

        buf.push_str(&self.trailing_text);

        buf
    }

    pub fn write_build_file(&self) {
        let existing_contents = read_to_string("build.ninja").unwrap_or_default();
        let new_contents = self.render();
        if existing_contents != new_contents {
            let folder = &self.buildroot;
            if !folder.exists() {
                std::fs::create_dir_all(folder).expect("create build dir");
            }
            std::fs::write(folder.join("build.ninja"), new_contents).expect("write build.ninja");
        }

        // dbg!(&self.groups);
    }
}
