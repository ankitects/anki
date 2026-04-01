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

        if last_author == "49699333+dependabot[bot]@users.noreply.github.com" {
            println!("Dependabot whitelisted.");
            std::process::exit(0);
        }

        if let Ok(bypass) = std::env::var("CONTRIBUTORS_BYPASS_EMAILS") {
            if bypass.split(',').any(|e| e.trim() == last_author) {
                println!("Author allowlisted via CONTRIBUTORS_BYPASS_EMAILS.");
                return Ok(());
            }
        }

        let contents = fs::read_to_string("CONTRIBUTORS")?;

        if contributor_email_matches(&last_author, &contents) {
            return Ok(());
        }

        if let Ok(login) = std::env::var("PR_AUTHOR_LOGIN") {
            let login = login.trim();
            if github_login_matches(login, &contents) {
                println!("Author matched via PR_AUTHOR_LOGIN ({login}).");
                return Ok(());
            }
        }

        let all_contributors: HashSet<&str> = parse_contributor_identifiers(&contents);
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
    // Used by `fix:minilints` locally. CI uses EmbarkStudios/cargo-deny-action.
    Command::run("cargo install cargo-deny@0.19.0")?;
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

/// Parse the `<identifier>` part from each CONTRIBUTORS entry.
/// Entries may use an email address or a GitHub profile URL.
fn parse_contributor_identifiers(contents: &str) -> HashSet<&str> {
    contents
        .lines()
        .filter_map(|line| {
            let start = line.find('<')?;
            let end = line.find('>')?;
            Some(&line[start + 1..end])
        })
        .collect()
}

/// Return true when `email` belongs to a listed contributor.
///
/// Recognised patterns in CONTRIBUTORS:
/// - Direct email: `user@example.com`
/// - GitHub noreply with numeric ID: `12345+user@users.noreply.github.com`
/// - GitHub noreply without ID:       `user@users.noreply.github.com`
///
/// All three are matched against entries of the forms:
///   `github.com/user`, `https://github.com/user`, `http://github.com/user`
/// (trailing slash is ignored, comparison is case-insensitive).
fn contributor_email_matches(email: &str, contributors_content: &str) -> bool {
    let all_contributors = parse_contributor_identifiers(contributors_content);

    // Direct match (plain email or exact noreply address stored in file).
    if all_contributors.contains(email) {
        return true;
    }

    // Match GitHub noreply emails against `github.com/<user>` entries.
    // Handles both `ID+user@users.noreply.github.com` and
    // `user@users.noreply.github.com` (commits made via the GitHub UI).
    if let Some(stripped) = email.strip_suffix("@users.noreply.github.com") {
        // If there's a `+`, the part after it is the username; otherwise the
        // whole prefix is the username.
        let username = stripped.rsplit_once('+').map_or(stripped, |(_, u)| u);
        let gh_entry = format!("github.com/{username}");
        if all_contributors.iter().any(|c| {
            let normalized = c
                .trim_end_matches('/')
                .trim_start_matches("https://")
                .trim_start_matches("http://");
            normalized.eq_ignore_ascii_case(&gh_entry)
        }) {
            return true;
        }
    }

    false
}

/// Return true when `login` (a GitHub username) matches a CONTRIBUTORS entry
/// of the form `github.com/<login>` or `https://github.com/<login>`.
fn github_login_matches(login: &str, contributors_content: &str) -> bool {
    let all_contributors = parse_contributor_identifiers(contributors_content);
    let gh_entry = format!("github.com/{login}");
    all_contributors.iter().any(|c| {
        let normalized = c
            .trim_end_matches('/')
            .trim_start_matches("https://")
            .trim_start_matches("http://");
        normalized.eq_ignore_ascii_case(&gh_entry)
    })
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

#[cfg(test)]
mod tests {
    use super::*;

    fn make_contributors(entries: &[&str]) -> String {
        entries
            .iter()
            .map(|e| format!("Some Name <{e}>\n"))
            .collect()
    }

    // --- direct email matches ---

    #[test]
    fn direct_email_match() {
        let c = make_contributors(&["spaghetti-monster@example.com"]);
        assert!(contributor_email_matches("spaghetti-monster@example.com", &c));
    }

    #[test]
    fn direct_email_no_match() {
        let c = make_contributors(&["spaghetti-monster@example.com"]);
        assert!(!contributor_email_matches("pippin@example.com", &c));
    }

    // --- noreply emails stored verbatim ---

    #[test]
    fn verbatim_noreply_email() {
        let c = make_contributors(&["424242+spaghetti-monster@users.noreply.github.com"]);
        assert!(contributor_email_matches(
            "424242+spaghetti-monster@users.noreply.github.com",
            &c
        ));
    }

    // --- github.com/user entries ---

    #[test]
    fn github_url_plain() {
        let c = make_contributors(&["github.com/pippin"]);
        assert!(contributor_email_matches(
            "424242+pippin@users.noreply.github.com",
            &c
        ));
    }

    #[test]
    fn github_url_https() {
        let c = make_contributors(&["https://github.com/pippin"]);
        assert!(contributor_email_matches(
            "424242+pippin@users.noreply.github.com",
            &c
        ));
    }

    #[test]
    fn github_url_https_trailing_slash() {
        let c = make_contributors(&["https://github.com/pippin/"]);
        assert!(contributor_email_matches(
            "424242+pippin@users.noreply.github.com",
            &c
        ));
    }

    #[test]
    fn github_url_http() {
        let c = make_contributors(&["http://github.com/pippin"]);
        assert!(contributor_email_matches(
            "424242+pippin@users.noreply.github.com",
            &c
        ));
    }

    #[test]
    fn github_url_case_insensitive() {
        let c = make_contributors(&["https://github.com/Pippin"]);
        assert!(contributor_email_matches(
            "424242+pippin@users.noreply.github.com",
            &c
        ));
    }

    // --- noreply without numeric ID (commits made through GitHub UI) ---

    #[test]
    fn noreply_without_id_prefix_github_url() {
        // GitHub UI commits sometimes produce `user@users.noreply.github.com`
        // with no numeric ID prefix.
        let c = make_contributors(&["https://github.com/pippin"]);
        assert!(contributor_email_matches(
            "pippin@users.noreply.github.com",
            &c
        ));
    }

    #[test]
    fn noreply_without_id_prefix_plain_github_url() {
        let c = make_contributors(&["github.com/pippin"]);
        assert!(contributor_email_matches(
            "pippin@users.noreply.github.com",
            &c
        ));
    }

    // --- github_login_matches (PR_AUTHOR_LOGIN fallback) ---

    #[test]
    fn login_matches_https_github_url() {
        let c = make_contributors(&["https://github.com/pippin"]);
        assert!(github_login_matches("pippin", &c));
    }

    #[test]
    fn login_matches_plain_github_url() {
        let c = make_contributors(&["github.com/pippin"]);
        assert!(github_login_matches("pippin", &c));
    }

    #[test]
    fn login_matches_case_insensitive() {
        let c = make_contributors(&["https://github.com/Pippin"]);
        assert!(github_login_matches("pippin", &c));
    }

    #[test]
    fn login_no_match_for_plain_email_entry() {
        // A plain email entry does not satisfy a login lookup.
        let c = make_contributors(&["spaghetti-monster@example.com"]);
        assert!(!github_login_matches("spaghetti-monster", &c));
    }

    #[test]
    fn login_unknown_rejected() {
        let c = make_contributors(&["https://github.com/pippin"]);
        assert!(!github_login_matches("spaghetti-monster", &c));
    }

    // --- unknown author ---

    #[test]
    fn unknown_author_rejected() {
        let c = make_contributors(&[
            "spaghetti-monster@example.com",
            "github.com/spaghetti-monster",
        ]);
        assert!(!contributor_email_matches("pippin@example.com", &c));
        assert!(!contributor_email_matches(
            "99+pippin@users.noreply.github.com",
            &c
        ));
    }

    // --- parse_contributor_identifiers edge cases ---

    #[test]
    fn entries_without_angle_brackets_ignored() {
        // Entries like "Ijgnd" or "jariji" have no <>, so they contribute
        // nothing to the identifier set (and can't be matched).
        let c = "Spaghetti Monster\nPippin\n";
        assert!(parse_contributor_identifiers(c).is_empty());
    }

    #[test]
    fn multiple_entries_parsed() {
        let c = make_contributors(&[
            "a@example.com",
            "github.com/b",
            "https://github.com/c",
        ]);
        let ids = parse_contributor_identifiers(&c);
        assert_eq!(ids.len(), 3);
        assert!(ids.contains("a@example.com"));
        assert!(ids.contains("github.com/b"));
        assert!(ids.contains("https://github.com/c"));
    }
}
