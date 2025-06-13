# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import platform
import sys
from pathlib import Path
from typing import Any, Dict

from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class CustomBuildHook(BuildHookInterface):
    """Build hook to include compiled rsbridge from out/pylib."""

    PLUGIN_NAME = "custom"

    def initialize(self, version: str, build_data: Dict[str, Any]) -> None:
        """Initialize the build hook."""
        force_include = build_data.setdefault("force_include", {})

        # Set platform-specific wheel tag
        if not (platform_tag := os.environ.get("ANKI_WHEEL_TAG")):
            # On Windows, uv invokes this build hook during the initial uv sync,
            # when the tag has not been declared by our build script.
            return
        build_data.setdefault("tag", platform_tag)

        # Mark as non-pure Python since we include compiled extension
        build_data["pure_python"] = False

        # Look for generated files in out/pylib/anki
        project_root = Path(self.root).parent
        generated_root = project_root / "out" / "pylib" / "anki"

        assert generated_root.exists(), "you should build with --wheel"
        for path in generated_root.rglob("*"):
            if path.is_file():
                relative_path = path.relative_to(generated_root)
                # Place files under anki/ in the distribution
                dist_path = "anki" / relative_path
                force_include[str(path)] = str(dist_path)
