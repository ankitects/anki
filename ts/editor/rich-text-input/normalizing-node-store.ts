// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { DecoratedElement } from "../../editable/decorated";
import type { NodeStore } from "../../sveltelib/node-store";
import { nodeStore } from "../../sveltelib/node-store";
import { decoratedElements } from "../decorated-elements";

function normalizeFragment(fragment: DocumentFragment): void {
    fragment.normalize();

    for (const decorated of decoratedElements) {
        for (
            const element of fragment.querySelectorAll(
                decorated.tagName,
            ) as NodeListOf<DecoratedElement>
        ) {
            element.undecorate();
        }
    }
}

function getStore(): NodeStore<DocumentFragment> {
    return nodeStore<DocumentFragment>(undefined, normalizeFragment);
}

export default getStore;
