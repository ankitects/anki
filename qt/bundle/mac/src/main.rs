// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Munge the output of PyOxidizer into a macOS app bundle, and combine it
//! with our other runtime dependencies.

use std::{
    path::{Path, PathBuf},
    str::FromStr,
};

use anyhow::{bail, Context, Result};
use apple_bundles::MacOsApplicationBundleBuilder;
use plist::Value;
use tugger_file_manifest::FileEntry;
use walkdir::WalkDir;

const CODESIGN_ARGS: &[&str] = &["-vvvv", "-o", "runtime", "-s", "Developer ID Application:"];

#[derive(Clone, Copy, Debug)]
enum Variant {
    StandardX86,
    StandardArm,
    AlternateX86,
}

impl FromStr for Variant {
    type Err = anyhow::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(match s {
            "qt6_arm64" => Variant::StandardArm,
            "qt6_amd64" => Variant::StandardX86,
            "qt5_amd64" => Variant::AlternateX86,
            other => bail!("unexpected variant: {other}"),
        })
    }
}

impl Variant {
    fn output_base(&self) -> &str {
        match self {
            Variant::StandardX86 => "qt6_amd64",
            Variant::StandardArm => "qt6_arm64",
            Variant::AlternateX86 => "qt5_amd64",
        }
    }

    fn macos_min(&self) -> &str {
        match self {
            Variant::StandardX86 => "10.14.4",
            Variant::StandardArm => "11",
            Variant::AlternateX86 => "10.13.4",
        }
    }

    fn qt_repo(&self) -> &str {
        match self {
            Variant::StandardX86 => "pyqt6.2_mac_bundle_amd64",
            Variant::StandardArm => "pyqt6.2_mac_bundle_arm64",
            Variant::AlternateX86 => "pyqt5.14_mac_bundle_amd64",
        }
    }

    fn audio_repo(&self) -> &str {
        match self {
            Variant::StandardX86 | Variant::AlternateX86 => "audio_mac_amd64",
            Variant::StandardArm => "audio_mac_arm64",
        }
    }
}

fn main() -> anyhow::Result<()> {
    let args: Vec<_> = std::env::args().collect();
    let variant: Variant = args.get(1).context("variant")?.parse()?;
    let bundle_folder = PathBuf::from(args.get(2).context("bundle folder")?);
    let anki_version = args.get(3).context("anki version")?;
    let bazel_external = PathBuf::from(args.get(4).context("bazel external folder")?);

    let plist = get_plist(anki_version);
    make_app(variant, &bundle_folder, plist, &bazel_external)
}

fn make_app(
    variant: Variant,
    input_folder: &Path,
    mut plist: plist::Dictionary,
    bazel_external: &Path,
) -> Result<()> {
    let output_folder = input_folder
        .with_file_name("app")
        .join(variant.output_base())
        .join("Anki.app");
    if output_folder.exists() {
        std::fs::remove_dir_all(&output_folder)?;
    }
    std::fs::create_dir_all(&output_folder)?;

    let mut builder = MacOsApplicationBundleBuilder::new("Anki")?;
    plist.insert(
        "LSMinimumSystemVersion".into(),
        Value::from(variant.macos_min()),
    );
    builder.set_info_plist_from_dictionary(plist)?;
    builder.add_file_resources("Assets.car", &include_bytes!("../icon/Assets.car")[..])?;

    for entry in WalkDir::new(&input_folder)
        .into_iter()
        .map(Result::unwrap)
        .filter(|e| !e.file_type().is_dir())
    {
        let path = entry.path();
        let entry = FileEntry::try_from(path)?;
        let relative_path = path.strip_prefix(&input_folder)?;
        let path_str = relative_path.to_str().unwrap();
        if path_str.contains("libankihelper") {
            builder.add_file_macos("libankihelper.dylib", entry)?;
        } else if path_str.contains("aqt/data") {
            builder.add_file_resources(relative_path.strip_prefix("lib").unwrap(), entry)?;
        } else {
            if path_str.contains("__pycache__") {
                continue;
            }
            builder.add_file_macos(relative_path, entry)?;
        }
    }

    let dry_run = false;
    if dry_run {
        for file in builder.files().iter_files() {
            println!("{}", file.path_string());
        }
    } else {
        builder.files().materialize_files(&output_folder)?;
        fix_rpath(output_folder.join("Contents/MacOS/anki"))?;
        codesign_python_libs(&output_folder)?;
        copy_in_audio(&output_folder, variant, bazel_external)?;
        copy_in_qt(&output_folder, variant, bazel_external)?;
        codesign_app(&output_folder)?;
    }

    Ok(())
}

/// Copy everything at the provided path into the Contents/ folder of our app.
/// Excludes standard Bazel repo files.
fn extend_app_contents(source: &Path, bundle_dir: &Path) -> Result<()> {
    let status = std::process::Command::new("rsync")
        .arg("-a")
        .args(["--exclude", "BUILD.bazel", "--exclude", "WORKSPACE"])
        .arg(format!("{}/", source.to_string_lossy()))
        .arg(bundle_dir.join("Contents/"))
        .status()?;
    if !status.success() {
        bail!("error syncing {source:?}");
    }
    Ok(())
}

fn copy_in_audio(bundle_dir: &Path, variant: Variant, bazel_external: &Path) -> Result<()> {
    println!("Copying in audio...");
    extend_app_contents(&bazel_external.join(variant.audio_repo()), bundle_dir)
}

fn copy_in_qt(bundle_dir: &Path, variant: Variant, bazel_external: &Path) -> Result<()> {
    println!("Copying in Qt...");
    extend_app_contents(&bazel_external.join(variant.qt_repo()), bundle_dir)
}

fn codesign_file(path: &Path, extra_args: &[&str]) -> Result<()> {
    if option_env!("ANKI_CODESIGN").is_some() {
        let status = std::process::Command::new("codesign")
            .args(CODESIGN_ARGS)
            .args(extra_args)
            .arg(path.to_str().unwrap())
            .status()?;
        if !status.success() {
            bail!("codesign failed");
        }
    }

    Ok(())
}

fn codesign_python_libs(bundle_dir: &PathBuf) -> Result<()> {
    for entry in glob::glob(
        bundle_dir
            .join("Contents/MacOS/lib/**/*.so")
            .to_str()
            .unwrap(),
    )? {
        let entry = entry?;
        codesign_file(&entry, &[])?;
    }
    codesign_file(&bundle_dir.join("Contents/MacOS/libankihelper.dylib"), &[])
}

fn codesign_app(bundle_dir: &PathBuf) -> Result<()> {
    codesign_file(bundle_dir, &["--entitlements", "entitlements.python.xml"])
}

fn fix_rpath(exe_path: PathBuf) -> Result<()> {
    let status = std::process::Command::new("install_name_tool")
        .arg("-add_rpath")
        .arg("@executable_path/../Frameworks")
        .arg(exe_path.to_str().unwrap())
        .status()?;
    assert!(status.success());
    Ok(())
}

fn get_plist(anki_version: &str) -> plist::Dictionary {
    let reader = std::io::Cursor::new(include_bytes!("Info.plist"));
    let mut plist = plist::Value::from_reader(reader)
        .unwrap()
        .into_dictionary()
        .unwrap();
    plist.insert(
        "CFBundleShortVersionString".into(),
        Value::from(anki_version),
    );
    plist
}
