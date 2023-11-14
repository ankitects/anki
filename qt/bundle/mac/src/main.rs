// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(unix)]

//! Munge the output of PyOxidizer into a macOS app bundle, and combine it
//! with our other runtime dependencies.

mod codesign;
mod dmg;
mod notarize;

use std::env;
use std::fs;
use std::os::unix::prelude::PermissionsExt;
use std::process::Command;

use anyhow::bail;
use anyhow::Result;
use apple_bundles::MacOsApplicationBundleBuilder;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Parser;
use clap::Subcommand;
use clap::ValueEnum;
use codesign::codesign_app;
use codesign::codesign_python_libs;
use dmg::make_dmgs;
use dmg::BuildDmgsArgs;
use notarize::notarize_app;
use plist::Value;
use simple_file_manifest::FileEntry;
use walkdir::WalkDir;

#[derive(Clone, ValueEnum)]
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

    fn input_folder(&self) -> Utf8PathBuf {
        Utf8Path::new("out/bundle").join(self.folder_name())
    }

    fn output_folder(&self) -> Utf8PathBuf {
        Utf8Path::new("out/bundle/app")
            .join(self.folder_name())
            .join("Anki.app")
    }

    fn macos_min(&self) -> &str {
        match self {
            DistKind::Standard => {
                if env::var("MAC_X86").is_ok() {
                    "11"
                } else {
                    "12"
                }
            }
            DistKind::Alternate => "10.13.4",
        }
    }

    fn qt_repo(&self) -> &Utf8Path {
        Utf8Path::new(match self {
            DistKind::Standard => {
                if cfg!(target_arch = "aarch64") && env::var("MAC_X86").is_err() {
                    "out/extracted/mac_arm_qt6"
                } else {
                    "out/extracted/mac_amd_qt6"
                }
            }
            DistKind::Alternate => "out/extracted/mac_amd_qt5",
        })
    }
}

#[derive(Parser)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    BuildApp {
        version: String,
        kind: DistKind,
        stamp: Utf8PathBuf,
    },
    BuildDmgs(BuildDmgsArgs),
}

fn main() -> Result<()> {
    match Cli::parse().command {
        Commands::BuildApp {
            version,
            kind,
            stamp,
        } => {
            let plist = get_plist(&version);
            make_app(kind, plist, &stamp)
        }
        Commands::BuildDmgs(args) => make_dmgs(args),
    }
}

fn make_app(kind: DistKind, mut plist: plist::Dictionary, stamp: &Utf8Path) -> Result<()> {
    let input_folder = kind.input_folder();
    let output_folder = kind.output_folder();
    let output_variant = output_folder.parent().unwrap();
    if output_variant.exists() {
        fs::remove_dir_all(output_variant)?;
    }
    fs::create_dir_all(&output_folder)?;

    let mut builder = MacOsApplicationBundleBuilder::new("Anki")?;
    plist.insert(
        "LSMinimumSystemVersion".into(),
        Value::from(kind.macos_min()),
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
        } else if path_str.contains("aqt/data")
            || path_str.contains("certifi")
            || path_str.contains("google/protobuf")
        {
            builder.add_file_resources(relative_path.strip_prefix("lib").unwrap(), entry)?;
        } else {
            if path_str.contains("__pycache__") {
                continue;
            }
            builder.add_file_macos(relative_path, entry)?;
        }
    }

    builder.files().materialize_files(&output_folder)?;
    fix_rpath(output_folder.join("Contents/MacOS/anki"))?;
    codesign_python_libs(&output_folder)?;
    copy_in_audio(&output_folder)?;
    copy_in_qt(&output_folder, kind)?;
    codesign_app(&output_folder)?;
    fixup_perms(&output_folder)?;
    notarize_app(&output_folder)?;
    fs::write(stamp, b"")?;

    Ok(())
}

/// The bundle builder writes some files without world read/execute perms,
/// which prevents them from being opened by a non-admin user.
fn fixup_perms(dir: &Utf8Path) -> Result<()> {
    let status = Command::new("find")
        .arg(dir)
        .args(["-not", "-perm", "-a=r", "-exec", "chmod", "a+r", "{}", ";"])
        .status()?;
    if !status.success() {
        bail!("error setting perms");
    }
    fs::set_permissions(
        dir.join("Contents/MacOS/anki"),
        PermissionsExt::from_mode(0o755),
    )?;
    Ok(())
}

/// Copy everything at the provided path into the Contents/ folder of our app.
fn extend_app_contents(source: &Utf8Path, target_dir: &Utf8Path) -> Result<()> {
    let status = Command::new("rsync")
        .arg("-a")
        .arg(format!("{}/", source.as_str()))
        .arg(target_dir)
        .status()?;
    if !status.success() {
        bail!("error syncing {source:?}");
    }
    Ok(())
}

fn copy_in_audio(bundle_dir: &Utf8Path) -> Result<()> {
    println!("Copying in audio...");

    let src_folder = Utf8Path::new(
        if cfg!(target_arch = "aarch64") && env::var("MAC_X86").is_err() {
            "out/extracted/mac_arm_audio"
        } else {
            "out/extracted/mac_amd_audio"
        },
    );
    extend_app_contents(src_folder, &bundle_dir.join("Contents/Resources"))
}

fn copy_in_qt(bundle_dir: &Utf8Path, kind: DistKind) -> Result<()> {
    println!("Copying in Qt...");
    extend_app_contents(kind.qt_repo(), &bundle_dir.join("Contents"))
}

fn fix_rpath(exe_path: Utf8PathBuf) -> Result<()> {
    let status = Command::new("install_name_tool")
        .arg("-add_rpath")
        .arg("@executable_path/../Frameworks")
        .arg(exe_path.as_str())
        .status()?;
    assert!(status.success());
    Ok(())
}

fn get_plist(anki_version: &str) -> plist::Dictionary {
    let reader = std::io::Cursor::new(include_bytes!("Info.plist"));
    let mut plist = Value::from_reader(reader)
        .unwrap()
        .into_dictionary()
        .unwrap();
    plist.insert(
        "CFBundleShortVersionString".into(),
        Value::from(anki_version),
    );
    plist
}
