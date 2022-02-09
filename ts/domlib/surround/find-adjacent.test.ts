// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeToChildNodeRange } from "./child-node-range";
import { findAfter, findBefore } from "./find-adjacent";
import { matchTagName } from "./matcher";

const parser = new DOMParser();

function p(html: string): Element {
    const parsed = parser.parseFromString(html, "text/html");
    return parsed.body;
}

describe("in a simple search", () => {
    const html = p("<b>Before</b><u>This is a test</u><i>After</i>");
    const range = nodeToChildNodeRange(html.children[1]);

    describe("findBefore", () => {
        test("finds an element", () => {
            const matches = findBefore(range, matchTagName("b"));

            expect(matches).toHaveLength(1);
        });

        test("does not find non-existing element", () => {
            const matches = findBefore(range, matchTagName("i"));

            expect(matches).toHaveLength(0);
        });
    });

    describe("findAfter", () => {
        test("finds an element", () => {
            const matches = findAfter(range, matchTagName("i"));

            expect(matches).toHaveLength(1);
        });

        test("does not find non-existing element", () => {
            const matches = findAfter(range, matchTagName("b"));

            expect(matches).toHaveLength(0);
        });
    });
});

describe("in a nested search", () => {
    const htmlNested = p("<u><b>before</b></u>within<u><b>after</b></u>");
    const rangeNested = nodeToChildNodeRange(htmlNested.childNodes[1]);

    describe("findBefore", () => {
        test("finds a nested element", () => {
            const matches = findBefore(rangeNested, matchTagName("b"));

            expect(matches).toHaveLength(1);
        });
    });

    describe("findAfter", () => {
        test("finds a nested element", () => {
            const matches = findAfter(rangeNested, matchTagName("b"));

            expect(matches).toHaveLength(1);
        });
    });
});
