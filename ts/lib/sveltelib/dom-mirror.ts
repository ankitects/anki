// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "@tslib/events";
import type { Writable } from "svelte/store";
import { writable } from "svelte/store";

import storeSubscribe from "./store-subscribe";

const config = {
    childList: true,
    subtree: true,
    attributes: true,
    characterData: true,
};

export type MirrorAction = (
    element: HTMLElement,
    params: { store: Writable<DocumentFragment> },
) => { destroy(): void };

interface DOMMirrorAPI {
    mirror: MirrorAction;
    preventResubscription(): () => void;
}

function cloneNode(node: Node): DocumentFragment {
    /**
     * Creates a deep clone
     * This seems to be less buggy than node.cloneNode(true)
     */
    const range = document.createRange();

    range.selectNodeContents(node);
    return range.cloneContents();
}

/**
 * Allows you to keep an element's inner HTML bidirectionally
 * in sync with a store containing a DocumentFragment.
 * While the element has focus, this connection is tethered.
 * In practice, this will sync changes from PlainTextInput to RichTextInput.
 */
function useDOMMirror(): DOMMirrorAPI {
    const allowResubscription = writable(true);

    function preventResubscription() {
        allowResubscription.set(false);

        return () => {
            allowResubscription.set(true);
        };
    }

    function mirror(
        element: HTMLElement,
        { store }: { store: Writable<DocumentFragment> },
    ): { destroy(): void } {
        function saveHTMLToStore(): void {
            store.set(cloneNode(element));
        }

        const observer = new MutationObserver(saveHTMLToStore);
        observer.observe(element, config);

        function mirrorToElement(node: Node): void {
            observer.disconnect();
            // element.replaceChildren(...node.childNodes); // TODO use once available
            while (element.firstChild) {
                element.firstChild.remove();
            }

            while (node.firstChild) {
                element.appendChild(node.firstChild);
            }
            observer.observe(element, config);
        }

        function mirrorFromFragment(fragment: DocumentFragment): void {
            mirrorToElement(cloneNode(fragment));
        }

        const { subscribe, unsubscribe } = storeSubscribe(
            store,
            mirrorFromFragment,
            false,
        );

        /* do not update when focused as it will reset caret */
        const removeFocus = on(element, "focus", unsubscribe);
        let removeBlur: (() => void) | undefined;

        const unsubResubscription = allowResubscription.subscribe(
            (allow: boolean): void => {
                if (allow) {
                    if (!removeBlur) {
                        removeBlur = on(element, "blur", subscribe);
                    }

                    const root = element.getRootNode() as Document | ShadowRoot;

                    if (root.activeElement !== element) {
                        subscribe();
                    }
                } else if (removeBlur) {
                    removeBlur();
                    removeBlur = undefined;
                }
            },
        );

        return {
            destroy() {
                observer.disconnect();

                removeFocus();
                removeBlur?.();

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

export default useDOMMirror;
