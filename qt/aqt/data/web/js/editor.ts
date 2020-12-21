/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

let currentField = null;
let changeTimer = null;
let currentNoteId = null;

declare interface String {
    format(...args): string;
}

/* kept for compatibility with add-ons */
String.prototype.format = function () {
    const args = arguments;
    return this.replace(/\{\d+\}/g, function (m) {
        return args[m.match(/\d+/)];
    });
};

function setFGButton(col) {
    $("#forecolor")[0].style.backgroundColor = col;
}

function saveNow(keepFocus) {
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

function triggerKeyTimer() {
    clearChangeTimer();
    changeTimer = setTimeout(function () {
        updateButtonState();
        saveField("key");
    }, 600);
}

interface Selection {
    modify(s: string, t: string, u: string): void;
}

function onKey(evt: KeyboardEvent) {
    // esc clears focus, allowing dialog to close
    if (evt.which === 27) {
        currentField.blur();
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
        if (evt.which === 39) {
            selection.modify(alter, "right", granularity);
            evt.preventDefault();
            return;
        } else if (evt.which === 37) {
            selection.modify(alter, "left", granularity);
            evt.preventDefault();
            return;
        }
    }

    triggerKeyTimer();
}

function insertNewline() {
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
function inPreEnvironment() {
    let n = window.getSelection().anchorNode as Element;
    if (n.nodeType === 3) {
        n = n.parentNode as Element;
    }
    return window.getComputedStyle(n).whiteSpace.startsWith("pre");
}

function onInput() {
    // empty field?
    if (currentField.innerHTML === "") {
        currentField.innerHTML = "<br>";
    }

    // make sure IME changes get saved
    triggerKeyTimer();
}

function updateButtonState() {
    const buts = ["bold", "italic", "underline", "superscript", "subscript"];
    for (const name of buts) {
        if (document.queryCommandState(name)) {
            $("#" + name).addClass("highlighted");
        } else {
            $("#" + name).removeClass("highlighted");
        }
    }

    // fixme: forecolor
    //    'col': document.queryCommandValue("forecolor")
}

function toggleEditorButton(buttonid) {
    if ($(buttonid).hasClass("highlighted")) {
        $(buttonid).removeClass("highlighted");
    } else {
        $(buttonid).addClass("highlighted");
    }
}

function setFormat(cmd: string, arg?: any, nosave: boolean = false) {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField("key");
        updateButtonState();
    }
}

function clearChangeTimer() {
    if (changeTimer) {
        clearTimeout(changeTimer);
        changeTimer = null;
    }
}

function onFocus(elem) {
    if (currentField === elem) {
        // anki window refocused; current element unchanged
        return;
    }
    currentField = elem;
    pycmd("focus:" + currentFieldOrdinal());
    enableButtons();
    // don't adjust cursor on mouse clicks
    if (mouseDown) {
        return;
    }
    // do this twice so that there's no flicker on newer versions
    caretToEnd();
    // scroll if bottom of element off the screen
    function pos(obj) {
        let cur = 0;
        do {
            cur += obj.offsetTop;
        } while ((obj = obj.offsetParent));
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

function focusField(n) {
    if (n === null) {
        return;
    }
    $("#f" + n).focus();
}

function focusIfField(x, y) {
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

function onPaste(elem) {
    pycmd("paste");
    window.event.preventDefault();
}

function caretToEnd() {
    const r = document.createRange();
    r.selectNodeContents(currentField);
    r.collapse(false);
    const s = document.getSelection();
    s.removeAllRanges();
    s.addRange(r);
}

function onBlur() {
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

function saveField(type) {
    clearChangeTimer();
    if (!currentField) {
        // no field has been focused yet
        return;
    }
    // type is either 'blur' or 'key'
    pycmd(
        type +
            ":" +
            currentFieldOrdinal() +
            ":" +
            currentNoteId +
            ":" +
            currentField.innerHTML
    );
}

function currentFieldOrdinal() {
    return currentField.id.substring(1);
}

function wrappedExceptForWhitespace(text, front, back) {
    const match = text.match(/^(\s*)([^]*?)(\s*)$/);
    return match[1] + front + match[2] + back + match[3];
}

function disableButtons() {
    $("button.linkb:not(.perm)").prop("disabled", true);
}

function enableButtons() {
    $("button.linkb").prop("disabled", false);
}

// disable the buttons if a field is not currently focused
function maybeDisableButtons() {
    if (!document.activeElement || document.activeElement.className !== "field") {
        disableButtons();
    } else {
        enableButtons();
    }
}

function wrap(front, back) {
    wrapInternal(front, back, false);
}

/* currently unused */
function wrapIntoText(front, back) {
    wrapInternal(front, back, true);
}

function wrapInternal(front, back, plainText) {
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

function onCutOrCopy() {
    pycmd("cutOrCopy");
    return true;
}

function setFields(fields) {
    let txt = "";
    for (let i = 0; i < fields.length; i++) {
        const n = fields[i][0];
        let f = fields[i][1];
        if (!f) {
            f = "<br>";
        }
        txt += `
        <tr>
            <td class=fname id="name${i}">${n}</td>
        </tr>
        <tr>
            <td width=100%>
                <div id=f${i}
                     onkeydown='onKey(window.event);'
                     oninput='onInput();'
                     onmouseup='onKey(window.event);'
                     onfocus='onFocus(this);'
                     onblur='onBlur();'
                     class='field clearfix'
                     onpaste='onPaste(this);'
                     oncopy='onCutOrCopy(this);'
                     oncut='onCutOrCopy(this);'
                     contentEditable=true
                     class=field
                >${f}</div>
            </td>
        </tr>`;
    }
    $("#fields").html(`
    <table cellpadding=0 width=100% style='table-layout: fixed;'>
${txt}
    </table>`);
    maybeDisableButtons();
}

function setBackgrounds(cols) {
    for (let i = 0; i < cols.length; i++) {
        if (cols[i] == "dupe") {
            $("#f" + i).addClass("dupe");
        } else {
            $("#f" + i).removeClass("dupe");
        }
    }
}

function setFonts(fonts) {
    for (let i = 0; i < fonts.length; i++) {
        const n = $("#f" + i);
        n.css("font-family", fonts[i][0]).css("font-size", fonts[i][1]);
        n[0].dir = fonts[i][2] ? "rtl" : "ltr";
    }
}

function setNoteId(id) {
    currentNoteId = id;
}

function showDupes() {
    $("#dupes").show();
}

function hideDupes() {
    $("#dupes").hide();
}

/// If the field has only an empty br, remove it first.
let insertHtmlRemovingInitialBR = function (html: string) {
    if (html !== "") {
        // remove <br> in empty field
        if (currentField && currentField.innerHTML === "<br>") {
            currentField.innerHTML = "";
        }
        setFormat("inserthtml", html);
    }
};

let pasteHTML = function (html, internal, extendedMode) {
    html = filterHTML(html, internal, extendedMode);
    insertHtmlRemovingInitialBR(html);
};

let filterHTML = function (html, internal, extendedMode) {
    // wrap it in <top> as we aren't allowed to change top level elements
    const top = $.parseHTML("<ankitop>" + html + "</ankitop>")[0] as Element;
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

let filterExternalSpan = function (node) {
    // filter out attributes
    let toRemove = [];
    for (const attr of node.attributes) {
        const attrName = attr.name.toUpperCase();
        if (attrName !== "STYLE") {
            toRemove.push(attr);
        }
    }
    for (const attributeToRemove of toRemove) {
        node.removeAttributeNode(attributeToRemove);
    }
    // filter styling
    toRemove = [];
    for (const name of node.style) {
        if (!allowedStyling.hasOwnProperty(name)) {
            toRemove.push(name);
        }
        if (name === "background-color" && node.style[name] === "transparent") {
            // google docs adds this unnecessarily
            toRemove.push(name);
        }
        if (isNightMode()) {
            // ignore coloured text in night mode for now
            if (name === "background-color" || name == "color") {
                toRemove.push(name);
            }
        }
    }
    for (let name of toRemove) {
        node.style.removeProperty(name);
    }
};

allowedTagsExtended["SPAN"] = filterExternalSpan;

// add basic tags to extended
Object.assign(allowedTagsExtended, allowedTagsBasic);

// filtering from another field
let filterInternalNode = function (node) {
    if (node.style) {
        node.style.removeProperty("background-color");
        node.style.removeProperty("font-size");
        node.style.removeProperty("font-family");
    }
    // recurse
    for (const child of node.childNodes) {
        filterInternalNode(child);
    }
};

// filtering from external sources
let filterNode = function (node, extendedMode) {
    // text node?
    if (node.nodeType === 3) {
        return;
    }

    // descend first, and take a copy of the child nodes as the loop will skip
    // elements due to node modifications otherwise

    const nodes = [];
    for (const child of node.childNodes) {
        nodes.push(child);
    }
    for (const child of nodes) {
        filterNode(child, extendedMode);
    }

    if (node.tagName === "ANKITOP") {
        return;
    }

    let tag;
    if (extendedMode) {
        tag = allowedTagsExtended[node.tagName];
    } else {
        tag = allowedTagsBasic[node.tagName];
    }
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
            const toRemove = [];
            for (const attr of node.attributes) {
                const attrName = attr.name.toUpperCase();
                if (tag.attrs.indexOf(attrName) === -1) {
                    toRemove.push(attr);
                }
            }
            for (const attributeToRemove of toRemove) {
                node.removeAttributeNode(attributeToRemove);
            }
        }
    }
};

let adjustFieldsTopMargin = function () {
    const topHeight = $("#topbuts").height();
    const margin = topHeight + 8;
    document.getElementById("fields").style.marginTop = margin + "px";
};

let mouseDown = 0;

$(function () {
    document.body.onmousedown = function () {
        mouseDown++;
    };

    document.body.onmouseup = function () {
        mouseDown--;
    };

    document.onclick = function (evt: MouseEvent) {
        const src = evt.target as Element;
        if (src.tagName === "IMG") {
            // image clicked; find contenteditable parent
            let p = src;
            while ((p = p.parentNode as Element)) {
                if (p.className === "field") {
                    $("#" + p.id).focus();
                    break;
                }
            }
        }
    };

    // prevent editor buttons from taking focus
    $("button.linkb").on("mousedown", function (e) {
        e.preventDefault();
    });

    window.onresize = function () {
        adjustFieldsTopMargin();
    };

    adjustFieldsTopMargin();
});
