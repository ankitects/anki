// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;
use std::fs;
use std::io::Write;
use std::process::Command;
use std::time::Instant;

use camino::Utf8Path;
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
    let build_root = setup_build_root();

    let path = if cfg!(windows) {
        format!(
            "out\\bin;out\\extracted\\node;{};\\msys64\\usr\\bin",
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
    } else {
        maybe_reconfigure_build(&build_file, &path);
    }

    // automatically convert foo:bar references to foo_bar, as Ninja can not
    // represent the former
    let ninja_args = args.args.into_iter().map(|a| a.replace(':', "_"));

    let start_time = Instant::now();
    let mut command = Command::new("ninja");
    command
        .arg("-f")
        .arg(&build_file)
        .args(ninja_args)
        .env("NINJA_STATUS", "[%f/%t; %r active; %es] ")
        .env("PATH", path)
        .env(
            "MYPY_CACHE_DIR",
            build_root.join("tests").join("mypy").into_string(),
        )
        .env("PYTHONPYCACHEPREFIX", build_root.join("pycache"))
        // commands will not show colors by default, as we do not provide a tty
        .env("FORCE_COLOR", "1")
        .env("MYPY_FORCE_COLOR", "1")
        .env("TERM", std::env::var("TERM").unwrap_or_default())
        // Prevents 'Warn: You must provide the URL of lib/mappings.wasm'.
        // Updating svelte-check or its deps will likely remove the need for it.
        .env("NODE_OPTIONS", "--no-experimental-fetch");

    // run build
    let mut status = command.status().expect("ninja not installed");
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
        writeln!(&mut stdout, "\nBuild succeeded.").unwrap();
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

fn setup_build_root() -> &'static Utf8Path {
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
            println!("Switching build root to {}", new_target);
            std::os::unix::fs::symlink(new_target, build_root).unwrap();
        }
    }

    fs::create_dir_all(build_root).unwrap();

    build_root
}

fn maybe_reconfigure_build(build_file: &Utf8Path, path: &str) {
    let output = Command::new("ninja")
        .arg("-f")
        .arg(build_file)
        .arg("build_run_configure")
        .env("PATH", path)
        .output()
        .expect("ninja installed");
    if !output.status.success() {
        // The existing build.ninja may be invalid if files have been renamed/removed;
        // resort to a slower cargo invocation instead to regenerate it.
        bootstrap_build();
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
    if env::var("RELEASE").is_ok() || !path.exists() {
        write_if_changed(&path, &get_buildhash())
    }
}

fn get_buildhash() -> String {
    let output = Command::new("git")
        .args(["rev-parse", "--short=8", "HEAD"])
        .output()
        .expect("git");
    assert!(output.status.success(), "git failed");
    String::from_utf8(output.stdout).unwrap().trim().into()
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
