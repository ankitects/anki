import os
import sys

import pytest

try:
    import bazelfixes

    bazelfixes.fix_pywin32_in_bazel(force=True)
except ImportError:
    pass

if __name__ == "__main__":
    print(os.path.dirname(__file__))
    folder = os.path.join(os.path.dirname(__file__), "..", "tests")
    sys.exit(pytest.main(["--verbose", "-s", folder]))
