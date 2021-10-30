# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import glob
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

is_win = sys.platform == "win32"

workspace = Path(sys.argv[1])
output_root = workspace / "bazel-pkg"
dist_folder = output_root / "dist"
venv = output_root / "venv"
cargo_target = output_root / "target"
bazel_external = Path(sys.argv[2])
artifacts = output_root / "artifacts"
pyo3_config = output_root / "pyo3-build-config-file.txt"

if is_win:
    python_bin_folder = venv / "scripts"
    os.environ["PATH"] = os.getenv("USERPROFILE") + r"\.cargo\bin;" + os.getenv("PATH")
    cargo_features = "build-mode-prebuilt-artifacts"
else:
    python_bin_folder = venv / "bin"
    os.environ["PATH"] = os.getenv("HOME") + "/.cargo/bin:" + os.getenv("PATH")
    cargo_features = (
        "build-mode-prebuilt-artifacts global-allocator-jemalloc allocator-jemalloc"
    )

os.environ["PYOXIDIZER_ARTIFACT_DIR"] = str(artifacts)
os.environ["PYOXIDIZER_CONFIG"] = str(Path(os.getcwd()) / "pyoxidizer.bzl")
os.environ["CARGO_TARGET_DIR"] = str(cargo_target)

# OS-specific things
pyqt5_folder_name = "pyqt515"
is_lin = False
if is_win:
    os.environ["TARGET"] = "x86_64-pc-windows-msvc"
elif sys.platform.startswith("darwin"):
    if platform.machine() == "arm64":
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
        raise Exception("building on this architecture is not currently supported")


python = python_bin_folder / "python"
pip = python_bin_folder / "pip"
artifacts_in_build = (
    output_root
    / "build"
    / os.getenv("TARGET")
    / "release"
    / "resources"
    / "extra_files"
)


def build_pyoxidizer():
    subprocess.run(
        [
            "cargo",
            "install",
            "--locked",
            "--git",
            "https://github.com/ankitects/PyOxidizer.git",
            "--rev",
            "ffbfe66912335bc816074c7a08aed06e26bfca7f",
            "pyoxidizer",
        ],
        check=True,
    )


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
        f.write("\n".join(extracted))

    # install wheels and upgrade any deps
    wheels = glob.glob(str(workspace / "bazel-dist" / "*.whl"))
    subprocess.run(
        [pip, "install", "--upgrade", "-c", constraints, *wheels], check=True
    )
    # always reinstall our wheels
    subprocess.run(
        [pip, "install", "--force-reinstall", "--no-deps", *wheels], check=True
    )
    # pypi protobuf lacks C extension on darwin-arm; use a locally built version
    protobuf = Path.home() / "protobuf-3.19.1-cp39-cp39-macosx_11_0_arm64.whl"
    if protobuf.exists():
        subprocess.run(
            [pip, "install", "--force-reinstall", "--no-deps", protobuf], check=True
        )


def build_artifacts():
    if os.path.exists(artifacts):
        shutil.rmtree(artifacts)
    if os.path.exists(artifacts_in_build):
        shutil.rmtree(artifacts_in_build)

    subprocess.run(
        [
            "pyoxidizer",
            "--system-rust",
            "run-build-script",
            "build.rs",
            "--var",
            "venv",
            venv,
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


def merge_into_dist(output_folder: Path, pyqt_src_path: Path):
    if not output_folder.exists():
        output_folder.mkdir(parents=True)
    # PyQt
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
    # Executable and other resources
    resources = [
        adj_path_for_windows_rsync(
            cargo_target / "release" / ("anki.exe" if is_win else "anki")
        ),
        adj_path_for_windows_rsync(artifacts_in_build) + "/",
    ]
    if is_win:
        resources.append(adj_path_for_windows_rsync(Path("win")) + "/")
    elif is_lin:
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


build_pyoxidizer()
install_wheels_into_venv()
build_artifacts()
build_pkg()
merge_into_dist(dist_folder / "std", bazel_external / "pyqt6" / "PyQt6")
if pyqt5_folder_name:
    merge_into_dist(dist_folder / "alt", bazel_external / pyqt5_folder_name / "PyQt5")
