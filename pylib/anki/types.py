from typing import NoReturn


def assert_exhaustive(arg: NoReturn) -> NoReturn:
    raise Exception(f"unexpected arg received: {type(arg)} {arg}")
