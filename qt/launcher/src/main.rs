// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![windows_subsystem = "windows"]

use std::io::stdin;
use std::process::Command;

use anki_io::copy_if_newer;
use anki_io::create_dir_all;
use anki_io::modified_time;
use anki_io::remove_file;
use anki_io::write_file;
use anki_process::CommandExt;
use anyhow::Context;
use anyhow::Result;

use crate::platform::exec_anki;
use crate::platform::get_anki_binary_path;
use crate::platform::get_exe_and_resources_dirs;
use crate::platform::get_uv_binary_name;
use crate::platform::handle_first_launch;
use crate::platform::handle_terminal_launch;
use crate::platform::initial_terminal_setup;
use crate::platform::launch_anki_detached;

mod platform;

#[derive(Debug, Clone, Default)]
pub struct Config {
    pub show_console: bool,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("Error: {:#}", e);
        eprintln!("Press enter to close...");
        let mut input = String::new();
        let _ = stdin().read_line(&mut input);

        std::process::exit(1);
    }
}

fn run() -> Result<()> {
    let mut config = Config::default();
    initial_terminal_setup(&mut config);

    let uv_install_root = dirs::data_local_dir()
        .context("Unable to determine data_dir")?
        .join("AnkiProgramFiles");

    let sync_complete_marker = uv_install_root.join(".sync_complete");
    let prerelease_marker = uv_install_root.join("prerelease");
    let (exe_dir, resources_dir) = get_exe_and_resources_dirs()?;
    let dist_pyproject_path = resources_dir.join("pyproject.toml");
    let user_pyproject_path = uv_install_root.join("pyproject.toml");
    let dist_python_version_path = resources_dir.join(".python-version");
    let user_python_version_path = uv_install_root.join(".python-version");
    let uv_lock_path = uv_install_root.join("uv.lock");
    let uv_path: std::path::PathBuf = exe_dir.join(get_uv_binary_name());

    // Create install directory and copy project files in
    create_dir_all(&uv_install_root)?;
    copy_if_newer(&dist_pyproject_path, &user_pyproject_path)?;
    copy_if_newer(&dist_python_version_path, &user_python_version_path)?;

    let pyproject_has_changed =
        !user_pyproject_path.exists() || !sync_complete_marker.exists() || {
            let pyproject_toml_time = modified_time(&user_pyproject_path)?;
            let sync_complete_time = modified_time(&sync_complete_marker)?;
            Ok::<bool, anyhow::Error>(pyproject_toml_time > sync_complete_time)
        }
        .unwrap_or(true);

    if !pyproject_has_changed {
        // If venv is already up to date, exec as normal
        let anki_bin = get_anki_binary_path(&uv_install_root);
        exec_anki(&anki_bin, &config)?;
        return Ok(());
    }

    // we'll need to launch uv; reinvoke ourselves in a terminal so the user can see
    handle_terminal_launch()?;

    // Remove sync marker before attempting sync
    let _ = remove_file(&sync_complete_marker);

    // Sync the venv
    let mut command = Command::new(&uv_path);
    command
        .current_dir(&uv_install_root)
        .args(["sync", "--upgrade", "--managed-python"]);

    // Set UV_PRERELEASE=allow if prerelease file exists
    if prerelease_marker.exists() {
        command.env("UV_PRERELEASE", "allow");
    }

    if let Err(e) = command.ensure_success() {
        // If sync fails due to things like a missing wheel on pypi,
        // we need to remove the lockfile or uv will cache the bad result.
        let _ = remove_file(&uv_lock_path);
        return Err(e.into());
    }

    // Write marker file to indicate successful sync
    write_file(&sync_complete_marker, "")?;

    // First launch
    let anki_bin = get_anki_binary_path(&uv_install_root);
    handle_first_launch(&anki_bin)?;

    // Then launch the binary as detached subprocess so the terminal can close
    launch_anki_detached(&anki_bin, &config)?;

    Ok(())
}
