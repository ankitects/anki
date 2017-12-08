var currentField = null;
var changeTimer = null;
var dropTarget = null;

String.prototype.format = function () {
    var args = arguments;
    return this.replace(/\{\d+\}/g, function (m) {
        return args[m.match(/\d+/)];
    });
};

function setFGButton(col) {
    $("#forecolor")[0].style.backgroundColor = col;
}

function saveNow() {
    clearChangeTimer();
    if (currentField) {
        currentField.blur();
    }
}

function onKey() {
    // esc clears focus, allowing dialog to close
    if (window.event.which === 27) {
        currentField.blur();
        return;
    }
    // shift+tab goes to previous field
    if (navigator.platform === "MacIntel" &&
        window.event.which === 9 && window.event.shiftKey) {
        window.event.preventDefault();
        focusPrevious();
        return;
    }
    clearChangeTimer();
    changeTimer = setTimeout(function () {
        updateButtonState();
        saveField("key");
    }, 600);
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

    var r = window.getSelection().getRangeAt(0);
    if (!r.collapsed) {
        // delete any currently selected text first, making
        // sure the delete is undoable
        setFormat("delete");
    }

    var oldHeight = currentField.clientHeight;
    setFormat("inserthtml", "\n");
    if (currentField.clientHeight === oldHeight) {
        setFormat("inserthtml", "\n");
    }
}

// is the cursor in an environment that respects whitespace?
function inPreEnvironment() {
    var n = window.getSelection().anchorNode;
    if (n.nodeType === 3) {
        n = n.parentNode;
    }
    return window.getComputedStyle(n).whiteSpace.startsWith("pre");
}

function checkForEmptyField() {
    if (currentField.innerHTML === "") {
        currentField.innerHTML = "<br>";
    }
}

function updateButtonState() {
    var buts = ["bold", "italic", "underline", "superscript", "subscript"];
    for (var i = 0; i < buts.length; i++) {
        var name = buts[i];
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

function setFormat(cmd, arg, nosave) {
    document.execCommand(cmd, false, arg);
    if (!nosave) {
        saveField('key');
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
        var cur = 0;
        do {
            cur += obj.offsetTop;
        } while (obj = obj.offsetParent);
        return cur;
    }

    var y = pos(elem);
    if ((window.pageYOffset + window.innerHeight) < (y + elem.offsetHeight) ||
        window.pageYOffset > y) {
        window.scroll(0, y + elem.offsetHeight - window.innerHeight);
    }
}

function focusField(n) {
    if (n === null) {
        return;
    }
    $("#f" + n).focus();
}

function focusPrevious() {
    if (!currentField) {
        return;
    }
    var previous = currentFieldOrdinal() - 1;
    if (previous >= 0) {
        focusField(previous);
    }
}

function onDragOver(elem) {
    // if we focus the target element immediately, the drag&drop turns into a
    // copy, so note it down for later instead
    dropTarget = elem;
}

function makeDropTargetCurrent() {
    dropTarget.focus();
    // the focus event may not fire if the window is not active, so make sure
    // the current field is set
    currentField = dropTarget;
}

function onPaste(elem) {
    pycmd("paste");
    window.event.preventDefault();
}

function caretToEnd() {
    var r = document.createRange();
    r.selectNodeContents(currentField);
    r.collapse(false);
    var s = document.getSelection();
    s.removeAllRanges();
    s.addRange(r);
}

function onBlur() {
    if (currentField) {
        saveField("blur");
        currentField = null;
    }
    clearChangeTimer();
    disableButtons();
}

function saveField(type) {
    if (!currentField) {
        // no field has been focused yet
        return;
    }
    // type is either 'blur' or 'key'
    pycmd(type + ":" + currentFieldOrdinal() + ":" + currentField.innerHTML);
    clearChangeTimer();
}

function currentFieldOrdinal() {
    return currentField.id.substring(1);
}

function wrappedExceptForWhitespace(text, front, back) {
    var match = text.match(/^(\s*)([^]*?)(\s*)$/);
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
    if (currentField.dir === "rtl") {
        front = "&#8235;" + front + "&#8236;";
        back = "&#8235;" + back + "&#8236;";
    }
    var s = window.getSelection();
    var r = s.getRangeAt(0);
    var content = r.cloneContents();
    var span = document.createElement("span");
    span.appendChild(content);
    var new_ = wrappedExceptForWhitespace(span.innerHTML, front, back);
    setFormat("inserthtml", new_);
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
    var txt = "";
    for (var i = 0; i < fields.length; i++) {
        var n = fields[i][0];
        var f = fields[i][1];
        if (!f) {
            f = "<br>";
        }
        txt += "<tr><td class=fname>{0}</td></tr><tr><td width=100%>".format(n);
        txt += "<div id=f{0} onkeydown='onKey();' oninput='checkForEmptyField()' onmouseup='onKey();'".format(i);
        txt += " onfocus='onFocus(this);' onblur='onBlur();' class=field ";
        txt += "ondragover='onDragOver(this);' onpaste='onPaste(this);' ";
        txt += "oncopy='onCutOrCopy(this);' oncut='onCutOrCopy(this);' ";
        txt += "contentEditable=true class=field>{0}</div>".format(f);
        txt += "</td></tr>";
    }
    $("#fields").html("<table cellpadding=0 width=100% style='table-layout: fixed;'>" + txt + "</table>");
    maybeDisableButtons();
}

function setBackgrounds(cols) {
    for (var i = 0; i < cols.length; i++) {
        $("#f" + i).css("background", cols[i]);
    }
}

function setFonts(fonts) {
    for (var i = 0; i < fonts.length; i++) {
        var n = $("#f" + i);
        n.css("font-family", fonts[i][0])
         .css("font-size", fonts[i][1]);
        n[0].dir = fonts[i][2] ? "rtl" : "ltr";
    }
}

function showDupes() {
    $("#dupes").show();
}

function hideDupes() {
    $("#dupes").hide();
}

var pasteHTML = function (html, internal, extendedMode) {
    html = filterHTML(html, internal, extendedMode);
    setFormat("inserthtml", html);
};

var filterHTML = function (html, internal, extendedMode) {
    // wrap it in <top> as we aren't allowed to change top level elements
    var top = $.parseHTML("<ankitop>" + html + "</ankitop>")[0];
    if (internal) {
        filterInternalNode(top);
    }  else {
        filterNode(top, extendedMode);
    }
    var outHtml = top.innerHTML;
    // remove newlines in HTML, as they break cloze deletions, and collapse whitespace
    outHtml = outHtml.replace(/[\n\t ]+/g, " ").trim();
    //console.log(`input html: ${html}`);
    //console.log(`outpt html: ${outHtml}`);
    return outHtml;
};

var allowedTagsBasic = {};
var allowedTagsExtended = {};

var TAGS_WITHOUT_ATTRS = ["P", "DIV", "BR",
    "B", "I", "U", "EM", "STRONG", "SUB", "SUP"];
var i;
for (i = 0; i < TAGS_WITHOUT_ATTRS.length; i++) {
    allowedTagsBasic[TAGS_WITHOUT_ATTRS[i]] = {"attrs": []};
}

TAGS_WITHOUT_ATTRS = ["H1", "H2", "H3", "LI", "UL", "BLOCKQUOTE", "CODE",
    "PRE", "TABLE", "DD", "DT", "DL"];
for (i = 0; i < TAGS_WITHOUT_ATTRS.length; i++) {
    allowedTagsExtended[TAGS_WITHOUT_ATTRS[i]] = {"attrs": []};
}

allowedTagsBasic["IMG"] = {"attrs": ["SRC"]};

allowedTagsExtended["A"] = {"attrs": ["HREF"]};
allowedTagsExtended["TR"] = {"attrs": ["ROWSPAN"]};
allowedTagsExtended["TD"] = {"attrs": ["COLSPAN", "ROWSPAN"]};
allowedTagsExtended["TH"] = {"attrs": ["COLSPAN", "ROWSPAN"]};

// add basic tags to extended
Object.assign(allowedTagsExtended, allowedTagsBasic);

// filtering from another field
var filterInternalNode = function (node) {
    if (node.tagName === "SPAN") {
        node.style.removeProperty("background-color");
        node.style.removeProperty("font-size");
        node.style.removeProperty("font-family");
    }
    // recurse
    for (var i = 0; i < node.childNodes.length; i++) {
        filterInternalNode(node.childNodes[i]);
    }
};

// filtering from external sources
var filterNode = function (node, extendedMode) {
    // text node?
    if (node.nodeType === 3) {
        return;
    }

    // descend first, and take a copy of the child nodes as the loop will skip
    // elements due to node modifications otherwise

    var nodes = [];
    var i;
    for (i = 0; i < node.childNodes.length; i++) {
        nodes.push(node.childNodes[i]);
    }
    for (i = 0; i < nodes.length; i++) {
        filterNode(nodes[i], extendedMode);
    }

    if (node.tagName === "ANKITOP") {
        return;
    }

    var tag;
    if (extendedMode) {
        tag = allowedTagsExtended[node.tagName];
    } else {
        tag = allowedTagsBasic[node.tagName];
    }
    if (!tag) {
        if (!node.innerHTML) {
            node.parentNode.removeChild(node);
        } else {
            node.outerHTML = node.innerHTML;
        }
    } else {
        // allowed, filter out attributes
        var toRemove = [];
        for (i = 0; i < node.attributes.length; i++) {
            var attr = node.attributes[i];
            var attrName = attr.name.toUpperCase();
            if (tag.attrs.indexOf(attrName) === -1) {
                toRemove.push(attr);
            }
        }
        for (i = 0; i < toRemove.length; i++) {
            node.removeAttributeNode(toRemove[i]);
        }
    }
};

var mouseDown = 0;

$(function () {
    document.body.onmousedown = function () {
        mouseDown++;
    };

    document.body.onmouseup = function () {
        mouseDown--;
    };

    document.onclick = function (evt) {
        var src = window.event.srcElement;
        if (src.tagName === "IMG") {
            // image clicked; find contenteditable parent
            var p = src;
            while (p = p.parentNode) {
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
});
