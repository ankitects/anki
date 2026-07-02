# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Unit tests for the pure `pick_next` interleaving policy.

See qt/aqt/_interleave.py's module docstring and contracts/api.md section 4
(run 2026-07-01-interleaved-study) for the authoritative policy description.
All randomness here uses a seeded `random.Random` instance -- never the
global `random` module -- so results are fully reproducible.
"""

from __future__ import annotations

import copy
import random

from aqt._interleave import CategorizedCard, InterleaveState, pick_next

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def fresh_state() -> InterleaveState:
    return InterleaveState()


def run_many(
    window: list[str],
    state: InterleaveState,
    rng: random.Random,
    n: int,
) -> list[tuple[CategorizedCard, InterleaveState]]:
    """Drive pick_next n times, feeding each returned state back in."""
    results = []
    for _ in range(n):
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, state = result
        results.append((chosen, state))
    return results


# ---------------------------------------------------------------------------
# 1. Run-length bound (1-3)
# ---------------------------------------------------------------------------


def test_run_target_always_in_1_to_3_across_many_seeds():
    window = ["a", "b", "c"]
    run_targets: set[int] = set()
    for seed in range(500):
        rng = random.Random(seed)
        result = pick_next(window, fresh_state(), rng)
        assert result is not None
        _, new_state = result
        assert 1 <= new_state.run_target <= 3
        run_targets.add(new_state.run_target)
    # Sanity: with 500 seeds we should see all three possible values.
    assert run_targets == {1, 2, 3}


def test_run_target_in_bounds_across_many_consecutive_switches():
    # Single category forces a "new run" pick on every call once served_in_run
    # reaches run_target; check the bound holds across a long chain of switches.
    window = ["x"]
    rng = random.Random(12345)
    state = fresh_state()
    for _ in range(200):
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, state = result
        assert 1 <= state.run_target <= 3
        assert chosen.category == "x"


# ---------------------------------------------------------------------------
# 2. Stays in category for exactly run_target calls, then switches
# ---------------------------------------------------------------------------


def test_stays_in_category_for_exactly_run_target_then_switches():
    window = ["a", "a", "a", "a", "a", "b", "b", "b", "b", "b"]
    for seed in range(50):
        rng = random.Random(seed)
        state = fresh_state()
        # Kick off the first run.
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, state = result
        run_category = chosen.category
        run_target = state.run_target
        served = 1

        # Keep calling; category must stay constant for (run_target - served)
        # more calls, then must change on the following call.
        while served < run_target:
            result = pick_next(window, state, rng)
            assert result is not None
            chosen, state = result
            assert chosen.category == run_category, (
                f"seed={seed} expected to stay in {run_category} "
                f"(served={served}, run_target={run_target})"
            )
            served += 1

        # Now the run is exhausted -- the next call must switch category
        # (since both "a" and "b" are present, i.e. >1 distinct category).
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, state = result
        assert chosen.category != run_category, (
            f"seed={seed} expected a switch away from {run_category} "
            f"after serving run_target={run_target}"
        )


# ---------------------------------------------------------------------------
# 3. Switch always picks a DIFFERENT category when >1 present
# ---------------------------------------------------------------------------


def test_switch_never_repeats_current_category_when_multiple_present():
    window = ["a", "b", "c", "a", "b", "c"]
    for seed in range(300):
        rng = random.Random(seed)
        # Force an immediate "start new run" by setting served_in_run to have
        # already reached run_target (so pick_next re-evaluates from scratch).
        state = InterleaveState(current_category="a", served_in_run=1, run_target=1)
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, new_state = result
        assert chosen.category != "a", f"seed={seed} re-picked previous category"
        assert new_state.current_category != "a"


def test_switch_never_repeats_current_category_across_long_chain():
    window = ["a", "b"]
    rng = random.Random(777)
    state = InterleaveState(current_category="a", served_in_run=5, run_target=1)
    for _ in range(100):
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, new_state = result
        # Whatever the previous current_category was, the switch (forced by
        # served_in_run always exceeding run_target=1 immediately after) must
        # differ from it, since both categories remain present throughout.
        prev_category = state.current_category
        if prev_category is not None:
            assert chosen.category != prev_category
        # Force another switch next iteration too.
        state = InterleaveState(
            current_category=new_state.current_category,
            served_in_run=new_state.run_target,
            run_target=new_state.run_target,
        )


# ---------------------------------------------------------------------------
# 4. Switch may re-pick the same category when it's the only one present
# ---------------------------------------------------------------------------


def test_switch_repicks_same_category_when_only_one_present():
    window = ["solo", "solo", "solo"]
    rng = random.Random(99)
    state = InterleaveState(current_category="solo", served_in_run=1, run_target=1)
    for _ in range(50):
        result = pick_next(window, state, rng)
        assert result is not None
        chosen, state = result
        assert chosen.category == "solo"
        assert state.current_category == "solo"


# ---------------------------------------------------------------------------
# 5. Single-category window never errors / never infinite-loops
# ---------------------------------------------------------------------------


def test_single_category_window_never_errors_over_many_calls():
    window = ["only"]
    rng = random.Random(2026)
    state = fresh_state()
    for i in range(75):
        result = pick_next(window, state, rng)
        assert result is not None, f"call {i} unexpectedly returned None"
        chosen, state = result
        assert chosen.category == "only"
        assert chosen.window_index == 0


# ---------------------------------------------------------------------------
# 6. Under-target / re-evaluate each call (category disappears from window)
# ---------------------------------------------------------------------------


def test_falls_through_to_new_category_when_current_disappears():
    rng = random.Random(5)
    # Start a run in "a" with a run_target that would normally require more
    # than one serving.
    window_with_a = ["a", "a", "b"]
    state = InterleaveState(current_category=None, served_in_run=0, run_target=0)
    result = pick_next(window_with_a, state, rng)
    assert result is not None
    chosen, state = result
    # Force a run in "a" explicitly regardless of what was randomly chosen,
    # to deterministically exercise the "still mid-run but category vanishes"
    # path.
    state = InterleaveState(current_category="a", served_in_run=1, run_target=3)

    # Simulate the category disappearing from the window between calls (e.g.
    # a learning-card requeue moved it out of the visible window).
    window_without_a = ["b", "c"]
    result = pick_next(window_without_a, state, rng)
    assert result is not None
    chosen, new_state = result
    assert chosen.category in ("b", "c")
    assert new_state.current_category == chosen.category
    # A fresh run was started: served_in_run resets to 1.
    assert new_state.served_in_run == 1
    assert 1 <= new_state.run_target <= 3


def test_reevaluation_is_fresh_each_call_not_remembered():
    rng = random.Random(6)
    # current_category "a" is present and mid-run (served < target): should stay.
    state = InterleaveState(current_category="a", served_in_run=0, run_target=2)
    window_with_a = ["a", "b"]
    result = pick_next(window_with_a, state, rng)
    assert result is not None
    chosen, state = result
    assert chosen.category == "a"
    assert state.served_in_run == 1

    # Now "a" vanishes for exactly one call -- must switch away immediately,
    # not "remember" that it was mid-run previously.
    window_without_a = ["b", "c"]
    result = pick_next(window_without_a, state, rng)
    assert result is not None
    chosen, state = result
    assert chosen.category != "a"
    assert state.served_in_run == 1  # a new run was started

    # If "a" reappears on a later call while state.current_category is now
    # something else, "a" should NOT be force-resumed -- normal policy
    # (stay-in-new-current-category-if-possible) applies fresh.
    window_with_a_again = ["a", state.current_category]
    result = pick_next(window_with_a_again, state, rng)
    assert result is not None
    chosen, state2 = result
    # Either it stays in the (new) current category, or -- if the run target
    # was already exhausted -- it may switch again; both are valid per
    # policy, but it must never silently resume "a" outside of the documented
    # random-switch mechanism unless "a" was actually (re-)selected by that
    # mechanism.
    assert chosen.category in window_with_a_again


# ---------------------------------------------------------------------------
# 7. Category composition changes between calls (requeue simulation)
# ---------------------------------------------------------------------------


def test_requeue_simulation_two_different_windows():
    rng = random.Random(42)
    state = fresh_state()

    window1 = ["a", "b", "c"]
    result1 = pick_next(window1, state, rng)
    assert result1 is not None
    chosen1, state1 = result1
    assert 0 <= chosen1.window_index < len(window1)
    assert window1[chosen1.window_index] == chosen1.category

    # Composition changes: "a" is gone (requeued elsewhere), "d" appears new.
    window2 = ["b", "d", "c"]
    result2 = pick_next(window2, state1, rng)
    assert result2 is not None
    chosen2, state2 = result2
    assert 0 <= chosen2.window_index < len(window2)
    assert window2[chosen2.window_index] == chosen2.category


def test_no_hidden_state_leaks_deterministic_with_identical_inputs():
    # Calling pick_next twice with identical window/state and freshly-seeded
    # equivalent rngs must produce identical results -- the only channel
    # between calls is the returned InterleaveState, no module-level state.
    window = ["a", "b", "c", "a", "b"]
    state = InterleaveState(current_category="b", served_in_run=0, run_target=2)

    rng1 = random.Random(123)
    result1 = pick_next(window, state, rng1)

    rng2 = random.Random(123)
    result2 = pick_next(window, state, rng2)

    assert result1 is not None and result2 is not None
    chosen1, new_state1 = result1
    chosen2, new_state2 = result2
    assert chosen1 == chosen2
    assert new_state1 == new_state2

    # Calling pick_next again with the *original* state object a third time
    # (simulating another independent caller) must still reproduce the same
    # result -- proving no hidden/global state was mutated by prior calls.
    rng3 = random.Random(123)
    result3 = pick_next(window, state, rng3)
    assert result3 is not None
    chosen3, new_state3 = result3
    assert chosen3 == chosen1
    assert new_state3 == new_state1


# ---------------------------------------------------------------------------
# 8. Empty window -> None
# ---------------------------------------------------------------------------


def test_empty_window_returns_none_with_default_state():
    assert pick_next([], InterleaveState()) is None


def test_empty_window_returns_none_with_nondefault_state():
    state = InterleaveState(current_category="a", served_in_run=2, run_target=3)
    assert pick_next([], state) is None


# ---------------------------------------------------------------------------
# 9. No mutation of inputs
# ---------------------------------------------------------------------------


def test_window_list_not_mutated():
    window = ["a", "b", "c"]
    window_copy = list(window)
    state = InterleaveState(current_category="a", served_in_run=0, run_target=2)
    rng = random.Random(1)

    result = pick_next(window, state, rng)
    assert result is not None
    assert window == window_copy


def test_state_object_not_mutated_new_state_returned():
    original_state = InterleaveState(current_category="a", served_in_run=0, run_target=2)
    state_snapshot = copy.deepcopy(original_state)
    window = ["a", "b"]
    rng = random.Random(2)

    result = pick_next(window, original_state, rng)
    assert result is not None
    chosen, new_state = result

    assert new_state is not original_state
    assert original_state == state_snapshot
    assert original_state.current_category == "a"
    assert original_state.served_in_run == 0
    assert original_state.run_target == 2


def test_state_object_not_mutated_on_new_run_branch():
    # Exercise the "start a new run" branch specifically (served_in_run
    # already >= run_target) to ensure that path also doesn't mutate state.
    original_state = InterleaveState(current_category="a", served_in_run=3, run_target=1)
    state_snapshot = copy.deepcopy(original_state)
    window = ["a", "b", "c"]
    rng = random.Random(3)

    result = pick_next(window, original_state, rng)
    assert result is not None
    _, new_state = result

    assert new_state is not original_state
    assert original_state == state_snapshot


# ---------------------------------------------------------------------------
# 10. Scheduler order preserved (first/lowest matching index chosen)
# ---------------------------------------------------------------------------


def test_scheduler_order_preserved_on_stay_pick():
    # Category "a" appears at indices 1 and 3; a "stay" pick must choose the
    # first (lowest) matching index, i.e. 1.
    window = ["b", "a", "c", "a"]
    state = InterleaveState(current_category="a", served_in_run=0, run_target=2)
    rng = random.Random(4)

    result = pick_next(window, state, rng)
    assert result is not None
    chosen, _ = result
    assert chosen.category == "a"
    assert chosen.window_index == 1


def test_scheduler_order_preserved_on_switch_pick():
    # Force the switch to land on "c", which appears at indices 2 and 4;
    # the chosen index must be the first (lowest), i.e. 2.
    window = ["a", "a", "c", "a", "c"]
    state = InterleaveState(current_category="a", served_in_run=1, run_target=1)

    class ForceCChoice:
        def choice(self, seq):
            assert "c" in seq
            return "c"

        def randint(self, a, b):
            return 2

    result = pick_next(window, state, ForceCChoice())
    assert result is not None
    chosen, new_state = result
    assert chosen.category == "c"
    assert chosen.window_index == 2
    assert new_state.run_target == 2


# ---------------------------------------------------------------------------
# 11. Selected index always refers to a real window entry
# ---------------------------------------------------------------------------


def test_chosen_index_always_valid_across_many_random_windows_and_states():
    categories_pool = ["a", "b", "c", "d"]
    rng_setup = random.Random(0xC0FFEE)

    for seed in range(500):
        rng = random.Random(seed)
        window_len = rng_setup.randint(1, 8)
        window = [rng_setup.choice(categories_pool) for _ in range(window_len)]

        state = InterleaveState(
            current_category=rng_setup.choice(categories_pool + [None]),
            served_in_run=rng_setup.randint(0, 3),
            run_target=rng_setup.randint(0, 3),
        )

        result = pick_next(window, state, rng)
        assert result is not None
        chosen, new_state = result

        assert 0 <= chosen.window_index < len(window), (
            f"seed={seed} window={window} state={state} "
            f"chosen={chosen} out of bounds"
        )
        assert window[chosen.window_index] == chosen.category, (
            f"seed={seed} window={window} state={state} "
            f"chosen category mismatch at index"
        )
        assert new_state.current_category == chosen.category
