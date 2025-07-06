// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use anyhow::Context;
use anyhow::Result;

pub fn relaunch_in_terminal() -> Result<()> {
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;

    // Try terminals in roughly most specific to least specific.
    // First, try commonly used terminals for riced systems.
    // Second, try the minimalist/compatibility terminals.
    // Finally, try terminals usually installed by default.
    let terminals = [
        // commonly used for riced systems
        ("alacritty", vec!["-e"]),
        ("kitty", vec![]),
        // minimalistic terminals for constrained systems
        ("foot", vec![]),
        ("urxvt", vec!["-e"]),
        ("xterm", vec!["-e"]),
        ("x-terminal-emulator", vec!["-e"]),
        // default installs for the most common distros
        ("xfce4-terminal", vec!["-e"]),
        ("gnome-terminal", vec!["--"]),
        ("konsole", vec!["-e"]),
    ];

    for (terminal_cmd, args) in &terminals {
        // Check if terminal exists
        if Command::new("which")
            .arg(terminal_cmd)
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false)
        {
            // Try to launch the terminal
            let mut cmd = Command::new(terminal_cmd);
            if args.is_empty() {
                cmd.arg(&current_exe);
            } else {
                cmd.args(args).arg(&current_exe);
            }

            if cmd.spawn().is_ok() {
                std::process::exit(0);
            }
        }
    }

    // If no terminal worked, continue without relaunching
    Ok(())
}

pub fn finalize_uninstall() {
    use std::io::stdin;
    use std::io::stdout;
    use std::io::Write;

    let uninstall_script = std::path::Path::new("/usr/local/share/anki/uninstall.sh");

    if uninstall_script.exists() {
        println!("To finish uninstalling, run 'sudo /usr/local/share/anki/uninstall.sh'");
    } else {
        println!("Anki has been uninstalled.");
    }
    println!("Press enter to quit.");
    let _ = stdout().flush();
    let mut input = String::new();
    let _ = stdin().read_line(&mut input);
}

pub fn ensure_glibc_supported() -> Result<()> {
    use std::ffi::CStr;
    let get_glibc_version = || -> Option<(u32, u32)> {
        let version_ptr = unsafe { libc::gnu_get_libc_version() };
        if version_ptr.is_null() {
            return None;
        }

        let version_cstr = unsafe { CStr::from_ptr(version_ptr) };
        let version_str = version_cstr.to_str().ok()?;

        // Parse version string (format: "2.36" or "2.36.1")
        let version_parts: Vec<&str> = version_str.split('.').collect();
        if version_parts.len() < 2 {
            return None;
        }

        let major: u32 = version_parts[0].parse().ok()?;
        let minor: u32 = version_parts[1].parse().ok()?;

        Some((major, minor))
    };

    let (major, minor) = get_glibc_version().unwrap_or_default();
    if major < 2 || (major == 2 && minor < 36) {
        anyhow::bail!("Anki requires a modern Linux distro with glibc 2.36 or later.");
    }

    Ok(())
}
