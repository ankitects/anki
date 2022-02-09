// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeToChildNodeRange } from "./child-node-range";
import { findAfter, findBefore } from "./find-adjacent";
import { easyBold, easyItalic, p, u, b, t, div } from "./test-utils";

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
    describe("single top-level match", () => {
        const within = t("within");
        div(u(b(t("before"))), within, u(b(t("after"))));

        const range = nodeToChildNodeRange(within);

        test("findBefore", () => {
            const matches = findBefore(range, easyBold.matcher);
            expect(matches).toHaveLength(1);
        });

        test("findAfter", () => {
            const matches = findAfter(range, easyBold.matcher);
            expect(matches).toHaveLength(1);
        });
    });

    describe("consecutive top-level", () => {
        const within = t("within");
        div(u(b(t("before"))), u(b(t("before"))), within, u(b(t("after"))), u(b(t("after"))));

        const range = nodeToChildNodeRange(within);

        test("findBefore", () => {
            const matches = findBefore(range, easyBold.matcher);
            expect(matches).toHaveLength(2);
        });

        test("findAfter", () => {
            const matches = findAfter(range, easyBold.matcher);
            expect(matches).toHaveLength(2);
        });
    });

//     describe("top-level consecutive within", () => {
//         const within = t("within");
//         div(u(b(t("before")), b(t("before"))), within, u(b(t("after")), b(t("after"))));

//         const range = nodeToChildNodeRange(within);

//         test("findBefore", () => {
//             const matches = findBefore(range, easyBold.matcher);
//             expect(matches).toHaveLength(2);
//         });

//         test("findAfter", () => {
//             const matches = findAfter(range, easyBold.matcher);
//             expect(matches).toHaveLength(2);
//         });
//     });

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
