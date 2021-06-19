// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { filterHTML } from ".";

describe("filterHTML", () => {
    test("zero input creates zero output", () => {
        expect(filterHTML("", true, false)).toBe("");
        expect(filterHTML("", true, false)).toBe("");
        expect(filterHTML("", false, false)).toBe("");
    });
});
