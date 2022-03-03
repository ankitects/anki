// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fs,
    io::prelude::*,
    path::{Component, Path, PathBuf, Prefix},
    process::Command,
};

use anyhow::{bail, Context, Result};
use slog::*;
use tugger_windows_codesign::{CodeSigningCertificate, SigntoolSign, SystemStore, TimestampServer};
use walkdir::WalkDir;

fn main() -> anyhow::Result<()> {
    let plain = slog_term::PlainSyncDecorator::new(std::io::stdout());
    let logger = Logger::root(slog_term::FullFormat::new(plain).build().fuse(), o!());

    let args: Vec<_> = std::env::args().collect();
    let build_folder = PathBuf::from(args.get(1).context("build folder")?);
    let bazel_external = PathBuf::from(args.get(2).context("bazel external")?);
    // bundle/build.py folder
    let build_py_folder = PathBuf::from(args.get(3).context("build_py_folder")?);
    let version = args.get(4).context("version")?;

    let std_folder = build_folder.join("std");
    let alt_folder = build_folder.join("alt");
    let folders = &[&std_folder, &alt_folder];

    for folder in folders {
        fs::copy(
            build_py_folder.join("win").join("anki-console.bat"),
            folder.join("anki-console.bat"),
        )
        .context("anki-console")?;
    }

    println!("--- Copy in audio");
    copy_in_audio(&std_folder, &bazel_external)?;
    copy_in_audio(&alt_folder, &bazel_external)?;

    println!("--- Build uninstaller");
    build_installer(&std_folder, &build_folder, version, true).context("uninstaller")?;

    // sign the anki.exe and uninstaller.exe in std, then copy into alt
    println!("--- Sign binaries");
    codesign(
        &logger,
        &[
            &std_folder.join("anki.exe"),
            &std_folder.join("uninstall.exe"),
        ],
    )?;
    for fname in &["anki.exe", "uninstall.exe"] {
        fs::copy(std_folder.join(fname), alt_folder.join(fname))
            .with_context(|| format!("copy {fname}"))?;
    }

    println!("--- Build manifest");
    for folder in folders {
        build_manifest(folder).context("manifest")?;
    }

    let mut installer_paths = vec![];
    for (folder, variant) in folders.iter().zip(&["qt6", "qt5"]) {
        println!(
            "--- Build installer for {}",
            folder.file_name().unwrap().to_str().unwrap()
        );
        build_installer(folder, &build_folder, version, false)?;
        let installer_filename = format!("anki-{version}-windows-{variant}.exe");
        let installer_path = build_folder
            .join("..")
            .join("dist")
            .join(installer_filename);

        fs::rename(build_folder.join("anki-setup.exe"), &installer_path)
            .context("rename installer")?;
        installer_paths.push(installer_path);
    }

    println!("--- Sign installers");
    codesign(&logger, &installer_paths)?;

    Ok(())
}

fn build_installer(
    variant_folder: &Path,
    build_folder: &Path,
    version: &str,
    uninstaller: bool,
) -> Result<()> {
    let rendered_nsi = include_str!("../anki.template.nsi")
        .replace("@@SRC@@", variant_folder.to_str().unwrap())
        .replace("@@VERSION@@", version);
    let rendered_nsi_path = build_folder.join("anki.nsi");
    fs::write(&rendered_nsi_path, rendered_nsi).context("anki.nsi")?;
    fs::write(
        build_folder.join("fileassoc.nsh"),
        include_str!("../fileassoc.nsh"),
    )?;
    let mut cmd = Command::new("c:/program files (x86)/nsis/makensis.exe");
    cmd.arg("-V3");
    if uninstaller {
        cmd.arg("-DWRITE_UNINSTALLER");
    };
    if option_env!("NO_COMPRESS").is_some() {
        cmd.arg("-DNO_COMPRESS");
    }
    cmd.arg(rendered_nsi_path);
    let status = cmd.status()?;
    if !status.success() {
        bail!("makensis failed");
    }
    Ok(())
}

/// Copy everything at the provided path into the bundle dir.
/// Excludes standard Bazel repo files.
fn extend_app_contents(source: &Path, bundle_dir: &Path) -> Result<()> {
    let status = Command::new("rsync")
        .arg("-a")
        .args(["--exclude", "BUILD.bazel", "--exclude", "WORKSPACE"])
        .arg(format!("{}/", path_for_rsync(source, true)?))
        .arg(format!("{}/", path_for_rsync(bundle_dir, true)?))
        .status()?;
    if !status.success() {
        bail!("error syncing {source:?}");
    }
    Ok(())
}

/// Munge path into a format rsync expects on Windows.
fn path_for_rsync(path: &Path, trailing_slash: bool) -> Result<String> {
    let mut components = path.components();
    let mut drive = None;
    if let Some(Component::Prefix(prefix)) = components.next() {
        if let Prefix::Disk(letter) = prefix.kind() {
            drive = Some(char::from(letter));
        }
    };
    let drive = drive.context("missing drive letter")?;
    let remaining_path: PathBuf = components.collect();
    Ok(format!(
        "/{}{}{}",
        drive,
        remaining_path
            .to_str()
            .context("remaining_path")?
            .replace("\\", "/"),
        if trailing_slash { "/" } else { "" }
    ))
}

fn copy_in_audio(bundle_dir: &Path, bazel_external: &Path) -> Result<()> {
    extend_app_contents(&bazel_external.join("audio_win_amd64"), bundle_dir)
}

fn codesign(logger: &Logger, paths: &[impl AsRef<Path>]) -> Result<()> {
    if option_env!("ANKI_CODESIGN").is_none() {
        return Ok(());
    }
    let cert = CodeSigningCertificate::Sha1Thumbprint(
        SystemStore::My,
        "60abdb9cb52b7dc13550e8838486a00e693770d9".into(),
    );
    let mut sign = SigntoolSign::new(cert);
    sign.file_digest_algorithm("sha256")
        .timestamp_server(TimestampServer::Rfc3161(
            "http://time.certum.pl".into(),
            "sha256".into(),
        ))
        .verbose();
    paths.iter().for_each(|path| {
        sign.sign_file(path);
    });
    sign.run(logger)
}

// FIXME: check uninstall.exe required or not
fn build_manifest(base_path: &Path) -> Result<()> {
    let mut buf = vec![];
    for entry in WalkDir::new(base_path)
        .min_depth(1)
        .sort_by_file_name()
        .into_iter()
    {
        let entry = entry?;
        let path = entry.path();
        let relative_path = path.strip_prefix(base_path)?;
        write!(
            &mut buf,
            "{}\r\n",
            relative_path.to_str().context("relative_path utf8")?
        )?;
    }
    fs::write(base_path.join("anki.install-manifest"), buf)?;
    Ok(())
}

#[cfg(test)]
mod test {
    #[allow(unused_imports)]
    use super::*;

    #[test]
    #[cfg(windows)]
    fn test_path_for_rsync() -> Result<()> {
        assert_eq!(
            path_for_rsync(Path::new("c:\\foo\\bar"), false)?,
            "/C/foo/bar"
        );
        assert_eq!(
            path_for_rsync(Path::new("c:\\foo\\bar"), true)?,
            "/C/foo/bar/"
        );

        Ok(())
    }
}
