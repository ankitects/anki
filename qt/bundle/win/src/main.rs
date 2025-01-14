// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::io::prelude::*;
use std::path::Path;
use std::process::Command;

use anyhow::bail;
use anyhow::Context;
use anyhow::Result;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Parser;
use tugger_windows_codesign::CodeSigningCertificate;
use tugger_windows_codesign::SigntoolSign;
use tugger_windows_codesign::SystemStore;
use tugger_windows_codesign::TimestampServer;
use walkdir::WalkDir;

#[derive(Parser)]
struct Args {
    version: String,
    bundle_root: Utf8PathBuf,
    qt6_setup_path: Utf8PathBuf,
}

fn main() -> Result<()> {
    let args = Args::parse();

    let src_win_folder = Utf8Path::new("qt/bundle/win");
    let std_dist_folder = args.bundle_root.join("std");
    // folder->installer
    let dists = [(&std_dist_folder, &args.qt6_setup_path)];

    for (folder, _) in dists {
        fs::copy(
            src_win_folder.join("anki-console.bat"),
            folder.join("anki-console.bat"),
        )
        .context("anki-console")?;
    }

    println!("--- Build uninstaller");
    build_installer(
        &args.bundle_root,
        &std_dist_folder,
        &args.qt6_setup_path,
        &args.version,
        true,
    )
    .context("uninstaller")?;

    // sign the anki.exe and uninstaller.exe in std
    println!("--- Sign binaries");
    codesign([
        &std_dist_folder.join("anki.exe"),
        &std_dist_folder.join("uninstall.exe"),
    ])?;

    println!("--- Build manifest");
    for (folder, _) in dists {
        build_manifest(folder).context("manifest")?;
    }

    for (folder, installer) in dists {
        println!("--- Build {}", installer);
        build_installer(&args.bundle_root, folder, installer, &args.version, false)?;
    }

    println!("--- Sign installers");
    codesign(dists.iter().map(|tup| tup.1))?;

    Ok(())
}

fn build_installer(
    bundle_root: &Utf8Path,
    dist_folder: &Utf8Path,
    installer: &Utf8Path,
    version: &str,
    uninstaller: bool,
) -> Result<()> {
    let rendered_nsi = include_str!("../anki.template.nsi")
        .replace("@@SRC@@", dist_folder.as_str())
        .replace("@@INSTALLER@@", installer.as_str())
        .replace("@@VERSION@@", version);
    let rendered_nsi_path = bundle_root.join("anki.nsi");
    fs::write(&rendered_nsi_path, rendered_nsi).context("anki.nsi")?;
    fs::write(
        bundle_root.join("fileassoc.nsh"),
        include_str!("../fileassoc.nsh"),
    )?;
    fs::copy(
        "out/extracted/nsis_plugins/nsProcess.dll",
        bundle_root.join("nsProcess.dll"),
    )?;
    let mut cmd = Command::new("c:/program files (x86)/nsis/makensis.exe");
    cmd.arg("-V3");
    if uninstaller {
        cmd.arg("-DWRITE_UNINSTALLER");
    };
    if option_env!("RELEASE").is_none() {
        cmd.arg("-DNO_COMPRESS");
    }
    cmd.arg(rendered_nsi_path);
    let status = cmd.status()?;
    if !status.success() {
        bail!("makensis failed");
    }
    Ok(())
}

fn codesign(paths: impl IntoIterator<Item = impl AsRef<Path>>) -> Result<()> {
    if option_env!("ANKI_CODESIGN").is_none() {
        return Ok(());
    }
    let cert = CodeSigningCertificate::Sha1Thumbprint(
        SystemStore::My,
        "dccfc6d312fc0432197bb7be951478e5866eebf8".into(),
    );
    let mut sign = SigntoolSign::new(cert);
    sign.file_digest_algorithm("sha256")
        .timestamp_server(TimestampServer::Rfc3161(
            "http://time.certum.pl".into(),
            "sha256".into(),
        ))
        .verbose();
    paths.into_iter().for_each(|path| {
        sign.sign_file(path);
    });
    sign.run()
}

fn build_manifest(base_path: &Utf8Path) -> Result<()> {
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
