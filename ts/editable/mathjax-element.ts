// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "@tslib/events";

import { placeCaretAfter, placeCaretBefore } from "$lib/domlib/place-caret";

import { mount } from "svelte";
import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
import { FrameElement, frameElement } from "./frame-element";
import Mathjax_svelte from "./Mathjax.svelte";

const mathjaxTagPattern = /<anki-mathjax(?:[^>]*?block="(.*?)")?[^>]*?>(.*?)<\/anki-mathjax>/gsu;

const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;
const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;

function trimBreaks(text: string): string {
    return text
        .replace(/<br[ ]*\/?>/gsu, "\n")
        .replace(/^\n*/, "")
        .replace(/\n*$/, "");
}

export const mathjaxConfig = {
    enabled: true,
};

interface MathjaxProps {
    mathjax: string;
    block: boolean;
    fontSize: number;
}

export const Mathjax: DecoratedElementConstructor = class Mathjax extends HTMLElement implements DecoratedElement {
    static tagName = "anki-mathjax";

    static toStored(undecorated: string): string {
        const stored = undecorated.replace(
            mathjaxTagPattern,
            (_match: string, block: string | undefined, text: string) => {
                const trimmed = trimBreaks(text);
                return typeof block === "string" && block !== "false"
                    ? `\\[${trimmed}\\]`
                    : `\\(${trimmed}\\)`;
            },
        );

        return stored;
    }

    static toUndecorated(stored: string): string {
        if (!mathjaxConfig.enabled) {
            return stored;
        }
        return stored
            .replace(mathjaxBlockDelimiterPattern, (_match: string, text: string) => {
                const trimmed = trimBreaks(text);
                return `<${Mathjax.tagName} block="true">${trimmed}</${Mathjax.tagName}>`;
            })
            .replace(mathjaxInlineDelimiterPattern, (_match: string, text: string) => {
                const trimmed = trimBreaks(text);
                return `<${Mathjax.tagName}>${trimmed}</${Mathjax.tagName}>`;
            });
    }

    block = false;
    frame?: FrameElement;
    component?: Record<string, any> | null;
    props?: MathjaxProps;

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
                if (this.props) { this.props.block = this.block; }
                this.frame?.setAttribute("block", String(this.block));
                break;

            case "data-mathjax":
                if (typeof newValue !== "string") {
                    return;
                }
                if (this.props) { this.props.mathjax = newValue; }
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

        this.dataset.mathjax = this.innerHTML;
        this.innerHTML = "";
        this.style.whiteSpace = "normal";

        const props = $state<MathjaxProps>({
            mathjax: this.dataset.mathjax,
            block: this.block,
            fontSize: 20,
        });

        const component = mount(Mathjax_svelte, {
            target: this,
            props,
        });

        this.component = component;
        this.props = props;

        if (this.hasAttribute("focusonmount")) {
            let position: [number, number] | undefined = undefined;

            if (this.getAttribute("focusonmount")!.length > 0) {
                position = this.getAttribute("focusonmount")!
                    .split(",")
                    .map(Number) as [number, number];
            }

            this.component.moveCaretAfter(position);
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

        this.removeMoveInEnd = on(this, "moveinend" as keyof HTMLElementEventMap, () => this.component!.selectAll());
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
