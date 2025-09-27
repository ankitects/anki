// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cell::LazyCell;
use std::collections::BTreeMap;
use std::collections::HashSet;
use std::env;
use std::fs;
use std::fs::File;
use std::io::Read;
use std::io::Write;
use std::path::Path;
use std::process::Command;

use anki_io::read_to_string;
use anki_io::write_file;
use anki_process::CommandExt;
use anyhow::Context;
use anyhow::Result;
use camino::Utf8Path;
use walkdir::WalkDir;

const NONSTANDARD_HEADER: &[&str] = &[
    "./pylib/anki/_vendor/stringcase.py",
    "./pylib/anki/statsbg.py",
    "./qt/aqt/mpv.py",
    "./qt/aqt/winpaths.py",
];

const IGNORED_FOLDERS: &[&str] = &[
    "./out",
    "./node_modules",
    "./qt/aqt/forms",
    "./tools/workspace-hack",
    "./target",
    ".mypy_cache",
    "./extra",
    "./ts/.svelte-kit",
];

fn main() -> Result<()> {
    let mut args = env::args();
    let want_fix = args.nth(1) == Some("fix".to_string());
    let stamp = args.next().unwrap();
    let mut ctx = LintContext::new(want_fix);
    ctx.check_contributors()?;
    ctx.check_rust_licenses()?;
    ctx.walk_folders(Path::new("."))?;
    if ctx.found_problems {
        std::process::exit(1);
    }
    write_file(stamp, "")?;

    Ok(())
}

struct LintContext {
    want_fix: bool,
    unstaged_changes: LazyCell<()>,
    found_problems: bool,
    nonstandard_headers: HashSet<&'static Utf8Path>,
}

impl LintContext {
    pub fn new(want_fix: bool) -> Self {
        Self {
            want_fix,
            unstaged_changes: LazyCell::new(check_for_unstaged_changes),
            found_problems: false,
            nonstandard_headers: NONSTANDARD_HEADER.iter().map(Utf8Path::new).collect(),
        }
    }

    pub fn walk_folders(&mut self, root: &Path) -> Result<()> {
        let ignored_folders: HashSet<_> = IGNORED_FOLDERS.iter().map(Utf8Path::new).collect();
        let walker = WalkDir::new(root).into_iter();
        for entry in walker.filter_entry(|e| {
            !ignored_folders.contains(&Utf8Path::from_path(e.path()).expect("utf8"))
        }) {
            let entry = entry.unwrap();
            let path = Utf8Path::from_path(entry.path()).context("utf8")?;

            let exts: HashSet<_> = ["py", "ts", "rs", "svelte", "mjs"]
                .into_iter()
                .map(Some)
                .collect();
            if exts.contains(&path.extension()) && !sveltekit_temp_file(path.as_str()) {
                self.check_copyright(path)?;
                self.check_triple_slash(path)?;
            }
        }
        Ok(())
    }

    fn check_copyright(&mut self, path: &Utf8Path) -> Result<()> {
        if path.file_name().unwrap().ends_with(".d.ts") {
            return Ok(());
        }
        let head = head_of_file(path)?;
        if head.is_empty() {
            return Ok(());
        }
        if self.nonstandard_headers.contains(&path) {
            return Ok(());
        }
        let missing = !head.contains("Ankitects Pty Ltd and contributors");
        if missing {
            if self.want_fix {
                LazyCell::force(&self.unstaged_changes);
                fix_copyright(path)?;
            } else {
                println!("missing standard copyright header: {path:?}");
                self.found_problems = true;
            }
        }
        Ok(())
    }

    fn check_triple_slash(&mut self, path: &Utf8Path) -> Result<()> {
        if !matches!(path.extension(), Some("ts") | Some("svelte")) {
            return Ok(());
        }
        for line in fs::read_to_string(path)?.lines() {
            if line.contains("///") && !line.contains("/// <reference") {
                println!("not a docstring: {path}: {line}");
                self.found_problems = true;
            }
        }
        Ok(())
    }

    fn check_contributors(&self) -> Result<()> {
        let antispam = ", at the domain ";

        let last_author = String::from_utf8(
            Command::new("git")
                .args(["log", "-1", "--pretty=format:%ae"])
                .output()?
                .stdout,
        )?;

        let all_contributors = String::from_utf8(
            Command::new("git")
                .args(["log", "--pretty=format:%ae", "CONTRIBUTORS"])
                .output()?
                .stdout,
        )?;
        let all_contributors = all_contributors.lines().collect::<HashSet<&str>>();

        if last_author == "49699333+dependabot[bot]@users.noreply.github.com" {
            println!("Dependabot whitelisted.");
            std::process::exit(0);
        } else if all_contributors.contains(last_author.as_str()) {
            return Ok(());
        }

        println!("All contributors:");
        println!("{}", {
            let mut contribs: Vec<_> = all_contributors
                .iter()
                .map(|s| s.replace('@', antispam))
                .collect();
            contribs.sort();
            contribs.join("\n")
        });

        println!(
            "Author {} NOT found in list",
            last_author.replace('@', antispam)
        );

        println!(
            "\nPlease make sure you modify the CONTRIBUTORS file using the email address you \
                are committing from. If you have GitHub configured to hide your email address, \
                you may need to make a change to the CONTRIBUTORS file using the GitHub UI, \
                then try again."
        );

        std::process::exit(1);
    }

    fn check_rust_licenses(&mut self) -> Result<()> {
        let license_path = Path::new("cargo/licenses.json");
        let licenses = generate_licences()?;
        let existing_licenses = read_to_string(license_path)?;
        if licenses != existing_licenses {
            if self.want_fix {
                check_cargo_deny()?;
                write_file(license_path, licenses)?;
            } else {
                println!("cargo/licenses.json is out of date; run ./ninja fix:minilints");
                self.found_problems = true;
            }
        }
        Ok(())
    }
}

/// Annoyingly, sveltekit writes temp files into ts/ folder when it's running.
fn sveltekit_temp_file(path: &str) -> bool {
    path.contains("vite.config.ts.timestamp")
}

fn check_cargo_deny() -> Result<()> {
    Command::run("cargo install cargo-deny@0.18.3")?;
    Command::run("cargo deny check")?;
    Ok(())
}

fn head_of_file(path: &Utf8Path) -> Result<String> {
    let mut file = File::open(path)?;
    let mut buffer = vec![0; 256];
    let size = file.read(&mut buffer)?;
    buffer.truncate(size);
    Ok(String::from_utf8(buffer).unwrap_or_default())
}

fn fix_copyright(path: &Utf8Path) -> Result<()> {
    let header = match path.extension().unwrap() {
        "py" => {
            r#"# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"#
        }
        "ts" | "rs" | "mjs" => {
            r#"// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"#
        }
        "svelte" => {
            r#"<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
"#
        }
        _ => unreachable!(),
    };

    let data = fs::read_to_string(path).with_context(|| format!("reading {path}"))?;
    let mut file = fs::OpenOptions::new()
        .write(true)
        .open(path)
        .with_context(|| format!("opening {path}"))?;
    write!(file, "{header}{data}").with_context(|| format!("writing {path}"))?;
    Ok(())
}

fn check_for_unstaged_changes() {
    let output = Command::new("git").arg("diff").output().unwrap();
    if !output.stdout.is_empty() {
        println!("stage any changes first");
        std::process::exit(1);
    }
}

fn generate_licences() -> Result<String> {
    Command::run("cargo install cargo-license@0.7.0")?;
    let output = Command::run_with_output([
        "cargo-license",
        "--features",
        "rustls",
        "--features",
        "native-tls",
        "--json",
        "--manifest-path",
        "rslib/Cargo.toml",
    ])?;

    let licenses: Vec<BTreeMap<String, serde_json::Value>> = serde_json::from_str(&output.stdout)?;

    let filtered: Vec<BTreeMap<String, serde_json::Value>> = licenses
        .into_iter()
        .map(|mut entry| {
            entry.remove("version");
            entry
        })
        .collect();

    Ok(serde_json::to_string_pretty(&filtered)?)
}
