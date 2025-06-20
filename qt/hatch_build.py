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

        # Pin anki==<our version>
        self._set_anki_dependency(version, build_data)

        # Look for generated files in out/qt/_aqt
        project_root = Path(self.root).parent
        generated_root = project_root / "out" / "qt" / "_aqt"

        if not os.environ.get("ANKI_WHEEL_TAG"):
            # On Windows, uv invokes this build hook during the initial uv sync,
            # when the tag has not been declared by our build script.
            return

        assert generated_root.exists(), "you should build with --wheel"
        self._add_aqt_files(force_include, generated_root)

    def _set_anki_dependency(self, version: str, build_data: Dict[str, Any]) -> None:
        # Get current dependencies and replace 'anki' with exact version
        dependencies = build_data.setdefault("dependencies", [])

        # Remove any existing anki dependency
        dependencies[:] = [dep for dep in dependencies if not dep.startswith("anki")]

        # Handle version detection
        actual_version = version
        if version == "standard":
            # Read actual version from .version file
            project_root = Path(self.root).parent
            version_file = project_root / ".version"
            if version_file.exists():
                actual_version = version_file.read_text().strip()

        # Only add exact version for real releases, not editable installs
        if actual_version != "editable":
            dependencies.append(f"anki=={actual_version}")
        else:
            # For editable installs, just add anki without version constraint
            dependencies.append("anki")

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
