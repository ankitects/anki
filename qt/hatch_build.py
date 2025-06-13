# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys
from pathlib import Path
from typing import Any, Dict

from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class CustomBuildHook(BuildHookInterface):
    """Build hook to copy generated files into both sdist and wheel."""

    PLUGIN_NAME = "custom"

    def initialize(self, version: str, build_data: Dict[str, Any]) -> None:
        """Initialize the build hook."""
        force_include = build_data.setdefault("force_include", {})

        # Look for generated files in out/qt/_aqt
        project_root = Path(self.root).parent
        generated_root = project_root / "out" / "qt" / "_aqt"

        if not os.environ.get("ANKI_WHEEL_TAG"):
            # On Windows, uv invokes this build hook during the initial uv sync,
            # when the tag has not been declared by our build script.
            return

        assert generated_root.exists(), "you should build with --wheel"
        self._add_aqt_files(force_include, generated_root)

    def _add_aqt_files(self, force_include: Dict[str, str], aqt_root: Path) -> None:
        """Add _aqt files to the build."""
        for path in aqt_root.rglob("*"):
            if path.is_file() and not self._should_exclude(path):
                relative_path = path.relative_to(aqt_root)
                # Place files under _aqt/ in the distribution
                dist_path = "_aqt" / relative_path
                force_include[str(path)] = str(dist_path)

    def _should_exclude(self, path: Path) -> bool:
        """Check if a file should be excluded from the wheel."""
        # Match the exclusions from write_wheel.py exclude_aqt function
        if path.suffix in [".ui", ".scss", ".map", ".ts"]:
            return True
        if path.name.startswith("tsconfig"):
            return True
        if "/aqt/data" in str(path):
            return True
        return False
