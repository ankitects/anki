// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { beforeEach, describe, expect, test } from "vitest";

import { surround } from "./surround";
import { easyBold, easyUnderline, p } from "./test-utils";

describe("surround text", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("111222");
    });

    test("all text", () => {
        const range = new Range();
        range.selectNode(body.firstChild!);

        const surroundedRange = surround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "<b>111222</b>");
        expect(surroundedRange.toString()).toEqual("111222");
    });

    test("first half", () => {
        const range = new Range();
        range.setStart(body.firstChild!, 0);
        range.setEnd(body.firstChild!, 3);

        const surroundedRange = surround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "<b>111</b>222");
        expect(surroundedRange.toString()).toEqual("111");
    });

    test("second half", () => {
        const range = new Range();
        range.setStart(body.firstChild!, 3);
        range.setEnd(body.firstChild!, 6);

        const surroundedRange = surround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "111<b>222</b>");
        expect(surroundedRange.toString()).toEqual("222");
    });
});

describe("surround text next to nested", () => {
    describe("before", () => {
        let body: HTMLBodyElement;

        beforeEach(() => {
            body = p("before<u><b>after</b></u>");
        });

        test("enlarges bottom tag of nested", () => {
            const range = new Range();
            range.selectNode(body.firstChild!);
            surround(range, body, easyUnderline);

            expect(body).toHaveProperty("innerHTML", "<u>before<b>after</b></u>");
            // expect(surroundedRange.toString()).toEqual("before");
        });

        test("moves nested down", () => {
            const range = new Range();
            range.selectNode(body.firstChild!);
            surround(range, body, easyBold);

            expect(body).toHaveProperty("innerHTML", "<b>before<u>after</u></b>");
            // expect(surroundedRange.toString()).toEqual("before");
        });
    });

    describe("after", () => {
        let body: HTMLBodyElement;

        beforeEach(() => {
            body = p("<u><b>before</b></u>after");
        });

        test("enlarges bottom tag of nested", () => {
            const range = new Range();
            range.selectNode(body.childNodes[1]);
            surround(range, body, easyUnderline);

            expect(body).toHaveProperty("innerHTML", "<u><b>before</b>after</u>");
            // expect(surroundedRange.toString()).toEqual("after");
        });

        test("moves nested down", () => {
            const range = new Range();
            range.selectNode(body.childNodes[1]);
            surround(range, body, easyBold);

            expect(body).toHaveProperty("innerHTML", "<b><u>before</u>after</b>");
            // expect(surroundedRange.toString()).toEqual("after");
        });
    });

    describe("two nested", () => {
        let body: HTMLBodyElement;

        beforeEach(() => {
            body = p("aaa<i><b>bbb</b></i><i><b>ccc</b></i>");
        });

        test("extends to both", () => {
            const range = new Range();
            range.selectNode(body.firstChild!);
            surround(range, body, easyBold);

            expect(body).toHaveProperty("innerHTML", "<b>aaa<i>bbb</i><i>ccc</i></b>");
            // expect(surroundedRange.toString()).toEqual("aaa");
        });
    });
});

describe("surround across block element", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("Before<br><ul><li>First</li><li>Second</li></ul>");
    });

    test("does not insert empty elements", () => {
        const range = new Range();
        range.setStartBefore(body.firstChild!);
        range.setEndAfter(body.lastChild!);
        const surroundedRange = surround(range, body, easyBold);

        expect(body).toHaveProperty(
            "innerHTML",
            "<b>Before</b><br><ul><li><b>First</b></li><li><b>Second</b></li></ul>",
        );
        expect(surroundedRange.toString()).toEqual("BeforeFirstSecond");
    });
});

describe("next to nested", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("111<b>222<b>333<b>444</b></b></b>555");
    });

    test("surround after", () => {
        const range = new Range();
        range.selectNode(body.lastChild!);
        surround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "111<b>222333444555</b>");
        // expect(surroundedRange.toString()).toEqual("555");
    });
});

describe("next to element with nested non-matching", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("111<b>222<i>333<i>444</i></i></b>555");
    });

    test("surround after", () => {
        const range = new Range();
        range.selectNode(body.lastChild!);
        surround(range, body, easyBold);

        expect(body).toHaveProperty(
            "innerHTML",
            "111<b>222<i>333<i>444</i></i>555</b>",
        );
        // expect(surroundedRange.toString()).toEqual("555");
    });
});

describe("next to element with text element text", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("111<b>222<b>333</b>444</b>555");
    });

    test("surround after", () => {
        const range = new Range();
        range.selectNode(body.lastChild!);
        surround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "111<b>222333444555</b>");
        // expect(surroundedRange.toString()).toEqual("555");
    });
});

describe("surround elements that already have nested block", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b>1<b>2</b></b><br>");
    });

    test("normalizes nodes", () => {
        const range = new Range();
        range.selectNode(body.children[0]);

        surround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "<b>12</b><br>");
        // expect(surroundedRange.toString()).toEqual("12");
    });
});

describe("surround complicated nested structure", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<i>1</i><b><i>2</i>3<i>4</i></b><i>5</i>");
    });

    test("normalize nodes", () => {
        const range = new Range();
        range.setStartBefore(body.firstElementChild!.firstChild!);
        range.setEndAfter(body.lastElementChild!.firstChild!);

        const surroundedRange = surround(range, body, easyBold);

        expect(body).toHaveProperty(
            "innerHTML",
            "<b><i>1</i><i>2</i>3<i>4</i><i>5</i></b>",
        );
        expect(surroundedRange.toString()).toEqual("12345");
    });
});

describe("skips over empty elements", () => {
    describe("joins two newly created", () => {
        let body: HTMLBodyElement;

        beforeEach(() => {
            body = p("before<br>after");
        });

        test("normalize nodes", () => {
            const range = new Range();
            range.setStartBefore(body.firstChild!);
            range.setEndAfter(body.childNodes[2]!);

            const surroundedRange = surround(range, body, easyBold);

            expect(body).toHaveProperty("innerHTML", "<b>before<br>after</b>");
            expect(surroundedRange.toString()).toEqual("beforeafter");
        });
    });

    describe("joins with already existing", () => {
        let body: HTMLBodyElement;

        beforeEach(() => {
            body = p("before<br><b>after</b>");
        });

        test("normalize nodes", () => {
            const range = new Range();
            range.selectNode(body.firstChild!);

            surround(range, body, easyBold);

            expect(body).toHaveProperty("innerHTML", "<b>before<br>after</b>");
            // expect(surroundedRange.toString()).toEqual("before");
        });

        test("normalize node contents", () => {
            const range = new Range();
            range.selectNodeContents(body.firstChild!);

            const surroundedRange = surround(range, body, easyBold);

            expect(body).toHaveProperty("innerHTML", "<b>before<br>after</b>");
            expect(surroundedRange.toString()).toEqual("before");
        });
    });
});

// TODO
// describe("special cases when surrounding within range.commonAncestor", () => {
//     // these are not vital but rather define how the algorithm works in edge cases

//     test("does not normalize beyond level of contained text nodes", () => {
//         const body = p("<b>before<u>nested</u>after</b>");
//         const range = new Range();
//         range.selectNode(body.firstChild!.childNodes[1].firstChild!);

//         const { addedNodes, removedNodes, surroundedRange } = surround(
//             range,
//             body,
//             easyBold,
//         );

//         expect(addedNodes).toHaveLength(1);
//         expect(removedNodes).toHaveLength(0);
//         expect(body).toHaveProperty(
//             "innerHTML",
//             "<b>before<b><u>nested</u></b>after</b>",
//         );
//         expect(surroundedRange.toString()).toEqual("nested");
//     });

//     test("does not normalize beyond level of contained text nodes 2", () => {
//         const body = p("<b>aaa<b>bbb</b><b>ccc</b></b>");
//         const range = new Range();
//         range.setStartBefore(body.firstChild!.firstChild!);
//         range.setEndAfter(body.firstChild!.childNodes[1].firstChild!);

//         const { addedNodes, removedNodes } = surround(range, body, easyBold);

//         expect(body).toHaveProperty("innerHTML", "<b><b>aaabbbccc</b></b>");
//         expect(addedNodes).toHaveLength(1);
//         expect(removedNodes).toHaveLength(2);
//         // expect(surroundedRange.toString()).toEqual("aaabbb"); // is aaabbbccc instead
//     });

//     test("does normalize beyond level of contained text nodes", () => {
//         const body = p("<b><b>aaa</b><b><b>bbb</b><b>ccc</b></b></b>");
//         const range = new Range();
//         range.setStartBefore(body.firstChild!.childNodes[1].firstChild!.firstChild!);
//         range.setEndAfter(body.firstChild!.childNodes[1].childNodes[1].firstChild!);

//         const { addedNodes, removedNodes } = surround(range, body, easyBold);

//         expect(body).toHaveProperty("innerHTML", "<b><b>aaabbbccc</b></b>");
//         expect(addedNodes).toHaveLength(1);
//         expect(removedNodes).toHaveLength(4);
//         // expect(surroundedRange.toString()).toEqual("aaabbb"); // is aaabbbccc instead
//     });

//     test("does remove even if there is already equivalent surrounding in place", () => {
//         const body = p("<b>before<b><u>nested</u></b>after</b>");
//         const range = new Range();
//         range.selectNode(body.firstChild!.childNodes[1].firstChild!.firstChild!);

//         const { addedNodes, removedNodes, surroundedRange } = surround(
//             range,
//             body,
//             easyBold,
//         );

//         expect(addedNodes).toHaveLength(1);
//         expect(removedNodes).toHaveLength(1);
//         expect(body).toHaveProperty(
//             "innerHTML",
//             "<b>before<b><u>nested</u></b>after</b>",
//         );
//         expect(surroundedRange.toString()).toEqual("nested");
//     });
// });
