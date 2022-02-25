// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "mathjax/es5/tex-svg-full";

import { placeCaretAfter, placeCaretBefore } from "../domlib/place-caret";
import { on } from "../lib/events";
import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
import { FrameElement, frameElement } from "./frame-element";
import Mathjax_svelte from "./Mathjax.svelte";

const mathjaxTagPattern =
    /<anki-mathjax(?:[^>]*?block="(.*?)")?[^>]*?>(.*?)<\/anki-mathjax>/gsu;

const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;
const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;

/**
 * If the user enters the Mathjax with delimiters, "<" and ">" will
 * be first translated to entities.
 */
function translateEntitiesToMathjax(value: string) {
    return value.replace(/&lt;/g, "{\\lt}").replace(/&gt;/g, "{\\gt}");
}

export const Mathjax: DecoratedElementConstructor = class Mathjax
    extends HTMLElement
    implements DecoratedElement
{
    static tagName = "anki-mathjax";

    static toStored(undecorated: string): string {
        const stored = undecorated.replace(
            mathjaxTagPattern,
            (_match: string, block: string | undefined, text: string) => {
                return typeof block === "string" && block !== "false"
                    ? `\\[${text}\\]`
                    : `\\(${text}\\)`;
            },
        );

        return stored;
    }

    static toUndecorated(stored: string): string {
        return stored
            .replace(mathjaxBlockDelimiterPattern, (_match: string, text: string) => {
                const escaped = translateEntitiesToMathjax(text);
                return `<${Mathjax.tagName} block="true">${escaped}</${Mathjax.tagName}>`;
            })
            .replace(mathjaxInlineDelimiterPattern, (_match: string, text: string) => {
                const escaped = translateEntitiesToMathjax(text);
                return `<${Mathjax.tagName}>${escaped}</${Mathjax.tagName}>`;
            });
    }

    block = false;
    frame?: FrameElement;
    component?: Mathjax_svelte;

    static get observedAttributes(): string[] {
        return ["block", "data-mathjax"];
    }

    connectedCallback(): void {
        this.decorate();
        this.addEventListeners();
    }

    disconnectedCallback(): void {
        this.removeEventListeners();
    }

    attributeChangedCallback(name: string, old: string, newValue: string): void {
        if (newValue === old) {
            return;
        }

        switch (name) {
            case "block":
                this.block = newValue !== "false";
                this.component?.$set({ block: this.block });
                this.frame?.setAttribute("block", String(this.block));
                break;

            case "data-mathjax":
                if (!newValue) {
                    return;
                }
                this.component?.$set({ mathjax: newValue });
                break;
        }
    }

    decorate(): void {
        if (this.hasAttribute("decorated")) {
            this.undecorate();
        }

        if (this.parentElement?.tagName === FrameElement.tagName.toUpperCase()) {
            this.frame = this.parentElement as FrameElement;
        } else {
            frameElement(this, this.block);
            /* Framing will place this element inside of an anki-frame element,
             * causing the connectedCallback to be called again.
             * If we'd continue decorating at this point, we'd loose all the information */
            return;
        }

        this.dataset.mathjax = this.innerText;
        this.innerHTML = "";
        this.style.whiteSpace = "normal";

        this.component = new Mathjax_svelte({
            target: this,
            props: {
                mathjax: this.dataset.mathjax,
                block: this.block,
                fontSize: 20,
            },
        });

        if (this.hasAttribute("focusonmount")) {
            this.component.moveCaretAfter();
        }

        this.setAttribute("contentEditable", "false");
        this.setAttribute("decorated", "true");
    }

    undecorate(): void {
        if (this.parentElement?.tagName === FrameElement.tagName.toUpperCase()) {
            this.parentElement.replaceWith(this);
        }

        this.innerHTML = this.dataset.mathjax ?? "";
        delete this.dataset.mathjax;
        this.removeAttribute("style");
        this.removeAttribute("focusonmount");

        if (this.block) {
            this.setAttribute("block", "true");
        } else {
            this.removeAttribute("block");
        }

        this.removeAttribute("contentEditable");
        this.removeAttribute("decorated");
    }

    removeMoveInStart?: () => void;
    removeMoveInEnd?: () => void;

    addEventListeners(): void {
        this.removeMoveInStart = on(
            this,
            "moveinstart" as keyof HTMLElementEventMap,
            () => this.component!.selectAll(),
        );

        this.removeMoveInEnd = on(this, "moveinend" as keyof HTMLElementEventMap, () =>
            this.component!.selectAll(),
        );
    }

    removeEventListeners(): void {
        this.removeMoveInStart?.();
        this.removeMoveInStart = undefined;

        this.removeMoveInEnd?.();
        this.removeMoveInEnd = undefined;
    }

    placeCaretBefore(): void {
        if (this.frame) {
            placeCaretBefore(this.frame);
        }
    }

    placeCaretAfter(): void {
        if (this.frame) {
            placeCaretAfter(this.frame);
        }
    }
};
