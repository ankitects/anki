/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

let currentField = null;
let changeTimer = null;
let currentNoteId = null;

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
    $("#forecolor")[0].style.backgroundColor = col;
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
        currentField.blur();
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
        currentField.blur();
        return;
    }

    // prefer <br> instead of <div></div>
    if (evt.code === "Enter" && !inListItem()) {
        evt.preventDefault();
        document.execCommand("insertLineBreak");
        return;
    }

    // fix Ctrl+right/left handling in RTL fields
    if (currentField.dir === "rtl") {
        const selection = window.getSelection();
        let granularity = "character";
        let alter = "move";
        if (evt.ctrlKey) {
            granularity = "word";
        }
        if (evt.shiftKey) {
            alter = "extend";
        }
        if (evt.code === "ArrowRight") {
            selection.modify(alter, "right", granularity);
            evt.preventDefault();
            return;
        } else if (evt.code === "ArrowLeft") {
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
        const anchor = window.getSelection().anchorNode;

        if (
            nodeIsElement(anchor) &&
            anchor.tagName === "DIV" &&
            !anchor.classList.contains("field") &&
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
    const anchor = window.getSelection().anchorNode;

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

    const r = window.getSelection().getRangeAt(0);
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
    const anchor = window.getSelection().anchorNode;
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
    const elem = evt.currentTarget as HTMLElement;
    if (currentField === elem) {
        // anki window refocused; current element unchanged
        return;
    }
    currentField = elem;
    pycmd(`focus:${currentFieldOrdinal()}`);
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
    if (n === null) {
        return;
    }
    $(`#f${n}`).focus();
}

function focusIfField(x: number, y: number): boolean {
    const elements = document.elementsFromPoint(x, y);
    for (let i = 0; i < elements.length; i++) {
        let elem = elements[i] as HTMLElement;
        if (elem.classList.contains("field")) {
            elem.focus();
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
    const r = document.createRange();
    r.selectNodeContents(currentField);
    r.collapse(false);
    const s = document.getSelection();
    s.removeAllRanges();
    s.addRange(r);
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

function fieldContainsInlineContent(field: Element): boolean {
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

    pycmd(
        `${type}:${currentFieldOrdinal()}:${currentNoteId}:${currentField.fieldHTML}`
    );
}

function currentFieldOrdinal(): string {
    return currentField.id.substring(1);
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
    if (!document.activeElement || document.activeElement.className !== "field") {
        disableButtons();
    } else {
        enableButtons();
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
    const s = window.getSelection();
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

class EditingArea extends HTMLElement {
    connectedCallback() {
        this.setAttribute("contenteditable", "true");
    }

    initialize(color: string): void {
        this.style.color = color;
    }

    set fieldHTML(content: string) {
        this.innerHTML = content;

        if (fieldContainsInlineContent(this)) {
            this.appendChild(document.createElement("br"));
        }
    }

    get fieldHTML(): string {
        return fieldContainsInlineContent(this) && this.innerHTML.endsWith("<br>")
            ? this.innerHTML.slice(0, -4) // trim trailing <br>
            : this.innerHTML;
    }
}

customElements.define("editing-area", EditingArea);

class EditingContainer extends HTMLDivElement {
    editingArea: EditingArea;

    connectedCallback(): void {
        this.className = "field";

        this.addEventListener("keydown", onKey);
        this.addEventListener("keyup", onKeyUp);
        this.addEventListener("input", onInput);
        this.addEventListener("focus", onFocus);
        this.addEventListener("blur", onBlur);
        this.addEventListener("paste", onPaste);
        this.addEventListener("copy", onCutOrCopy);
        this.addEventListener("oncut", onCutOrCopy);

        const editingShadow = this.attachShadow({ mode: "open" });

        const style = editingShadow.appendChild(document.createElement("link"));
        style.setAttribute("rel", "stylesheet");
        style.setAttribute("href", "./_anki/css/editing-area.css");

        this.editingArea = editingShadow.appendChild(
            document.createElement("editing-area")
        ) as EditingArea;
    }

    initialize(index: number, color: string, content: string): void {
        this.id = `f${index}`;
        this.editingArea.initialize(color);
        this.editingArea.fieldHTML = content;
    }

    set fieldHTML(content: string) {
        this.editingArea.fieldHTML = content;
    }

    get fieldHTML(): string {
        return this.editingArea.fieldHTML;
    }
}

customElements.define("editing-container", EditingContainer, { extends: "div" });

class EditorField extends HTMLDivElement {
    labelContainer: HTMLDivElement;
    label: HTMLSpanElement;
    editingContainer: EditingContainer;

    connectedCallback(): void {
        this.labelContainer = this.appendChild(document.createElement("div"));
        this.labelContainer.className = "fname";

        this.label = this.labelContainer.appendChild(document.createElement("span"));
        this.label.className = "fieldname";

        this.editingContainer = this.appendChild(
            document.createElement("div", { is: "editing-container" })
        ) as EditingContainer;
    }

    initialize(index: number, label: string, color: string, content: string): void {
        this.labelContainer.id = `name${index}`;
        this.label.innerText = label;
        this.editingContainer.initialize(index, color, content);
    }
}

customElements.define("editor-field", EditorField, { extends: "div" });

function adjustFieldAmount(amount: number): void {
    const fieldsContainer = document.getElementById("fields");

    while (fieldsContainer.childElementCount < amount) {
        fieldsContainer.appendChild(
            document.createElement("div", { is: "editor-field" })
        );
    }

    while (fieldsContainer.childElementCount > amount) {
        fieldsContainer.removeChild(fieldsContainer.lastElementChild);
    }
}

function setFields(fields: [string, string][]): void {
    // webengine will include the variable after enter+backspace
    // if we don't convert it to a literal colour
    const color = window
        .getComputedStyle(document.documentElement)
        .getPropertyValue("--text-fg");

    adjustFieldAmount(fields.length);

    const fieldsContainer = document.getElementById("fields");
    const children = [...fieldsContainer.children] as EditorField[];
    for (const [index, child] of children.entries()) {
        const [name, fieldContent] = fields[index];
        child.initialize(index, name, color, fieldContent);
    }

    maybeDisableButtons();
}

function setBackgrounds(cols: "dupe"[]) {
    for (let i = 0; i < cols.length; i++) {
        const element = document.querySelector(`#f${i}`);
        element.classList.toggle("dupe", cols[i] === "dupe");
    }
}

function setFonts(fonts: [string, number, boolean][]): void {
    for (let i = 0; i < fonts.length; i++) {
        const n = $(`#f${i}`);
        n.css("font-family", fonts[i][0]).css("font-size", fonts[i][1]);
        n[0].dir = fonts[i][2] ? "rtl" : "ltr";
    }
}

function setNoteId(id: number): void {
    currentNoteId = id;
}

function showDupes(): void {
    $("#dupes").show();
}

function hideDupes(): void {
    $("#dupes").hide();
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
