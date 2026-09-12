// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "vitest";

import { ChangeTimer } from "./change-timer";

test("an action scheduled during a flush is not discarded", async () => {
    const timer = new ChangeTimer();
    const ran: string[] = [];
    let release!: () => void;
    const gate = new Promise<void>((resolve) => (release = resolve));

    timer.schedule(async () => {
        ran.push("first");
        await gate;
    }, 10_000);
    const flush = timer.fireImmediately();

    // arrives while the first action is still running
    timer.schedule(async () => {
        ran.push("second");
    }, 10_000);
    // replaces the second; only the latest pending action may run
    timer.schedule(async () => {
        ran.push("third");
    }, 10_000);
    release();
    await flush;
    await timer.fireImmediately();

    expect(ran).toEqual(["first", "third"]);
});
