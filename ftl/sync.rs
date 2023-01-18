// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! A helper script to update commit references to the latest translations,
//! and copy source files to the translation repos. Requires access to the
//! i18n repos to run.

use std::process::Command;

use camino::Utf8Path;
use snafu::prelude::*;
use snafu::Whatever;

type Result<T> = std::result::Result<T, Whatever>;

#[derive(Debug)]
struct Module {
    template_folder: &'static Utf8Path,
    translation_repo: &'static Utf8Path,
}

/// Our ftl submodules are checked out over unauthenticated https; a separate
/// remote is used to push via authenticated ssh.
const GIT_REMOTE: &str = "ssh";

#[snafu::report]
fn main() -> Result<()> {
    let modules = [
        Module {
            template_folder: "ftl/core".into(),
            translation_repo: "ftl/core-repo/core".into(),
        },
        Module {
            template_folder: "ftl/qt".into(),
            translation_repo: "ftl/qt-repo/desktop".into(),
        },
    ];

    check_clean()?;
    for module in modules {
        fetch_new_translations(&module)?;
        push_new_templates(&module)?;
    }
    commit(".", "Update translations")
        .whatever_context("failure expected if no translations changed")?;
    Ok(())
}

fn check_clean() -> Result<()> {
    let output = Command::new("git")
        .arg("diff")
        .output()
        .whatever_context("git diff")?;
    ensure_whatever!(output.status.success(), "git diff");
    ensure_whatever!(
        output.stdout.is_empty(),
        "please commit any outstanding changes first"
    );
    Ok(())
}

fn run(command: &mut Command) -> Result<()> {
    let status = command
        .status()
        .with_whatever_context(|_| format!("{:?}", command))?;
    if !status.success() {
        whatever!("{:?} exited with code: {:?}", command, status.code());
    }
    Ok(())
}

fn fetch_new_translations(module: &Module) -> Result<()> {
    run(Command::new("git")
        .current_dir(module.translation_repo)
        .args(["checkout", "main"]))?;
    run(Command::new("git")
        .current_dir(module.translation_repo)
        .args(["pull", "origin", "main"]))?;
    Ok(())
}

fn push_new_templates(module: &Module) -> Result<()> {
    run(Command::new("rsync")
        .args(["-ai", "--delete", "--no-perms", "--no-times", "-c"])
        .args([
            format!("{}/", module.template_folder),
            format!("{}/", module.translation_repo.join("templates")),
        ]))?;
    let changes_pending = !Command::new("git")
        .current_dir(module.translation_repo)
        .args(["diff", "--exit-code"])
        .status()
        .whatever_context("git")?
        .success();
    if changes_pending {
        commit(module.translation_repo, "Update templates")?;
        push(module.translation_repo)?;
    }
    Ok(())
}

fn push(repo: &Utf8Path) -> Result<()> {
    run(Command::new("git")
        .current_dir(repo)
        .args(["push", GIT_REMOTE, "main"]))?;
    // ensure origin matches ssh remote
    run(Command::new("git").current_dir(repo).args(["fetch"]))?;
    Ok(())
}

fn commit<F>(folder: F, message: &str) -> Result<()>
where
    F: AsRef<str>,
{
    run(Command::new("git")
        .current_dir(folder.as_ref())
        .args(["commit", "-a", "-m", message]))?;
    Ok(())
}
