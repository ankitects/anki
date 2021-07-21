// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import ImageHandle from "./ImageHandle.svelte";

import type { EditableContainer } from "./editable-container";
import type { Editable } from "./editable";
import type { Codable } from "./codable";

import { updateActiveButtons } from "./toolbar";
import { bridgeCommand } from "./lib";
import { onInput, onKey, onKeyUp } from "./input-handlers";
import { onFocus, onBlur } from "./focus-handlers";
import { nightModeKey } from "components/context-keys";

function onCutOrCopy(): void {
    bridgeCommand("cutOrCopy");
}

export class EditingArea extends HTMLDivElement {
    imageHandle: ImageHandle;
    editableContainer: EditableContainer;
    editable: Editable;
    codable: Codable;

    constructor() {
        super();
        this.className = "field";

        this.editableContainer = document.createElement("div", {
            is: "anki-editable-container",
        }) as EditableContainer;
        this.editable = document.createElement("anki-editable") as Editable;
        this.editableContainer.shadowRoot!.appendChild(this.editable);
        this.appendChild(this.editableContainer);

        const context = new Map();
        context.set(
            nightModeKey,
            document.documentElement.classList.contains("night-mode")
        );

        this.imageHandle = new ImageHandle({
            target: this,
            anchor: this.editableContainer,
            props: { container: this.editable },
            context,
        } as any);

        this.codable = document.createElement("textarea", {
            is: "anki-codable",
        }) as Codable;
        this.appendChild(this.codable);

        this.onPaste = this.onPaste.bind(this);
        this.showImageHandle = this.showImageHandle.bind(this);
    }

    get activeInput(): Editable | Codable {
        return this.codable.active ? this.codable : this.editable;
    }

    get ord(): number {
        return Number(this.getAttribute("ord"));
    }

    set fieldHTML(content: string) {
        this.activeInput.fieldHTML = content;
    }

    get fieldHTML(): string {
        return this.activeInput.fieldHTML;
    }

    connectedCallback(): void {
        this.addEventListener("keydown", onKey);
        this.addEventListener("keyup", onKeyUp);
        this.addEventListener("input", onInput);
        this.addEventListener("focusin", onFocus);
        this.addEventListener("focusout", onBlur);
        this.addEventListener("paste", this.onPaste);
        this.addEventListener("copy", onCutOrCopy);
        this.addEventListener("oncut", onCutOrCopy);
        this.addEventListener("mouseup", updateActiveButtons);
        this.editable.addEventListener("click", this.showImageHandle);
    }

    disconnectedCallback(): void {
        this.removeEventListener("keydown", onKey);
        this.removeEventListener("keyup", onKeyUp);
        this.removeEventListener("input", onInput);
        this.removeEventListener("focusin", onFocus);
        this.removeEventListener("focusout", onBlur);
        this.removeEventListener("paste", this.onPaste);
        this.removeEventListener("copy", onCutOrCopy);
        this.removeEventListener("oncut", onCutOrCopy);
        this.removeEventListener("mouseup", updateActiveButtons);
        this.editable.removeEventListener("click", this.showImageHandle);
    }

    initialize(color: string, content: string): void {
        this.setBaseColor(color);
        this.fieldHTML = content;
    }

    setBaseColor(color: string): void {
        this.editableContainer.setBaseColor(color);
    }

    quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    setBaseStyling(fontFamily: string, fontSize: string, direction: string): void {
        this.editableContainer.setBaseStyling(
            this.quoteFontFamily(fontFamily),
            fontSize,
            direction,
        );
    }

    isRightToLeft(): boolean {
        return this.editableContainer.isRightToLeft();
    }

    focus(): void {
        this.activeInput.focus();
    }

    blur(): void {
        this.activeInput.blur();
    }

    caretToEnd(): void {
        this.activeInput.caretToEnd();
    }

    hasFocus(): boolean {
        return document.activeElement?.closest(".field") === this;
    }

    getSelection(): Selection {
        const root = this.activeInput.getRootNode() as Document | ShadowRoot;
        return root.getSelection()!;
    }

    surroundSelection(before: string, after: string): void {
        this.activeInput.surroundSelection(before, after);
    }

    onEnter(event: KeyboardEvent): void {
        this.activeInput.onEnter(event);
    }

    onPaste(event: ClipboardEvent): void {
        this.activeInput.onPaste(event);
    }

    showImageHandle(event: MouseEvent): void {
        if (event.target instanceof HTMLImageElement) {
            (this.imageHandle as any).$set({
                image: event.target,
            });
        } else {
            (this.imageHandle as any).$set({
                image: null,
            });
        }
    }

    toggleHtmlEdit(): void {
        const hadFocus = this.hasFocus();

        if (this.codable.active) {
            this.fieldHTML = this.codable.teardown();
            this.editable.hidden = false;
        } else {
            this.editable.hidden = true;
            this.codable.setup(this.editable.fieldHTML);
        }

        if (hadFocus) {
            this.focus();
            this.caretToEnd();
        }
    }

    /**
     * @deprecated Use focus instead
     */
    focusEditable(): void {
        focus();
    }
    /**
     * @deprecated Use blur instead
     */
    blurEditable(): void {
        blur();
    }
}
