// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::process::Command;

use anyhow::Context;
use anyhow::Result;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Args;

use crate::notarize::wait_then_staple_app;

#[derive(Args)]
pub struct BuildDmgsArgs {
    qt6_dmg: Utf8PathBuf,
    qt5_dmg: Option<Utf8PathBuf>,
}

pub fn make_dmgs(args: BuildDmgsArgs) -> Result<()> {
    let root = Utf8Path::new("out/bundle/app");
    let mut variants = vec![("std", args.qt6_dmg)];
    if let Some(alt) = args.qt5_dmg {
        variants.push(("alt", alt));
    }

    for (variant, dmg) in variants {
        let app = root.join(variant).join("Anki.app");
        if std::env::var("ANKI_CODESIGN").is_ok() {
            let uuid = fs::read_to_string(app.with_extension("uuid")).context("read uuid")?;
            wait_then_staple_app(&app, uuid)?;
        }

        make_dmg(&app, &dmg)?;
    }

    Ok(())
}

fn make_dmg(app_folder: &Utf8Path, dmg: &Utf8Path) -> Result<()> {
    assert!(
        Command::new("qt/bundle/mac/dmg/build.sh")
            .args([app_folder.parent().unwrap().as_str(), dmg.as_str()])
            .status()
            .context("dmg")?
            .success(),
        "dmg"
    );
    Ok(())
}
