// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * decorated elements know three states:
 * - stored, which is stored in the DB, e.g. `\(\alpha + \beta\)`
 * - undecorated, which is displayed to the user in Codable, e.g. `<anki-mathjax>\alpha + \beta</anki-mathjax>`
 * - decorated, which is displayed to the user in Editable, e.g. `<anki-mathjax data-mathjax="\alpha + \beta"><img src="data:..."></anki-mathjax>`
 */

export interface DecoratedElement extends HTMLElement {
    /**
     * Transforms itself from undecorated to decorated state.
     * Should be called in connectedCallback.
     */
    decorate(): void;
    /**
     * Transforms itself from decorated to undecorated state.
     */
    undecorate(): void;
}

export interface DecoratedElementConstructor extends CustomElementConstructor {
    prototype: DecoratedElement;
    tagName: string;
    /**
     * Transforms elements in input HTML from undecorated to stored state.
     */
    toStored(undecorated: string): string;
    /**
     * Transforms elements in input HTML from stored to undecorated state.
     */
    toUndecorated(stored: string): string;
}

export class DefineArray extends Array {
    push(...elements: DecoratedElementConstructor[]): number {
        for (const element of elements) {
            customElements.define(element.tagName, element);
        }
        return super.push(...elements);
    }
}
