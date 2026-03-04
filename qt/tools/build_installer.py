import shutil
import subprocess
import sys
from pathlib import Path

import jinja2

installer_dir = Path("qt/installer")
env = jinja2.Environment(loader=jinja2.FileSystemLoader(installer_dir))


def read_version() -> str:
    with open(".version", "r", encoding="utf-8") as f:
        return f.read().strip()


def normalize_wheel_path(out_dir: Path, path: str) -> str:
    path = Path(path).relative_to(out_dir.parent).as_posix()
    return f"../{path}"


def get_python_path() -> Path:
    pyenv_dir = Path("out/pyenv")
    if sys.platform == "win32":
        python_path = pyenv_dir / "Scripts" / "python"
    else:
        python_path = pyenv_dir / "bin" / "python"
    return python_path.absolute()


def main(aqt_wheel: str, anki_wheel: str, out_dir: Path) -> None:
    aqt_wheel = normalize_wheel_path(out_dir, aqt_wheel)
    anki_wheel = normalize_wheel_path(out_dir, anki_wheel)
    template = env.get_template("pyproject.toml.template").render(
        aqt_wheel=aqt_wheel, anki_wheel=anki_wheel, version=read_version()
    )
    shutil.rmtree(out_dir, ignore_errors=True)
    shutil.copytree(installer_dir, out_dir)
    (out_dir / "pyproject.toml").write_text(template, encoding="utf-8")
    shutil.copy("LICENSE", out_dir / "LICENSE")
    subprocess.check_call(
        [get_python_path(), "-m", "briefcase", "package"], cwd=out_dir
    )


if __name__ == "__main__":
    aqt_wheel = sys.argv[1]
    anki_wheel = sys.argv[2]
    out_dir = Path(sys.argv[3])
    main(aqt_wheel, anki_wheel, out_dir)
