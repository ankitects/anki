// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ninja_gen::action::BuildAction;
use ninja_gen::archives::download_and_extract;
use ninja_gen::archives::empty_manifest;
use ninja_gen::archives::with_exe;
use ninja_gen::archives::OnlineArchive;
use ninja_gen::cargo::CargoBuild;
use ninja_gen::cargo::RustOutput;
use ninja_gen::git::SyncSubmodule;
use ninja_gen::glob;
use ninja_gen::input::BuildInput;
use ninja_gen::inputs;
use ninja_gen::python::PythonEnvironment;
use ninja_gen::Build;
use ninja_gen::Result;
use ninja_gen::Utf8Path;

use crate::anki_version;
use crate::platform::overriden_python_target_platform;
use crate::platform::overriden_rust_target_triple;

#[derive(Debug, PartialEq, Eq)]
enum DistKind {
    Standard,
    Alternate,
}

impl DistKind {
    fn folder_name(&self) -> &'static str {
        match self {
            DistKind::Standard => "std",
            DistKind::Alternate => "alt",
        }
    }

    fn name(&self) -> &'static str {
        match self {
            DistKind::Standard => "standard",
            DistKind::Alternate => "alternate",
        }
    }
}

pub fn build_bundle(build: &mut Build) -> Result<()> {
    // install into venv
    setup_primary_venv(build)?;
    install_anki_wheels(build)?;

    // bundle venv into output binary + extra_files
    build_pyoxidizer(build)?;
    build_artifacts(build)?;
    build_binary(build)?;

    // package up outputs with Qt/other deps
    download_dist_folder_deps(build)?;
    build_dist_folder(build, DistKind::Standard)?;

    // repeat for Qt5
    if !targetting_macos_arm() {
        if !cfg!(target_os = "macos") {
            setup_qt5_venv(build)?;
        }
        build_dist_folder(build, DistKind::Alternate)?;
    }

    build_packages(build)?;

    Ok(())
}

fn targetting_macos_arm() -> bool {
    cfg!(all(target_os = "macos", target_arch = "aarch64"))
        && overriden_python_target_platform().is_none()
}

const WIN_AUDIO: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/audio-win-amd64.tar.gz",
    sha256: "0815a601baba05e03bc36b568cdc2332b1cf4aa17125fc33c69de125f8dd687f",
};

const MAC_ARM_AUDIO: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-05-26/audio-mac-arm64.tar.gz",
    sha256: "f6c4af9be59ae1c82a16f5c6307f13cbf31b49ad7b69ce1cb6e0e7b403cfdb8f",
};

const MAC_AMD_AUDIO: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-05-26/audio-mac-amd64.tar.gz",
    sha256: "ecbb3c878805cdd58b1a0b8e3fd8c753b8ce3ad36c8b5904a79111f9db29ff42",
};

const MAC_ARM_QT6: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-09-21/pyqt6.3-mac-arm64.tar.gz",
    sha256: "5c30f6952b498bb0df31ca23bd3b35e09ea732df528f70df454580b495ecbdfd",
};

const MAC_AMD_QT6: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-09-21/pyqt6.3-mac-amd64.tar.gz",
    sha256: "252922cfc2c5848d50ef90a903eed43545ef66b189db791bbe621704ef58bcf1",
};

const MAC_AMD_QT5: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/pyqt5.14-mac-amd64.tar.gz",
    sha256: "474951bed79ddb9570ee4c5a6079041772551ea77e77171d9e33d6f5e7877ec1",
};

const LINUX_QT_PLUGINS: OnlineArchive = OnlineArchive {
    url: "https://github.com/ankitects/anki-bundle-extras/releases/download/anki-2022-02-09/qt-plugins-linux-amd64.tar.gz",
    sha256: "cbfb41fb750ae19b381f8137bd307e1167fdc68420052977f6e1887537a131b0",
};

fn download_dist_folder_deps(build: &mut Build) -> Result<()> {
    let mut bundle_deps = vec![":wheels"];
    if cfg!(windows) {
        download_and_extract(build, "win_amd64_audio", WIN_AUDIO, empty_manifest())?;
        bundle_deps.push(":extract:win_amd64_audio");
    } else if cfg!(target_os = "macos") {
        if targetting_macos_arm() {
            download_and_extract(build, "mac_arm_audio", MAC_ARM_AUDIO, empty_manifest())?;
            download_and_extract(build, "mac_arm_qt6", MAC_ARM_QT6, empty_manifest())?;
            bundle_deps.extend([":extract:mac_arm_audio", ":extract:mac_arm_qt6"]);
        } else {
            download_and_extract(build, "mac_amd_audio", MAC_AMD_AUDIO, empty_manifest())?;
            download_and_extract(build, "mac_amd_qt6", MAC_AMD_QT6, empty_manifest())?;
            download_and_extract(build, "mac_amd_qt5", MAC_AMD_QT5, empty_manifest())?;
            bundle_deps.extend([
                ":extract:mac_amd_audio",
                ":extract:mac_amd_qt6",
                ":extract:mac_amd_qt5",
            ]);
        }
    } else {
        download_and_extract(
            build,
            "linux_qt_plugins",
            LINUX_QT_PLUGINS,
            empty_manifest(),
        )?;
        bundle_deps.extend([":extract:linux_qt_plugins"]);
    }
    build.add_inputs_to_group(
        "bundle:deps",
        inputs![bundle_deps
            .iter()
            .map(ToString::to_string)
            .collect::<Vec<_>>()],
    );
    Ok(())
}

struct Venv {
    label: &'static str,
    path_without_builddir: &'static str,
}

impl Venv {
    fn label_as_target(&self, suffix: &str) -> String {
        format!(":{}{suffix}", self.label)
    }
}

const PRIMARY_VENV: Venv = Venv {
    label: "bundle:pyenv",
    path_without_builddir: "bundle/pyenv",
};

/// Only used for copying Qt libs on Windows/Linux.
const QT5_VENV: Venv = Venv {
    label: "bundle:pyenv-qt5",
    path_without_builddir: "bundle/pyenv-qt5",
};

fn setup_primary_venv(build: &mut Build) -> Result<()> {
    let mut qt6_reqs = inputs![
        "python/requirements.bundle.txt",
        if cfg!(target_os = "macos") {
            "python/requirements.qt6_3.txt"
        } else {
            "python/requirements.qt6_4.txt"
        }
    ];
    if cfg!(windows) {
        qt6_reqs = inputs![qt6_reqs, "python/requirements.win.txt"];
    }
    build.add(
        PRIMARY_VENV.label,
        PythonEnvironment {
            folder: PRIMARY_VENV.path_without_builddir,
            base_requirements_txt: "python/requirements.base.txt".into(),
            requirements_txt: qt6_reqs,
            extra_binary_exports: &[],
        },
    )?;
    Ok(())
}

fn setup_qt5_venv(build: &mut Build) -> Result<()> {
    let qt5_reqs = inputs![
        "python/requirements.base.txt",
        if cfg!(target_os = "macos") {
            "python/requirements.qt5_14.txt"
        } else {
            "python/requirements.qt5_15.txt"
        }
    ];
    build.add(
        QT5_VENV.label,
        PythonEnvironment {
            folder: QT5_VENV.path_without_builddir,
            base_requirements_txt: "python/requirements.base.txt".into(),
            requirements_txt: qt5_reqs,
            extra_binary_exports: &[],
        },
    )
}

struct InstallAnkiWheels {
    venv: Venv,
}

impl BuildAction for InstallAnkiWheels {
    fn command(&self) -> &str {
        "$pip install --force-reinstall --no-deps $in"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        build.add_inputs("pip", inputs![self.venv.label_as_target(":pip")]);
        build.add_inputs("in", inputs![":wheels"]);
        build.add_output_stamp("bundle/wheels.stamp");
    }
}

fn install_anki_wheels(build: &mut Build) -> Result<()> {
    build.add(
        "bundle:add_wheels:qt6",
        InstallAnkiWheels { venv: PRIMARY_VENV },
    )?;
    Ok(())
}

fn build_pyoxidizer(build: &mut Build) -> Result<()> {
    build.add(
        "bundle:pyoxidizer:repo",
        SyncSubmodule {
            path: "qt/bundle/PyOxidizer",
        },
    )?;
    build.add(
        "bundle:pyoxidizer:bin",
        CargoBuild {
            inputs: inputs![":bundle:pyoxidizer:repo", glob!["qt/bundle/PyOxidizer/**"]],
            // can't use ::Binary() here, as we're in a separate workspace
            outputs: &[RustOutput::Data(
                "bin",
                &with_exe("bundle/rust/release/pyoxidizer"),
            )],
            target: None,
            extra_args: &format!(
                "--manifest-path={} --target-dir={} -p pyoxidizer",
                "qt/bundle/PyOxidizer/Cargo.toml", "$builddir/bundle/rust"
            ),
            release_override: Some(true),
        },
    )?;
    Ok(())
}

struct BuildArtifacts {}

impl BuildAction for BuildArtifacts {
    fn command(&self) -> &str {
        "$runner build-artifacts $bundle_root $pyoxidizer_bin"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        build.add_inputs("pyoxidizer_bin", inputs![":bundle:pyoxidizer:bin"]);
        build.add_inputs("", inputs![PRIMARY_VENV.label_as_target("")]);
        build.add_inputs("", inputs![":bundle:add_wheels:qt6", glob!["qt/bundle/**"]]);
        build.add_variable("bundle_root", "$builddir/bundle");
        build.add_outputs_ext(
            "pyo3_config",
            vec!["bundle/artifacts/pyo3-build-config-file.txt"],
            true,
        );
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}

fn build_artifacts(build: &mut Build) -> Result<()> {
    build.add("bundle:artifacts", BuildArtifacts {})
}

struct BuildBundle {}

impl BuildAction for BuildBundle {
    fn command(&self) -> &str {
        "$runner build-bundle-binary"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        build.add_inputs("", inputs![":bundle:artifacts", glob!["qt/bundle/**"]]);
        build.add_outputs(
            "",
            vec![RustOutput::Binary("anki").path(
                Utf8Path::new("$builddir/bundle/rust"),
                overriden_rust_target_triple(),
                true,
            )],
        );
    }
}

fn build_binary(build: &mut Build) -> Result<()> {
    build.add("bundle:binary", BuildBundle {})
}

struct BuildDistFolder {
    kind: DistKind,
    deps: BuildInput,
}

impl BuildAction for BuildDistFolder {
    fn command(&self) -> &str {
        "$runner build-dist-folder $kind $out_folder "
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        build.add_inputs("", &self.deps);
        build.add_variable("kind", self.kind.name());
        let folder = match self.kind {
            DistKind::Standard => "bundle/std",
            DistKind::Alternate => "bundle/alt",
        };
        build.add_outputs("out_folder", vec![folder]);
        build.add_outputs("stamp", vec![format!("{folder}.stamp")]);
    }

    fn check_output_timestamps(&self) -> bool {
        true
    }
}

fn build_dist_folder(build: &mut Build, kind: DistKind) -> Result<()> {
    let mut deps = inputs![":bundle:deps", ":bundle:binary", glob!["qt/bundle/**"]];
    if kind == DistKind::Alternate && !cfg!(target_os = "macos") {
        deps = inputs![deps, QT5_VENV.label_as_target("")];
    }
    let group = match kind {
        DistKind::Standard => "bundle:folder:std",
        DistKind::Alternate => "bundle:folder:alt",
    };
    build.add(group, BuildDistFolder { kind, deps })
}

fn build_packages(build: &mut Build) -> Result<()> {
    if cfg!(windows) {
        build_windows_installers(build)
    } else if cfg!(target_os = "macos") {
        build_mac_app(build, DistKind::Standard)?;
        if !targetting_macos_arm() {
            build_mac_app(build, DistKind::Alternate)?;
        }
        build_dmgs(build)
    } else {
        build_tarball(build, DistKind::Standard)?;
        build_tarball(build, DistKind::Alternate)
    }
}

struct BuildTarball {
    kind: DistKind,
}

impl BuildAction for BuildTarball {
    fn command(&self) -> &str {
        "chmod -R a+r $folder && tar -I '$zstd' --transform $transform -cf $tarball -C $folder ."
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        let input_folder_name = self.kind.folder_name();
        let input_folder_target = format!(":bundle:folder:{input_folder_name}");
        let input_folder_path = format!("$builddir/bundle/{input_folder_name}");

        let version = anki_version();
        let qt = match self.kind {
            DistKind::Standard => "qt6",
            DistKind::Alternate => "qt5",
        };
        let output_folder_base = format!("anki-{version}-linux-{qt}");
        let output_tarball = format!("bundle/package/{output_folder_base}.tar.zst");

        build.add_inputs("", inputs![input_folder_target]);
        build.add_variable("zstd", "zstd -c --long -T0 -18");
        build.add_variable("transform", format!("s%^.%{output_folder_base}%S"));
        build.add_variable("folder", input_folder_path);
        build.add_outputs("tarball", vec![output_tarball]);
    }
}

fn build_tarball(build: &mut Build, kind: DistKind) -> Result<()> {
    let name = kind.folder_name();
    build.add(format!("bundle:package:{name}"), BuildTarball { kind })
}

struct BuildWindowsInstallers {}

impl BuildAction for BuildWindowsInstallers {
    fn command(&self) -> &str {
        "cargo run -p makeinstall --target-dir=out/rust -- $version $src_root $bundle_root $out"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        let version = anki_version();
        let outputs = ["qt6", "qt5"].iter().map(|qt| {
            let output_base = format!("anki-{version}-windows-{qt}");
            format!("bundle/package/{output_base}.exe")
        });

        build.add_inputs("", inputs![":bundle:folder:std", ":bundle:folder:alt"]);
        build.add_variable("version", version);
        build.add_variable("bundle_root", "$builddir/bundle");
        build.add_outputs("out", outputs);
    }
}

fn build_windows_installers(build: &mut Build) -> Result<()> {
    build.add("bundle:package", BuildWindowsInstallers {})
}

struct BuildMacApp {
    kind: DistKind,
}

impl BuildAction for BuildMacApp {
    fn command(&self) -> &str {
        "cargo run -p makeapp --target-dir=out/rust -- build-app $version $kind $stamp"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        let folder_name = self.kind.folder_name();
        build.add_inputs("", inputs![format!(":bundle:folder:{folder_name}")]);
        build.add_variable("version", anki_version());
        build.add_variable("kind", self.kind.name());
        build.add_outputs("stamp", vec![format!("bundle/app/{folder_name}.stamp")]);
    }
}

fn build_mac_app(build: &mut Build, kind: DistKind) -> Result<()> {
    build.add(format!("bundle:app:{}", kind.name()), BuildMacApp { kind })
}

struct BuildDmgs {}

impl BuildAction for BuildDmgs {
    fn command(&self) -> &str {
        "cargo run -p makeapp --target-dir=out/rust -- build-dmgs $dmgs"
    }

    fn files(&mut self, build: &mut impl ninja_gen::build::FilesHandle) {
        let version = anki_version();
        let platform = if targetting_macos_arm() {
            "apple"
        } else {
            "intel"
        };
        let qt = if targetting_macos_arm() {
            &["qt6"][..]
        } else {
            &["qt6", "qt5"]
        };
        let dmgs = qt
            .iter()
            .map(|qt| format!("bundle/dmg/anki-{version}-mac-{platform}-{qt}.dmg"));

        build.add_inputs("", inputs![":bundle:app"]);
        build.add_outputs("dmgs", dmgs);
    }
}

fn build_dmgs(build: &mut Build) -> Result<()> {
    build.add("bundle:dmg", BuildDmgs {})
}
