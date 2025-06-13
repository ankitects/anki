# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import platform
import subprocess
import sys
from pathlib import Path

out_dylib, *src_files = sys.argv[1:]
out_dir = Path(out_dylib).parent.resolve()
src_dir = Path(src_files[0]).parent.resolve()

# Build for both architectures
architectures = ["arm64", "x86_64"]
temp_files = []

for arch in architectures:
    target = f"{arch}-apple-macos11"
    temp_out = out_dir / f"temp_{arch}.dylib"
    temp_files.append(temp_out)
    
    args = [
        "swiftc",
        "-target",
        target,
        "-emit-library",
        "-module-name",
        "ankihelper",
        "-O",
    ]
    args.extend(src_dir / Path(file).name for file in src_files)
    args.extend(["-o", str(temp_out)])
    subprocess.run(args, check=True, cwd=out_dir)

# Create universal binary
lipo_args = ["lipo", "-create", "-output", out_dylib] + [str(f) for f in temp_files]
subprocess.run(lipo_args, check=True)

# Clean up temporary files
for temp_file in temp_files:
    temp_file.unlink()
