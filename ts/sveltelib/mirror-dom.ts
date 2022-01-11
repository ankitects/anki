// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";
import type { Writable } from "svelte/store";
import storeSubscribe from "./store-subscribe";
// import { getSelection } from "../lib/cross-browser";

const config = {
    childList: true,
    subtree: true,
    attributes: true,
    characterData: true,
};

export type MirrorAction = (
    element: Element,
    params: { store: Writable<DocumentFragment> },
) => { destroy(): void };

interface DOMMirrorAPI {
    mirror: MirrorAction;
    preventResubscription(): () => void;
}

/**
 * Allows you to keep an element's inner HTML bidirectionally
 * in sync with a store containing a DocumentFragment.
 * While the element has focus, this connection is tethered.
 */
function getDOMMirror(): DOMMirrorAPI {
    const allowResubscription = writable(true);

    function preventResubscription() {
        allowResubscription.set(false);

        return () => {
            allowResubscription.set(true);
        };
    }

    function mirror(
        element: Element,
        { store }: { store: Writable<DocumentFragment> },
    ): { destroy(): void } {
        function saveHTMLToStore(): void {
            const range = document.createRange();

            range.selectNodeContents(element);
            const contents = range.cloneContents();

            store.set(contents);
        }

        const observer = new MutationObserver(saveHTMLToStore);
        observer.observe(element, config);

        function mirrorToNode(node: Node): void {
            observer.disconnect();
            const clone = node.cloneNode(true);

            /* TODO use Element.replaceChildren */
            while (element.firstChild) {
                element.firstChild.remove();
            }

            while (clone.firstChild) {
                element.appendChild(clone.firstChild);
            }

            observer.observe(element, config);
        }

        const { subscribe, unsubscribe } = storeSubscribe(store, mirrorToNode);
        // const selection = getSelection(element)!;

        function doSubscribe(): void {
            // Might not be needed after all:
            // /**
            //  * Focused element and caret are two independent things in the browser.
            //  * When the ContentEditable calls blur, it will still have the selection inside of it.
            //  * Some elements (e.g. FrameElement) need to figure whether the intended focus is still
            //  * in the contenteditable or elsewhere because they might change the selection.
            //  */
            // selection.removeAllRanges();

            subscribe();
        }

        /* do not update when focused as it will reset caret */
        element.addEventListener("focus", unsubscribe);

        const unsubResubscription = allowResubscription.subscribe(
            (allow: boolean): void => {
                if (allow) {
                    element.addEventListener("blur", doSubscribe);

                    const root = element.getRootNode() as Document | ShadowRoot;

                    if (root.activeElement !== element) {
                        doSubscribe();
                    }
                } else {
                    element.removeEventListener("blur", doSubscribe);
                }
            },
        );

        return {
            destroy() {
                observer.disconnect();

                // removes blur event listener
                allowResubscription.set(false);
                element.removeEventListener("focus", unsubscribe);

                unsubscribe();
                unsubResubscription();
            },
        };
    }

    return {
        mirror,
        preventResubscription,
    };
}

export default getDOMMirror;
