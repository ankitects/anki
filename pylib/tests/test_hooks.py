# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pytest

from anki.hooks import addHook, remHook, runFilter, runHook, wrap
import anki.hooks as hooks_module


@pytest.fixture(autouse=True)
def cleanup_hooks():
    hooks_module._hooks.clear()
    yield
    hooks_module._hooks.clear()


def test_run_hook_calls_registered_callback():
    called_with = []
    addHook("test_hook", lambda x: called_with.append(x))
    runHook("test_hook", 42)
    assert called_with == [42]
    remHook("test_hook", called_with.append)


def test_hook_callback_is_retrievable_by_name():
    results = []
    cb = lambda: results.append("fired")
    addHook("named_hook", cb)
    runHook("named_hook")
    assert results == ["fired"]
    remHook("named_hook", cb)


def test_run_filter_transforms_and_returns_value():
    double_func = lambda x: x * 2
    addHook("double_filter", double_func)
    result = runFilter("double_filter", 5)
    assert result == 10
    remHook("double_filter", double_func)


def test_run_filter_chains_multiple_functions():
    plus_one_func = lambda x: x + 1
    multiply_three_func = lambda x: x * 3
    addHook("chain_filter", plus_one_func)
    addHook("chain_filter", multiply_three_func)
    result = runFilter("chain_filter", 2)
    # (2 + 1) * 3 = 9
    assert result == 9
    remHook("chain_filter", plus_one_func)
    remHook("chain_filter", multiply_three_func)


def test_rem_hook_prevents_callback_from_being_called():
    results = []
    cb = lambda: results.append("fired")
    addHook("removable_hook", cb)
    remHook("removable_hook", cb)
    runHook("removable_hook")
    assert results == []


def test_exception_in_hook_does_not_silently_swallow():
    def bad_cb():
        raise ValueError("boom")

    addHook("error_hook", bad_cb)
    with pytest.raises(ValueError, match="boom"):
        runHook("error_hook")
        
        assert bad_cb not in hooks_module._hooks.get("error_hook", [])


def test_exception_in_hook_stops_subsequent_hooks():
    results = []

    def bad_cb():
        raise RuntimeError("oops")

    good_cb = lambda: results.append("ok")

    # bad_cb raises and stops execution; verify the process itself survives
    addHook("mixed_hook", bad_cb)
    addHook("mixed_hook", good_cb)
    with pytest.raises(RuntimeError):
        runHook("mixed_hook")


def test_exception_in_filter_does_not_silently_swallow():
    def bad_filter(x):
        raise ValueError("filter boom")

    addHook("error_filter", bad_filter)
    with pytest.raises(ValueError, match="filter boom"):
        runFilter("error_filter", "value")


def test_wrap_after_calls_old_then_new():
    order = []

    def old_fn():
        order.append("old")

    def new_fn():
        order.append("new")
        return "result"

    wrapped = wrap(old_fn, new_fn)
    result = wrapped()
    assert order == ["old", "new"]
    assert result == "result"


def test_wrap_before_calls_new_then_old():
    order = []

    def old_fn():
        order.append("old")
        return "from_old"

    def new_fn():
        order.append("new")

    wrapped = wrap(old_fn, new_fn, pos="before")
    result = wrapped()
    assert order == ["new", "old"]
    assert result == "from_old"


def test_wrap_around_receives_old_as_callable():
    def old_fn():
        return "original"

    def new_fn(_old):
        return _old() + "_wrapped"

    wrapped = wrap(old_fn, new_fn, pos="around")
    assert wrapped() == "original_wrapped"
