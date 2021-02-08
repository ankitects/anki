/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

import { filterHTML } from "./filterHtml";
import { nodeIsInline } from "./helpers";
import { bridgeCommand } from "./lib";
import { saveField } from "./changeTimer";
import { updateButtonState, maybeDisableButtons } from "./toolbar";
import { onInput, onKey, onKeyUp } from "./inputHandlers";
import { onFocus, onBlur } from "./focusHandlers";

export { setNoteId, getNoteId } from "./noteId";
export { preventButtonFocus, toggleEditorButton, setFGButton } from "./toolbar";
export { saveNow } from "./changeTimer";

declare global {
    interface Selection {
        modify(s: string, t: string, u: string): void;
        addRange(r: Range): void;
        removeAllRanges(): void;
        getRangeAt(n: number): Range;
    }
}

export function getCurrentField(): EditingArea | null {
    return document.activeElement instanceof EditingArea
        ? document.activeElement
        : null;
}

export function focusField(n: number): void {
    const field = getEditorField(n);

    if (field) {
        field.editingArea.focusEditable();
    }
}

export function focusIfField(x: number, y: number): boolean {
    const elements = document.elementsFromPoint(x, y);
    for (let i = 0; i < elements.length; i++) {
        let elem = elements[i] as EditingArea;
        if (elem instanceof EditingArea) {
            elem.focusEditable();
            return true;
        }
    }
    return false;
}

function onPaste(evt: ClipboardEvent): void {
    bridgeCommand("paste");
    evt.preventDefault();
}

function onCutOrCopy(): boolean {
    bridgeCommand("cutOrCopy");
    return true;
}

function containsInlineContent(field: Element): boolean {
    if (field.childNodes.length === 0) {
        // for now, for all practical purposes, empty fields are in block mode
        return false;
    }

    for (const child of field.children) {
        if (!nodeIsInline(child)) {
            return false;
        }
    }

    return true;
}

class Editable extends HTMLElement {
    set fieldHTML(content: string) {
        this.innerHTML = content;

        if (containsInlineContent(this)) {
            this.appendChild(document.createElement("br"));
        }
    }

    get fieldHTML(): string {
        return containsInlineContent(this) && this.innerHTML.endsWith("<br>")
            ? this.innerHTML.slice(0, -4) // trim trailing <br>
            : this.innerHTML;
    }

    connectedCallback() {
        this.setAttribute("contenteditable", "");
    }
}

customElements.define("anki-editable", Editable);

export class EditingArea extends HTMLDivElement {
    editable: Editable;
    baseStyle: HTMLStyleElement;

    constructor() {
        super();
        this.attachShadow({ mode: "open" });
        this.className = "field";

        const rootStyle = document.createElement("link");
        rootStyle.setAttribute("rel", "stylesheet");
        rootStyle.setAttribute("href", "./_anki/css/editable.css");
        this.shadowRoot!.appendChild(rootStyle);

        this.baseStyle = document.createElement("style");
        this.baseStyle.setAttribute("rel", "stylesheet");
        this.shadowRoot!.appendChild(this.baseStyle);

        this.editable = document.createElement("anki-editable") as Editable;
        this.shadowRoot!.appendChild(this.editable);
    }

    get ord(): number {
        return Number(this.getAttribute("ord"));
    }

    set fieldHTML(content: string) {
        this.editable.fieldHTML = content;
    }

    get fieldHTML(): string {
        return this.editable.fieldHTML;
    }

    connectedCallback(): void {
        this.addEventListener("keydown", onKey);
        this.addEventListener("keyup", onKeyUp);
        this.addEventListener("input", onInput);
        this.addEventListener("focus", onFocus);
        this.addEventListener("blur", onBlur);
        this.addEventListener("paste", onPaste);
        this.addEventListener("copy", onCutOrCopy);
        this.addEventListener("oncut", onCutOrCopy);
        this.addEventListener("click", updateButtonState);

        const baseStyleSheet = this.baseStyle.sheet as CSSStyleSheet;
        baseStyleSheet.insertRule("anki-editable {}", 0);
    }

    disconnectedCallback(): void {
        this.removeEventListener("keydown", onKey);
        this.removeEventListener("keyup", onKeyUp);
        this.removeEventListener("input", onInput);
        this.removeEventListener("focus", onFocus);
        this.removeEventListener("blur", onBlur);
        this.removeEventListener("paste", onPaste);
        this.removeEventListener("copy", onCutOrCopy);
        this.removeEventListener("oncut", onCutOrCopy);
        this.removeEventListener("click", updateButtonState);
    }

    initialize(color: string, content: string): void {
        this.setBaseColor(color);
        this.editable.fieldHTML = content;
    }

    setBaseColor(color: string): void {
        const styleSheet = this.baseStyle.sheet as CSSStyleSheet;
        const firstRule = styleSheet.cssRules[0] as CSSStyleRule;
        firstRule.style.color = color;
    }

    setBaseStyling(fontFamily: string, fontSize: string, direction: string): void {
        const styleSheet = this.baseStyle.sheet as CSSStyleSheet;
        const firstRule = styleSheet.cssRules[0] as CSSStyleRule;
        firstRule.style.fontFamily = fontFamily;
        firstRule.style.fontSize = fontSize;
        firstRule.style.direction = direction;
    }

    isRightToLeft(): boolean {
        return this.editable.style.direction === "rtl";
    }

    getSelection(): Selection {
        return this.shadowRoot!.getSelection()!;
    }

    focusEditable(): void {
        this.editable.focus();
    }

    blurEditable(): void {
        this.editable.blur();
    }
}

customElements.define("anki-editing-area", EditingArea, { extends: "div" });

class EditorField extends HTMLDivElement {
    labelContainer: HTMLDivElement;
    label: HTMLSpanElement;
    editingArea: EditingArea;

    constructor() {
        super();
        this.labelContainer = document.createElement("div");
        this.labelContainer.className = "fname";
        this.appendChild(this.labelContainer);

        this.label = document.createElement("span");
        this.label.className = "fieldname";
        this.labelContainer.appendChild(this.label);

        this.editingArea = document.createElement("div", {
            is: "anki-editing-area",
        }) as EditingArea;
        this.appendChild(this.editingArea);
    }

    static get observedAttributes(): string[] {
        return ["ord"];
    }

    set ord(n: number) {
        this.setAttribute("ord", String(n));
    }

    attributeChangedCallback(name: string, _oldValue: string, newValue: string): void {
        switch (name) {
            case "ord":
                this.editingArea.setAttribute("ord", newValue);
        }
    }

    initialize(label: string, color: string, content: string): void {
        this.label.innerText = label;
        this.editingArea.initialize(color, content);
    }

    setBaseStyling(fontFamily: string, fontSize: string, direction: string): void {
        this.editingArea.setBaseStyling(fontFamily, fontSize, direction);
    }
}

customElements.define("anki-editor-field", EditorField, { extends: "div" });

function adjustFieldAmount(amount: number): void {
    const fieldsContainer = document.getElementById("fields")!;

    while (fieldsContainer.childElementCount < amount) {
        const newField = document.createElement("div", {
            is: "anki-editor-field",
        }) as EditorField;
        newField.ord = fieldsContainer.childElementCount;
        fieldsContainer.appendChild(newField);
    }

    while (fieldsContainer.childElementCount > amount) {
        fieldsContainer.removeChild(fieldsContainer.lastElementChild as Node);
    }
}

export function getEditorField(n: number): EditorField | null {
    const fields = document.getElementById("fields")!.children;
    return (fields[n] as EditorField) ?? null;
}

export function forEditorField<T>(
    values: T[],
    func: (field: EditorField, value: T) => void
): void {
    const fields = document.getElementById("fields")!.children;
    for (let i = 0; i < fields.length; i++) {
        const field = fields[i] as EditorField;
        func(field, values[i]);
    }
}

export function setFields(fields: [string, string][]): void {
    // webengine will include the variable after enter+backspace
    // if we don't convert it to a literal colour
    const color = window
        .getComputedStyle(document.documentElement)
        .getPropertyValue("--text-fg");

    adjustFieldAmount(fields.length);
    forEditorField(fields, (field, [name, fieldContent]) =>
        field.initialize(name, color, fieldContent)
    );

    maybeDisableButtons();
}

export function setBackgrounds(cols: ("dupe" | "")[]) {
    forEditorField(cols, (field, value) =>
        field.editingArea.classList.toggle("dupe", value === "dupe")
    );
    document
        .getElementById("dupes")!
        .classList.toggle("is-inactive", !cols.includes("dupe"));
}

export function setFonts(fonts: [string, number, boolean][]): void {
    forEditorField(fonts, (field, [fontFamily, fontSize, isRtl]) => {
        field.setBaseStyling(fontFamily, `${fontSize}px`, isRtl ? "rtl" : "ltr");
    });
}

function wrappedExceptForWhitespace(text: string, front: string, back: string): string {
    const match = text.match(/^(\s*)([^]*?)(\s*)$/)!;
    return match[1] + front + match[2] + back + match[3];
}

export function wrap(front: string, back: string): void {
    wrapInternal(front, back, false);
}

/* currently unused */
export function wrapIntoText(front: string, back: string): void {
    wrapInternal(front, back, true);
}

export function setFormat(cmd: string, arg?: any, nosave: boolean = false): void {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField(getCurrentField() as EditingArea, "key");
        updateButtonState();
    }
}

function wrapInternal(front: string, back: string, plainText: boolean): void {
    const currentField = getCurrentField()!;
    const s = currentField.getSelection();
    let r = s.getRangeAt(0);
    const content = r.cloneContents();
    const span = document.createElement("span");
    span.appendChild(content);

    if (plainText) {
        const new_ = wrappedExceptForWhitespace(span.innerText, front, back);
        setFormat("inserttext", new_);
    } else {
        const new_ = wrappedExceptForWhitespace(span.innerHTML, front, back);
        setFormat("inserthtml", new_);
    }

    if (!span.innerHTML) {
        // run with an empty selection; move cursor back past postfix
        r = s.getRangeAt(0);
        r.setStart(r.startContainer, r.startOffset - back.length);
        r.collapse(true);
        s.removeAllRanges();
        s.addRange(r);
    }
}

export let pasteHTML = function (
    html: string,
    internal: boolean,
    extendedMode: boolean
): void {
    html = filterHTML(html, internal, extendedMode);

    if (html !== "") {
        setFormat("inserthtml", html);
    }
};
