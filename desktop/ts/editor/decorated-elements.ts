// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { BLOCK_ELEMENTS } from "@tslib/dom";

import { CustomElementArray } from "../editable/decorated";
import { FrameElement } from "../editable/frame-element";
import { FrameEnd, FrameStart } from "../editable/frame-handle";
import { Mathjax } from "../editable/mathjax-element.svelte";
import { parsingInstructions } from "./plain-text-input";

const decoratedElements = new CustomElementArray();

function registerMathjax() {
    decoratedElements.push(Mathjax);
    parsingInstructions.push("<style>anki-mathjax { white-space: pre; }</style>");
}

function registerFrameElement() {
    customElements.define(FrameElement.tagName, FrameElement);
    customElements.define(FrameStart.tagName, FrameStart);
    customElements.define(FrameEnd.tagName, FrameEnd);

    /* This will ensure that they are not targeted by surrounding algorithms */
    BLOCK_ELEMENTS.push(FrameStart.tagName.toUpperCase());
    BLOCK_ELEMENTS.push(FrameEnd.tagName.toUpperCase());
}

registerMathjax();
registerFrameElement();

export { decoratedElements };
