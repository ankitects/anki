use std::os::unix::process::CommandExt;
use std::process::Command;
use std::process::Stdio;

fn main() {
    let Some(uv_install_root) =
        dirs::data_local_dir().map(|data_dir| data_dir.join("AnkiProgramFiles"))
    else {
        println!("Unable to determine data_dir");
        std::process::exit(1);
    };

    let sync_complete_marker = uv_install_root.join(".sync_complete");
    let exe_dir = std::env::current_exe()
        .unwrap()
        .parent()
        .unwrap()
        .to_owned();
    let resources_dir = exe_dir.parent().unwrap().join("Resources");
    let dist_pyproject_path = resources_dir.join("pyproject.toml");
    let user_pyproject_path = uv_install_root.join("pyproject.toml");

    let pyproject_has_changed =
        !user_pyproject_path.exists() || !sync_complete_marker.exists() || {
            let pyproject_toml_time = std::fs::metadata(&user_pyproject_path)
                .unwrap()
                .modified()
                .unwrap();
            let sync_complete_time = std::fs::metadata(&sync_complete_marker)
                .unwrap()
                .modified()
                .unwrap();
            pyproject_toml_time > sync_complete_time
        };

    // we'll need to launch uv; reinvoke ourselves in a terminal so the user can see
    if pyproject_has_changed {
        let stdout_is_terminal = std::io::IsTerminal::is_terminal(&std::io::stdout());
        if stdout_is_terminal {
            print!("\x1B[2J\x1B[H"); // Clear screen and move cursor to top
            println!("\x1B[1mPreparing to start Anki...\x1B[0m\n");
        } else {
            // If launched from GUI, relaunch in Terminal.app
            let current_exe = std::env::current_exe().unwrap();
            Command::new("open")
                .args(["-a", "Terminal"])
                .arg(current_exe)
                .spawn()
                .unwrap();
            std::process::exit(0);
        }
    }

    if pyproject_has_changed {
        let uv_path: std::path::PathBuf = exe_dir.join("uv");

        // Create install directory and copy pyproject.toml in if missing
        std::fs::create_dir_all(&uv_install_root).unwrap();
        if !user_pyproject_path.exists() {
            std::fs::copy(&dist_pyproject_path, &user_pyproject_path).unwrap();
        }

        // Remove sync marker before attempting sync
        let _ = std::fs::remove_file(&sync_complete_marker);

        // Sync the venv
        let sync_result = Command::new(&uv_path)
            .current_dir(&uv_install_root)
            .args(["sync"])
            .status()
            .unwrap();

        if !sync_result.success() {
            println!("uv sync failed");
            println!("Press enter to close");
            let mut input = String::new();
            let _ = std::io::stdin().read_line(&mut input);
            std::process::exit(1);
        }

        // Write marker file to indicate successful sync
        std::fs::write(&sync_complete_marker, "").unwrap();
    }

    // invoke anki from the synced venv
    if pyproject_has_changed {
        // Pre-validate by running --version to trigger any Gatekeeper checks
        let anki_bin = uv_install_root.join(".venv/bin/anki");
        println!("\n\x1B[1mThis may take a few minutes. Please wait...\x1B[0m");
        let _ = Command::new(&anki_bin).arg("--version").output();

        // Then launch the binary as detached subprocess so the terminal can close
        let child = Command::new(&anki_bin)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .process_group(0)
            .spawn()
            .unwrap();
        std::mem::forget(child);
        println!("Anki launched successfully");
    } else {
        // If venv already existed, exec as normal
        println!(
            "Anki return code: {:?}",
            Command::new(uv_install_root.join(".venv/bin/anki")).exec()
        );
    }
}
