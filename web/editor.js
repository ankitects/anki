var currentField = null;
var changeTimer = null;
var dropTarget = null;
var prewrapMode = false;

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
    // catch enter key in prewrap mode
    if (window.event.which === 13 && prewrapMode) {
        window.event.preventDefault();
        insertNewline();
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
        setFormat("insertText", "\\n");
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
    setFormat("inserthtml", "\\n");
    if (currentField.clientHeight === oldHeight) {
        setFormat("inserthtml", "\\n");
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
    pycmd("focus:" + currentField.id.substring(1));
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
    $("#f" + n).focus();
}

function onDragOver(elem) {
    // if we focus the target element immediately, the drag&drop turns into a
    // copy, so note it down for later instead
    dropTarget = elem;
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
    pycmd(type + ":" + currentField.innerHTML);
    clearChangeTimer();
}

function wrappedExceptForWhitespace(text, front, back) {
    var match = text.match(/^(\s*)([^]*?)(\s*)$/);
    return match[1] + front + match[2] + back + match[3];
}

function disableButtons() {
    $("button.linkb").prop("disabled", true);
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

function setFields(fields, focusTo, prewrap) {
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
        txt += "contentEditable=true class=field>{0}</div>".format(f);
        txt += "</td></tr>";
    }
    $("#fields").html("<table cellpadding=0 width=100%>" + txt + "</table>");
    if (!focusTo) {
        focusTo = 0;
    }
    if (focusTo >= 0) {
        $("#f" + focusTo).focus();
    }
    maybeDisableButtons();
    prewrapMode = prewrap;
    if (prewrap) {
        $(".field").addClass("prewrap");
    }
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

var pasteHTML = function (html, internal) {
    if (!internal) {
        html = filterHTML(html);
    }
    setFormat("inserthtml", html);
};

var filterHTML = function (html) {
    // wrap it in <top> as we aren't allowed to change top level elements
    var top = $.parseHTML("<ankitop>" + html + "</ankitop>")[0];
    filterNode(top);
    var outHtml = top.innerHTML;
    //console.log(`input html: ${html}`);
    //console.log(`outpt html: ${outHtml}`);
    return outHtml;
};

var allowedTags = {};

var TAGS_WITHOUT_ATTRS = ["H1", "H2", "H3", "P", "DIV", "BR", "LI", "UL",
    "OL", "B", "I", "U", "BLOCKQUOTE", "CODE", "EM",
    "STRONG", "PRE", "SUB", "SUP", "TABLE", "DD", "DT", "DL"];
for (var i = 0; i < TAGS_WITHOUT_ATTRS.length; i++) {
    allowedTags[TAGS_WITHOUT_ATTRS[i]] = {"attrs": []};
}

allowedTags["A"] = {"attrs": ["HREF"]};
allowedTags["TR"] = {"attrs": ["ROWSPAN"]};
allowedTags["TD"] = {"attrs": ["COLSPAN", "ROWSPAN"]};
allowedTags["TH"] = {"attrs": ["COLSPAN", "ROWSPAN"]};
allowedTags["IMG"] = {"attrs": ["SRC"]};

var blockRegex = /^(address|blockquote|br|center|div|dl|h[1-6]|hr|ol|p|pre|table|ul|dd|dt|li|tbody|td|tfoot|th|thead|tr)$/i;
function isBlockLevel(n) {
    return blockRegex.test(n.nodeName);
}

function isInlineElement(n) {
    return n && !isBlockLevel(n);
}

function convertDivToNewline(node, isParagraph) {
    var html = node.innerHTML;
    if (isInlineElement(node.previousSibling) && html) {
        html = "\\n" + html;
    }
    if (isInlineElement(node.nextSibling)) {
        html += "\\n";
    }
    if (isParagraph) {
        html += "\\n";
    }
    node.outerHTML = html;
}

var filterNode = function (node) {
    // text node?
    if (node.nodeType === 3) {
        if (prewrapMode) {
            // collapse standard whitespace
            var val = node.nodeValue.replace(/^[ \\r\\n\\t]+$/g, " ");

            // non-breaking spaces can be represented as normal spaces
            val = val.replace(/&nbsp;|\u00a0/g, " ");

            node.nodeValue = val;
        }
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
        filterNode(nodes[i]);
    }

    if (node.tagName === "ANKITOP") {
        return;
    }

    var tag = allowedTags[node.tagName];
    if (!tag) {
        if (!node.innerHTML) {
            node.parentNode.removeChild(node);
        } else {
            node.outerHTML = node.innerHTML;
        }
    } else if (prewrapMode && node.tagName === "BR") {
        node.outerHTML = "\\n";
    } else if (prewrapMode && node.tagName === "DIV") {
        convertBlockToNewline(node, false);
    } else if (prewrapMode && node.tagName === "P") {
        convertBlockToNewline(node, true);
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
