// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { expect, test } from "vitest";

import { untrustedHTML } from "./trusted-types";

test.each([
    ["script", "<script>alert(1)</script>"],
    ["link", "<link rel=\"stylesheet\" href=\"foo.css\">"],
    ["base", "<base href=\"https://foo.example/\">"],
])("%s tags are removed from field html", (_tag, markup) => {
    expect(untrustedHTML(`<p>before</p>${markup}<p>after</p>`).toString()).toBe(
        "<p>before</p><p>after</p>",
    );
});

test("ordinary markup is left untouched", () => {
    const html = "<p>text <b>bold</b> <img src=\"foo.jpg\"></p>";
    expect(untrustedHTML(html).toString()).toBe(html);
});
