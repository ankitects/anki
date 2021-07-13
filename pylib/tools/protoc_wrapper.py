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

(protoc, mypy_protobuf, outdir, *protos) = sys.argv[1:]

if protos[0].startswith("external"):
    prefix = "external/ankidesktop/proto/"
else:
    prefix = "proto/"

# invoke protoc
subprocess.run(
    [
        protoc,
        "--plugin=protoc-gen-mypy=" + mypy_protobuf,
        "--python_out=.",
        "--mypy_out=.",
        "-I" + prefix,
        "-Iexternal/ankidesktop/" + prefix,
        *protos,
    ],
    # mypy prints to stderr on success :-(
    stderr=subprocess.DEVNULL,
    check=True,
)

for proto in protos:
    without_prefix_and_ext, _ = os.path.splitext(proto[len(prefix) :])
    for ext in "_pb2.py", "_pb2.pyi":
        path = without_prefix_and_ext + ext
        shutil.move(path, os.path.join(outdir, os.path.basename(path)))
