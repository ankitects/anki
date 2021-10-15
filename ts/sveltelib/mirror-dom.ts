// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";
import type { Writable } from "svelte/store";
import storeSubscribe from "./store-subscribe";

const config = {
    childList: true,
    subtree: true,
    attributes: true,
    characterData: true,
};

interface DOMMirror {
    mirror(
        element: Element,
        params: { store: Writable<DocumentFragment> }
    ): { destroy(): void };
    preventResubscription(): () => void;
}

function getDOMMirror(): DOMMirror {
    const allowResubscription = writable(true);

    function preventResubscription() {
        allowResubscription.set(false);

        return () => {
            allowResubscription.set(true);
        };
    }

    function mirror(
        element: Element,
        { store }: { store: Writable<DocumentFragment> }
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

        /* do not update when focused as it will reset caret */
        element.addEventListener("focus", unsubscribe);

        const unsubResubscription = allowResubscription.subscribe(
            (allow: boolean): void => {
                if (allow) {
                    element.addEventListener("blur", subscribe);

                    const root = element.getRootNode() as Document | ShadowRoot;
                    if (root.activeElement !== element) {
                        subscribe();
                    }
                } else {
                    element.removeEventListener("blur", subscribe);
                }
            }
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
