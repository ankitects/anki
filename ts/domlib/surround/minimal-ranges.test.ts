// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { minimalRanges } from "./minimal-ranges";
import { p } from "./test-utils";

describe("direct siblings", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b>before</b><b>after</b>");
    });

    test("merge", () => {
        const before = body.firstChild! as Text;
        const after = body.lastChild! as Text;
        const result = minimalRanges([before, after], body);

        expect(result).toHaveLength(1);
        expect(result[0]).toHaveProperty("parent", body)
        expect(result[0]).toHaveProperty("startIndex", 0)
        expect(result[0]).toHaveProperty("endIndex", 2)
    });
});

describe("direct siblings with extra child", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b><u>Hello</u></b><b><i>World</i></b>");
    });

    test("merge", () => {
        const before = body.firstChild! as Text;
        const after = body.lastChild! as Text;
        const result = minimalRanges([before, after], body);

        expect(result).toHaveLength(1);
        expect(result[0]).toHaveProperty("parent", body)
        expect(result[0]).toHaveProperty("startIndex", 0)
        expect(result[0]).toHaveProperty("endIndex", 2)
    });
});
