# copied from mypy_protobuf:bin - simple launch wrapper
import re
import sys

from mypy_protobuf.main import main

if __name__ == "__main__":
    sys.argv[0] = re.sub(r"(-script\.pyw|\.exe)?$", "", sys.argv[0])
    sys.exit(main())
