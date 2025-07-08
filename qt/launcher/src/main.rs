// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![windows_subsystem = "windows"]

use std::io::stdin;
use std::io::stdout;
use std::io::Write;
use std::process::Command;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

use anki_io::copy_file;
use anki_io::copy_if_newer;
use anki_io::create_dir_all;
use anki_io::modified_time;
use anki_io::read_file;
use anki_io::remove_file;
use anki_io::write_file;
use anki_io::ToUtf8Path;
use anki_process::CommandExt as AnkiCommandExt;
use anyhow::Context;
use anyhow::Result;

use crate::platform::ensure_os_supported;
use crate::platform::ensure_terminal_shown;
use crate::platform::get_exe_and_resources_dirs;
use crate::platform::get_uv_binary_name;
use crate::platform::launch_anki_normally;
use crate::platform::respawn_launcher;

mod platform;

struct State {
    current_version: Option<String>,
    prerelease_marker: std::path::PathBuf,
    uv_install_root: std::path::PathBuf,
    uv_cache_dir: std::path::PathBuf,
    no_cache_marker: std::path::PathBuf,
    anki_base_folder: std::path::PathBuf,
    uv_path: std::path::PathBuf,
    uv_python_install_dir: std::path::PathBuf,
    user_pyproject_path: std::path::PathBuf,
    user_python_version_path: std::path::PathBuf,
    dist_pyproject_path: std::path::PathBuf,
    dist_python_version_path: std::path::PathBuf,
    uv_lock_path: std::path::PathBuf,
    sync_complete_marker: std::path::PathBuf,
    previous_version: Option<String>,
    resources_dir: std::path::PathBuf,
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
    ToggleCache,
    Uninstall,
    Quit,
}

fn main() {
    if let Err(e) = run() {
        eprintln!("Error: {e:#}");
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

    let mut state = State {
        current_version: None,
        prerelease_marker: uv_install_root.join("prerelease"),
        uv_install_root: uv_install_root.clone(),
        uv_cache_dir: uv_install_root.join("cache"),
        no_cache_marker: uv_install_root.join("nocache"),
        anki_base_folder: get_anki_base_path()?,
        uv_path: exe_dir.join(get_uv_binary_name()),
        uv_python_install_dir: uv_install_root.join("python"),
        user_pyproject_path: uv_install_root.join("pyproject.toml"),
        user_python_version_path: uv_install_root.join(".python-version"),
        dist_pyproject_path: resources_dir.join("pyproject.toml"),
        dist_python_version_path: resources_dir.join(".python-version"),
        uv_lock_path: uv_install_root.join("uv.lock"),
        sync_complete_marker: uv_install_root.join(".sync_complete"),
        previous_version: None,
        resources_dir,
    };

    // Check for uninstall request from Windows uninstaller
    if std::env::var("ANKI_LAUNCHER_UNINSTALL").is_ok() {
        ensure_terminal_shown()?;
        handle_uninstall(&state)?;
        return Ok(());
    }

    // Create install directory and copy project files in
    create_dir_all(&state.uv_install_root)?;
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
        let cmd = build_python_command(&state, &args)?;
        launch_anki_normally(cmd)?;
        return Ok(());
    }

    // If we weren't in a terminal, respawn ourselves in one
    ensure_terminal_shown()?;

    print!("\x1B[2J\x1B[H"); // Clear screen and move cursor to top
    println!("\x1B[1mAnki Launcher\x1B[0m\n");

    ensure_os_supported()?;

    check_versions(&mut state);

    let first_run = !state.uv_install_root.join(".venv").exists();
    if first_run {
        handle_version_install_or_update(&state, MainMenuChoice::Latest)?;
    } else {
        main_menu_loop(&state)?;
    }

    // Write marker file to indicate we've completed the sync process
    write_sync_marker(&state.sync_complete_marker)?;

    #[cfg(target_os = "macos")]
    {
        let cmd = build_python_command(&state, &[])?;
        platform::mac::prepare_for_launch_after_update(cmd, &uv_install_root)?;
    }

    if cfg!(unix) && !cfg!(target_os = "macos") {
        println!("\nPress enter to start Anki.");
        let mut input = String::new();
        let _ = stdin().read_line(&mut input);
    } else {
        // on Windows/macOS, the user needs to close the terminal/console
        // currently, but ideas on how we can avoid this would be good!
        println!();
        println!("Anki will start shortly.");
        println!("\x1B[1mYou can close this window.\x1B[0m\n");
    }

    // respawn the launcher as a disconnected subprocess for normal startup
    respawn_launcher()?;

    Ok(())
}

fn extract_aqt_version(
    uv_path: &std::path::Path,
    uv_install_root: &std::path::Path,
) -> Option<String> {
    let output = Command::new(uv_path)
        .current_dir(uv_install_root)
        .args(["pip", "show", "aqt"])
        .output()
        .ok()?;

    if !output.status.success() {
        return None;
    }

    let stdout = String::from_utf8(output.stdout).ok()?;
    for line in stdout.lines() {
        if let Some(version) = line.strip_prefix("Version: ") {
            return Some(version.trim().to_string());
        }
    }
    None
}

fn check_versions(state: &mut State) {
    // If sync_complete_marker is missing, do nothing
    if !state.sync_complete_marker.exists() {
        return;
    }

    // Determine current version by invoking uv pip show aqt
    match extract_aqt_version(&state.uv_path, &state.uv_install_root) {
        Some(version) => {
            state.current_version = Some(version);
        }
        None => {
            println!("Warning: Could not determine current Anki version");
        }
    }

    // Read previous version from "previous-version" file
    let previous_version_path = state.uv_install_root.join("previous-version");
    if let Ok(content) = read_file(&previous_version_path) {
        if let Ok(version_str) = String::from_utf8(content) {
            let version = version_str.trim().to_string();
            if !version.is_empty() {
                state.previous_version = Some(version);
            }
        }
    }
}

fn handle_version_install_or_update(state: &State, choice: MainMenuChoice) -> Result<()> {
    update_pyproject_for_version(
        choice.clone(),
        state.dist_pyproject_path.clone(),
        state.user_pyproject_path.clone(),
        state.dist_python_version_path.clone(),
        state.user_python_version_path.clone(),
    )?;

    // Extract current version before syncing (but don't write to file yet)
    let previous_version_to_save = extract_aqt_version(&state.uv_path, &state.uv_install_root);

    // Remove sync marker before attempting sync
    let _ = remove_file(&state.sync_complete_marker);

    println!("\x1B[1mUpdating Anki...\x1B[0m\n");

    let python_version_trimmed = if state.user_python_version_path.exists() {
        let python_version = read_file(&state.user_python_version_path)?;
        let python_version_str =
            String::from_utf8(python_version).context("Invalid UTF-8 in .python-version")?;
        Some(python_version_str.trim().to_string())
    } else {
        None
    };

    // `uv sync` sometimes does not pull in Python automatically
    // This might be system/platform specific and/or a uv bug.
    let mut command = Command::new(&state.uv_path);
    command
        .current_dir(&state.uv_install_root)
        .env("UV_CACHE_DIR", &state.uv_cache_dir)
        .env("UV_PYTHON_INSTALL_DIR", &state.uv_python_install_dir)
        .args(["python", "install", "--managed-python"]);

    // Add python version if .python-version file exists
    if let Some(version) = &python_version_trimmed {
        command.args([version]);
    }

    command.ensure_success().context("Python install failed")?;

    // Sync the venv
    let mut command = Command::new(&state.uv_path);
    command
        .current_dir(&state.uv_install_root)
        .env("UV_CACHE_DIR", &state.uv_cache_dir)
        .env("UV_PYTHON_INSTALL_DIR", &state.uv_python_install_dir)
        .args(["sync", "--upgrade", "--managed-python"]);

    // Add python version if .python-version file exists
    if let Some(version) = &python_version_trimmed {
        command.args(["--python", version]);
    }

    // Set UV_PRERELEASE=allow if beta mode is enabled
    if state.prerelease_marker.exists() {
        command.env("UV_PRERELEASE", "allow");
    }

    if state.no_cache_marker.exists() {
        command.env("UV_NO_CACHE", "1");
    }

    match command.ensure_success() {
        Ok(_) => {
            // Sync succeeded
            if matches!(&choice, MainMenuChoice::Version(VersionKind::PyOxidizer(_))) {
                inject_helper_addon(&state.uv_install_root)?;
            }

            // Now that sync succeeded, save the previous version
            if let Some(current_version) = previous_version_to_save {
                let previous_version_path = state.uv_install_root.join("previous-version");
                if let Err(e) = write_file(&previous_version_path, &current_version) {
                    println!("Warning: Could not save previous version: {e}");
                }
            }

            Ok(())
        }
        Err(e) => {
            // If sync fails due to things like a missing wheel on pypi,
            // we need to remove the lockfile or uv will cache the bad result.
            let _ = remove_file(&state.uv_lock_path);
            println!("Install failed: {e:#}");
            println!();
            Err(e.into())
        }
    }
}

fn main_menu_loop(state: &State) -> Result<()> {
    loop {
        let menu_choice = get_main_menu_choice(state)?;

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
            MainMenuChoice::ToggleCache => {
                // Toggle cache disable file
                if state.no_cache_marker.exists() {
                    let _ = remove_file(&state.no_cache_marker);
                    println!("Download caching enabled.");
                } else {
                    write_file(&state.no_cache_marker, "")?;
                    // Delete the cache directory and everything in it
                    if state.uv_cache_dir.exists() {
                        let _ = anki_io::remove_dir_all(&state.uv_cache_dir);
                    }
                    println!("Download caching disabled and cache cleared.");
                }
                println!();
                continue;
            }
            MainMenuChoice::Uninstall => {
                if handle_uninstall(state)? {
                    std::process::exit(0);
                }
                continue;
            }
            choice @ (MainMenuChoice::Latest | MainMenuChoice::Version(_)) => {
                if handle_version_install_or_update(state, choice.clone()).is_err() {
                    continue;
                }
                break;
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

fn get_main_menu_choice(state: &State) -> Result<MainMenuChoice> {
    loop {
        println!("1) Latest Anki (just press Enter)");
        println!("2) Choose a version");
        if let Some(current_version) = &state.current_version {
            let normalized_current = normalize_version(current_version);
            println!("3) Keep existing version ({normalized_current})");
        }
        if let Some(prev_version) = &state.previous_version {
            if state.current_version.as_ref() != Some(prev_version) {
                let normalized_prev = normalize_version(prev_version);
                println!("4) Revert to previous version ({normalized_prev})");
            }
        }
        println!();

        let betas_enabled = state.prerelease_marker.exists();
        println!(
            "5) Allow betas: {}",
            if betas_enabled { "on" } else { "off" }
        );
        let cache_enabled = !state.no_cache_marker.exists();
        println!(
            "6) Cache downloads: {}",
            if cache_enabled { "on" } else { "off" }
        );
        println!();
        println!("7) Uninstall");
        println!("8) Quit");
        print!("> ");
        let _ = stdout().flush();

        let mut input = String::new();
        let _ = stdin().read_line(&mut input);
        let input = input.trim();

        println!();

        return Ok(match input {
            "" | "1" => MainMenuChoice::Latest,
            "2" => {
                match get_version_kind(state)? {
                    Some(version_kind) => MainMenuChoice::Version(version_kind),
                    None => continue, // Return to main menu
                }
            }
            "3" => {
                if state.current_version.is_some() {
                    MainMenuChoice::KeepExisting
                } else {
                    println!("Invalid input. Please try again.\n");
                    continue;
                }
            }
            "4" => {
                if let Some(prev_version) = &state.previous_version {
                    if state.current_version.as_ref() != Some(prev_version) {
                        if let Some(version_kind) = parse_version_kind(prev_version) {
                            return Ok(MainMenuChoice::Version(version_kind));
                        }
                    }
                }
                println!("Invalid input. Please try again.\n");
                continue;
            }
            "5" => MainMenuChoice::ToggleBetas,
            "6" => MainMenuChoice::ToggleCache,
            "7" => MainMenuChoice::Uninstall,
            "8" => MainMenuChoice::Quit,
            _ => {
                println!("Invalid input. Please try again.");
                continue;
            }
        });
    }
}

fn get_version_kind(state: &State) -> Result<Option<VersionKind>> {
    println!("Please wait...");

    let include_prereleases = state.prerelease_marker.exists();
    let all_versions = fetch_versions(state)?;
    let all_versions = filter_and_normalize_versions(all_versions, include_prereleases);

    let latest_patches = with_only_latest_patch(&all_versions);
    let latest_releases: Vec<&String> = latest_patches.iter().take(5).collect();
    let releases_str = latest_releases
        .iter()
        .map(|v| v.as_str())
        .collect::<Vec<_>>()
        .join(", ");
    println!("Latest releases: {releases_str}");

    println!("Enter the version you want to install:");
    print!("> ");
    let _ = stdout().flush();

    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
    let input = input.trim();

    if input.is_empty() {
        return Ok(None);
    }

    // Normalize the input version for comparison
    let normalized_input = normalize_version(input);

    // Check if the version exists in the available versions
    let version_exists = all_versions.iter().any(|v| v == &normalized_input);

    match (parse_version_kind(input), version_exists) {
        (Some(version_kind), true) => {
            println!();
            Ok(Some(version_kind))
        }
        (None, true) => {
            println!("Versions before 2.1.50 can't be installedn");
            Ok(None)
        }
        _ => {
            println!("Invalid version.\n");
            Ok(None)
        }
    }
}

fn with_only_latest_patch(versions: &[String]) -> Vec<String> {
    // Only show the latest patch release for a given (major, minor)
    let mut seen_major_minor = std::collections::HashSet::new();
    versions
        .iter()
        .filter(|v| {
            let (major, minor, _, _) = parse_version_for_filtering(v);
            if major == 2 {
                return true;
            }
            let major_minor = (major, minor);
            if seen_major_minor.contains(&major_minor) {
                false
            } else {
                seen_major_minor.insert(major_minor);
                true
            }
        })
        .cloned()
        .collect()
}

fn parse_version_for_filtering(version_str: &str) -> (u32, u32, u32, bool) {
    // Remove any build metadata after +
    let version_str = version_str.split('+').next().unwrap_or(version_str);

    // Check for prerelease markers
    let is_prerelease = ["a", "b", "rc", "alpha", "beta"]
        .iter()
        .any(|marker| version_str.to_lowercase().contains(marker));

    // Extract numeric parts (stop at first non-digit/non-dot character)
    let numeric_end = version_str
        .find(|c: char| !c.is_ascii_digit() && c != '.')
        .unwrap_or(version_str.len());
    let numeric_part = &version_str[..numeric_end];

    let parts: Vec<&str> = numeric_part.split('.').collect();

    let major = parts.first().and_then(|s| s.parse().ok()).unwrap_or(0);
    let minor = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(0);
    let patch = parts.get(2).and_then(|s| s.parse().ok()).unwrap_or(0);

    (major, minor, patch, is_prerelease)
}

fn normalize_version(version: &str) -> String {
    let (major, minor, patch, _is_prerelease) = parse_version_for_filtering(version);

    if major <= 2 {
        // Don't transform versions <= 2.x
        return version.to_string();
    }

    // For versions > 2, pad the minor version with leading zero if < 10
    let normalized_minor = if minor < 10 {
        format!("0{minor}")
    } else {
        minor.to_string()
    };

    // Find any prerelease suffix
    let mut prerelease_suffix = "";

    // Look for prerelease markers after the numeric part
    let numeric_end = version
        .find(|c: char| !c.is_ascii_digit() && c != '.')
        .unwrap_or(version.len());
    if numeric_end < version.len() {
        let suffix_part = &version[numeric_end..];
        let suffix_lower = suffix_part.to_lowercase();

        for marker in ["alpha", "beta", "rc", "a", "b"] {
            if suffix_lower.starts_with(marker) {
                prerelease_suffix = &version[numeric_end..];
                break;
            }
        }
    }

    // Reconstruct the version
    if version.matches('.').count() >= 2 {
        format!("{major}.{normalized_minor}.{patch}{prerelease_suffix}")
    } else {
        format!("{major}.{normalized_minor}{prerelease_suffix}")
    }
}

fn filter_and_normalize_versions(
    all_versions: Vec<String>,
    include_prereleases: bool,
) -> Vec<String> {
    let mut valid_versions: Vec<String> = all_versions
        .into_iter()
        .map(|v| normalize_version(&v))
        .collect();

    // Reverse to get chronological order (newest first)
    valid_versions.reverse();

    if !include_prereleases {
        valid_versions.retain(|v| {
            let (_, _, _, is_prerelease) = parse_version_for_filtering(v);
            !is_prerelease
        });
    }

    valid_versions
}

fn fetch_versions(state: &State) -> Result<Vec<String>> {
    let versions_script = state.resources_dir.join("versions.py");

    let mut cmd = Command::new(&state.uv_path);
    cmd.current_dir(&state.uv_install_root)
        .args(["run", "--no-project"])
        .arg(&versions_script);

    let output = cmd.utf8_output()?;
    let versions = serde_json::from_str(&output.stdout).context("Failed to parse versions JSON")?;
    Ok(versions)
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
            unreachable!();
        }
        MainMenuChoice::ToggleCache => {
            unreachable!();
        }
        MainMenuChoice::Uninstall => {
            unreachable!();
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
                                "  \"anki-audio==0.1.0; sys.platform == 'win32' or sys.platform == 'darwin'\",\n",
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
                    content_str.replace("anki-release", &format!("anki-release=={version}"))
                }
            };
            write_file(&user_pyproject_path, &updated_content)?;

            // Update .python-version based on version kind
            match &version_kind {
                VersionKind::PyOxidizer(_) => {
                    write_file(&user_python_version_path, "3.9")?;
                }
                VersionKind::Uv(_) => {
                    copy_file(&dist_python_version_path, &user_python_version_path)?;
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

fn inject_helper_addon(_uv_install_root: &std::path::Path) -> Result<()> {
    let addons21_path = get_anki_addons21_path()?;

    if !addons21_path.exists() {
        return Ok(());
    }

    let addon_folder = addons21_path.join("anki-launcher");

    // Remove existing anki-launcher folder if it exists
    if addon_folder.exists() {
        anki_io::remove_dir_all(&addon_folder)?;
    }

    // Create the anki-launcher folder
    create_dir_all(&addon_folder)?;

    // Write the embedded files
    let init_py_content = include_str!("../addon/__init__.py");
    let manifest_json_content = include_str!("../addon/manifest.json");

    write_file(addon_folder.join("__init__.py"), init_py_content)?;
    write_file(addon_folder.join("manifest.json"), manifest_json_content)?;

    Ok(())
}

fn get_anki_base_path() -> Result<std::path::PathBuf> {
    let anki_base_path = if cfg!(target_os = "windows") {
        // Windows: %APPDATA%\Anki2
        dirs::config_dir()
            .context("Unable to determine config directory")?
            .join("Anki2")
    } else if cfg!(target_os = "macos") {
        // macOS: ~/Library/Application Support/Anki2
        dirs::data_dir()
            .context("Unable to determine data directory")?
            .join("Anki2")
    } else {
        // Linux: ~/.local/share/Anki2
        dirs::data_dir()
            .context("Unable to determine data directory")?
            .join("Anki2")
    };

    Ok(anki_base_path)
}

fn get_anki_addons21_path() -> Result<std::path::PathBuf> {
    Ok(get_anki_base_path()?.join("addons21"))
}

fn handle_uninstall(state: &State) -> Result<bool> {
    println!("Uninstall Anki's program files? (y/n)");
    print!("> ");
    let _ = stdout().flush();

    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
    let input = input.trim().to_lowercase();

    if input != "y" {
        println!("Uninstall cancelled.");
        println!();
        return Ok(false);
    }

    // Remove program files
    if state.uv_install_root.exists() {
        anki_io::remove_dir_all(&state.uv_install_root)?;
        println!("Program files removed.");
    }

    println!();
    println!("Remove all profiles/cards? (y/n)");
    print!("> ");
    let _ = stdout().flush();

    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
    let input = input.trim().to_lowercase();

    if input == "y" && state.anki_base_folder.exists() {
        anki_io::remove_dir_all(&state.anki_base_folder)?;
        println!("User data removed.");
    }

    println!();

    // Platform-specific messages
    #[cfg(target_os = "macos")]
    platform::mac::finalize_uninstall();

    #[cfg(target_os = "windows")]
    platform::windows::finalize_uninstall();

    #[cfg(all(unix, not(target_os = "macos")))]
    platform::unix::finalize_uninstall();

    Ok(true)
}

fn build_python_command(state: &State, args: &[String]) -> Result<Command> {
    let python_exe = if cfg!(target_os = "windows") {
        let show_console = std::env::var("ANKI_CONSOLE").is_ok();
        if show_console {
            state.uv_install_root.join(".venv/Scripts/python.exe")
        } else {
            state.uv_install_root.join(".venv/Scripts/pythonw.exe")
        }
    } else {
        state.uv_install_root.join(".venv/bin/python")
    };

    let mut cmd = Command::new(&python_exe);
    cmd.args(["-c", "import aqt, sys; sys.argv[0] = 'Anki'; aqt.run()"]);
    cmd.args(args);
    // tell the Python code it was invoked by the launcher, and updating is
    // available
    cmd.env("ANKI_LAUNCHER", std::env::current_exe()?.utf8()?.as_str());

    // Set UV and Python paths for the Python code
    cmd.env("ANKI_LAUNCHER_UV", state.uv_path.utf8()?.as_str());
    cmd.env("UV_PROJECT", state.uv_install_root.utf8()?.as_str());

    // Set UV_PRERELEASE=allow if beta mode is enabled
    if state.prerelease_marker.exists() {
        cmd.env("UV_PRERELEASE", "allow");
    }

    Ok(cmd)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normalize_version() {
        // Test versions <= 2.x (should not be transformed)
        assert_eq!(normalize_version("2.1.50"), "2.1.50");

        // Test basic versions > 2 with zero-padding
        assert_eq!(normalize_version("25.7"), "25.07");
        assert_eq!(normalize_version("25.07"), "25.07");
        assert_eq!(normalize_version("25.10"), "25.10");
        assert_eq!(normalize_version("24.6.1"), "24.06.1");
        assert_eq!(normalize_version("24.06.1"), "24.06.1");

        // Test prerelease versions
        assert_eq!(normalize_version("25.7a1"), "25.07a1");
        assert_eq!(normalize_version("25.7.1a1"), "25.07.1a1");

        // Test versions with patch = 0
        assert_eq!(normalize_version("25.7.0"), "25.07.0");
        assert_eq!(normalize_version("25.7.0a1"), "25.07.0a1");
    }
}
