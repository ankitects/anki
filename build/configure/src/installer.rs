// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::build::FilesHandle;
use ninja_gen::git::SyncSubmodule;
use ninja_gen::glob;
use ninja_gen::inputs;
use ninja_gen::Build;

use crate::anki_version;

pub struct BuildInstaller {
    pub version: String,
}

impl BuildAction for BuildInstaller {
    fn command(&self) -> &str {
        "$pyenv_bin $script --version $version --aqt_wheel $aqt_wheel --anki_wheel $anki_wheel --out_dir $out"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["qt/tools/build_installer.py"]);
        build.add_variable("version", &self.version);
        if cfg!(target_os = "linux") {
            build.add_variable("aqt_wheel", "_");
            build.add_variable("anki_wheel", "_");
        } else {
            build.add_inputs("aqt_wheel", inputs![":wheels:aqt"]);
            build.add_inputs("anki_wheel", inputs![":wheels:anki"]);
        };
        build.add_inputs(
            "",
            inputs![
                ":installer:template",
                ":pylib",
                ":qt",
                glob!["qt/installer/**"]
            ],
        );
        build.add_outputs("out", vec!["installer"]);
    }
}

pub fn build_installer(build: &mut Build) -> Result<()> {
    build.add_action(
        "installer:template:win",
        SyncSubmodule {
            path: "qt/installer/windows-template",
            offline_build: false,
        },
    )?;
    build.add_action(
        "installer:dist",
        BuildInstaller {
            version: anki_version(),
        },
    )?;
    Ok(())
}
