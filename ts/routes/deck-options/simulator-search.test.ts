// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "vitest";

import {
    deckSimulationSearch,
    presetSimulationSearch,
} from "./simulator-search";

test("preset simulator search excludes suspended cards", () => {
    expect(presetSimulationSearch("Default")).toBe(
        'preset:"Default" -is:suspended',
    );
});

test("deck simulator search can include or exclude subdecks", () => {
    expect(deckSimulationSearch("Languages::Japanese", true)).toBe(
        'deck:"Languages::Japanese" -is:suspended',
    );
    expect(deckSimulationSearch("Languages::Japanese", false)).toBe(
        'deck:"Languages::Japanese" -deck:"Languages::Japanese::*" -is:suspended',
    );
});

test("simulator search escapes quotes and backslashes", () => {
    expect(presetSimulationSearch('A "quoted" \\ preset')).toBe(
        'preset:"A \\"quoted\\" \\\\ preset" -is:suspended',
    );
    expect(deckSimulationSearch('A "quoted" \\ deck', false)).toBe(
        'deck:"A \\"quoted\\" \\\\ deck" -deck:"A \\"quoted\\" \\\\ deck::*" -is:suspended',
    );
});
