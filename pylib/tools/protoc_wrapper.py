#!/usr/bin/env
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Wrapper for protoc that strips the dirname from the output files,
# and generates mypy typechecking info.

import os
import shutil
import subprocess
import sys
from pathlib import Path

(protoc, mypy_protobuf, outdir, *protos) = sys.argv[1:]
outpath = Path(outdir).parent

if protos[0].startswith("external"):
    prefix = "external/ankidesktop/proto/"
else:
    prefix = "proto/"

# invoke protoc
subprocess.run(
    [
        protoc,
        f"--plugin=protoc-gen-mypy={mypy_protobuf}",
        f"--python_out={outpath}",
        f"--mypy_out={outpath}",
        f"-I{prefix}",
        f"-Iexternal/ankidesktop/{prefix}",
        *protos,
    ],
    # mypy prints to stderr on success :-(
    stderr=subprocess.DEVNULL,
    check=True,
)
