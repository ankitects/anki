// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { beforeEach, describe, expect, test } from "vitest";

import { unsurround } from "./surround";
import { easyBold, p } from "./test-utils";

describe("unsurround text", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b>test</b>");
    });

    test("normalizes nodes", () => {
        const range = new Range();
        range.selectNode(body.firstChild!);

        unsurround(range, body, easyBold);
        expect(body).toHaveProperty("innerHTML", "test");
        // expect(surroundedRange.toString()).toEqual("test");
    });
});

// describe("unsurround element and text", () => {
//     let body: HTMLBodyElement;

//     beforeEach(() => {
//         body = p("<b>before</b>after");
//     });

//     test("normalizes nodes", () => {
//         const range = new Range();
//         range.setStartBefore(body.childNodes[0].firstChild!);
//         range.setEndAfter(body.childNodes[1]);

//         const surroundedRange = unsurround(range, body, easyBold);

//         expect(body).toHaveProperty("innerHTML", "beforeafter");
//         expect(surroundedRange.toString()).toEqual("beforeafter");
//     });
// });

describe("unsurround element with surrounding text", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("11<b>22</b>33");
    });

    test("normalizes nodes", () => {
        const range = new Range();
        range.selectNode(body.firstElementChild!);

        unsurround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "112233");
        // expect(surroundedRange.toString()).toEqual("22");
    });
});

// describe("unsurround from one element to another", () => {
//     let body: HTMLBodyElement;

//     beforeEach(() => {
//         body = p("<b>111</b>222<b>333</b>");
//     });

//     test("unsurround whole", () => {
//         const range = new Range();
//         range.setStartBefore(body.children[0].firstChild!);
//         range.setEndAfter(body.children[1].firstChild!);

//         unsurround(range, body, easyBold);

//         expect(body).toHaveProperty("innerHTML", "111222333");
//         // expect(surroundedRange.toString()).toEqual("22");
//     });
// });

// describe("unsurround text portion of element", () => {
//     let body: HTMLBodyElement;

//     beforeEach(() => {
//         body = p("<b>112233</b>");
//     });

//     test("normalizes nodes", () => {
//         const range = new Range();
//         range.setStart(body.firstChild!, 2);
//         range.setEnd(body.firstChild!, 4);

//         const { addedNodes, removedNodes } = unsurround(
//             range,
//             document.createElement("b"),
//             body,
//         );

//         expect(addedNodes).toHaveLength(2);
//         expect(removedNodes).toHaveLength(1);
//         expect(body).toHaveProperty("innerHTML", "<b>11</b>22<b>33</b>");
//         // expect(surroundedRange.toString()).toEqual("22");
//     });
// });

describe("with bold around block item", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b>111<br><ul><li>222</li></ul></b>");
    });

    test("unsurround list item", () => {
        const range = new Range();
        range.selectNodeContents(
            body.firstChild!.childNodes[2].firstChild!.firstChild!,
        );

        unsurround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "<b>111</b><br><ul><li>222</li></ul>");
        // expect(surroundedRange.toString()).toEqual("222");
    });
});

describe("with two double nested and one single nested", () => {
    // test("unsurround one double and single nested", () => {
    //     const body = p("<b><b>aaa</b><b>bbb</b>ccc</b>");
    //     const range = new Range();
    //     range.setStartBefore(body.firstChild!.childNodes[1].firstChild!);
    //     range.setEndAfter(body.firstChild!.childNodes[2]);

    //     const surroundedRange = unsurround(
    //         range,
    //         body,
    //         easyBold,
    //     );

    //     expect(body).toHaveProperty("innerHTML", "<b>aaa</b>bbbccc");
    //     expect(surroundedRange.toString()).toEqual("bbbccc");
    // });

    test("unsurround single and one double nested", () => {
        const body = p("<b>aaa<b>bbb</b><b>ccc</b></b>");
        const range = new Range();
        range.setStartBefore(body.firstChild!.firstChild!);
        range.setEndAfter(body.firstChild!.childNodes[1].firstChild!);

        const surroundedRange = unsurround(range, body, easyBold);
        expect(body).toHaveProperty("innerHTML", "aaabbb<b>ccc</b>");
        expect(surroundedRange.toString()).toEqual("aaabbb");
    });
});

describe("unsurround additional regression cases", () => {
    test("selection outside bold containing BR is a no-op", () => {
        const body = p("<b>A<br>B</b>C");
        const range = new Range();
        const textNode = body.childNodes[1];
        range.setStart(textNode, 0);
        range.setEnd(textNode, 1);
        unsurround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "<b>A<br>B</b>C");
    });

    test("can unsurround trailing text from bold containing BR", () => {
        const body = p("<b>A<br>B</b>");
        const range = new Range();
        const bold = body.querySelector("b")!;
        const textNodeB = bold.childNodes[2];
        range.selectNodeContents(textNodeB);
        unsurround(range, body, easyBold);

        expect(body).toHaveProperty("innerHTML", "<b>A</b><br>B");
    });
});
