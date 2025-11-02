// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(all(unix, not(target_os = "macos")))]
pub mod unix;

#[cfg(target_os = "macos")]
pub mod mac;

#[cfg(target_os = "windows")]
pub mod windows;

#[cfg(unix)]
pub mod nix;

mod py313;
mod py39;

use std::ffi::CString;
use std::path::PathBuf;

use anki_process::CommandExt;
use anyhow::ensure;
use anyhow::Context;
use anyhow::Result;

pub fn get_exe_and_resources_dirs() -> Result<(PathBuf, PathBuf)> {
    let exe_dir = std::env::current_exe()
        .context("Failed to get current executable path")?
        .parent()
        .context("Failed to get executable directory")?
        .to_owned();

    let resources_dir = if cfg!(target_os = "macos") {
        // On macOS, resources are in ../Resources relative to the executable
        exe_dir
            .parent()
            .context("Failed to get parent directory")?
            .join("Resources")
    } else {
        // On other platforms, resources are in the same directory as executable
        exe_dir.clone()
    };

    Ok((exe_dir, resources_dir))
}

pub fn get_uv_binary_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "uv.exe"
    } else if cfg!(target_os = "macos") {
        "uv"
    } else if cfg!(target_arch = "x86_64") {
        "uv.amd64"
    } else {
        "uv.arm64"
    }
}

pub fn respawn_launcher() -> Result<()> {
    use std::process::Stdio;

    let mut launcher_cmd = if cfg!(target_os = "macos") {
        // On macOS, we need to launch the .app bundle, not the executable directly
        let current_exe =
            std::env::current_exe().context("Failed to get current executable path")?;

        // Navigate from Contents/MacOS/launcher to the .app bundle
        let app_bundle = current_exe
            .parent() // MacOS
            .and_then(|p| p.parent()) // Contents
            .and_then(|p| p.parent()) // .app
            .context("Failed to find .app bundle")?;

        let mut cmd = std::process::Command::new("open");
        cmd.arg(app_bundle);
        cmd
    } else {
        let current_exe =
            std::env::current_exe().context("Failed to get current executable path")?;
        std::process::Command::new(current_exe)
    };

    launcher_cmd
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
        const DETACHED_PROCESS: u32 = 0x00000008;
        launcher_cmd.creation_flags(CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS);
    }

    #[cfg(all(unix, not(target_os = "macos")))]
    {
        use std::os::unix::process::CommandExt;
        launcher_cmd.process_group(0);
    }

    let child = launcher_cmd.ensure_spawn()?;
    std::mem::forget(child);

    Ok(())
}

pub fn launch_anki_normally(mut cmd: std::process::Command) -> Result<()> {
    #[cfg(windows)]
    {
        crate::platform::windows::prepare_to_launch_normally();
        cmd.ensure_success()?;
    }
    #[cfg(unix)]
    cmd.ensure_exec()?;
    Ok(())
}

pub fn _run_anki_normally(state: &crate::State) -> Result<()> {
    #[cfg(windows)]
    {
        let console = std::env::var("ANKI_CONSOLE").is_ok();
        if console {
            // no pythonw.exe available for us to use
            ensure_terminal_shown()?;
        }
        crate::platform::windows::prepare_to_launch_normally();
        windows::run(state, console)?;
    }
    #[cfg(unix)]
    nix::run(state)?;
    Ok(())
}

pub fn run_anki_normally(state: &crate::State) -> bool {
    if let Err(e) = _run_anki_normally(state) {
        eprintln!("failed to run as embedded: {e:?}");
        return false;
    }
    true
}

#[cfg(windows)]
pub use windows::ensure_terminal_shown;

#[cfg(unix)]
pub fn ensure_terminal_shown() -> Result<()> {
    use std::io::IsTerminal;

    let want_terminal = std::env::var("ANKI_LAUNCHER_WANT_TERMINAL").is_ok();
    let stdout_is_terminal = IsTerminal::is_terminal(&std::io::stdout());
    if want_terminal || !stdout_is_terminal {
        #[cfg(target_os = "macos")]
        mac::relaunch_in_terminal()?;
        #[cfg(not(target_os = "macos"))]
        unix::relaunch_in_terminal()?;
    }

    // Set terminal title to "Anki Launcher"
    print!("\x1b]2;Anki Launcher\x07");
    Ok(())
}

pub fn ensure_os_supported() -> Result<()> {
    #[cfg(all(unix, not(target_os = "macos")))]
    unix::ensure_glibc_supported()?;

    #[cfg(target_os = "windows")]
    windows::ensure_windows_version_supported()?;

    Ok(())
}

pub type PyIsInitialized = extern "C" fn() -> std::ffi::c_int;
pub type PyRunSimpleString = extern "C" fn(command: *const std::ffi::c_char) -> std::ffi::c_int;
pub type PyFinalizeEx = extern "C" fn() -> std::ffi::c_int;
pub type PyConfigInitPythonConfig = extern "C" fn(*mut std::ffi::c_void);
// WARN: py39 and py313's PyStatus are identical
// check if this remains true in future versions
pub type PyConfigSetBytesString = extern "C" fn(
    config: *mut std::ffi::c_void,
    config_str: *mut *mut libc::wchar_t,
    str_: *const std::os::raw::c_char,
) -> py313::PyStatus;
pub type PyConfigSetBytesArgv = extern "C" fn(
    config: *mut std::ffi::c_void,
    argc: isize,
    argv: *const *mut std::os::raw::c_char,
) -> py313::PyStatus;
pub type PyInitializeFromConfig = extern "C" fn(*const std::ffi::c_void) -> py313::PyStatus;
pub type PyStatusException = extern "C" fn(err: py313::PyStatus) -> std::os::raw::c_int;

#[allow(non_snake_case)]
struct PyFfi {
    exec: CString,
    lib: *mut std::ffi::c_void,
    Py_IsInitialized: PyIsInitialized,
    PyRun_SimpleString: PyRunSimpleString,
    Py_FinalizeEx: PyFinalizeEx,
    PyConfig_InitPythonConfig: PyConfigInitPythonConfig,
    PyConfig_SetBytesString: PyConfigSetBytesString,
    Py_InitializeFromConfig: PyInitializeFromConfig,
    PyConfig_SetBytesArgv: PyConfigSetBytesArgv,
    PyStatus_Exception: PyStatusException,
}

impl PyFfi {
    fn run(self, preamble: impl AsRef<std::ffi::CStr>) -> Result<()> {
        (self.Py_InitializeEx)(1);

        let res = (self.Py_IsInitialized)();
        ensure!(res != 0, "failed to initialise");
        let res = (self.PyRun_SimpleString)(preamble.as_ref().as_ptr());
        ensure!(res == 0, "failed to run preamble");
        // test importing aqt first before falling back to usual launch
        let res = (self.PyRun_SimpleString)(c"import aqt".as_ptr());
        ensure!(res == 0, "failed to import aqt");
        // from here on, don't fallback if we fail
        let _ = (self.PyRun_SimpleString)(c"aqt.run()".as_ptr());

        Ok(())
    }
}
