# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import platform
import shutil
from pathlib import Path

from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class CustomBuildHook(BuildHookInterface):
    """Build hook to copy platform-specific audio binaries."""

    def initialize(self, version, build_data):
        """Initialize the build hook and set platform tags."""

        # Set platform-specific wheel tag
        if not (platform_tag := os.environ.get("ANKI_WHEEL_TAG")):
            # On Windows, uv invokes this build hook during the initial uv sync,
            # when the tag has not been declared by our build script.
            return
        build_data.setdefault("tag", platform_tag)
        build_data["pure_python"] = False

        project_root = Path(self.root).parent.parent
        extraction_folder = project_root / "out" / "extracted"

        # Assumes platform_tag matches host system
        system = platform.system()
        # machine = platform.machine()
        if system == "Darwin":
            # TODO
            pass
        elif system == "Windows":
            binary_files = [
                extraction_folder / "mpv" / "mpv.exe",
                extraction_folder / "mpv" / "vulkan-1.dll",
                extraction_folder / "lame" / "lame.exe",
                extraction_folder / "lame" / "lame_enc.dll",
            ]
        else:
            # Linux or other - no binaries available
            return

        # Copy files to anki_audio directory
        dst_dir = Path(self.root) / "anki_audio"
        dst_dir.mkdir(exist_ok=True)

        # Copy main binaries
        for src_file in binary_files:
            if src_file.exists():
                shutil.copy2(src_file, dst_dir / src_file.name)
