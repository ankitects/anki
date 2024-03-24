// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { describe, expect, test } from "vitest";

import { filterHTML } from ".";

describe("filterHTML", () => {
    test("zero input creates zero output", () => {
        expect(filterHTML("", true, false)).toBe("");
        expect(filterHTML("", true, false)).toBe("");
        expect(filterHTML("", false, false)).toBe("");
    });
    test("internal filtering", () => {
        // font-size is filtered, weight is not
        expect(
            filterHTML(
                "<div style=\"font-weight: bold; font-size: 10px;\"></div>",
                true,
                true,
            ),
        ).toBe("<div style=\"font-weight: bold;\"></div>");
    });
    test("background color", () => {
        // transparent is stripped, other colors are not
        expect(
            filterHTML(
                "<span style=\"background-color: transparent;\"></span>",
                false,
                true,
            ),
        ).toBe("<span style=\"\"></span>");
        expect(
            filterHTML("<span style=\"background-color: blue;\"></span>", false, true),
        ).toBe("<span style=\"background-color: blue;\"></span>");
        // except if extended mode is off
        expect(
            filterHTML("<span style=\"background-color: blue;\">x</span>", false, false),
        ).toBe("x");
        // no filtering on internal paste
        expect(
            filterHTML(
                "<span style=\"background-color: transparent;\"></span>",
                true,
                true,
            ),
        ).toBe("<span style=\"background-color: transparent;\"></span>");
    });
});
