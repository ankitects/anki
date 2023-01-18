// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::process::Command;

use anyhow::bail;
use anyhow::Result;
use camino::Utf8Path;
use camino::Utf8PathBuf;

const CODESIGN_ARGS: &[&str] = &["-vvvv", "-o", "runtime", "-s", "Developer ID Application:"];

pub fn codesign_python_libs(bundle_dir: &Utf8PathBuf) -> Result<()> {
    for entry in glob::glob(bundle_dir.join("Contents/MacOS/lib/**/*.so").as_str())? {
        let entry = entry?;
        let entry = Utf8PathBuf::from_path_buf(entry).unwrap();
        codesign_file(&entry, &[])?;
    }
    codesign_file(&bundle_dir.join("Contents/MacOS/libankihelper.dylib"), &[])
}

pub fn codesign_app(bundle_dir: &Utf8PathBuf) -> Result<()> {
    codesign_file(
        bundle_dir,
        &["--entitlements", "qt/bundle/mac/entitlements.python.xml"],
    )
}

fn codesign_file(path: &Utf8Path, extra_args: &[&str]) -> Result<()> {
    if env::var("ANKI_CODESIGN").is_ok() {
        let status = Command::new("codesign")
            .args(CODESIGN_ARGS)
            .args(extra_args)
            .arg(path.as_str())
            .status()?;
        if !status.success() {
            bail!("codesign failed");
        }
    }

    Ok(())
}
