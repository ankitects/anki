// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "mathjax/es5/tex-svg-full";

import { on } from "../lib/events";
import { placeCaretBefore, placeCaretAfter } from "../domlib/place-caret";
import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
import { FrameElement, frameElement } from "./frame-element";

import Mathjax_svelte from "./Mathjax.svelte";

const mathjaxTagPattern =
    /<anki-mathjax(?:[^>]*?block="(.*?)")?[^>]*?>(.*?)<\/anki-mathjax>/gsu;

const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;
const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;

export const Mathjax: DecoratedElementConstructor = class Mathjax
    extends HTMLElement
    implements DecoratedElement
{
    static tagName = "anki-mathjax";

    static toStored(undecorated: string): string {
        return undecorated.replace(
            mathjaxTagPattern,
            (_match: string, block: string | undefined, text: string) => {
                return typeof block === "string" && block !== "false"
                    ? `\\[${text}\\]`
                    : `\\(${text}\\)`;
            },
        );
    }

    static toUndecorated(stored: string): string {
        return stored
            .replace(
                mathjaxBlockDelimiterPattern,
                (_match: string, text: string) =>
                    `<${Mathjax.tagName} block="true">${text}</${Mathjax.tagName}>`,
            )
            .replace(
                mathjaxInlineDelimiterPattern,
                (_match: string, text: string) =>
                    `<${Mathjax.tagName}>${text}</${Mathjax.tagName}>`,
            );
    }

    block = false;
    frame?: FrameElement;
    component?: Mathjax_svelte;

    static get observedAttributes(): string[] {
        return ["block", "data-mathjax"];
    }

    connectedCallback(): void {
        this.decorate();
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
                this.component?.$set({ mathjax: newValue });
                break;
        }
    }

    removeMoveInStart?: () => void;
    removeMoveInEnd?: () => void;

    decorate(): void {
        this.frame =
            this.frame ??
            this.parentElement!.tagName === FrameElement.tagName.toUpperCase()
                ? (this.parentElement as FrameElement)
                : frameElement(this, this.block);

        if (this.hasAttribute("decorated")) {
            return;
        }

        this.frame.setAttribute("block", String(this.block));

        const mathjax = (this.dataset.mathjax = this.innerText);
        this.innerHTML = "";
        this.style.whiteSpace = "normal";

        this.component = new Mathjax_svelte({
            target: this,
            props: {
                mathjax,
                block: this.block,
                fontSize: 20,
            },
        });

        if (this.hasAttribute("focusonmount")) {
            this.component.moveCaretAfter();
        }

        this.setAttribute("contentEditable", "false");
        this.setAttribute("decorated", "true");

        this.removeMoveInStart = on(
            this.frame,
            "moveinstart" as keyof HTMLElementEventMap,
            () => this.component!.selectAll(),
        );
        this.removeMoveInEnd = on(
            this.frame,
            "moveinend" as keyof HTMLElementEventMap,
            () => this.component!.selectAll(),
        );
    }

    undecorate(): void {
        if (!this.hasAttribute("decorated")) {
            return;
        }

        this.frame =
            this.frame ??
            this.parentElement?.tagName === FrameElement.tagName.toUpperCase()
                ? (this.parentElement! as FrameElement)
                : undefined;
        this.frame?.replaceWith(this);
        this.frame = undefined;

        this.removeMoveInStart?.();
        this.removeMoveInStart = undefined;
        this.removeMoveInEnd?.();
        this.removeMoveInEnd = undefined;

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
