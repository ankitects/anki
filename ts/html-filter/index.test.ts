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

    test("event handler attributes are stripped", () => {
        expect(filterHTML("<p onclick=\"alert(1)\">hi</p>", false, false)).toBe(
            "<p>hi</p>",
        );
        expect(filterHTML("<div onclick=\"alert(1)\">hi</div>", false, false)).toBe(
            "<div>hi</div>",
        );
        expect(
            filterHTML("<img src=\"a.png\" onerror=\"alert(1)\">", false, false),
        ).toBe("<img src=\"a.png\">");
    });

    test("script tags are removed, including in nested contexts", () => {
        expect(filterHTML("<script></script>", false, false)).toBe("");
        expect(
            filterHTML("<div><script></script>hello</div>", false, false),
        ).toBe("<div>hello</div>");
        expect(
            filterHTML("<div><script>alert(1)</script>hello</div>", false, false),
        ).toBe("<div>alert(1)hello</div>");
    });

    test("title tag is removed entirely, including its content", () => {
        // TITLE is an explicitly allowed tag mapped to removeElement, so unlike
        // unknown tags (which unwrap their content), the title content is discarded.
        expect(filterHTML("<title>page title</title>", false, false)).toBe("");
        expect(filterHTML("<title>page title</title>", false, true)).toBe("");
    });

    test("unknown or disallowed tags are removed", () => {
        expect(filterHTML("<foo></foo>", false, false)).toBe("");
        expect(filterHTML("<foo>bar</foo>", false, false)).toBe("bar");
        expect(filterHTML("<marquee>x</marquee>", false, false)).toBe("x");
    });

    test("extended-only tags are unwrapped in basic mode but kept in extended mode", () => {
        // <b> exists only in tagsAllowedExtended; in basic mode it is treated as
        // an unknown tag and its content is unwrapped.
        expect(filterHTML("<b>bold</b>", false, false)).toBe("bold");
        expect(filterHTML("<b>bold</b>", false, true)).toBe("<b>bold</b>");

        // <a> keeps href in extended mode; in basic mode the tag is unwrapped.
        expect(filterHTML("<a href=\"x\">link</a>", false, false)).toBe("link");
        expect(filterHTML("<a href=\"x\">link</a>", false, true)).toBe("<a href=\"x\">link</a>");
    });

    test("span styling honours night mode", () => {
        document.body.classList.add("nightMode");

        try {
            expect(
                filterHTML(
                    "<span style=\"font-weight: bold; background-color: blue;\"></span>",
                    false,
                    true,
                ),
            ).toBe("<span style=\"font-weight: bold;\"></span>");
        } finally {
            document.body.classList.remove("nightMode");
        }
    });
});
