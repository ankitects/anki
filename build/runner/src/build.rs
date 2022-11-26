// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{env, fs, io::Write, process::Command};

use camino::Utf8Path;
use clap::Args;
use termcolor::{Color, ColorChoice, ColorSpec, StandardStream, WriteColor};

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
            "out/bin:out/extracted/node/bin:{}",
            env::var("PATH").unwrap()
        )
    };

    maybe_update_env_file(build_root);
    maybe_update_buildhash(build_root, &path);

    // Ensure build file is up to date
    let build_file = build_root.join("build.ninja");
    if !build_file.exists() {
        bootstrap_build();
    } else {
        maybe_reconfigure_build(&build_file, &path);
    }

    // automatically convert foo:bar references to foo_bar, as Ninja can not represent the former
    let ninja_args = args.args.into_iter().map(|a| a.replace(':', "_"));

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
        .env("TERM", "1")
        // Prevents 'Warn: You must provide the URL of lib/mappings.wasm'.
        // Updating svelte-check or its deps will likely remove the need for it.
        .env("NODE_OPTIONS", "--no-experimental-fetch");

    if cfg!(windows) {
        command.env(
            "PYO3_PYTHON",
            build_root
                .canonicalize_utf8()
                .unwrap()
                .join("extracted/python/python.exe")
                .as_str(),
        );
    }

    // run build
    let status = command.status().expect("ninja not installed");
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

        // One cause of build failures is when a source file that was included in a glob is
        // removed. Automatically reconfigure on next run so this situation resolves itself.
        fs::remove_file(build_file).expect("build file removal");

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
    let status = Command::new("ninja")
        .arg("-f")
        .arg(build_file)
        .arg("build_run_configure")
        .env("PATH", path)
        .status()
        .expect("ninja installed");
    if !status.success() {
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

fn maybe_update_buildhash(build_root: &Utf8Path, path_env: &str) {
    // only updated on release builds
    let path = build_root.join("buildhash");
    if env::var("RELEASE").is_ok() || !path.exists() {
        write_if_changed(&path, &get_buildhash(path_env))
    }
}

fn get_buildhash(path: &str) -> String {
    let output = Command::new("git")
        .args(["rev-parse", "--short=8", "HEAD"])
        .env("PATH", path)
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
