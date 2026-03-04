// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use ninja_gen::command::RunCommand;
use ninja_gen::glob;
use ninja_gen::hashmap;
use ninja_gen::inputs;
use ninja_gen::Build;

pub fn build_installer(build: &mut Build) -> Result<()> {
    build.add_action(
        "installer",
        RunCommand {
            command: ":pyenv:bin",
            args: "$script $aqt_wheel $anki_wheel $out",
            inputs: hashmap! {
                "script" => inputs!["qt/tools/build_installer.py"],
                "aqt_wheel" => inputs![":wheels:aqt"],
                "anki_wheel" => inputs![":wheels:anki"],
                "" => inputs![glob!["qt/installer/**"]],
            },
            outputs: hashmap! {
                "out" => vec!["installer"],
            },
        },
    )?;
    Ok(())
}
