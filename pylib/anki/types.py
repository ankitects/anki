from typing import NoReturn


def assert_exhaustive(arg: NoReturn) -> NoReturn:
    """The type definition will cause mypy to tell us if we've missed an enum case."""
    raise Exception(f"unexpected arg received: {type(arg)} {arg}")
