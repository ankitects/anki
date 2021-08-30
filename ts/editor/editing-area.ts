// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
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
    imageHandle: Promise<ImageHandle>;
    editableContainer: EditableContainer;
    editable: Editable;
    codable: Codable;

    constructor() {
        super();
        this.className = "field";

        this.editableContainer = document.createElement("div", {
            is: "anki-editable-container",
        }) as EditableContainer;

        const imageStyle = document.createElement("style");
        imageStyle.setAttribute("rel", "stylesheet");
        imageStyle.id = "imageHandleStyle";

        this.editable = document.createElement("anki-editable") as Editable;

        const context = new Map();
        context.set(
            nightModeKey,
            document.documentElement.classList.contains("night-mode")
        );

        let imageHandleResolve: (value: ImageHandle) => void;
        this.imageHandle = new Promise<ImageHandle>((resolve) => {
            imageHandleResolve = resolve;
        });

        imageStyle.addEventListener("load", () =>
            imageHandleResolve(
                new ImageHandle({
                    target: this,
                    anchor: this.editableContainer,
                    props: {
                        container: this.editable,
                        sheet: imageStyle.sheet,
                    },
                    context,
                } as any)
            )
        );

        this.editableContainer.shadowRoot!.appendChild(imageStyle);
        this.editableContainer.shadowRoot!.appendChild(this.editable);
        this.appendChild(this.editableContainer);

        this.codable = document.createElement("textarea", {
            is: "anki-codable",
        }) as Codable;
        this.appendChild(this.codable);

        this.onFocus = this.onFocus.bind(this);
        this.onBlur = this.onBlur.bind(this);
        this.onKey = this.onKey.bind(this);
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
        this.imageHandle.then(() => (this.activeInput.fieldHTML = content));
    }

    get fieldHTML(): string {
        return this.activeInput.fieldHTML;
    }

    connectedCallback(): void {
        this.addEventListener("keydown", this.onKey);
        this.addEventListener("keyup", onKeyUp);
        this.addEventListener("input", onInput);
        this.addEventListener("focusin", this.onFocus);
        this.addEventListener("focusout", this.onBlur);
        this.addEventListener("paste", this.onPaste);
        this.addEventListener("copy", onCutOrCopy);
        this.addEventListener("oncut", onCutOrCopy);
        this.addEventListener("mouseup", updateActiveButtons);
        this.editable.addEventListener("click", this.showImageHandle);
    }

    disconnectedCallback(): void {
        this.removeEventListener("keydown", this.onKey);
        this.removeEventListener("keyup", onKeyUp);
        this.removeEventListener("input", onInput);
        this.removeEventListener("focusin", this.onFocus);
        this.removeEventListener("focusout", this.onBlur);
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
            direction
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

    onFocus(event: FocusEvent): void {
        onFocus(event);
    }

    onBlur(event: FocusEvent): void {
        this.resetImageHandle();
        onBlur(event);
    }

    onEnter(event: KeyboardEvent): void {
        this.activeInput.onEnter(event);
    }

    onKey(event: KeyboardEvent): void {
        this.resetImageHandle();
        onKey(event);
    }

    onPaste(event: ClipboardEvent): void {
        this.resetImageHandle();
        this.activeInput.onPaste(event);
    }

    resetImageHandle(): void {
        this.imageHandle.then((imageHandle) =>
            (imageHandle as any).$set({
                activeImage: null,
            })
        );
    }

    showImageHandle(event: MouseEvent): void {
        if (event.target instanceof HTMLImageElement) {
            this.imageHandle.then((imageHandle) =>
                (imageHandle as any).$set({
                    activeImage: event.target,
                    isRtl: this.isRightToLeft(),
                })
            );
        } else {
            this.resetImageHandle();
        }
    }

    toggleHtmlEdit(): void {
        const hadFocus = this.hasFocus();

        if (this.codable.active) {
            this.fieldHTML = this.codable.teardown();
            this.editable.hidden = false;
        } else {
            this.resetImageHandle();
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
