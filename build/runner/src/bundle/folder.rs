// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fs;
use std::process::Command;

use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Args;
use clap::ValueEnum;

use crate::paths::absolute_msys_path;
use crate::paths::unix_path;
use crate::run::run_silent;

#[derive(Clone, Copy, ValueEnum, Debug)]
enum DistKind {
    Standard,
    Alternate,
}

#[derive(Args, Debug)]
pub struct BuildDistFolderArgs {
    kind: DistKind,
    folder_root: Utf8PathBuf,
}

pub fn build_dist_folder(args: BuildDistFolderArgs) {
    let BuildDistFolderArgs { kind, folder_root } = args;
    fs::create_dir_all(&folder_root).unwrap();
    // Start with Qt, as it's the largest, and we use --delete to ensure there are
    // no stale files in lib/. Skipped on macOS as Qt is handled later.
    if !cfg!(target_os = "macos") {
        copy_qt_from_venv(kind, &folder_root);
    }
    clean_top_level_files(&folder_root);
    copy_binary_and_pylibs(&folder_root);
    if cfg!(target_os = "linux") {
        copy_linux_extras(kind, &folder_root);
    } else if cfg!(windows) {
        copy_windows_extras(&folder_root);
    }
    fs::write(folder_root.with_extension("stamp"), b"").unwrap();
}

fn copy_qt_from_venv(kind: DistKind, folder_root: &Utf8Path) {
    let python39 = if cfg!(windows) { "" } else { "python3.9/" };
    let qt_root = match kind {
        DistKind::Standard => {
            folder_root.join(format!("../pyenv/lib/{python39}site-packages/PyQt6"))
        }
        DistKind::Alternate => {
            folder_root.join(format!("../pyenv-qt5/lib/{python39}site-packages/PyQt5"))
        }
    };
    let src_path = absolute_msys_path(&qt_root);
    let lib_path = folder_root.join("lib");
    fs::create_dir_all(&lib_path).unwrap();
    let dst_path = with_slash(absolute_msys_path(&lib_path));
    run_silent(Command::new("rsync").args([
        "-a",
        "--delete",
        "--exclude-from",
        "qt/bundle/qt.exclude",
        &src_path,
        &dst_path,
    ]));
}

fn copy_linux_extras(kind: DistKind, folder_root: &Utf8Path) {
    // add README, installer, etc
    run_silent(Command::new("rsync").args(["-a", "qt/bundle/lin/", &with_slash(folder_root)]));

    // add extra IME plugins from download
    let lib_path = folder_root.join("lib");
    let src_path = folder_root
        .join("../../extracted/linux_qt_plugins")
        .join(match kind {
            DistKind::Standard => "qt6",
            DistKind::Alternate => "qt5",
        });
    let dst_path = lib_path.join(match kind {
        DistKind::Standard => "PyQt6/Qt6/plugins",
        DistKind::Alternate => "PyQt5/Qt5/plugins",
    });
    run_silent(Command::new("rsync").args(["-a", &with_slash(src_path), &with_slash(dst_path)]));
}

fn copy_windows_extras(folder_root: &Utf8Path) {
    run_silent(Command::new("rsync").args([
        "-a",
        "out/extracted/win_amd64_audio/",
        &with_slash(folder_root),
    ]));
}

fn clean_top_level_files(folder_root: &Utf8Path) {
    let mut to_remove = vec![];
    for entry in fs::read_dir(folder_root).unwrap() {
        let entry = entry.unwrap();
        if entry.file_name() == "lib" {
            continue;
        } else {
            to_remove.push(entry.path());
        }
    }
    for path in to_remove {
        if path.is_dir() {
            fs::remove_dir_all(path).unwrap()
        } else {
            fs::remove_file(path).unwrap()
        }
    }
}

fn with_slash<P>(path: P) -> String
where
    P: AsRef<str>,
{
    format!("{}/", path.as_ref())
}

fn copy_binary_and_pylibs(folder_root: &Utf8Path) {
    let binary = folder_root
        .join("../rust")
        .join(env!("TARGET"))
        .join("release")
        .join(if cfg!(windows) { "anki.exe" } else { "anki" });
    let extra_files = folder_root
        .join("../build")
        .join(env!("TARGET"))
        .join("release/resources/extra_files");
    run_silent(Command::new("rsync").args([
        "-a",
        "--exclude",
        "PyQt6",
        // misleading, as it misses the GPL PyQt, and our Rust/JS
        // dependencies
        "--exclude",
        "COPYING.txt",
        &unix_path(&binary),
        &with_slash(unix_path(&extra_files)),
        &with_slash(unix_path(folder_root)),
    ]));
}
