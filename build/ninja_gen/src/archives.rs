// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;

use camino::Utf8Path;
use camino::Utf8PathBuf;

use crate::action::BuildAction;
use crate::cargo::CargoBuild;
use crate::cargo::RustOutput;
use crate::glob;
use crate::input::BuildInput;
use crate::inputs;
use crate::Build;
use crate::Result;

#[derive(Clone, Copy, Debug)]
pub struct OnlineArchive {
    pub url: &'static str,
    pub sha256: &'static str,
}

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Platform {
    LinuxX64,
    LinuxArm,
    MacX64,
    MacArm,
    WindowsX64,
}

impl Platform {
    pub fn current() -> Self {
        if cfg!(windows) {
            Self::WindowsX64
        } else {
            let os = std::env::consts::OS;
            let arch = std::env::consts::ARCH;
            match (os, arch) {
                ("linux", "x86_64") => Self::LinuxX64,
                ("linux", "aarch64") => Self::LinuxArm,
                ("macos", "x86_64") => Self::MacX64,
                ("macos", "aarch64") => Self::MacArm,
                _ => panic!("unsupported os/arch {os} {arch} - PR welcome!"),
            }
        }
    }

    pub fn tls_feature() -> &'static str {
        match Self::current() {
            // On Linux, wheels are not allowed to link to OpenSSL, and linking setup
            // caused pain for AnkiDroid in the past. On other platforms, we stick to
            // native libraries, for smaller binaries.
            Platform::LinuxX64 | Platform::LinuxArm => "rustls",
            _ => "native-tls",
        }
    }

    pub fn as_rust_triple(&self) -> &'static str {
        match self {
            Platform::MacX64 => "x86_64-apple-darwin",
            _ => unimplemented!(),
        }
    }
}

/// Append .exe to path if on Windows.
pub fn with_exe(path: &str) -> Cow<str> {
    if cfg!(windows) {
        format!("{path}.exe").into()
    } else {
        path.into()
    }
}

struct DownloadArchive {
    pub archive: OnlineArchive,
}

impl BuildAction for DownloadArchive {
    fn command(&self) -> &str {
        "$archives_bin download $url $checksum $out"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        let (_, filename) = self.archive.url.rsplit_once('/').unwrap();
        let output_path = Utf8Path::new("download").join(filename);

        build.add_inputs("archives_bin", inputs![":build:archives"]);
        build.add_variable("url", self.archive.url);
        build.add_variable("checksum", self.archive.sha256);
        build.add_outputs("out", &[output_path.into_string()])
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        build_archive_tool(build)
    }
}

struct ExtractArchive<'a, I> {
    pub archive_path: BuildInput,
    /// The folder that the archive should be extracted into, relative to
    /// $builddir/extracted. If the archive contains a single top-level
    /// folder, its contents will be extracted into the provided folder, so
    /// that output like tool-1.2/ can be extracted into tool/.
    pub extraction_folder_name: &'a str,
    /// Files contained inside the archive, relative to the archive root, and
    /// excluding the top-level folder if it is the sole top-level entry.
    /// Any files you wish to use as part of subsequent rules
    /// must be declared here.
    pub file_manifest: HashMap<&'static str, I>,
}

impl<I> ExtractArchive<'_, I> {
    fn extraction_folder(&self) -> Utf8PathBuf {
        Utf8Path::new("$builddir")
            .join("extracted")
            .join(self.extraction_folder_name)
    }
}

impl<I> BuildAction for ExtractArchive<'_, I>
where
    I: IntoIterator,
    I::Item: AsRef<str>,
{
    fn command(&self) -> &str {
        "$archive_tool extract $in $extraction_folder"
    }

    fn files(&mut self, build: &mut impl crate::build::FilesHandle) {
        build.add_inputs("archive_tool", inputs![":build:archives"]);
        build.add_inputs("in", inputs![self.archive_path.clone()]);

        let folder = self.extraction_folder();
        build.add_variable("extraction_folder", folder.to_string());
        for (subgroup, files) in self.file_manifest.drain() {
            build.add_outputs_ext(
                subgroup,
                files
                    .into_iter()
                    .map(|f| folder.join(f.as_ref()).to_string()),
                !subgroup.is_empty(),
            );
        }
        build.add_output_stamp(folder.with_extension("marker"));
    }

    fn on_first_instance(&self, build: &mut Build) -> Result<()> {
        build_archive_tool(build)
    }

    fn name(&self) -> &'static str {
        "extract"
    }
}

fn build_archive_tool(build: &mut Build) -> Result<()> {
    build.once_only("build_archive_tool", |build| {
        let features = Platform::tls_feature();
        build.add(
            "build:archives",
            CargoBuild {
                inputs: inputs![glob!("build/archives/**/*")],
                outputs: &[RustOutput::Binary("archives")],
                target: None,
                extra_args: &format!("-p archives --features {features}"),
                release_override: Some(false),
            },
        )?;
        Ok(())
    })
}

/// See [DownloadArchive] and [ExtractArchive].
pub fn download_and_extract<I>(
    build: &mut Build,
    group_name: &str,
    archive: OnlineArchive,
    file_manifest: HashMap<&'static str, I>,
) -> Result<()>
where
    I: IntoIterator,
    I::Item: AsRef<str>,
{
    let download_group = format!("download:{group_name}");
    build.add(&download_group, DownloadArchive { archive })?;

    let extract_group = format!("extract:{group_name}");
    build.add(
        extract_group,
        ExtractArchive {
            archive_path: inputs![format!(":{download_group}")],
            extraction_folder_name: group_name,
            file_manifest,
        },
    )?;
    Ok(())
}

pub fn empty_manifest() -> HashMap<&'static str, &'static [&'static str]> {
    Default::default()
}
