// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeToChildNodeRange } from "./child-node-range";
import { findAfter, findBefore } from "./find-adjacent";
import { easyBold, easyItalic,p } from "./test-utils";

describe("in a simple search", () => {
    const body = p("<b>Before</b><u>This is a test</u><i>After</i>");
    const range = nodeToChildNodeRange(body.children[1]);

    describe("findBefore", () => {
        test("finds an element", () => {
            const matches = findBefore(range, easyBold.matcher);
            expect(matches).toHaveLength(1);
        });

        test("does not find non-existing element", () => {
            const matches = findBefore(range, easyItalic.matcher);
            expect(matches).toHaveLength(0);
        });
    });

    describe("findAfter", () => {
        test("finds an element", () => {
            const matches = findAfter(range, easyItalic.matcher);
            expect(matches).toHaveLength(1);
        });

        test("does not find non-existing element", () => {
            const matches = findAfter(range, easyBold.matcher);
            expect(matches).toHaveLength(0);
        });
    });
});

describe("find by descension", () => {
    describe("single one", () => {
        const body = p("<u><b>before</b></u>within<u><b>after</b></u>");
        const range = nodeToChildNodeRange(body.childNodes[1]);

        test("findBefore", () => {
            const matches = findBefore(range, easyBold.matcher);
            expect(matches).toHaveLength(1);
        });

        test("findAfter", () => {
            const matches = findAfter(range, easyBold.matcher);
            expect(matches).toHaveLength(1);
        });
    });

    describe("multiple", () => {
        const body = p(
            "<u><b>before</b></u><u><b>before</b></u>within<u><b>after</b></u><u><b>after</b></u>",
        );
        const range = nodeToChildNodeRange(body.childNodes[2]);

        test("findBefore", () => {
            const matches = findBefore(range, easyBold.matcher);
            expect(matches).toHaveLength(2);
        });

        test("findAfter", () => {
            const matches = findAfter(range, easyBold.matcher);
            expect(matches).toHaveLength(2);
        });
    });

    describe("no block-level", () => {
        const body = p("<div><b>before</b></div>within<div><b>after</b></div>");
        const range = nodeToChildNodeRange(body.childNodes[1]);

        test("findBefore", () => {
            const matches = findBefore(range, easyBold.matcher);
            expect(matches).toHaveLength(0);
        });

        test("findAfter", () => {
            const matches = findAfter(range, easyBold.matcher);
            expect(matches).toHaveLength(0);
        });
    });
});
