// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;
use std::process::Command;

use anki_process::CommandExt as AnkiCommandExt;
use anki_proto::launcher::uninstall_response::ActionNeeded;
use anyhow::Result;

pub fn prepare_for_launch_after_update(mut cmd: Command, root: &Path) -> Result<()> {
    // Pre-validate by running --version to trigger any Gatekeeper checks

    let _ = cmd
        .env("ANKI_FIRST_RUN", "1")
        .arg("--version")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .ensure_success();

    if cfg!(target_os = "macos") {
        // older Anki versions had a short mpv timeout and didn't support
        // ANKI_FIRST_RUN, so we need to ensure mpv passes Gatekeeper
        // validation prior to launch
        let mpv_path = root.join(".venv/lib/python3.9/site-packages/anki_audio/mpv");
        if mpv_path.exists() {
            let _ = Command::new(&mpv_path)
                .arg("--version")
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .ensure_success();
        }
    }

    Ok(())
}

pub fn finalize_uninstall() -> Result<Option<ActionNeeded>> {
    if let Ok(exe_path) = std::env::current_exe() {
        // Find the .app bundle by walking up the directory tree
        let mut app_bundle_path = exe_path.as_path();
        while let Some(parent) = app_bundle_path.parent() {
            if let Some(name) = parent.file_name() {
                if name.to_string_lossy().ends_with(".app") {
                    let result = Command::new("trash").arg(parent).output();

                    return Ok(match result {
                        Ok(output) if output.status.success() => None,
                        _ => {
                            // Fall back to manual instructions
                            Some(ActionNeeded::MacManual(()))
                        }
                    });
                }
            }
            app_bundle_path = parent;
        }
    }
    Ok(None)
}
