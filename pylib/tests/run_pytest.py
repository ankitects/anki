import os
import sys

import pytest

os.environ["SHIFT_CLOCK_HACK"] = "1"

if __name__ == "__main__":
    folder = os.path.join(os.path.dirname(__file__), "..", "tests")
    args = ["--verbose", "-s", folder]
    # allow specifying an individual test, eg
    # bazel test //pylib:pytest --test_env=PYTEST=test_bury
    if pytest_extra := os.environ.get("PYTEST", ""):
        args.extend(["-k", pytest_extra])
    sys.exit(pytest.main(args))
