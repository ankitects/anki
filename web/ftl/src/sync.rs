// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::process::Command;

use anki_process::CommandExt;
use anyhow::bail;
use anyhow::Context;
use anyhow::Result;
use camino::Utf8Path;

#[derive(Debug)]
struct Module {
    template_folder: &'static Utf8Path,
    translation_repo: &'static Utf8Path,
}

/// Our ftl submodules are checked out over unauthenticated https; a separate
/// remote is used to push via authenticated ssh.
const GIT_REMOTE: &str = "ssh";

pub fn sync() -> Result<()> {
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
    commit(".", "Update translations").context("failure expected if no translations changed")?;
    Ok(())
}

fn check_clean() -> Result<()> {
    let output = Command::new("git")
        .arg("diff")
        .output()
        .context("git diff")?;
    if !output.status.success() {
        bail!("git diff");
    }
    if !output.stdout.is_empty() {
        bail!("please commit any outstanding changes first");
    }
    Ok(())
}

fn fetch_new_translations(module: &Module) -> Result<()> {
    Command::new("git")
        .current_dir(module.translation_repo)
        .args(["checkout", "main"])
        .ensure_success()?;
    Command::new("git")
        .current_dir(module.translation_repo)
        .args(["pull", "origin", "main"])
        .ensure_success()?;
    Ok(())
}

fn push_new_templates(module: &Module) -> Result<()> {
    Command::new("rsync")
        .args(["-ai", "--delete", "--no-perms", "--no-times", "-c"])
        .args([
            format!("{}/", module.template_folder),
            format!("{}/", module.translation_repo.join("templates")),
        ])
        .ensure_success()?;
    let changes_pending = !Command::new("git")
        .current_dir(module.translation_repo)
        .args(["diff", "--exit-code"])
        .status()
        .context("git")?
        .success();
    if changes_pending {
        commit(module.translation_repo, "Update templates")?;
        push(module.translation_repo)?;
    }
    Ok(())
}

fn push(repo: &Utf8Path) -> Result<()> {
    Command::new("git")
        .current_dir(repo)
        .args(["push", GIT_REMOTE, "main"])
        .ensure_success()?;
    // ensure origin matches ssh remote
    Command::new("git")
        .current_dir(repo)
        .args(["fetch"])
        .ensure_success()?;
    Ok(())
}

fn commit<F>(folder: F, message: &str) -> Result<()>
where
    F: AsRef<str>,
{
    Command::new("git")
        .current_dir(folder.as_ref())
        .args(["commit", "-a", "-m", message])
        .ensure_success()?;
    Ok(())
}
