#!/usr/bin/env
#
# Wrapper for protoc that strips the dirname from the output files,
# and generates mypy typechecking info.

import os
import shutil
import subprocess
import sys

(protoc, mypy_protobuf, proto, outdir) = sys.argv[1:]

# copy to current dir
basename = os.path.basename(proto)
shutil.copyfile(proto, basename)

# output filenames
without_ext = os.path.splitext(basename)[0]
pb2_py = without_ext + "_pb2.py"
pb2_pyi = without_ext + "_pb2.pyi"

# invoke protoc
subprocess.run(
    [
        protoc,
        "--plugin=protoc-gen-mypy=" + mypy_protobuf,
        "--python_out=.",
        "--mypy_out=.",
        basename,
    ],
    # mypy prints to stderr on success :-(
    stderr=subprocess.DEVNULL,
    check=True,
)

# move files into output
shutil.move(pb2_py, outdir + "/" + pb2_py)
shutil.move(pb2_pyi, outdir + "/" + pb2_pyi)
