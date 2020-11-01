import os
import sys

import pytest

os.environ["SHIFT_CLOCK_HACK"] = "1"

if __name__ == "__main__":
    folder = os.path.join(os.path.dirname(__file__), "..", "tests")
    sys.exit(pytest.main(["--verbose", "-s", folder]))
