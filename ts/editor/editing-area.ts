// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import type ImageHandle from "./ImageHandle.svelte";
// import type MathjaxHandle from "./MathjaxHandle.svelte";
// import EditableContainer from "editable/EditableContainer.svelte";

// import type { Codable } from "./codable";

// import { updateActiveButtons } from "./toolbar";
import { bridgeCommand } from "./lib";
import { onInput, onKey, onKeyUp } from "./input-handlers";
import { deferFocusDown, saveFieldIfFieldChanged } from "./focus-handlers";
import { nightModeKey } from "components/context-keys";
import { decoratedComponents } from "editable/decorated";

function onCutOrCopy(): void {
    bridgeCommand("cutOrCopy");
}

export class EditingArea extends HTMLDivElement {
    imageHandle: Promise<ImageHandle>;
    // mathjaxHandle: MathjaxHandle;
    // editableContainer: EditableContainer;
    // editable: Editable;
    codable: any;

    constructor() {
        super();
        this.className = "field";

        // new EditableContainer({
        //     target: this,
        // });

        // this.editable = document.createElement("anki-editable") as Editable;
        // this.editableContainer.shadowRoot!.appendChild(this.editable);
        // this.appendChild(this.editableContainer);

        const context = new Map();
        context.set(
            nightModeKey,
            document.documentElement.classList.contains("night-mode")
        );

        let imageHandleResolve: (value: ImageHandle) => void;
        this.imageHandle = new Promise<ImageHandle>(
            (resolve) => (imageHandleResolve = resolve)
        );

        // this.editableContainer.imagePromise.then(() =>
        //     imageHandleResolve(
        //         new ImageHandle({
        //             target: this,
        //             anchor: this.editableContainer,
        //             props: {
        //                 container: this.editable,
        //                 sheet: this.editableContainer.imageStyle.sheet,
        //             },
        //             context,
        //         } as any)
        //     )
        // );

        // this.mathjaxHandle = new MathjaxHandle({
        //     target: this,
        //     anchor: this.editableContainer,
        //     props: {
        //         container: this.editable,
        //     },
        //     context,
        // } as any);

        this.codable = document.createElement("textarea", {
            is: "anki-codable",
        }) as any;
        this.appendChild(this.codable);

        this.onFocus = this.onFocus.bind(this);
        this.onBlur = this.onBlur.bind(this);
        this.onKey = this.onKey.bind(this);
        this.onPaste = this.onPaste.bind(this);
        this.showHandles = this.showHandles.bind(this);
    }

    get activeInput(): /* Editable | */ any {
        return this.codable; //this.codable.active ? this.codable : this.editable;
    }

    get ord(): number {
        return Number(this.getAttribute("ord"));
    }

    set fieldHTML(content: string) {
        this.imageHandle.then(() => {
            let result = content;

            for (const component of decoratedComponents) {
                result = component.toUndecorated(result);
            }

            this.activeInput.fieldHTML = result;
        });
    }

    get fieldHTML(): string {
        let result = this.activeInput.fieldHTML;
        for (const component of decoratedComponents) {
            result = component.toStored(result);
        }

        return result;
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
        // this.addEventListener("mouseup", updateActiveButtons);
        // this.editable.addEventListener("click", this.showHandles);
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
        // this.removeEventListener("mouseup", updateActiveButtons);
        // this.editable.removeEventListener("click", this.showHandles);
    }

    initialize(color: string, content: string): void {
        // this.editableContainer.stylePromise.then(() => {
        //     this.setBaseColor(color);
        //     this.fieldHTML = content;
        // });
    }

    setBaseColor(color: string): void {
        // this.editableContainer.setBaseColor(color);
    }

    quoteFontFamily(fontFamily: string): string {
        // generic families (e.g. sans-serif) must not be quoted
        if (!/^[-a-z]+$/.test(fontFamily)) {
            fontFamily = `"${fontFamily}"`;
        }
        return fontFamily;
    }

    setBaseStyling(fontFamily: string, fontSize: string, direction: string): void {
        // this.editableContainer.setBaseStyling(
        //     this.quoteFontFamily(fontFamily),
        //     fontSize,
        //     direction
        // );
    }

    isRightToLeft(): boolean {
        return false;
        // return this.editableContainer.isRightToLeft();
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

    onFocus(): void {
        deferFocusDown(this);
    }

    onBlur(event: FocusEvent): void {
        saveFieldIfFieldChanged(this, event.relatedTarget as Element);
    }

    onEnter(): void {
        this.activeInput.onEnter();
    }

    onKey(event: KeyboardEvent): void {
        this.resetHandles();
        onKey(event);
    }

    onPaste(): void {
        this.resetHandles();
        this.activeInput.onPaste();
    }

    resetHandles(): Promise<void> {
        const promise = this.imageHandle.then((imageHandle) =>
            (imageHandle as any).$set({
                activeImage: null,
            })
        );

        // (this.mathjaxHandle as any).$set({
        //     activeImage: null,
        // });

        return promise;
    }

    async showHandles(event: MouseEvent): Promise<void> {
        if (event.target instanceof HTMLImageElement) {
            const image = event.target as HTMLImageElement;
            await this.resetHandles();

            if (!image.dataset.anki) {
                await this.imageHandle.then((imageHandle) =>
                    (imageHandle as any).$set({
                        activeImage: image,
                        isRtl: this.isRightToLeft(),
                    })
                );
            } else if (image.dataset.anki === "mathjax") {
                // (this.mathjaxHandle as any).$set({
                //     activeImage: image,
                //     isRtl: this.isRightToLeft(),
                // });
            }
        } else {
            await this.resetHandles();
        }
    }

    toggleHtmlEdit(): void {
        const hadFocus = this.hasFocus();

        if (this.codable.active) {
            this.fieldHTML = this.codable.teardown();
            // this.editable.hidden = false;
        } else {
            this.resetHandles();
            // this.editable.hidden = true;
            // this.codable.setup(this.editable.fieldHTML);
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

customElements.define("anki-editing-area", EditingArea, { extends: "div" });
