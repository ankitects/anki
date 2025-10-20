// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::stdin;
use std::io::stdout;
use std::io::Write;
use std::process::Command;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

use anki_io::copy_file;
use anki_io::create_dir_all;
use anki_io::modified_time;
use anki_io::read_file;
use anki_io::remove_file;
use anki_io::write_file;
use anki_io::ToUtf8Path;
use anki_process::CommandExt as AnkiCommandExt;
use anyhow::anyhow;
use anyhow::Context;
use anyhow::Result;

use crate::platform;
use crate::platform::ensure_os_supported;
use crate::platform::get_exe_and_resources_dirs;
use crate::platform::get_uv_binary_name;
pub use crate::platform::launch_anki_normally;
use crate::platform::respawn_launcher;
use crate::state::ExistingVersions;
use crate::state::Version;
use crate::state::Versions;

#[derive(Debug, Clone)]
pub struct Paths {
    pub prerelease_marker: std::path::PathBuf,
    uv_install_root: std::path::PathBuf,
    uv_cache_dir: std::path::PathBuf,
    pub no_cache_marker: std::path::PathBuf,
    anki_base_folder: std::path::PathBuf,
    uv_path: std::path::PathBuf,
    uv_python_install_dir: std::path::PathBuf,
    user_pyproject_path: std::path::PathBuf,
    user_python_version_path: std::path::PathBuf,
    dist_pyproject_path: std::path::PathBuf,
    dist_python_version_path: std::path::PathBuf,
    uv_lock_path: std::path::PathBuf,
    sync_complete_marker: std::path::PathBuf,
    launcher_trigger_file: std::path::PathBuf,
    pub mirror_path: std::path::PathBuf,
    pub pyproject_modified_by_user: bool,
    resources_dir: std::path::PathBuf,
    venv_folder: std::path::PathBuf,
    /// system Python + PyQt6 library mode
    system_qt: bool,
}

#[derive(Debug, Clone)]
pub enum VersionKind {
    PyOxidizer(String),
    Uv(String),
}

pub fn handle_version_install_or_update<F>(
    state: &Paths,
    version: &str,
    keep_existing: bool,
    previous_version_to_save: Option<&str>,
    on_pty_data: F,
) -> Result<()>
where
    F: Fn(String) + Send + 'static,
{
    let version_kind = parse_version_kind(version)
        .ok_or_else(|| anyhow!(r#""{version}" is not a valid version!"#))?;
    if !keep_existing {
        apply_version_kind(&version_kind, state)?;
    }

    // TODO: support this
    // Extract current version before syncing (but don't write to file yet)
    // let previous_version_to_save = state.current_version.clone();

    // Remove sync marker before attempting sync
    let _ = remove_file(&state.sync_complete_marker);

    let python_version_trimmed = if state.user_python_version_path.exists() {
        let python_version = read_file(&state.user_python_version_path)?;
        let python_version_str =
            String::from_utf8(python_version).context("Invalid UTF-8 in .python-version")?;
        Some(python_version_str.trim().to_string())
    } else {
        None
    };

    // Prepare to sync the venv
    let mut command = uv_pty_command(state)?;

    if cfg!(target_os = "macos") {
        // remove CONDA_PREFIX/bin from PATH to avoid conda interference
        if let Ok(conda_prefix) = std::env::var("CONDA_PREFIX") {
            if let Ok(current_path) = std::env::var("PATH") {
                let conda_bin = format!("{conda_prefix}/bin");
                let filtered_paths: Vec<&str> = current_path
                    .split(':')
                    .filter(|&path| path != conda_bin)
                    .collect();
                let new_path = filtered_paths.join(":");
                command.env("PATH", new_path);
            }
        }
        // put our fake install_name_tool at the top of the path to override
        // potential conflicts
        if let Ok(current_path) = std::env::var("PATH") {
            let exe_dir = std::env::current_exe()
                .ok()
                .and_then(|exe| exe.parent().map(|p| p.to_path_buf()));
            if let Some(exe_dir) = exe_dir {
                let new_path = format!("{}:{}", exe_dir.display(), current_path);
                command.env("PATH", new_path);
            }
        }
    }

    // Create venv with system site packages if system Qt is enabled
    if state.system_qt {
        let mut venv_command = uv_command(state)?;
        venv_command.args([
            "venv",
            "--no-managed-python",
            "--system-site-packages",
            "--no-config",
        ]);
        venv_command.ensure_success()?;
    }

    command.env("UV_CACHE_DIR", &state.uv_cache_dir);
    command.env("UV_PYTHON_INSTALL_DIR", &state.uv_python_install_dir);
    command.env(
        "UV_HTTP_TIMEOUT",
        std::env::var("UV_HTTP_TIMEOUT").unwrap_or_else(|_| "180".to_string()),
    );

    command.args(["sync", "--upgrade", "--no-config"]);
    if !state.system_qt {
        command.arg("--managed-python");
    }

    // Add python version if .python-version file exists (but not for system Qt)
    if let Some(version) = &python_version_trimmed {
        if !state.system_qt {
            command.args(["--python", version]);
        }
    }

    if state.no_cache_marker.exists() {
        command.env("UV_NO_CACHE", "1");
    }

    // NOTE: pty and child must live in the same thread
    let pty_system = portable_pty::NativePtySystem::default();

    use portable_pty::PtySystem;
    let pair = pty_system
        .openpty(portable_pty::PtySize {
            // NOTE: must be the same as xterm.js', otherwise text won't wrap
            // TODO: maybe don't hardcode?
            rows: 10,
            cols: 50,
            pixel_width: 0,
            pixel_height: 0,
        })
        .with_context(|| "failed to open pty")?;

    let mut reader = pair.master.try_clone_reader()?;
    let mut writer = pair.master.take_writer()?;

    tauri::async_runtime::spawn_blocking(move || {
        let mut buf = [0u8; 1024];
        loop {
            let res = reader.read(&mut buf);
            match res {
                // EOF
                Ok(0) => break,
                Ok(n) => {
                    let output = String::from_utf8_lossy(&buf[..n]).to_string();
                    // NOTE: windows requests cursor position before actually running child
                    if output == "\x1b[6n" {
                        writeln!(&mut writer, "\x1b[0;0R").unwrap();
                    }
                    // cheaper to base64ise a string than jsonify an [u8]
                    let data = data_encoding::BASE64.encode(&buf[..n]);
                    on_pty_data(data);
                }
                Err(e) => {
                    eprintln!("Error reading from PTY: {}", e);
                    break;
                }
            }
        }
    });

    let cmdline = command.as_unix_command_line()?;

    let mut child = pair.slave.spawn_command(command).unwrap();
    drop(pair.slave);
    println!("waiting on uv...");
    let status = child.wait();
    println!("uv exited with status: {:?}", status);

    match status {
        // Sync succeeded
        Ok(exit_status) if exit_status.success() => {
            if !keep_existing && matches!(version_kind, VersionKind::PyOxidizer(_)) {
                inject_helper_addon()?;
            }

            // Now that sync succeeded, save the previous version
            if let Some(current_version) = previous_version_to_save {
                let previous_version_path = state.uv_install_root.join("previous-version");
                if let Err(e) = write_file(&previous_version_path, current_version) {
                    // TODO:
                    println!("Warning: Could not save previous version: {e}");
                }
            }

            Ok(())
        }
        // If sync fails due to things like a missing wheel on pypi,
        // we need to remove the lockfile or uv will cache the bad result.
        Ok(exit_status) => {
            let _ = remove_file(&state.uv_lock_path);
            let code = exit_status.exit_code();
            Err(anyhow!("Failed to run ({code}): {cmdline}"))
        }
        Err(e) => {
            let _ = remove_file(&state.uv_lock_path);
            Err(e.into())
        }
    }
}

pub fn set_allow_betas(state: &Paths, allow_betas: bool) -> Result<()> {
    if allow_betas {
        write_file(&state.prerelease_marker, "")?;
    } else {
        let _ = remove_file(&state.prerelease_marker);
    }
    Ok(())
}

pub fn set_cache_enabled(state: &Paths, cache_enabled: bool) -> Result<()> {
    if cache_enabled {
        let _ = remove_file(&state.no_cache_marker);
    } else {
        write_file(&state.no_cache_marker, "")?;
        // Delete the cache directory and everything in it
        if state.uv_cache_dir.exists() {
            let _ = anki_io::remove_dir_all(&state.uv_cache_dir);
        }
    }
    Ok(())
}

/// Get mtime of provided file, or 0 if unavailable
fn file_timestamp_secs(path: &std::path::Path) -> i64 {
    modified_time(path)
        .map(|t| t.duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() as i64)
        .unwrap_or_default()
}

fn with_only_latest_patch(versions: &[Version]) -> Vec<Version> {
    // Only show the latest patch release for a given (major, minor)
    let mut seen_major_minor = std::collections::HashSet::new();
    versions
        .iter()
        .filter(|v| {
            let (major, minor, _, _) = parse_version_for_filtering(&v.version);
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

fn normalize_version(version: &str) -> Version {
    let (major, minor, patch, is_prerelease) = parse_version_for_filtering(version);

    if major <= 2 {
        // Don't transform versions <= 2.x
        return Version {
            version: version.to_string(),
            is_prerelease,
        };
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
    let version = if version.matches('.').count() >= 2 {
        format!("{major}.{normalized_minor}.{patch}{prerelease_suffix}")
    } else {
        format!("{major}.{normalized_minor}{prerelease_suffix}")
    };

    Version {
        version,
        is_prerelease,
    }
}

fn filter_and_normalize_versions1(all_versions: Vec<String>) -> Vec<Version> {
    let mut valid_versions: Vec<Version> = all_versions
        .into_iter()
        .map(|v| normalize_version(&v))
        .collect();

    // Reverse to get chronological order (newest first)
    valid_versions.reverse();

    valid_versions
}

pub fn apply_version_kind(version_kind: &VersionKind, state: &Paths) -> Result<()> {
    let content = read_file(&state.dist_pyproject_path)?;
    let content_str = String::from_utf8(content).context("Invalid UTF-8 in pyproject.toml")?;
    let updated_content = match version_kind {
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
        VersionKind::Uv(version) => content_str.replace(
            "anki-release",
            &format!("anki-release=={version}\",\n  \"anki=={version}\",\n  \"aqt=={version}"),
        ),
    };

    let final_content = if state.system_qt {
        format!(
            concat!(
                "{}\n\n[tool.uv]\n",
                "override-dependencies = [\n",
                "  \"pyqt6; sys_platform=='never'\",\n",
                "  \"pyqt6-qt6; sys_platform=='never'\",\n",
                "  \"pyqt6-webengine; sys_platform=='never'\",\n",
                "  \"pyqt6-webengine-qt6; sys_platform=='never'\",\n",
                "  \"pyqt6_sip; sys_platform=='never'\"\n",
                "]\n"
            ),
            updated_content
        )
    } else {
        updated_content
    };

    write_file(&state.user_pyproject_path, &final_content)?;

    // Update .python-version based on version kind
    match version_kind {
        VersionKind::PyOxidizer(_) => {
            write_file(&state.user_python_version_path, "3.9")?;
        }
        VersionKind::Uv(_) => {
            copy_file(
                &state.dist_python_version_path,
                &state.user_python_version_path,
            )?;
        }
    }
    Ok(())
}

pub fn parse_version_kind(version: &str) -> Option<VersionKind> {
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

fn inject_helper_addon() -> Result<()> {
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
    let init_py_content = include_str!("../../../launcher/addon/__init__.py");
    let manifest_json_content = include_str!("../../../launcher/addon/manifest.json");

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

// TODO: revert
#[allow(unused)]
fn handle_uninstall(state: &Paths) -> Result<bool> {
    // println!("{}", state.tr.launcher_uninstall_confirm());
    print!("> ");
    let _ = stdout().flush();

    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
    let input = input.trim().to_lowercase();

    if input != "y" {
        // println!("{}", state.tr.launcher_uninstall_cancelled());
        println!();
        return Ok(false);
    }

    // Remove program files
    if state.uv_install_root.exists() {
        anki_io::remove_dir_all(&state.uv_install_root)?;
        // println!("{}", state.tr.launcher_program_files_removed());
    }

    println!();
    // println!("{}", state.tr.launcher_remove_all_profiles_confirm());
    print!("> ");
    let _ = stdout().flush();

    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
    let input = input.trim().to_lowercase();

    if input == "y" && state.anki_base_folder.exists() {
        anki_io::remove_dir_all(&state.anki_base_folder)?;
        // println!("{}", state.tr.launcher_user_data_removed());
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

fn uv_command(state: &Paths) -> Result<Command> {
    let mut command = Command::new(&state.uv_path);
    command.current_dir(&state.uv_install_root);

    // remove UV_* environment variables to avoid interference
    for (key, _) in std::env::vars() {
        if key.starts_with("UV_") {
            command.env_remove(key);
        }
    }
    command
        .env_remove("VIRTUAL_ENV")
        .env_remove("SSLKEYLOGFILE");

    // Add mirror environment variable if enabled
    if let Some((python_mirror, pypi_mirror)) = get_mirror_urls(state)? {
        command
            .env("UV_PYTHON_INSTALL_MIRROR", &python_mirror)
            .env("UV_DEFAULT_INDEX", &pypi_mirror);
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;

        command.creation_flags(windows::Win32::System::Threading::CREATE_NO_WINDOW.0);
    }
    Ok(command)
}

fn uv_pty_command(state: &Paths) -> Result<portable_pty::CommandBuilder> {
    let mut command = portable_pty::CommandBuilder::new(&state.uv_path);
    command.cwd(&state.uv_install_root);

    // remove UV_* environment variables to avoid interference
    for (key, _) in std::env::vars() {
        if key.starts_with("UV_") {
            command.env_remove(key);
        }
    }
    command.env_remove("VIRTUAL_ENV");
    command.env_remove("SSLKEYLOGFILE");

    // Add mirror environment variable if enabled
    if let Some((python_mirror, pypi_mirror)) = get_mirror_urls(state)? {
        command.env("UV_PYTHON_INSTALL_MIRROR", &python_mirror);
        command.env("UV_DEFAULT_INDEX", &pypi_mirror);
    }

    Ok(command)
}

fn get_mirror_urls(state: &Paths) -> Result<Option<(String, String)>> {
    if !state.mirror_path.exists() {
        return Ok(None);
    }

    let content = read_file(&state.mirror_path)?;
    let content_str = String::from_utf8(content).context("Invalid UTF-8 in mirror file")?;

    let lines: Vec<&str> = content_str.lines().collect();
    if lines.len() >= 2 {
        Ok(Some((
            lines[0].trim().to_string(),
            lines[1].trim().to_string(),
        )))
    } else {
        Ok(None)
    }
}

pub fn set_mirror(state: &Paths, enabled: bool) -> Result<()> {
    if enabled {
        let china_mirrors = "https://registry.npmmirror.com/-/binary/python-build-standalone/\nhttps://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple/";
        write_file(&state.mirror_path, china_mirrors)?;
    } else if state.mirror_path.exists() {
        let _ = remove_file(&state.mirror_path);
    }
    Ok(())
}

impl crate::state::State {
    pub fn init() -> Result<Self> {
        let uv_install_root = if let Ok(custom_root) = std::env::var("ANKI_LAUNCHER_VENV_ROOT") {
            std::path::PathBuf::from(custom_root)
        } else {
            dirs::data_local_dir()
                .context("Unable to determine data_dir")?
                .join("AnkiProgramFiles")
        };

        let (exe_dir, resources_dir) = get_exe_and_resources_dirs()?;

        let mut paths = Paths {
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
            launcher_trigger_file: uv_install_root.join(".want-launcher"),
            mirror_path: uv_install_root.join("mirror"),
            pyproject_modified_by_user: false, // calculated later
            system_qt: (cfg!(unix) && !cfg!(target_os = "macos"))
                && resources_dir.join("system_qt").exists(),
            resources_dir,
            venv_folder: uv_install_root.join(".venv"),
        };

        // Check for uninstall request from Windows uninstaller
        if std::env::var("ANKI_LAUNCHER_UNINSTALL").is_ok() {
            return Ok(Self::Uninstall(paths.into()));
        }

        // Create install directory
        create_dir_all(&paths.uv_install_root)?;

        let launcher_requested =
            paths.launcher_trigger_file.exists() || !paths.user_pyproject_path.exists();

        // TODO: remove
        let skip = std::env::var("ANKI_LAUNCHER_SKIP").is_ok();

        // Calculate whether user has custom edits that need syncing
        let pyproject_time = file_timestamp_secs(&paths.user_pyproject_path);
        let sync_time = file_timestamp_secs(&paths.sync_complete_marker);
        paths.pyproject_modified_by_user = pyproject_time > sync_time;
        let pyproject_has_changed = paths.pyproject_modified_by_user;

        #[allow(clippy::nonminimal_bool)]
        let debug = true && cfg!(debug_assertions);

        if !launcher_requested && !pyproject_has_changed && (!debug || skip) {
            return Ok(Self::LaunchAnki(paths.into()));
        }

        if launcher_requested {
            // Remove the trigger file to make request ephemeral
            let _ = remove_file(&paths.launcher_trigger_file);
        }

        if let Err(e) = ensure_os_supported() {
            return Ok(Self::OsUnsupported(e));
        }

        Ok(Self::Normal(paths.into()))
    }
}

impl Paths {
    fn get_mirror_urls(&self) -> Result<Option<(String, String)>> {
        if !self.mirror_path.exists() {
            return Ok(None);
        }

        let content = read_file(&self.mirror_path)?;
        let content_str = String::from_utf8(content).context("Invalid UTF-8 in mirror file")?;

        let lines: Vec<&str> = content_str.lines().collect();
        if lines.len() >= 2 {
            Ok(Some((
                lines[0].trim().to_string(),
                lines[1].trim().to_string(),
            )))
        } else {
            Ok(None)
        }
    }

    fn uv_command(&self) -> Result<Command> {
        let mut command = Command::new(&self.uv_path);
        command.current_dir(&self.uv_install_root);

        // remove UV_* environment variables to avoid interference
        for (key, _) in std::env::vars() {
            if key.starts_with("UV_") {
                command.env_remove(key);
            }
        }
        command
            .env_remove("VIRTUAL_ENV")
            .env_remove("SSLKEYLOGFILE");

        // Add mirror environment variable if enabled
        if let Some((python_mirror, pypi_mirror)) = self.get_mirror_urls()? {
            command
                .env("UV_PYTHON_INSTALL_MIRROR", &python_mirror)
                .env("UV_DEFAULT_INDEX", &pypi_mirror);
        }

        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;

            command.creation_flags(windows::Win32::System::Threading::CREATE_NO_WINDOW.0);
        }
        Ok(command)
    }

    fn fetch_versions(&self) -> Result<Vec<String>> {
        let versions_script = self.resources_dir.join("versions.py");

        let mut cmd = self.uv_command()?;
        cmd.args(["run", "--no-project", "--no-config", "--managed-python"])
            .args(["--with", "pip-system-certs,requests[socks]"]);

        let python_version = read_file(&self.dist_python_version_path)?;
        let python_version_str =
            String::from_utf8(python_version).context("Invalid UTF-8 in .python-version")?;
        let version_trimmed = python_version_str.trim();
        if !version_trimmed.is_empty() {
            cmd.args(["--python", version_trimmed]);
        }

        cmd.arg(&versions_script);

        let output = match cmd.utf8_output() {
            Ok(output) => output,
            Err(e) => {
                return Err(e.into());
            }
        };
        let versions =
            serde_json::from_str(&output.stdout).context("Failed to parse versions JSON")?;
        Ok(versions)
    }

    pub fn get_releases(&self) -> Result<Versions> {
        let all_versions = self.fetch_versions()?;
        let all_versions = filter_and_normalize_versions1(all_versions);

        let latest_patches = with_only_latest_patch(&all_versions);
        let latest_releases: Vec<Version> = latest_patches.into_iter().take(5).collect();

        Ok(Versions {
            latest: latest_releases,
            all: all_versions,
        })
    }

    fn extract_aqt_version(&self) -> Option<String> {
        // Check if .venv exists first
        if !self.venv_folder.exists() {
            return None;
        }

        let output = self
            .uv_command()
            .ok()?
            .env("VIRTUAL_ENV", &self.venv_folder)
            .args(["pip", "show", "aqt"])
            .output();

        let output = output.ok()?;

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

    pub fn check_versions(&self) -> Result<ExistingVersions> {
        let mut res = ExistingVersions {
            pyproject_modified_by_user: self.pyproject_modified_by_user,
            ..Default::default()
        };

        // If sync_complete_marker is missing, do nothing
        if !self.sync_complete_marker.exists() {
            return Ok(res);
        }

        // Determine current version by invoking uv pip show aqt
        match self.extract_aqt_version() {
            Some(version) => {
                res.current = Some(normalize_version(&version));
            }
            None => {
                Err(anyhow::anyhow!(
                    "Warning: Could not determine current Anki version"
                ))?;
            }
        }

        // Read previous version from "previous-version" file
        let previous_version_path = self.uv_install_root.join("previous-version");
        if let Ok(content) = read_file(&previous_version_path) {
            if let Ok(version_str) = String::from_utf8(content) {
                let version = version_str.trim().to_string();
                if !version.is_empty() {
                    res.previous = Some(normalize_version(&version));
                }
            }
        }

        Ok(res)
    }

    fn write_sync_marker(&self) -> Result<()> {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .context("Failed to get system time")?
            .as_secs();
        write_file(&self.sync_complete_marker, timestamp.to_string())?;
        Ok(())
    }

    pub fn post_install(&self) -> Result<bool> {
        // Write marker file to indicate we've completed the sync process
        self.write_sync_marker()?;

        // whether or not anki needs to warm up
        Ok(cfg!(target_os = "macos"))
    }

    pub fn launch_anki(&self) -> Result<()> {
        #[cfg(target_os = "macos")]
        {
            let cmd = self.build_python_command(&[])?;
            platform::mac::prepare_for_launch_after_update(cmd, &self.uv_install_root)?;
        }

        // respawn the launcher as a disconnected subprocess for normal startup
        respawn_launcher()
    }

    pub fn build_python_command(&self, args: &[String]) -> Result<Command> {
        let python_exe = if cfg!(target_os = "windows") {
            let show_console = std::env::var("ANKI_CONSOLE").is_ok();
            if show_console {
                self.venv_folder.join("Scripts/python.exe")
            } else {
                self.venv_folder.join("Scripts/pythonw.exe")
            }
        } else {
            self.venv_folder.join("bin/python")
        };

        let mut cmd = Command::new(&python_exe);
        cmd.args(["-c", "import aqt, sys; sys.argv[0] = 'Anki'; aqt.run()"]);
        cmd.args(args);
        // tell the Python code it was invoked by the launcher, and updating is
        // available
        cmd.env("ANKI_LAUNCHER", std::env::current_exe()?.utf8()?.as_str());

        // Set UV and Python paths for the Python code
        cmd.env("ANKI_LAUNCHER_UV", self.uv_path.utf8()?.as_str());
        cmd.env("UV_PROJECT", self.uv_install_root.utf8()?.as_str());
        cmd.env_remove("SSLKEYLOGFILE");

        Ok(cmd)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normalize_version() {
        let normalize_version = |v| normalize_version(v).version;

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
