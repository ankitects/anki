// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::PathBuf;
use std::process::Command;

use anki_process::CommandExt;
use anyhow::Context;
use anyhow::Result;

use crate::Config;

pub fn handle_terminal_launch() -> Result<()> {
    // uv will do this itself
    Ok(())
}

/// If parent process has a console (eg cmd.exe), redirect our output there.
/// Sets config.show_console to true if successfully attached to console.
pub fn initial_terminal_setup(config: &mut Config) {
    use std::ffi::CString;

    use libc_stdhandle::*;
    use winapi::um::wincon;

    let console_attached = unsafe { wincon::AttachConsole(wincon::ATTACH_PARENT_PROCESS) };
    if console_attached == 0 {
        return;
    }

    let conin = CString::new("CONIN$").unwrap();
    let conout = CString::new("CONOUT$").unwrap();
    let r = CString::new("r").unwrap();
    let w = CString::new("w").unwrap();

    // Python uses the CRT for I/O, and it requires the descriptors are reopened.
    unsafe {
        libc::freopen(conin.as_ptr(), r.as_ptr(), stdin());
        libc::freopen(conout.as_ptr(), w.as_ptr(), stdout());
        libc::freopen(conout.as_ptr(), w.as_ptr(), stderr());
    }

    config.show_console = true;
}

pub fn get_anki_binary_path(uv_install_root: &std::path::Path) -> std::path::PathBuf {
    uv_install_root.join(".venv/Scripts/anki.exe")
}

fn build_python_command(
    anki_bin: &std::path::Path,
    args: &[String],
    config: &Config,
) -> Result<Command> {
    let venv_dir = anki_bin
        .parent()
        .context("Failed to get venv Scripts directory")?
        .parent()
        .context("Failed to get venv directory")?;

    // Use python.exe if show_console is true, otherwise pythonw.exe
    let python_exe = if config.show_console {
        venv_dir.join("Scripts/python.exe")
    } else {
        venv_dir.join("Scripts/pythonw.exe")
    };

    let mut cmd = Command::new(python_exe);
    cmd.args(["-c", "import aqt; aqt.run()"]);
    cmd.args(args);

    Ok(cmd)
}

pub fn launch_anki_detached(anki_bin: &std::path::Path, config: &Config) -> Result<()> {
    use std::os::windows::process::CommandExt;
    use std::process::Stdio;

    const CREATE_NEW_PROCESS_GROUP: u32 = 0x00000200;
    const DETACHED_PROCESS: u32 = 0x00000008;

    let mut cmd = build_python_command(anki_bin, &[], config)?;
    cmd.stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .creation_flags(CREATE_NEW_PROCESS_GROUP | DETACHED_PROCESS)
        .ensure_spawn()?;
    Ok(())
}

pub fn handle_first_launch(_anki_bin: &std::path::Path) -> Result<()> {
    Ok(())
}

pub fn exec_anki(anki_bin: &std::path::Path, config: &Config) -> Result<()> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut cmd = build_python_command(anki_bin, &args, config)?;
    cmd.ensure_success()?;
    Ok(())
}

pub fn get_exe_and_resources_dirs() -> Result<(PathBuf, PathBuf)> {
    let exe_dir = std::env::current_exe()
        .context("Failed to get current executable path")?
        .parent()
        .context("Failed to get executable directory")?
        .to_owned();

    // On Windows, resources dir is the same as exe_dir
    let resources_dir = exe_dir.clone();

    Ok((exe_dir, resources_dir))
}

pub fn get_uv_binary_name() -> &'static str {
    // Windows uses standard uv binary name
    "uv.exe"
}
