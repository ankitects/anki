// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { createDummyDoc } from "@tslib/parsing";

const parser = new DOMParser();

function removeTag(element: HTMLElement, tagName: string): void {
    for (const elem of element.getElementsByTagName(tagName)) {
        elem.remove();
    }
}

const prohibitedTags = ["script", "link"];

/**
 * The use cases for using those tags in the field html are slim to none.
 * We want to make it easier to possibly display cards in an iframe in the future.
 */
function removeProhibitedTags(html: string): string {
    const doc = parser.parseFromString(createDummyDoc(html), "text/html");
    const body = doc.body;

    for (const tag of prohibitedTags) {
        removeTag(body, tag);
    }

    return doc.body.innerHTML;
}

export default removeProhibitedTags;
