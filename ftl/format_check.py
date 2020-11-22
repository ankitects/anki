import os
import format
import sys
import glob

template_root = os.path.dirname(sys.argv[1])
template_files = glob.glob(os.path.join(template_root, "*", "*.ftl"), recursive=True)

if not format.check_files(template_files, fix=False):
    sys.exit(1)
