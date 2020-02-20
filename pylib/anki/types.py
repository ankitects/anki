import enum
from typing import Any, NoReturn


class _Impossible(enum.Enum):
    pass


def assert_impossible(arg: NoReturn) -> NoReturn:
    raise Exception(f"unexpected arg received: {type(arg)} {arg}")


# mypy is not yet smart enough to do exhaustiveness checking on literal types,
# so this will fail at runtime instead of typecheck time :-(
def assert_impossible_literal(arg: Any) -> NoReturn:
    raise Exception(f"unexpected arg received: {type(arg)} {arg}")
