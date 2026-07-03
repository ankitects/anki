// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { fragmentToString, nodeContainsInlineContent, nodeIsElement } from "@tslib/dom";
import { createDummyDoc } from "@tslib/parsing";

import { decoratedElements } from "../decorated-elements";

function adjustInputHTML(html: string): string {
    for (const component of decoratedElements) {
        html = component.toUndecorated(html);
    }

    return html;
}

function adjustInputFragment(fragment: DocumentFragment): void {
    if (nodeContainsInlineContent(fragment)) {
        fragment.appendChild(document.createElement("br"));
    }
}

export function storedToFragment(storedHTML: string): DocumentFragment {
    /* We need .createContextualFragment so that customElements are initialized */
    const fragment = document
        .createRange()
        .createContextualFragment(createDummyDoc(adjustInputHTML(storedHTML)));

    adjustInputFragment(fragment);
    return fragment;
}

function adjustOutputFragment(fragment: DocumentFragment): void {
    if (
        fragment.hasChildNodes()
        && nodeIsElement(fragment.lastChild!)
        && nodeContainsInlineContent(fragment)
        && fragment.lastChild!.tagName === "BR"
    ) {
        fragment.lastChild!.remove();
    }
}

function adjustOutputHTML(html: string): string {
    for (const component of decoratedElements) {
        html = component.toStored(html);
    }

    return html;
}

export function fragmentToStored(fragment: DocumentFragment): string {
    const clone = document.importNode(fragment, true);
    adjustOutputFragment(clone);

    const storedHTML = adjustOutputHTML(fragmentToString(clone));
    return storedHTML;
}
