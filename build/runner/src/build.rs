// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fs;
use std::io::Write;
use std::process::Command;
use std::time::Instant;

use anki_process::CommandExt;
use anyhow::Context;
use camino::Utf8Path;
use camino::Utf8PathBuf;
use clap::Args;
use termcolor::Color;
use termcolor::ColorChoice;
use termcolor::ColorSpec;
use termcolor::StandardStream;
use termcolor::WriteColor;

#[derive(Args)]
pub struct BuildArgs {
    #[arg(trailing_var_arg = true)]
    args: Vec<String>,
}

pub fn run_build(args: BuildArgs) {
    let build_root = &setup_build_root();

    let path = if cfg!(windows) {
        format!(
            "out\\bin;out\\extracted\\node;node_modules\\.bin;{};\\msys64\\usr\\bin",
            env::var("PATH").unwrap()
        )
    } else {
        format!(
            "{br}/bin:{br}/extracted/node/bin:{path}",
            br = build_root
                .canonicalize_utf8()
                .expect("resolving build root")
                .as_str(),
            path = env::var("PATH").unwrap()
        )
    };

    maybe_update_env_file(build_root);
    maybe_update_buildhash(build_root);

    // Ensure build file is up to date
    let build_file = build_root.join("build.ninja");
    if !build_file.exists() {
        bootstrap_build();
    }

    // automatically convert foo:bar references to foo_bar, as Ninja can not
    // represent the former
    let ninja_args = args.args.into_iter().map(|a| a.replace(':', "_"));

    let start_time = Instant::now();
    let mut command = Command::new(get_ninja_command());
    command
        .arg("-f")
        .arg(&build_file)
        .args(ninja_args)
        .env("PATH", &path)
        .env(
            "MYPY_CACHE_DIR",
            build_root.join("tests").join("mypy").into_string(),
        )
        .env(
            "PYTHONPYCACHEPREFIX",
            std::path::absolute(build_root.join("pycache")).unwrap(),
        )
        // commands will not show colors by default, as we do not provide a tty
        .env("FORCE_COLOR", "1")
        .env("MYPY_FORCE_COLOR", "1")
        .env("TERM", std::env::var("TERM").unwrap_or_default());
    if env::var("NINJA_STATUS").is_err() {
        command.env("NINJA_STATUS", "[%f/%t; %r active; %es] ");
    }

    // run build
    let Ok(mut status) = command.status() else {
        panic!("\nn2 and ninja missing/failed. did you forget 'bash tools/install-n2'?");
    };
    if !status.success() && Instant::now().duration_since(start_time).as_secs() < 3 {
        // if the build fails quickly, there's a reasonable chance that build.ninja
        // references a file that has been renamed/deleted. We currently don't
        // capture stderr, so we can't confirm, but in case that's the case, we
        // regenerate the build.ninja file then try again.
        bootstrap_build();
        status = command.status().expect("ninja missing");
    }
    let mut stdout = StandardStream::stdout(ColorChoice::Always);
    if status.success() {
        stdout
            .set_color(ColorSpec::new().set_fg(Some(Color::Green)).set_bold(true))
            .unwrap();
        writeln!(
            &mut stdout,
            "\nBuild succeeded in {:.2}s.",
            start_time.elapsed().as_secs_f32()
        )
        .unwrap();
        stdout.reset().unwrap();
    } else {
        stdout
            .set_color(ColorSpec::new().set_fg(Some(Color::Red)).set_bold(true))
            .unwrap();
        writeln!(&mut stdout, "\nBuild failed.").unwrap();
        stdout.reset().unwrap();

        std::process::exit(1);
    }
}

fn get_ninja_command() -> &'static str {
    if which::which("n2").is_ok() {
        "n2"
    } else {
        "ninja"
    }
}

fn setup_build_root() -> Utf8PathBuf {
    let build_root = Utf8Path::new("out");

    #[cfg(unix)]
    if let Ok(new_target) = env::var("BUILD_ROOT").map(camino::Utf8PathBuf::from) {
        let create = if let Ok(existing_target) = build_root.read_link_utf8() {
            if existing_target != new_target {
                fs::remove_file(build_root).unwrap();
                true
            } else {
                false
            }
        } else {
            true
        };
        if create {
            println!("Switching build root to {new_target}");
            std::os::unix::fs::symlink(new_target, build_root).unwrap();
        }
    }

    fs::create_dir_all(build_root).unwrap();
    if cfg!(windows) {
        build_root.to_owned()
    } else {
        build_root.canonicalize_utf8().unwrap()
    }
}

fn bootstrap_build() {
    let status = Command::new("cargo")
        .args(["run", "-p", "configure"])
        .status();
    assert!(status.expect("ninja").success());
}

fn maybe_update_buildhash(build_root: &Utf8Path) {
    // only updated on release builds
    let path = build_root.join("buildhash");
    if (env::var("RELEASE").is_ok() && env::var("OFFLINE_BUILD").is_err()) || !path.exists() {
        write_if_changed(&path, &get_buildhash())
    }
}

fn get_buildhash() -> String {
    let output = Command::new("git")
        .args(["rev-parse", "--short=8", "HEAD"])
        .utf8_output()
        .context(
            "Make sure you're building from a clone of the git repo, and that 'git' is installed.",
        )
        .unwrap();
    output.stdout.trim().into()
}

fn write_if_changed(path: &Utf8Path, contents: &str) {
    if let Ok(old_contents) = fs::read_to_string(path) {
        if old_contents == contents {
            return;
        }
    }
    fs::write(path, contents).unwrap();
}

/// Trigger reconfigure when our env vars change
fn maybe_update_env_file(build_root: &Utf8Path) {
    let env_file = build_root.join("env");
    let build_root_env = env::var("BUILD_ROOT").unwrap_or_default();
    let release = env::var("RELEASE").unwrap_or_default();
    let other_watched_env = env::var("RECONFIGURE_KEY").unwrap_or_default();
    let current_env = format!("{build_root_env};{release};{other_watched_env}");

    write_if_changed(&env_file, &current_env);
}
