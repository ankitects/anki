// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![windows_subsystem = "windows"]

use std::io::stdin;
use std::io::stdout;
use std::io::Write;
use std::process::Command;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

use anki_io::copy_if_newer;
use anki_io::create_dir_all;
use anki_io::modified_time;
use anki_io::read_file;
use anki_io::remove_file;
use anki_io::write_file;
use anki_io::ToUtf8Path;
use anki_process::CommandExt;
use anyhow::Context;
use anyhow::Result;

use crate::platform::ensure_terminal_shown;
use crate::platform::get_exe_and_resources_dirs;
use crate::platform::get_uv_binary_name;
use crate::platform::launch_anki_after_update;
use crate::platform::launch_anki_normally;

mod platform;

// todo: -c appearing as app name now

struct State {
    has_existing_install: bool,
    prerelease_marker: std::path::PathBuf,
    uv_install_root: std::path::PathBuf,
    uv_path: std::path::PathBuf,
    user_pyproject_path: std::path::PathBuf,
    user_python_version_path: std::path::PathBuf,
    dist_pyproject_path: std::path::PathBuf,
    dist_python_version_path: std::path::PathBuf,
    uv_lock_path: std::path::PathBuf,
    sync_complete_marker: std::path::PathBuf,
}

#[derive(Debug, Clone)]
pub enum VersionKind {
    PyOxidizer(String),
    Uv(String),
}

#[derive(Debug, Clone)]
pub enum MainMenuChoice {
    Latest,
    KeepExisting,
    Version(VersionKind),
    ToggleBetas,
    Quit,
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
    let uv_install_root = dirs::data_local_dir()
        .context("Unable to determine data_dir")?
        .join("AnkiProgramFiles");

    let (exe_dir, resources_dir) = get_exe_and_resources_dirs()?;

    let state = State {
        has_existing_install: uv_install_root.join(".sync_complete").exists(),
        prerelease_marker: uv_install_root.join("prerelease"),
        uv_install_root: uv_install_root.clone(),
        uv_path: exe_dir.join(get_uv_binary_name()),
        user_pyproject_path: uv_install_root.join("pyproject.toml"),
        user_python_version_path: uv_install_root.join(".python-version"),
        dist_pyproject_path: resources_dir.join("pyproject.toml"),
        dist_python_version_path: resources_dir.join(".python-version"),
        uv_lock_path: uv_install_root.join("uv.lock"),
        sync_complete_marker: uv_install_root.join(".sync_complete"),
    };

    // Create install directory and copy project files in
    create_dir_all(&state.uv_install_root)?;
    let had_user_pyproj = state.user_pyproject_path.exists();
    if !had_user_pyproj {
        // during initial launcher testing, enable betas by default
        write_file(&state.prerelease_marker, "")?;
    }

    copy_if_newer(&state.dist_pyproject_path, &state.user_pyproject_path)?;
    copy_if_newer(
        &state.dist_python_version_path,
        &state.user_python_version_path,
    )?;

    let pyproject_has_changed = !state.sync_complete_marker.exists() || {
        let pyproject_toml_time = modified_time(&state.user_pyproject_path)?;
        let sync_complete_time = modified_time(&state.sync_complete_marker)?;
        Ok::<bool, anyhow::Error>(pyproject_toml_time > sync_complete_time)
    }
    .unwrap_or(true);

    if !pyproject_has_changed {
        // If venv is already up to date, launch Anki normally
        let args: Vec<String> = std::env::args().skip(1).collect();
        let cmd = build_python_command(&state.uv_install_root, &args)?;
        launch_anki_normally(cmd)?;
        return Ok(());
    }

    // If we weren't in a terminal, respawn ourselves in one
    ensure_terminal_shown()?;

    print!("\x1B[2J\x1B[H"); // Clear screen and move cursor to top
    println!("\x1B[1mAnki Launcher\x1B[0m\n");

    main_menu_loop(&state)?;

    // Write marker file to indicate we've completed the sync process
    write_sync_marker(&state.sync_complete_marker)?;

    #[cfg(target_os = "macos")]
    {
        let cmd = build_python_command(&state.uv_install_root, &[])?;
        platform::mac::prepare_for_launch_after_update(cmd)?;
    }

    if cfg!(unix) && !cfg!(target_os = "macos") {
        println!("\nPress enter to start Anki.");
        let mut input = String::new();
        let _ = stdin().read_line(&mut input);
    } else {
        // on Windows/macOS, the user needs to close the terminal/console
        // currently, but ideas on how we can avoid this would be good!
        println!("Anki will start shortly.");
        println!("\x1B[1mYou can close this window.\x1B[0m\n");
    }

    let cmd = build_python_command(&state.uv_install_root, &[])?;
    launch_anki_after_update(cmd)?;

    Ok(())
}

fn main_menu_loop(state: &State) -> Result<()> {
    loop {
        let menu_choice =
            get_main_menu_choice(state.has_existing_install, &state.prerelease_marker);

        match menu_choice {
            MainMenuChoice::Quit => std::process::exit(0),
            MainMenuChoice::KeepExisting => {
                // Skip sync, just launch existing installation
                break;
            }
            MainMenuChoice::ToggleBetas => {
                // Toggle beta prerelease file
                if state.prerelease_marker.exists() {
                    let _ = remove_file(&state.prerelease_marker);
                    println!("Beta releases disabled.");
                } else {
                    write_file(&state.prerelease_marker, "")?;
                    println!("Beta releases enabled.");
                }
                println!();
                continue;
            }
            choice @ (MainMenuChoice::Latest | MainMenuChoice::Version(_)) => {
                // For other choices, update project files and sync
                update_pyproject_for_version(
                    choice,
                    state.dist_pyproject_path.clone(),
                    state.user_pyproject_path.clone(),
                    state.dist_python_version_path.clone(),
                    state.user_python_version_path.clone(),
                )?;

                // Remove sync marker before attempting sync
                let _ = remove_file(&state.sync_complete_marker);

                // Sync the venv
                let mut command = Command::new(&state.uv_path);
                command.current_dir(&state.uv_install_root).args([
                    "sync",
                    "--upgrade",
                    "--managed-python",
                ]);

                // Add python version if .python-version file exists
                if state.user_python_version_path.exists() {
                    let python_version = read_file(&state.user_python_version_path)?;
                    let python_version_str = String::from_utf8(python_version)
                        .context("Invalid UTF-8 in .python-version")?;
                    let python_version_trimmed = python_version_str.trim();
                    command.args(["--python", python_version_trimmed]);
                }

                // Set UV_PRERELEASE=allow if beta mode is enabled
                if state.prerelease_marker.exists() {
                    command.env("UV_PRERELEASE", "allow");
                }

                println!("\x1B[1mUpdating Anki...\x1B[0m\n");

                match command.ensure_success() {
                    Ok(_) => {
                        // Sync succeeded, break out of loop
                        break;
                    }
                    Err(e) => {
                        // If sync fails due to things like a missing wheel on pypi,
                        // we need to remove the lockfile or uv will cache the bad result.
                        let _ = remove_file(&state.uv_lock_path);
                        println!("Install failed: {:#}", e);
                        println!();
                        continue;
                    }
                }
            }
        }
    }
    Ok(())
}

fn write_sync_marker(sync_complete_marker: &std::path::Path) -> Result<()> {
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .context("Failed to get system time")?
        .as_secs();
    write_file(sync_complete_marker, timestamp.to_string())?;
    Ok(())
}

fn get_main_menu_choice(
    has_existing_install: bool,
    prerelease_marker: &std::path::Path,
) -> MainMenuChoice {
    loop {
        println!("1) Latest Anki (just press enter)");
        println!("2) Choose a version");
        if has_existing_install {
            println!("3) Keep existing version");
        }
        println!();

        let betas_enabled = prerelease_marker.exists();
        println!(
            "4) Allow betas: {}",
            if betas_enabled { "on" } else { "off" }
        );
        println!("5) Quit");
        print!("> ");
        let _ = stdout().flush();

        let mut input = String::new();
        let _ = stdin().read_line(&mut input);
        let input = input.trim();

        println!();

        return match input {
            "" | "1" => MainMenuChoice::Latest,
            "2" => MainMenuChoice::Version(get_version_kind()),
            "3" => {
                if has_existing_install {
                    MainMenuChoice::KeepExisting
                } else {
                    println!("Invalid input. Please try again.\n");
                    continue;
                }
            }
            "4" => MainMenuChoice::ToggleBetas,
            "5" => MainMenuChoice::Quit,
            _ => {
                println!("Invalid input. Please try again.");
                continue;
            }
        };
    }
}

fn get_version_kind() -> VersionKind {
    loop {
        println!("Enter the version you want to install:");
        print!("> ");
        let _ = stdout().flush();

        let mut input = String::new();
        let _ = stdin().read_line(&mut input);
        let input = input.trim();

        if input.is_empty() {
            println!("Please enter a version.");
            continue;
        }

        match parse_version_kind(input) {
            Some(version_kind) => {
                println!();
                return version_kind;
            }
            None => {
                println!("Invalid version format. Please enter a version like 24.10 or 25.06.1 (minimum 2.1.50)");
                continue;
            }
        }
    }
}

fn update_pyproject_for_version(
    menu_choice: MainMenuChoice,
    dist_pyproject_path: std::path::PathBuf,
    user_pyproject_path: std::path::PathBuf,
    dist_python_version_path: std::path::PathBuf,
    user_python_version_path: std::path::PathBuf,
) -> Result<()> {
    match menu_choice {
        MainMenuChoice::Latest => {
            let content = read_file(&dist_pyproject_path)?;
            write_file(&user_pyproject_path, &content)?;
            let python_version_content = read_file(&dist_python_version_path)?;
            write_file(&user_python_version_path, &python_version_content)?;
        }
        MainMenuChoice::KeepExisting => {
            // Do nothing - keep existing pyproject.toml and .python-version
        }
        MainMenuChoice::ToggleBetas => {
            // This should not be reached as ToggleBetas is handled in the loop
            unreachable!("ToggleBetas should be handled in the main loop");
        }
        MainMenuChoice::Version(version_kind) => {
            let content = read_file(&dist_pyproject_path)?;
            let content_str =
                String::from_utf8(content).context("Invalid UTF-8 in pyproject.toml")?;
            let updated_content = match &version_kind {
                VersionKind::PyOxidizer(version) => {
                    // Replace package name and add PyQt6 dependencies
                    content_str.replace(
                        "anki-release",
                        &format!(
                            concat!(
                                "aqt[qt6]=={}\",\n",
                                "  \"pyqt6==6.6.1\",\n",
                                "  \"pyqt6-qt6==6.6.2\",\n",
                                "  \"pyqt6-webengine==6.6.0\",\n",
                                "  \"pyqt6-webengine-qt6==6.6.2\",\n",
                                "  \"pyqt6_sip==13.6.0"
                            ),
                            version
                        ),
                    )
                }
                VersionKind::Uv(version) => {
                    content_str.replace("anki-release", &format!("anki-release=={}", version))
                }
            };
            write_file(&user_pyproject_path, &updated_content)?;

            // Update .python-version based on version kind
            match &version_kind {
                VersionKind::PyOxidizer(_) => {
                    write_file(&user_python_version_path, "3.9")?;
                }
                VersionKind::Uv(_) => {
                    copy_if_newer(&dist_python_version_path, &user_python_version_path)?;
                }
            }
        }
        MainMenuChoice::Quit => {
            std::process::exit(0);
        }
    }
    Ok(())
}

fn parse_version_kind(version: &str) -> Option<VersionKind> {
    let numeric_chars: String = version
        .chars()
        .filter(|c| c.is_ascii_digit() || *c == '.')
        .collect();

    let parts: Vec<&str> = numeric_chars.split('.').collect();

    if parts.len() < 2 {
        return None;
    }

    let major: u32 = match parts[0].parse() {
        Ok(val) => val,
        Err(_) => return None,
    };

    let minor: u32 = match parts[1].parse() {
        Ok(val) => val,
        Err(_) => return None,
    };

    let patch: u32 = if parts.len() >= 3 {
        match parts[2].parse() {
            Ok(val) => val,
            Err(_) => return None,
        }
    } else {
        0 // Default patch to 0 if not provided
    };

    // Reject versions < 2.1.50
    if major == 2 && (minor != 1 || patch < 50) {
        return None;
    }

    if major < 25 || (major == 25 && minor < 6) {
        Some(VersionKind::PyOxidizer(version.to_string()))
    } else {
        Some(VersionKind::Uv(version.to_string()))
    }
}

fn build_python_command(uv_install_root: &std::path::Path, args: &[String]) -> Result<Command> {
    let python_exe = if cfg!(target_os = "windows") {
        let show_console = std::env::var("ANKI_CONSOLE").is_ok();
        if show_console {
            uv_install_root.join(".venv/Scripts/python.exe")
        } else {
            uv_install_root.join(".venv/Scripts/pythonw.exe")
        }
    } else {
        uv_install_root.join(".venv/bin/python")
    };

    let mut cmd = Command::new(python_exe);
    cmd.args(["-c", "import aqt; aqt.run()"]);
    cmd.args(args);
    // tell the Python code it was invoked by the launcher, and updating is
    // available
    cmd.env("ANKI_LAUNCHER", std::env::current_exe()?.utf8()?.as_str());

    Ok(cmd)
}
