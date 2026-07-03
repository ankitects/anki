// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use camino::Utf8Path;

use crate::action::BuildAction;
use crate::build::FilesHandle;
use crate::input::space_separated;
use crate::input::BuildInput;

/// Rsync the provided inputs into `output_folder`, preserving directory
/// structure, eg foo/bar.js -> out/$target_folder/foo/bar.js. `strip_prefix`
/// can be used to remove a portion of the the path when copying. If the input
/// files are from previous build outputs, the prefix should begin with
/// `$builddir/`.
pub struct RsyncFiles<'a> {
    pub inputs: BuildInput,
    pub target_folder: &'a str,
    pub strip_prefix: &'static str,
    pub extra_args: &'a str,
}

impl BuildAction for RsyncFiles<'_> {
    fn command(&self) -> &str {
        "$runner rsync $extra_args --prefix $stripped_prefix --inputs $inputs_without_prefix --output-dir $builddir/$output_folder"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        let inputs = build.expand_inputs(&self.inputs);
        build.add_inputs_vec("", inputs.clone());
        let output_folder = Utf8Path::new(self.target_folder);
        let (prefix, inputs_without_prefix) = if self.strip_prefix.is_empty() {
            (".", inputs)
        } else {
            let stripped_inputs = inputs
                .iter()
                .map(|p| {
                    Utf8Path::new(p)
                        .strip_prefix(self.strip_prefix)
                        .unwrap_or_else(|_| {
                            panic!("expected {} to start with {}", p, self.strip_prefix)
                        })
                        .to_string()
                })
                .collect();
            (self.strip_prefix, stripped_inputs)
        };
        build.add_variable(
            "inputs_without_prefix",
            space_separated(&inputs_without_prefix),
        );
        build.add_variable("stripped_prefix", prefix);
        build.add_variable("output_folder", self.target_folder);
        if !self.extra_args.is_empty() {
            build.add_variable(
                "extra_args",
                format!("--extra-args {}", self.extra_args.replace(' ', ",")),
            );
        }

        let outputs = inputs_without_prefix
            .iter()
            .map(|p| output_folder.join(p).to_string());
        build.add_outputs("", outputs);
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}
