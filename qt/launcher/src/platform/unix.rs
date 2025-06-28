// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use anyhow::Context;
use anyhow::Result;

pub fn relaunch_in_terminal() -> Result<()> {
    let current_exe = std::env::current_exe().context("Failed to get current executable path")?;

    // Try terminals in order of preference
    let terminals = [
        ("x-terminal-emulator", vec!["-e"]),
        ("gnome-terminal", vec!["--"]),
        ("konsole", vec!["-e"]),
        ("xfce4-terminal", vec!["-e"]),
        ("alacritty", vec!["-e"]),
        ("kitty", vec![]),
        ("foot", vec![]),
        ("urxvt", vec!["-e"]),
        ("xterm", vec!["-e"]),
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
