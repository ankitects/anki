// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { unsurround } from "./unsurround";

const parser = new DOMParser();

function p(html: string): HTMLBodyElement {
    const parsed = parser.parseFromString(html, "text/html");
    return parsed.body as HTMLBodyElement;
}

describe("unsurround text", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b>test</b>");
    });

    test("normalizes nodes", () => {
        const range = new Range();
        range.selectNode(body.firstChild!);

        const { addedNodes, removedNodes, surroundedRange } = unsurround(
            range,
            document.createElement("b"),
            body,
        );

        expect(addedNodes).toHaveLength(0);
        expect(removedNodes).toHaveLength(1);
        expect(body).toHaveProperty("innerHTML", "test");
        expect(surroundedRange.toString()).toEqual("test");
    });
});

describe("unsurround element with surrounding text", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("11<b>22</b>33");
    });

    test("normalizes nodes", () => {
        const range = new Range();
        range.selectNode(body.firstElementChild!);

        const { addedNodes, removedNodes } = unsurround(
            range,
            document.createElement("b"),
            body,
        );

        expect(addedNodes).toHaveLength(0);
        expect(removedNodes).toHaveLength(1);
        expect(body).toHaveProperty("innerHTML", "112233");
        // expect(surroundedRange.toString()).toEqual("22");
    });
});

describe("unsurround from one element to another", () => {
    let body: HTMLBodyElement;

    beforeEach(() => {
        body = p("<b>111</b>222<b>333</b>");
    });

    test("unsurround whole", () => {
        const range = new Range();
        range.setStartBefore(body.children[0].firstChild!);
        range.setEndAfter(body.children[1].firstChild!);

        const { addedNodes, removedNodes } = unsurround(
            range,
            document.createElement("b"),
            body,
        );

        expect(addedNodes).toHaveLength(0);
        expect(removedNodes).toHaveLength(2);
        expect(body).toHaveProperty("innerHTML", "111222333");
        // expect(surroundedRange.toString()).toEqual("22");
    });
});

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

        const { addedNodes, removedNodes } = unsurround(
            range,
            document.createElement("b"),
            body,
        );

        expect(addedNodes).toHaveLength(1);
        expect(removedNodes).toHaveLength(1);
        expect(body).toHaveProperty("innerHTML", "<b>111</b><br><ul><li>222</li></ul>");
        // expect(surroundedRange.toString()).toEqual("222");
    });
});
