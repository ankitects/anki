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

        dist_dir = Path(self.root).parent.parent / "out" / "extracted"

        # Assumes platform_tag matches host system
        system = platform.system()
        if system == "Darwin":
            binary_files = [dist_dir / "mpv" / "mpv", dist_dir / "lame" / "lame"]
            # Check for both 'lib' and 'libs' directories
            lib_files = []
            lib_dir = (
                dist_dir / "libs" if (dist_dir / "libs").exists() else dist_dir / "lib"
            )
            if lib_dir.exists():
                lib_files.extend(list(lib_dir.glob("*.dylib")))
        elif system == "Windows":
            binary_files = [
                dist_dir / "mpv" / "mpv.exe",
                dist_dir / "mpv" / "vulkan-1.dll",
                dist_dir / "lame" / "lame.exe",
                dist_dir / "lame" / "lame_enc.dll",
            ]
            lib_files = []
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

        # Copy library files (for macOS) - preserve directory structure
        if lib_files:
            lib_dst_dir = dst_dir / lib_dir.name  # Use same dir name (lib or libs)
            lib_dst_dir.mkdir(exist_ok=True)
            for lib_file in lib_files:
                if lib_file.exists():
                    shutil.copy2(lib_file, lib_dst_dir / lib_file.name)
