// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fs;
use std::process::Command;

use anyhow::bail;
use anyhow::Context;
use anyhow::Result;
use camino::Utf8Path;
use serde::Deserialize;

#[derive(Deserialize)]
struct NotarySubmitOutput {
    id: String,
}

pub fn notarize_app(output_folder: &Utf8Path) -> Result<()> {
    if env::var("ANKI_CODESIGN").is_err() {
        return Ok(());
    }
    if env::var("ANKI_NO_NOTARIZE").is_ok() {
        return Ok(());
    }
    let zip_file = output_folder.with_extension("zip");
    assert!(
        Command::new("ditto")
            .args([
                "-c",
                "-k",
                "--keepParent",
                output_folder.as_str(),
                zip_file.as_str(),
            ])
            .status()
            .unwrap()
            .success(),
        "zip build"
    );
    let output = Command::new("xcrun")
        .args([
            "notarytool",
            "submit",
            zip_file.as_str(),
            "-f",
            "json",
            "-p",
            "default",
        ])
        .output()
        .expect("notarytool");
    if !output.status.success() {
        panic!(
            "notarytool submit failed: {} {}",
            String::from_utf8_lossy(&output.stderr),
            String::from_utf8_lossy(&output.stdout)
        )
    }
    let output: NotarySubmitOutput = match serde_json::from_slice(&output.stdout) {
        Ok(out) => out,
        Err(err) => panic!(
            "unable to parse notary output: {err} {} {}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        ),
    };
    let uuid_path = output_folder.with_extension("uuid");
    fs::write(uuid_path, output.id).expect("write uuid");
    Ok(())
}

#[derive(Deserialize)]
struct NotaryWaitOutput {
    status: String,
}

pub fn wait_then_staple_app(app: &Utf8Path, uuid: String) -> Result<()> {
    let output = Command::new("xcrun")
        .args(["notarytool", "wait", &uuid, "-p", "default", "-f", "json"])
        .output()
        .context("notary wait")?;
    let output: NotaryWaitOutput = serde_json::from_slice(&output.stdout)
        .with_context(|| String::from_utf8_lossy(&output.stderr).to_string())?;
    if output.status != "Accepted" {
        bail!("unexpected status: {}", output.status);
    }

    assert!(
        Command::new("xcrun")
            .args(["stapler", "staple", app.as_str()])
            .status()
            .context("staple")?
            .success(),
        "staple"
    );

    // clean up temporary files
    fs::remove_file(app.with_extension("zip")).context("app.zip")?;
    fs::remove_file(app.with_extension("uuid")).context("app.uuid")?;

    Ok(())
}
