# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html


from __future__ import annotations

import glob
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

is_win = sys.platform == "win32"
is_mac = sys.platform == "darwin"

workspace = Path(sys.argv[1])
bazel_external = Path(sys.argv[2])


def with_exe_extension(program: str) -> str:
    if is_win:
        return program + ".exe"
    else:
        return program


output_root = workspace / ".bazel" / "out" / "build"
dist_folder = output_root / ".." / "dist"
venv = output_root / f"venv-{platform.machine()}"
build_folder = output_root / f"build-{platform.machine()}"
cargo_target = output_root / f"target-{platform.machine()}"
artifacts = output_root / "artifacts"
pyo3_config = output_root / "pyo3-build-config-file.txt"
pyoxidizer_folder = bazel_external / "pyoxidizer"
arm64_protobuf_wheel = bazel_external / "protobuf_wheel_mac_arm64"
pyoxidizer_binary = cargo_target / "release" / with_exe_extension("pyoxidizer")

for path in dist_folder.glob("*.zst"):
    path.unlink()

os.environ["PYOXIDIZER_ARTIFACT_DIR"] = str(artifacts)
os.environ["PYOXIDIZER_CONFIG"] = str(Path(os.getcwd()) / "pyoxidizer.bzl")
os.environ["CARGO_TARGET_DIR"] = str(cargo_target)

# OS-specific things
pyqt5_folder_name = "pyqt515"
pyqt6_folder_path = bazel_external / "pyqt6" / "PyQt6"
extra_linux_deps = bazel_external / "bundle_extras_linux_amd64"
extra_qt5_linux_plugins = extra_linux_deps / "qt5"
extra_qt6_linux_plugins = extra_linux_deps / "qt6"
is_lin = False
arm64_linux = arm64_mac = False
if is_win:
    os.environ["TARGET"] = "x86_64-pc-windows-msvc"
elif sys.platform.startswith("darwin"):
    if platform.machine() == "arm64":
        arm64_mac = True
        pyqt5_folder_name = None
        os.environ["TARGET"] = "aarch64-apple-darwin"
        os.environ["MACOSX_DEPLOYMENT_TARGET"] = "11.0"
    else:
        pyqt5_folder_name = "pyqt514"
        os.environ["TARGET"] = "x86_64-apple-darwin"
        os.environ["MACOSX_DEPLOYMENT_TARGET"] = "10.13"
else:
    is_lin = True
    if platform.machine() == "x86_64":
        os.environ["TARGET"] = "x86_64-unknown-linux-gnu"
    else:
        os.environ["TARGET"] = "aarch64-unknown-linux-gnu"
        pyqt5_folder_name = None
        pyqt6_folder_path = None
        arm64_linux = True

if is_win:
    python_bin_folder = venv / "scripts"
    os.environ["PATH"] += rf";{os.getenv('USERPROFILE')}\.cargo\bin"
    cargo_features = "build-mode-prebuilt-artifacts"
else:
    python_bin_folder = venv / "bin"
    # PyOxidizer build depends on a system-installed version of Python,
    # as the standalone build does not have its config set up properly,
    # leading to "directory not found for option '-L/install/lib'".
    # On macOS, after installing a system Python in /usr/local/bin,
    # make sure /usr/local/bin/python3 is symlinked to /usr/local/bin/python.
    os.environ["PATH"] = ":".join(
        ["/usr/local/bin", f"{os.getenv('HOME')}/.cargo/bin", os.getenv("PATH")]
    )
    cargo_features = "build-mode-prebuilt-artifacts"
    if not is_mac or arm64_mac:
        cargo_features += " global-allocator-jemalloc allocator-jemalloc"

python = python_bin_folder / with_exe_extension("python")
pip = python_bin_folder / with_exe_extension("pip")
artifacts_in_build = (
    build_folder / os.getenv("TARGET") / "release" / "resources" / "extra_files"
)


def build_pyoxidizer():
    pyoxidizer_folder_mtime = pyoxidizer_folder.stat().st_mtime
    if (
        pyoxidizer_binary.exists()
        and pyoxidizer_binary.stat().st_mtime == pyoxidizer_folder_mtime
    ):
        # avoid recompiling if pyoxidizer folder has not changed
        return
    subprocess.run(
        [
            "cargo",
            "build",
            "--release",
        ],
        cwd=pyoxidizer_folder,
        check=True,
    )
    os.utime(pyoxidizer_binary, (pyoxidizer_folder_mtime, pyoxidizer_folder_mtime))


def install_wheels_into_venv():
    # Pip's handling of hashes is somewhat broken. It spots the hashes in the constraints
    # file and forces all files to have a hash. We can manually hash our generated wheels
    # and pass them in with hashes, but it still breaks, because the 'protobuf>=3.17'
    # specifier in the pylib wheel is not allowed. Nevermind that a specific version is
    # included in the constraints file we pass along! To get things working, we're
    # forced to strip the hashes out before installing. This should be safe, as the files
    # have already been validated as part of the build process.
    constraints = output_root / "deps_without_hashes.txt"
    with open(workspace / "python" / "requirements.txt") as f:
        buf = f.read()
    with open(constraints, "w") as f:
        extracted = re.findall("^(\S+==\S+) ", buf, flags=re.M)
        extracted = [
            line for line in extracted if not arm64_mac or "protobuf" not in line
        ]
        f.write("\n".join(extracted))
    # pypi protobuf lacks C extension on darwin-arm64, so we have to use a version
    # we built ourselves
    if arm64_mac:
        wheels = glob.glob(str(arm64_protobuf_wheel / "*.whl"))
        subprocess.run(
            [pip, "install", "--upgrade", "-c", constraints, *wheels], check=True
        )
    # install wheels and upgrade any deps
    wheels = glob.glob(str(workspace / ".bazel" / "out" / "dist" / "*.whl"))
    subprocess.run(
        [pip, "install", "--upgrade", "-c", constraints, *wheels], check=True
    )
    # always reinstall our wheels
    subprocess.run(
        [pip, "install", "--force-reinstall", "--no-deps", *wheels], check=True
    )


def build_artifacts():
    if os.path.exists(artifacts):
        shutil.rmtree(artifacts)
    if os.path.exists(artifacts_in_build):
        shutil.rmtree(artifacts_in_build)

    subprocess.run(
        [
            pyoxidizer_binary,
            "--system-rust",
            "run-build-script",
            "build.rs",
            "--var",
            "venv",
            venv,
            "--var",
            "build",
            build_folder,
        ],
        check=True,
        env=os.environ
        | dict(
            CARGO_MANIFEST_DIR=".",
            OUT_DIR=str(artifacts),
            PROFILE="release",
            PYO3_PYTHON=str(python),
        ),
    )

    existing_config = None
    if os.path.exists(pyo3_config):
        with open(pyo3_config) as f:
            existing_config = f.read()

    with open(artifacts / "pyo3-build-config-file.txt") as f:
        new_config = f.read()

    # avoid bumping mtime, which triggers crate recompile
    if new_config != existing_config:
        with open(pyo3_config, "w") as f:
            f.write(new_config)


def build_pkg():
    subprocess.run(
        [
            "cargo",
            "build",
            "--release",
            "--no-default-features",
            "--features",
            cargo_features,
        ],
        check=True,
        env=os.environ | dict(PYO3_CONFIG_FILE=str(pyo3_config)),
    )


def adj_path_for_windows_rsync(path: Path) -> str:
    if not is_win:
        return str(path)

    path = path.absolute()
    rest = str(path)[2:].replace("\\", "/")
    return f"/{path.drive[0]}{rest}"


def merge_into_dist(output_folder: Path, pyqt_src_path: Path | None):
    if output_folder.exists():
        shutil.rmtree(output_folder)
    output_folder.mkdir(parents=True)
    # PyQt
    if pyqt_src_path and not is_mac:
        subprocess.run(
            [
                "rsync",
                "-a",
                "--delete",
                "--exclude-from",
                "qt.exclude",
                adj_path_for_windows_rsync(pyqt_src_path),
                adj_path_for_windows_rsync(output_folder / "lib") + "/",
            ],
            check=True,
        )
    if is_lin:
        if "PyQt5" in str(pyqt_src_path):
            src = extra_qt5_linux_plugins
            dest = output_folder / "lib" / "PyQt5" / "Qt5" / "plugins"
        else:
            src = extra_qt6_linux_plugins
            dest = output_folder / "lib" / "PyQt6" / "Qt6" / "plugins"
        subprocess.run(
            ["rsync", "-a", str(src) + "/", str(dest) + "/"],
            check=True,
        )

    # Executable and other resources
    resources = [
        adj_path_for_windows_rsync(
            cargo_target / "release" / ("anki.exe" if is_win else "anki")
        ),
        adj_path_for_windows_rsync(artifacts_in_build) + "/",
    ]
    if is_lin:
        resources.append("lin/")

    subprocess.run(
        [
            "rsync",
            "-a",
            "--delete",
            "--exclude",
            "PyQt6",
            "--exclude",
            "PyQt5",
            *resources,
            adj_path_for_windows_rsync(output_folder) + "/",
        ],
        check=True,
    )
    # Ensure all files are world-readable
    if not is_win:
        subprocess.run(["chmod", "-R", "a+r", output_folder])


def anki_version() -> str:
    with open(workspace / "defs.bzl") as fobj:
        data = fobj.read()
    return re.search('^anki_version = "(.*)"$', data, re.MULTILINE).group(1)


def annotated_linux_folder_name(variant: str) -> str:
    components = ["anki", anki_version(), "linux", variant]
    return "-".join(components)


def annotated_mac_dmg_name(variant: str) -> str:
    if platform.machine() == "arm64":
        arch = "apple"
    else:
        arch = "intel"
    components = ["anki", anki_version(), "mac", arch, variant]
    return "-".join(components)


def build_bundle(src_path: Path, variant: str) -> None:
    if is_lin:
        print("--- Build tarball")
        build_tarball(src_path, variant)
    elif is_mac:
        print("--- Build app bundle")
        build_app_bundle(src_path, variant)


def build_app_bundle(src_path: Path, variant: str) -> None:
    if arm64_mac:
        variant = "qt6_arm64"
    else:
        variant = f"{variant}_amd64"
    subprocess.run(
        ["cargo", "run", variant, src_path, anki_version(), bazel_external],
        check=True,
        cwd=workspace / "qt" / "bundle" / "mac",
    )
    variant_path = src_path.parent / "app" / variant
    if os.getenv("NOTARIZE_USER"):
        subprocess.run(
            ["python", "mac/notarize.py", "upload", variant_path],
            check=True,
        )
    # note down the dmg name for later
    open(variant_path / "dmg_name", "w").write(
        annotated_mac_dmg_name(variant[0:3]) + ".dmg"
    )


def build_tarball(src_path: Path, variant: str) -> None:
    if not is_lin:
        return
    dest_path = src_path.with_name(annotated_linux_folder_name(variant))
    if dest_path.exists():
        shutil.rmtree(dest_path)
    os.rename(src_path, dest_path)
    print("compress", dest_path.name, "...")
    subprocess.run(
        [
            "tar",
            "--zstd",
            "-cf",
            dist_folder / (dest_path.name + ".tar.zst"),
            dest_path.name,
        ],
        check=True,
        env=dict(ZSTD_CLEVEL="9"),
        cwd=dest_path.parent,
    )


def build_windows_installers() -> None:
    subprocess.run(
        [
            "cargo",
            "run",
            output_root,
            bazel_external,
            Path(__file__).parent,
            anki_version(),
        ],
        check=True,
        cwd=workspace / "qt" / "bundle" / "win",
    )


print("--- Build PyOxidizer")
build_pyoxidizer()
print("--- Install wheels into venv")
install_wheels_into_venv()
print("--- Build PyOxidizer artifacts")
build_artifacts()
print("--- Build Anki binary")
build_pkg()
print("--- Copy binary+resources into folder (Qt6)")
merge_into_dist(output_root / "std", pyqt6_folder_path)
build_bundle(output_root / "std", "qt6")
if pyqt5_folder_name:
    print("--- Copy binary+resources into folder (Qt5)")
    merge_into_dist(output_root / "alt", bazel_external / pyqt5_folder_name / "PyQt5")
    build_bundle(output_root / "alt", "qt5")

if is_win:
    build_windows_installers()

if is_mac:
    print("outputs are in .bazel/out/build/{std,alt}")
    print("dmg can be created with mac/finalize.py dmg")
else:
    print("outputs are in .bazel/out/dist/")
