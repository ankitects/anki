/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

let currentField: EditingArea | null = null;
let changeTimer: number | null = null;
let currentNoteId: number | null = null;

declare interface String {
    format(...args: string[]): string;
}

/* kept for compatibility with add-ons */
String.prototype.format = function (...args: string[]): string {
    return this.replace(/\{\d+\}/g, (m: string): void => {
        const match = m.match(/\d+/);

        return match ? args[match[0]] : "";
    });
};

function setFGButton(col: string): void {
    document.getElementById("forecolor").style.backgroundColor = col;
}

function saveNow(keepFocus: boolean): void {
    if (!currentField) {
        return;
    }

    clearChangeTimer();

    if (keepFocus) {
        saveField("key");
    } else {
        // triggers onBlur, which saves
        currentField.blurEditable();
    }
}

function triggerKeyTimer(): void {
    clearChangeTimer();
    changeTimer = setTimeout(function () {
        updateButtonState();
        saveField("key");
    }, 600);
}

interface Selection {
    modify(s: string, t: string, u: string): void;
}

function onKey(evt: KeyboardEvent): void {
    // esc clears focus, allowing dialog to close
    if (evt.code === "Escape") {
        currentField.blurEditable();
        return;
    }

    // prefer <br> instead of <div></div>
    if (evt.code === "Enter" && !inListItem()) {
        evt.preventDefault();
        document.execCommand("insertLineBreak");
    }

    // // fix Ctrl+right/left handling in RTL fields
    if (currentField.isRightToLeft()) {
        const selection = currentField.getSelection();
        const granularity = evt.ctrlKey ? "word" : "character";
        const alter = evt.shiftKey ? "extend" : "move";

        switch (evt.code) {
            case "ArrowRight":
                selection.modify(alter, "right", granularity);
                evt.preventDefault();
                return;
            case "ArrowLeft":
                selection.modify(alter, "left", granularity);
                evt.preventDefault();
                return;
        }
    }

    triggerKeyTimer();
}

function onKeyUp(evt: KeyboardEvent): void {
    // Avoid div element on remove
    if (evt.code === "Enter" || evt.code === "Backspace") {
        const anchor = currentField.getSelection().anchorNode;

        if (
            nodeIsElement(anchor) &&
            anchor.tagName === "DIV" &&
            !(anchor instanceof EditingArea) &&
            anchor.childElementCount === 1 &&
            anchor.children[0].tagName === "BR"
        ) {
            anchor.replaceWith(anchor.children[0]);
        }
    }
}

function nodeIsElement(node: Node): node is Element {
    return node.nodeType === Node.ELEMENT_NODE;
}

const INLINE_TAGS = [
    "A",
    "ABBR",
    "ACRONYM",
    "AUDIO",
    "B",
    "BDI",
    "BDO",
    "BIG",
    "BR",
    "BUTTON",
    "CANVAS",
    "CITE",
    "CODE",
    "DATA",
    "DATALIST",
    "DEL",
    "DFN",
    "EM",
    "EMBED",
    "I",
    "IFRAME",
    "IMG",
    "INPUT",
    "INS",
    "KBD",
    "LABEL",
    "MAP",
    "MARK",
    "METER",
    "NOSCRIPT",
    "OBJECT",
    "OUTPUT",
    "PICTURE",
    "PROGRESS",
    "Q",
    "RUBY",
    "S",
    "SAMP",
    "SCRIPT",
    "SELECT",
    "SLOT",
    "SMALL",
    "SPAN",
    "STRONG",
    "SUB",
    "SUP",
    "SVG",
    "TEMPLATE",
    "TEXTAREA",
    "TIME",
    "U",
    "TT",
    "VAR",
    "VIDEO",
    "WBR",
];

function nodeIsInline(node: Node): boolean {
    return !nodeIsElement(node) || INLINE_TAGS.includes(node.tagName);
}

function inListItem(): boolean {
    const anchor = currentField.getSelection().anchorNode;

    let inList = false;
    let n = nodeIsElement(anchor) ? anchor : anchor.parentElement;
    while (n) {
        inList = inList || window.getComputedStyle(n).display == "list-item";
        n = n.parentElement;
    }

    return inList;
}

function insertNewline(): void {
    if (!inPreEnvironment()) {
        setFormat("insertText", "\n");
        return;
    }

    // in some cases inserting a newline will not show any changes,
    // as a trailing newline at the end of a block does not render
    // differently. so in such cases we note the height has not
    // changed and insert an extra newline.

    const r = currentField.getSelection().getRangeAt(0);
    if (!r.collapsed) {
        // delete any currently selected text first, making
        // sure the delete is undoable
        setFormat("delete");
    }

    const oldHeight = currentField.clientHeight;
    setFormat("inserthtml", "\n");
    if (currentField.clientHeight === oldHeight) {
        setFormat("inserthtml", "\n");
    }
}

// is the cursor in an environment that respects whitespace?
function inPreEnvironment(): boolean {
    const anchor = currentField.getSelection().anchorNode;
    const n = nodeIsElement(anchor) ? anchor : anchor.parentElement;

    return window.getComputedStyle(n).whiteSpace.startsWith("pre");
}

function onInput(): void {
    // make sure IME changes get saved
    triggerKeyTimer();
}

function updateButtonState(): void {
    const buts = ["bold", "italic", "underline", "superscript", "subscript"];
    for (const name of buts) {
        const elem = document.querySelector(`#${name}`) as HTMLElement;
        elem.classList.toggle("highlighted", document.queryCommandState(name));
    }

    // fixme: forecolor
    //    'col': document.queryCommandValue("forecolor")
}

function toggleEditorButton(buttonid: string): void {
    const button = $(buttonid)[0];
    button.classList.toggle("highlighted");
}

function setFormat(cmd: string, arg?: any, nosave: boolean = false): void {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField("key");
        updateButtonState();
    }
}

function clearChangeTimer(): void {
    if (changeTimer) {
        clearTimeout(changeTimer);
        changeTimer = null;
    }
}

function onFocus(evt: FocusEvent): void {
    const elem = evt.currentTarget as EditingArea;
    if (currentField === elem) {
        // anki window refocused; current element unchanged
        return;
    }
    elem.focusEditable();
    currentField = elem;
    pycmd(`focus:${currentField.ord}`);
    enableButtons();
    // do this twice so that there's no flicker on newer versions
    caretToEnd();
    // scroll if bottom of element off the screen
    function pos(elem: HTMLElement): number {
        let cur = 0;
        do {
            cur += elem.offsetTop;
            elem = elem.offsetParent as HTMLElement;
        } while (elem);
        return cur;
    }

    const y = pos(elem);
    if (
        window.pageYOffset + window.innerHeight < y + elem.offsetHeight ||
        window.pageYOffset > y
    ) {
        window.scroll(0, y + elem.offsetHeight - window.innerHeight);
    }
}

function focusField(n: number): void {
    const field = getEditorField(n);

    if (field) {
        field.editingArea.focusEditable();
    }
}

function focusIfField(x: number, y: number): boolean {
    const elements = document.elementsFromPoint(x, y);
    for (let i = 0; i < elements.length; i++) {
        let elem = elements[i] as EditingArea;
        if (elem instanceof EditingArea) {
            elem.focusEditable();
            // the focus event may not fire if the window is not active, so make sure
            // the current field is set
            currentField = elem;
            return true;
        }
    }
    return false;
}

function onPaste(): void {
    pycmd("paste");
    window.event.preventDefault();
}

function caretToEnd(): void {
    const range = document.createRange();
    range.selectNodeContents(currentField.editable);
    range.collapse(false);
    const selection = currentField.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
}

function onBlur(): void {
    if (!currentField) {
        return;
    }

    if (document.activeElement === currentField) {
        // other widget or window focused; current field unchanged
        saveField("key");
    } else {
        saveField("blur");
        currentField = null;
        disableButtons();
    }
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

function saveField(type: "blur" | "key"): void {
    clearChangeTimer();
    if (!currentField) {
        // no field has been focused yet
        return;
    }

    pycmd(`${type}:${currentField.ord}:${currentNoteId}:${currentField.fieldHTML}`);
}

function wrappedExceptForWhitespace(text: string, front: string, back: string): string {
    const match = text.match(/^(\s*)([^]*?)(\s*)$/);
    return match[1] + front + match[2] + back + match[3];
}

function preventButtonFocus(): void {
    for (const element of document.querySelectorAll("button.linkb")) {
        element.addEventListener("mousedown", (evt: Event) => {
            evt.preventDefault();
        });
    }
}

function disableButtons(): void {
    $("button.linkb:not(.perm)").prop("disabled", true);
}

function enableButtons(): void {
    $("button.linkb").prop("disabled", false);
}

// disable the buttons if a field is not currently focused
function maybeDisableButtons(): void {
    if (document.activeElement instanceof EditingArea) {
        enableButtons();
    } else {
        disableButtons();
    }
}

function wrap(front: string, back: string): void {
    wrapInternal(front, back, false);
}

/* currently unused */
function wrapIntoText(front: string, back: string): void {
    wrapInternal(front, back, true);
}

function wrapInternal(front: string, back: string, plainText: boolean): void {
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

function onCutOrCopy(): boolean {
    pycmd("cutOrCopy");
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

class EditingArea extends HTMLDivElement {
    editable: Editable;
    baseStyle: HTMLStyleElement;

    constructor() {
        super();
        this.attachShadow({ mode: "open" });
        this.className = "field";

        const rootStyle = document.createElement("link");
        rootStyle.setAttribute("rel", "stylesheet");
        rootStyle.setAttribute("href", "./_anki/css/editable.css");
        this.shadowRoot.appendChild(rootStyle);

        this.baseStyle = document.createElement("style");
        this.baseStyle.setAttribute("rel", "stylesheet");
        this.shadowRoot.appendChild(this.baseStyle);

        this.editable = document.createElement("anki-editable") as Editable;
        this.shadowRoot.appendChild(this.editable);
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
        return this.shadowRoot.getSelection();
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
    const fieldsContainer = document.getElementById("fields");

    while (fieldsContainer.childElementCount < amount) {
        const newField = document.createElement("div", {
            is: "anki-editor-field",
        }) as EditorField;
        newField.ord = fieldsContainer.childElementCount;
        fieldsContainer.appendChild(newField);
    }

    while (fieldsContainer.childElementCount > amount) {
        fieldsContainer.removeChild(fieldsContainer.lastElementChild);
    }
}

function getEditorField(n: number): EditorField | null {
    const fields = document.getElementById("fields").children;
    return (fields[n] as EditorField) ?? null;
}

function forEditorField<T>(
    values: T[],
    func: (field: EditorField, value: T) => void
): void {
    const fields = document.getElementById("fields").children;
    for (let i = 0; i < fields.length; i++) {
        const field = fields[i] as EditorField;
        func(field, values[i]);
    }
}

function setFields(fields: [string, string][]): void {
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

function setBackgrounds(cols: ("dupe" | "")[]) {
    forEditorField(cols, (field, value) =>
        field.editingArea.classList.toggle("dupe", value === "dupe")
    );
    document
        .querySelector("#dupes")
        .classList.toggle("is-inactive", !cols.includes("dupe"));
}

function setFonts(fonts: [string, number, boolean][]): void {
    forEditorField(fonts, (field, [fontFamily, fontSize, isRtl]) => {
        field.setBaseStyling(fontFamily, `${fontSize}px`, isRtl ? "rtl" : "ltr");
    });
}

function setNoteId(id: number): void {
    currentNoteId = id;
}

let pasteHTML = function (
    html: string,
    internal: boolean,
    extendedMode: boolean
): void {
    html = filterHTML(html, internal, extendedMode);

    if (html !== "") {
        setFormat("inserthtml", html);
    }
};

let filterHTML = function (
    html: string,
    internal: boolean,
    extendedMode: boolean
): string {
    // wrap it in <top> as we aren't allowed to change top level elements
    const top = document.createElement("ankitop");
    top.innerHTML = html;

    if (internal) {
        filterInternalNode(top);
    } else {
        filterNode(top, extendedMode);
    }
    let outHtml = top.innerHTML;
    if (!extendedMode && !internal) {
        // collapse whitespace
        outHtml = outHtml.replace(/[\n\t ]+/g, " ");
    }
    outHtml = outHtml.trim();
    return outHtml;
};

let allowedTagsBasic = {};
let allowedTagsExtended = {};

let TAGS_WITHOUT_ATTRS = ["P", "DIV", "BR", "SUB", "SUP"];
for (const tag of TAGS_WITHOUT_ATTRS) {
    allowedTagsBasic[tag] = { attrs: [] };
}

TAGS_WITHOUT_ATTRS = [
    "B",
    "BLOCKQUOTE",
    "CODE",
    "DD",
    "DL",
    "DT",
    "EM",
    "H1",
    "H2",
    "H3",
    "I",
    "LI",
    "OL",
    "PRE",
    "RP",
    "RT",
    "RUBY",
    "STRONG",
    "TABLE",
    "U",
    "UL",
];
for (const tag of TAGS_WITHOUT_ATTRS) {
    allowedTagsExtended[tag] = { attrs: [] };
}

allowedTagsBasic["IMG"] = { attrs: ["SRC"] };

allowedTagsExtended["A"] = { attrs: ["HREF"] };
allowedTagsExtended["TR"] = { attrs: ["ROWSPAN"] };
allowedTagsExtended["TD"] = { attrs: ["COLSPAN", "ROWSPAN"] };
allowedTagsExtended["TH"] = { attrs: ["COLSPAN", "ROWSPAN"] };
allowedTagsExtended["FONT"] = { attrs: ["COLOR"] };

const allowedStyling = {
    color: true,
    "background-color": true,
    "font-weight": true,
    "font-style": true,
    "text-decoration-line": true,
};

let isNightMode = function (): boolean {
    return document.body.classList.contains("nightMode");
};

let filterExternalSpan = function (elem: HTMLElement) {
    // filter out attributes
    for (const attr of [...elem.attributes]) {
        const attrName = attr.name.toUpperCase();

        if (attrName !== "STYLE") {
            elem.removeAttributeNode(attr);
        }
    }

    // filter styling
    for (const name of [...elem.style]) {
        const value = elem.style.getPropertyValue(name);

        if (
            !allowedStyling.hasOwnProperty(name) ||
            // google docs adds this unnecessarily
            (name === "background-color" && value === "transparent") ||
            // ignore coloured text in night mode for now
            (isNightMode() && (name === "background-color" || name === "color"))
        ) {
            elem.style.removeProperty(name);
        }
    }
};

allowedTagsExtended["SPAN"] = filterExternalSpan;

// add basic tags to extended
Object.assign(allowedTagsExtended, allowedTagsBasic);

function isHTMLElement(elem: Element): elem is HTMLElement {
    return elem instanceof HTMLElement;
}

// filtering from another field
let filterInternalNode = function (elem: Element) {
    if (isHTMLElement(elem)) {
        elem.style.removeProperty("background-color");
        elem.style.removeProperty("font-size");
        elem.style.removeProperty("font-family");
    }
    // recurse
    for (let i = 0; i < elem.children.length; i++) {
        const child = elem.children[i];
        filterInternalNode(child);
    }
};

// filtering from external sources
let filterNode = function (node: Node, extendedMode: boolean): void {
    if (!nodeIsElement(node)) {
        return;
    }

    // descend first, and take a copy of the child nodes as the loop will skip
    // elements due to node modifications otherwise
    for (const child of [...node.children]) {
        filterNode(child, extendedMode);
    }

    if (node.tagName === "ANKITOP") {
        return;
    }

    const tag = extendedMode
        ? allowedTagsExtended[node.tagName]
        : allowedTagsBasic[node.tagName];

    if (!tag) {
        if (!node.innerHTML || node.tagName === "TITLE") {
            node.parentNode.removeChild(node);
        } else {
            node.outerHTML = node.innerHTML;
        }
    } else {
        if (typeof tag === "function") {
            // filtering function provided
            tag(node);
        } else {
            // allowed, filter out attributes
            for (const attr of [...node.attributes]) {
                const attrName = attr.name.toUpperCase();
                if (tag.attrs.indexOf(attrName) === -1) {
                    node.removeAttributeNode(attr);
                }
            }
        }
    }
};
