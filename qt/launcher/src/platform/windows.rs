// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::ffi::CString;
use std::io::stdin;
use std::os::windows::ffi::OsStrExt;
use std::process::Command;

use anyhow::anyhow;
use anyhow::Context;
use anyhow::Result;
use widestring::u16cstr;
use windows::core::PCWSTR;
use windows::Wdk::System::SystemServices::RtlGetVersion;
use windows::Win32::Foundation::FreeLibrary;
use windows::Win32::Foundation::HMODULE;
use windows::Win32::System::Console::AttachConsole;
use windows::Win32::System::Console::GetConsoleWindow;
use windows::Win32::System::Console::ATTACH_PARENT_PROCESS;
use windows::Win32::System::LibraryLoader::LoadLibraryExW;
use windows::Win32::System::LibraryLoader::LOAD_LIBRARY_FLAGS;
use windows::Win32::System::Registry::RegCloseKey;
use windows::Win32::System::Registry::RegOpenKeyExW;
use windows::Win32::System::Registry::RegQueryValueExW;
use windows::Win32::System::Registry::HKEY;
use windows::Win32::System::Registry::HKEY_CURRENT_USER;
use windows::Win32::System::Registry::KEY_READ;
use windows::Win32::System::Registry::REG_SZ;
use windows::Win32::System::SystemInformation::OSVERSIONINFOW;
use windows::Win32::UI::Shell::SetCurrentProcessExplicitAppUserModelID;

use crate::platform::PyFfi;

/// Returns true if running on Windows 10 (not Windows 11)
fn is_windows_10() -> bool {
    unsafe {
        let mut info = OSVERSIONINFOW {
            dwOSVersionInfoSize: std::mem::size_of::<OSVERSIONINFOW>() as u32,
            ..Default::default()
        };
        if RtlGetVersion(&mut info).is_ok() {
            // Windows 10 has build numbers < 22000, Windows 11 >= 22000
            info.dwBuildNumber < 22000 && info.dwMajorVersion == 10
        } else {
            false
        }
    }
}

/// Ensures Windows 10 version 1809 or later
pub fn ensure_windows_version_supported() -> Result<()> {
    unsafe {
        let mut info = OSVERSIONINFOW {
            dwOSVersionInfoSize: std::mem::size_of::<OSVERSIONINFOW>() as u32,
            ..Default::default()
        };

        if RtlGetVersion(&mut info).is_err() {
            anyhow::bail!("Failed to get Windows version information");
        }

        if info.dwBuildNumber >= 17763 {
            return Ok(());
        }

        anyhow::bail!("Windows 10 version 1809 or later is required.")
    }
}

pub fn ensure_terminal_shown() -> Result<()> {
    unsafe {
        if !GetConsoleWindow().is_invalid() {
            // We already have a console, no need to spawn anki-console.exe
            return Ok(());
        }
    }

    if std::env::var("ANKI_IMPLICIT_CONSOLE").is_ok() && attach_to_parent_console() {
        // This black magic triggers Windows to switch to the new
        // ANSI-supporting console host, which is usually only available
        // when the app is built with the console subsystem.
        // Only needed on Windows 10, not Windows 11.
        if is_windows_10() {
            let _ = Command::new("cmd").args(["/C", ""]).status();
        }

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
        if !GetConsoleWindow().is_invalid() {
            // we have a console already
            return false;
        }

        if AttachConsole(ATTACH_PARENT_PROCESS).is_ok() {
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
        let mut hkey = HKEY::default();

        // Convert the registry path to wide string
        let subkey = u16cstr!("SOFTWARE\\Anki");

        // Open the registry key
        let result = RegOpenKeyExW(
            HKEY_CURRENT_USER,
            PCWSTR(subkey.as_ptr()),
            Some(0),
            KEY_READ,
            &mut hkey,
        );

        if result.is_err() {
            return None;
        }

        // Query the Install_Dir64 value
        let value_name = u16cstr!("Install_Dir64");

        let mut value_type = REG_SZ;
        let mut data_size = 0u32;

        // First call to get the size
        let result = RegQueryValueExW(
            hkey,
            PCWSTR(value_name.as_ptr()),
            None,
            Some(&mut value_type),
            None,
            Some(&mut data_size),
        );

        if result.is_err() || data_size == 0 {
            let _ = RegCloseKey(hkey);
            return None;
        }

        // Allocate buffer and read the value
        let mut buffer: Vec<u16> = vec![0; (data_size / 2) as usize];
        let result = RegQueryValueExW(
            hkey,
            PCWSTR(value_name.as_ptr()),
            None,
            Some(&mut value_type),
            Some(buffer.as_mut_ptr() as *mut u8),
            Some(&mut data_size),
        );

        let _ = RegCloseKey(hkey);

        if result.is_ok() {
            // Convert wide string back to PathBuf
            let len = buffer.iter().position(|&x| x == 0).unwrap_or(buffer.len());
            let path_str = String::from_utf16_lossy(&buffer[..len]);
            Some(std::path::PathBuf::from(path_str))
        } else {
            None
        }
    }
}

pub fn prepare_to_launch_normally() {
    // Set the App User Model ID for Windows taskbar grouping
    unsafe {
        let _ =
            SetCurrentProcessExplicitAppUserModelID(PCWSTR(u16cstr!("Ankitects.Anki").as_ptr()));
    }

    attach_to_parent_console();
}

impl Drop for PyFfi {
    fn drop(&mut self) {
        unsafe {
            (self.Py_FinalizeEx)();
            let _ = FreeLibrary(HMODULE(self.lib));
        };
    }
}

macro_rules! ffi {
    ($lib:expr, $exec:expr, $($field:ident),* $(,)?) => {
        #[allow(clippy::missing_transmute_annotations)]
        $crate::platform::PyFfi {
            exec: $exec,
            $($field: {
                let sym = ::std::ffi::CString::new(stringify!($field)).map_err(|_| ::anyhow::anyhow!("failed to construct sym"))?;
                ::std::mem::transmute(
                    ::windows::Win32::System::LibraryLoader::GetProcAddress(
                        $lib,
                        ::windows::core::PCSTR::from_raw(sym.as_ptr().cast()),
                    )
                    .ok_or_else(|| anyhow!("failed to load {}", stringify!($field)))?,
                )
            },)*
            lib: $lib.0, }
    };
}

impl PyFfi {
    #[allow(non_snake_case)]
    pub fn load(path: impl AsRef<std::path::Path>, exec: CString) -> Result<Self> {
        unsafe {
            let wide_filename: Vec<u16> = path
                .as_ref()
                .as_os_str()
                .encode_wide()
                .chain(Some(0))
                .collect();

            let lib = LoadLibraryExW(
                PCWSTR::from_raw(wide_filename.as_ptr()),
                None,
                LOAD_LIBRARY_FLAGS::default(),
            )?;

            Ok(ffi! {
                lib,
                exec,
                Py_IsInitialized,
                PyRun_SimpleString,
                Py_FinalizeEx,
                PyConfig_InitPythonConfig,
                PyConfig_SetBytesString,
                Py_InitializeFromConfig,
                PyConfig_SetBytesArgv,
                PyStatus_Exception,
            })
        }
    }
}
