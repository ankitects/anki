// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::ffi::OsStr;
use std::io::stdin;
use std::os::windows::ffi::OsStrExt;
use std::process::Command;

use anyhow::Context;
use anyhow::Result;
use winapi::shared::minwindef::HKEY;
use winapi::um::wincon;
use winapi::um::winnt::KEY_READ;
use winapi::um::winreg;

pub fn ensure_terminal_shown() -> Result<()> {
    unsafe {
        if !wincon::GetConsoleWindow().is_null() {
            // We already have a console, no need to spawn anki-console.exe
            return Ok(());
        }
    }

    if std::env::var("ANKI_IMPLICIT_CONSOLE").is_ok() && attach_to_parent_console() {
        // Successfully attached to parent console
        reconnect_stdio_to_console();
        return Ok(());
    }

    // No console available, spawn anki-console.exe and exit
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;
    let exe_dir = current_exe
        .parent()
        .context("Failed to get executable directory")?;

    let console_exe = exe_dir.join("anki-console.exe");

    if !console_exe.exists() {
        anyhow::bail!("anki-console.exe not found in the same directory");
    }

    // Spawn anki-console.exe without waiting
    Command::new(&console_exe)
        .env("ANKI_IMPLICIT_CONSOLE", "1")
        .spawn()
        .context("Failed to spawn anki-console.exe")?;

    // Exit immediately after spawning
    std::process::exit(0);
}

pub fn attach_to_parent_console() -> bool {
    unsafe {
        if !wincon::GetConsoleWindow().is_null() {
            // we have a console already
            return false;
        }

        if wincon::AttachConsole(wincon::ATTACH_PARENT_PROCESS) != 0 {
            // successfully attached to parent
            reconnect_stdio_to_console();
            true
        } else {
            false
        }
    }
}

/// Reconnect stdin/stdout/stderr to the console.
fn reconnect_stdio_to_console() {
    use std::ffi::CString;

    use libc_stdhandle::*;

    // we launched without a console, so we'll need to open stdin/out/err
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
}

pub fn finalize_uninstall() {
    let uninstaller_path = get_uninstaller_path();

    match uninstaller_path {
        Some(path) => {
            println!("Launching Windows uninstaller...");
            let result = Command::new(&path).env("ANKI_LAUNCHER", "1").spawn();

            match result {
                Ok(_) => {
                    println!("Uninstaller launched successfully.");
                    return;
                }
                Err(e) => {
                    println!("Failed to launch uninstaller: {e}");
                    println!("You can manually run: {}", path.display());
                }
            }
        }
        None => {
            println!("Windows uninstaller not found.");
            println!("You may need to uninstall via Windows Settings > Apps.");
        }
    }
    println!("Press enter to close...");
    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
}

fn get_uninstaller_path() -> Option<std::path::PathBuf> {
    // Try to read install directory from registry
    if let Some(install_dir) = read_registry_install_dir() {
        let uninstaller = install_dir.join("uninstall.exe");
        if uninstaller.exists() {
            return Some(uninstaller);
        }
    }

    // Fall back to default location
    let default_dir = dirs::data_local_dir()?.join("Programs").join("Anki");
    let uninstaller = default_dir.join("uninstall.exe");
    if uninstaller.exists() {
        return Some(uninstaller);
    }

    None
}

fn read_registry_install_dir() -> Option<std::path::PathBuf> {
    unsafe {
        let mut hkey: HKEY = std::ptr::null_mut();

        // Convert the registry path to wide string
        let subkey: Vec<u16> = OsStr::new("SOFTWARE\\Anki")
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();

        // Open the registry key
        let result = winreg::RegOpenKeyExW(
            winreg::HKEY_CURRENT_USER,
            subkey.as_ptr(),
            0,
            KEY_READ,
            &mut hkey,
        );

        if result != 0 {
            return None;
        }

        // Query the Install_Dir64 value
        let value_name: Vec<u16> = OsStr::new("Install_Dir64")
            .encode_wide()
            .chain(std::iter::once(0))
            .collect();

        let mut value_type = 0u32;
        let mut data_size = 0u32;

        // First call to get the size
        let result = winreg::RegQueryValueExW(
            hkey,
            value_name.as_ptr(),
            std::ptr::null_mut(),
            &mut value_type,
            std::ptr::null_mut(),
            &mut data_size,
        );

        if result != 0 || data_size == 0 {
            winreg::RegCloseKey(hkey);
            return None;
        }

        // Allocate buffer and read the value
        let mut buffer: Vec<u16> = vec![0; (data_size / 2) as usize];
        let result = winreg::RegQueryValueExW(
            hkey,
            value_name.as_ptr(),
            std::ptr::null_mut(),
            &mut value_type,
            buffer.as_mut_ptr() as *mut u8,
            &mut data_size,
        );

        winreg::RegCloseKey(hkey);

        if result == 0 {
            // Convert wide string back to PathBuf
            let len = buffer.iter().position(|&x| x == 0).unwrap_or(buffer.len());
            let path_str = String::from_utf16_lossy(&buffer[..len]);
            Some(std::path::PathBuf::from(path_str))
        } else {
            None
        }
    }
}
