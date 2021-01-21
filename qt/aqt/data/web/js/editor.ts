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

function inListItem(): boolean {
    const anchor = window.getSelection().anchorNode;

    let n = nodeIsElement(anchor) ? anchor : anchor.parentElement;

    let inList = false;

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

function onFocus(elem: HTMLElement): void {
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

function saveField(type: "blur" | "key"): void {
    clearChangeTimer();
    if (!currentField) {
        // no field has been focused yet
        return;
    }
    // type is either 'blur' or 'key'
    pycmd(
        `${type}:${currentFieldOrdinal()}:${currentNoteId}:${currentField.innerHTML}`
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

function setFields(fields: [string, string][]): void {
    let txt = "";
    // webengine will include the variable after enter+backspace
    // if we don't convert it to a literal colour
    const color = window
        .getComputedStyle(document.documentElement)
        .getPropertyValue("--text-fg");
    for (let i = 0; i < fields.length; i++) {
        const n = fields[i][0];
        let f = fields[i][1];
        txt += `
        <tr>
            <td class=fname id="name${i}">
                <span class="fieldname">${n}</span>
            </td>
        </tr>
        <tr>
            <td width=100%>
                <div id="f${i}"
                     onkeydown="onKey(window.event);"
                     onkeyup="onKeyUp(window.event);"
                     oninput="onInput();"
                     onmouseup="onKey(window.event);"
                     onfocus="onFocus(this);"
                     onblur="onBlur();"
                     class="field clearfix"
                     onpaste="onPaste(this);"
                     oncopy="onCutOrCopy(this);"
                     oncut="onCutOrCopy(this);"
                     contentEditable
                     style="color: ${color}"
                >${f}</div>
            </td>
        </tr>`;
    }
    $("#fields").html(
        `<table cellpadding=0 width=100% style='table-layout: fixed;'>${txt}</table>`
    );
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
    //console.log(`input html: ${html}`);
    //console.log(`outpt html: ${outHtml}`);
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
