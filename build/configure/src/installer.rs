// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use anyhow::Result;
use ninja_gen::action::BuildAction;
use ninja_gen::archives::Platform;
use ninja_gen::build::FilesHandle;
use ninja_gen::git::SyncSubmodule;
use ninja_gen::glob;
use ninja_gen::input::BuildInput;
use ninja_gen::inputs;
use ninja_gen::Build;

use crate::anki_version;

struct BuildCommand {
    version: String,
    template: BuildInput,
}

impl BuildAction for BuildCommand {
    fn command(&self) -> &str {
        "$pyenv_bin $script --version $version build --aqt_wheel $aqt_wheel --anki_wheel $anki_wheel"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["qt/tools/build_installer.py"]);
        build.add_variable("version", &self.version);
        build.add_inputs("aqt_wheel", inputs![":wheels:aqt"]);
        build.add_inputs("anki_wheel", inputs![":wheels:anki"]);
        build.add_inputs("", inputs![&self.template, glob!["qt/installer/**"]]);
        build.add_output_stamp("installer/briefcase.build.stamp");
    }
}

struct PackageCommand {
    version: String,
}

impl BuildAction for PackageCommand {
    fn command(&self) -> &str {
        "$pyenv_bin $script --version $version package"
    }

    fn files(&mut self, build: &mut impl FilesHandle) {
        build.add_inputs("pyenv_bin", inputs![":pyenv:bin"]);
        build.add_inputs("script", inputs!["qt/tools/build_installer.py"]);
        build.add_variable("version", &self.version);
        build.add_inputs("", inputs![":installer:build",]);
        build.add_output_stamp("installer/briefcase.package.stamp");
    }
}

pub fn setup_installer_templates(build: &mut Build) -> Result<()> {
    let offline_build = env::var("OFFLINE_BUILD").is_ok();
    build.add_action(
        "installer:template:win",
        SyncSubmodule {
            path: "qt/installer/windows-template",
            offline_build,
        },
    )?;
    build.add_action(
        "installer:template:mac",
        SyncSubmodule {
            path: "qt/installer/mac-template",
            offline_build,
        },
    )
}

pub fn installer_template_inputs(platform: Platform) -> BuildInput {
    match platform {
        Platform::WindowsX64 | Platform::WindowsArm => inputs![
            ":installer:template:win",
            glob!["qt/installer/windows-template/**"]
        ],
        Platform::MacX64 | Platform::MacArm => {
            inputs![
                ":installer:template:mac",
                glob!["qt/installer/mac-template/**"]
            ]
        }
        Platform::LinuxX64 | Platform::LinuxArm => {
            inputs![glob!["qt/installer/linux-template/**"]]
        }
    }
}

pub fn build_installer(build: &mut Build) -> Result<()> {
    let version = anki_version();
    build.add_action(
        "installer:build",
        BuildCommand {
            version: version.clone(),
            template: installer_template_inputs(build.host_platform),
        },
    )?;
    build.add_action("installer:package", PackageCommand { version })?;

    Ok(())
}
