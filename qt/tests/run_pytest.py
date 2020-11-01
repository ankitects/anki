import os
import sys

import pytest

if __name__ == "__main__":
    print(os.path.dirname(__file__))
    folder = os.path.join(os.path.dirname(__file__), "..", "tests")
    sys.exit(pytest.main(["--verbose", "-s", folder]))
