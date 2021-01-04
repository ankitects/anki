function withLineNumbers(highlight, options = {}) {
    const opts = Object.assign({ class: "codejar-linenumbers", wrapClass: "codejar-wrap", width: "35px" }, options);
    let lineNumbers;

    function init(editor, opts) {
        const css = getComputedStyle(editor);
        const wrap = document.createElement("div");
        wrap.className = opts.wrapClass;
        wrap.style.position = "relative";
        const lineNumbers = document.createElement("div");
        lineNumbers.className = opts.class;
        wrap.appendChild(lineNumbers);
        // Add own styles
        lineNumbers.style.position = "absolute";
        lineNumbers.style.top = "0px";
        lineNumbers.style.left = "0px";
        lineNumbers.style.bottom = "0px";
        lineNumbers.style.width = opts.width;
        lineNumbers.style.overflow = "hidden";
        lineNumbers.style.backgroundColor = "rgba(255, 255, 255, 0.05)";
        lineNumbers.style.color = "#fff";
        // lineNumbers.style.setProperty("mix-blend-mode", "difference");
        // Copy editor styles
        lineNumbers.style.fontFamily = css.fontFamily;
        lineNumbers.style.fontSize = css.fontSize;
        lineNumbers.style.lineHeight = css.lineHeight;
        lineNumbers.style.paddingTop = css.paddingTop;
        lineNumbers.style.paddingLeft = css.paddingLeft;
        lineNumbers.style.borderTopLeftRadius = css.borderTopLeftRadius;
        lineNumbers.style.borderBottomLeftRadius = css.borderBottomLeftRadius;
        // Tweak editor styles
        editor.style.paddingLeft = `calc(${opts.width} + ${lineNumbers.style.paddingLeft})`;
        //editor.style.whiteSpace = "pre";
        // Swap editor with a wrap
        editor.parentNode.insertBefore(wrap, editor);
        wrap.appendChild(editor);
        return lineNumbers;
    }

    return function (editor) {
        highlight(editor);
        if (!lineNumbers) {
            lineNumbers = init(editor, opts);
        }
        const code = editor.textContent || "";
        const linesCount = code.replace(/\n+$/, "\n").split("\n").length + 1;
        let text = "";
        for (let i = 1; i < linesCount; i++) {
            text += `${i}\n`;
        }
        lineNumbers.innerText = text;
    };
}

function CodeJar(editor, highlight, opt = {}) {
    const options = Object.assign({ tab: "\t", indentOn: /{$/ }, opt);
    let listeners = [];
    let history = [];
    let at = -1;
    let focus = false;
    let callback;
    let prev; // code content prior keydown event
    let isFirefox = navigator.userAgent.toLowerCase().indexOf("firefox") > -1;
    editor.setAttribute("contentEditable", isFirefox ? "true" : "plaintext-only");
    editor.setAttribute("spellcheck", "false");
    editor.style.outline = "none";
    editor.style.overflowWrap = "break-word";
    editor.style.overflowY = "auto";
    editor.style.resize = "vertical";
    editor.style.whiteSpace = "pre-wrap";
    highlight(editor);
    const debounceHighlight = debounce(() => {
        const pos = save();
        highlight(editor);
        restore(pos);
    }, 30);
    let recording = false;
    const shouldRecord = (event) => {
        return !isUndo(event) && !isRedo(event)
          && event.key !== "Meta"
          && event.key !== "Control"
          && event.key !== "Alt"
          && !event.key.startsWith("Arrow");
    };
    const debounceRecordHistory = debounce((event) => {
        if (shouldRecord(event)) {
            recordHistory();
            recording = false;
        }
    }, 300);
    const on = (type, fn) => {
        listeners.push([type, fn]);
        editor.addEventListener(type, fn);
    };
    on("keydown", event => {
        if (event.defaultPrevented)
            return;
        prev = toString();
        handleNewLine(event);
        handleTabCharacters(event);
        handleSelfClosingCharacters(event);
        handleUndoRedo(event);
        if (shouldRecord(event) && !recording) {
            recordHistory();
            recording = true;
        }
    });
    on("keyup", event => {
        if (event.defaultPrevented)
            return;
        if (event.isComposing)
            return;
        if (prev !== toString())
            debounceHighlight();
        debounceRecordHistory(event);
        if (callback)
            callback(toString());
    });
    on("focus", _event => {
        focus = true;
    });
    on("blur", _event => {
        focus = false;
    });
    on("paste", event => {
        recordHistory();
        handlePaste(event);
        recordHistory();
        if (callback)
            callback(toString());
    });
    function save() {
        const s = window.getSelection();
        const pos = { start: 0, end: 0, dir: undefined };
        visit(editor, el => {
            if (el === s.anchorNode && el === s.focusNode) {
                pos.start += s.anchorOffset;
                pos.end += s.focusOffset;
                pos.dir = s.anchorOffset <= s.focusOffset ? "->" : "<-";
                return "stop";
            }
            if (el === s.anchorNode) {
                pos.start += s.anchorOffset;
                if (!pos.dir) {
                    pos.dir = "->";
                }
                else {
                    return "stop";
                }
            }
            else if (el === s.focusNode) {
                pos.end += s.focusOffset;
                if (!pos.dir) {
                    pos.dir = "<-";
                }
                else {
                    return "stop";
                }
            }
            if (el.nodeType === Node.TEXT_NODE) {
                if (pos.dir != "->")
                    pos.start += el.nodeValue.length;
                if (pos.dir != "<-")
                    pos.end += el.nodeValue.length;
            }
        });
        return pos;
    }
    function restore(pos) {
        const s = window.getSelection();
        let startNode, startOffset = 0;
        let endNode, endOffset = 0;
        if (!pos.dir)
            pos.dir = "->";
        if (pos.start < 0)
            pos.start = 0;
        if (pos.end < 0)
            pos.end = 0;
        // Flip start and end if the direction reversed
        if (pos.dir == "<-") {
            const { start, end } = pos;
            pos.start = end;
            pos.end = start;
        }
        let current = 0;
        visit(editor, el => {
            if (el.nodeType !== Node.TEXT_NODE)
                return;
            const len = (el.nodeValue || "").length;
            if (current + len >= pos.start) {
                if (!startNode) {
                    startNode = el;
                    startOffset = pos.start - current;
                }
                if (current + len >= pos.end) {
                    endNode = el;
                    endOffset = pos.end - current;
                    return "stop";
                }
            }
            current += len;
        });
        // If everything deleted place cursor at editor
        if (!startNode)
            startNode = editor;
        if (!endNode)
            endNode = editor;
        // Flip back the selection
        if (pos.dir == "<-") {
            [startNode, startOffset, endNode, endOffset] = [endNode, endOffset, startNode, startOffset];
        }
        s.setBaseAndExtent(startNode, startOffset, endNode, endOffset);
    }
    function beforeCursor() {
        const s = window.getSelection();
        const r0 = s.getRangeAt(0);
        const r = document.createRange();
        r.selectNodeContents(editor);
        r.setEnd(r0.startContainer, r0.startOffset);
        return r.toString();
    }
    function afterCursor() {
        const s = window.getSelection();
        const r0 = s.getRangeAt(0);
        const r = document.createRange();
        r.selectNodeContents(editor);
        r.setStart(r0.endContainer, r0.endOffset);
        return r.toString();
    }
    function handleNewLine(event) {
        if (event.key === "Enter") {
            const before = beforeCursor();
            const after = afterCursor();
            let [padding] = findPadding(before);
            let newLinePadding = padding;
            // If last symbol is "{" ident new line
            // Allow user defines indent rule
            if (options.indentOn.test(before)) {
                newLinePadding += options.tab;
            }
            if (isFirefox) {
                preventDefault(event);
                insert("\n" + newLinePadding);
            }
            else {
                // Normal browsers
                if (newLinePadding.length > 0) {
                    preventDefault(event);
                    insert("\n" + newLinePadding);
                }
            }
            // Place adjacent "}" on next line
            if (newLinePadding !== padding && after[0] === "}") {
                const pos = save();
                insert("\n" + padding);
                restore(pos);
            }
        }
    }
    function handleSelfClosingCharacters(event) {
        const open = `([{'"`;
        const close = `)]}'"`;
        const codeAfter = afterCursor();
        if (close.includes(event.key) && codeAfter.substr(0, 1) === event.key) {
            const pos = save();
            preventDefault(event);
            pos.start = ++pos.end;
            restore(pos);
        }
        else if (open.includes(event.key)) {
            const pos = save();
            preventDefault(event);
            const text = event.key + close[open.indexOf(event.key)];
            insert(text);
            pos.start = ++pos.end;
            restore(pos);
        }
    }
    function handleTabCharacters(event) {
        if (event.key === "Tab") {
            preventDefault(event);
            if (event.shiftKey) {
                const before = beforeCursor();
                let [padding, start,] = findPadding(before);
                if (padding.length > 0) {
                    const pos = save();
                    // Remove full length tab or just remaining padding
                    const len = Math.min(options.tab.length, padding.length);
                    restore({ start, end: start + len });
                    document.execCommand("delete");
                    pos.start -= len;
                    pos.end -= len;
                    restore(pos);
                }
            }
            else {
                insert(options.tab);
            }
        }
    }
    function handleUndoRedo(event) {
        if (isUndo(event)) {
            preventDefault(event);
            at--;
            const record = history[at];
            if (record) {
                editor.innerHTML = record.html;
                restore(record.pos);
            }
            if (at < 0)
                at = 0;
        }
        if (isRedo(event)) {
            preventDefault(event);
            at++;
            const record = history[at];
            if (record) {
                editor.innerHTML = record.html;
                restore(record.pos);
            }
            if (at >= history.length)
                at--;
        }
    }
    function recordHistory() {
        if (!focus)
            return;
        const html = editor.innerHTML;
        const pos = save();
        const lastRecord = history[at];
        if (lastRecord) {
            if (lastRecord.html === html
              && lastRecord.pos.start === pos.start
              && lastRecord.pos.end === pos.end)
                return;
        }
        at++;
        history[at] = { html, pos };
        history.splice(at + 1);
        const maxHistory = 300;
        if (at > maxHistory) {
            at = maxHistory;
            history.splice(0, 1);
        }
    }
    function handlePaste(event) {
        preventDefault(event);
        const text = (event.originalEvent || event).clipboardData.getData("text/plain");
        const pos = save();
        insert(text);
        highlight(editor);
        restore({ start: pos.end + text.length, end: pos.end + text.length });
    }
    function visit(editor, visitor) {
        const queue = [];
        if (editor.firstChild)
            queue.push(editor.firstChild);
        let el = queue.pop();
        while (el) {
            if (visitor(el) === "stop")
                break;
            if (el.nextSibling)
                queue.push(el.nextSibling);
            if (el.firstChild)
                queue.push(el.firstChild);
            el = queue.pop();
        }
    }
    function isCtrl(event) {
        return event.metaKey || event.ctrlKey;
    }
    function isUndo(event) {
        return isCtrl(event) && !event.shiftKey && event.code === "KeyZ";
    }
    function isRedo(event) {
        return isCtrl(event) && event.shiftKey && event.code === "KeyZ";
    }
    function insert(text) {
        text = text
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/"/g, "&quot;")
          .replace(/'/g, "&#039;");
        document.execCommand("insertHTML", false, text);
    }
    function debounce(cb, wait) {
        let timeout = 0;
        return (...args) => {
            clearTimeout(timeout);
            timeout = window.setTimeout(() => cb(...args), wait);
        };
    }
    function findPadding(text) {
        // Find beginning of previous line.
        let i = text.length - 1;
        while (i >= 0 && text[i] !== "\n")
            i--;
        i++;
        // Find padding of the line.
        let j = i;
        while (j < text.length && /[ \t]/.test(text[j]))
            j++;
        return [text.substring(i, j) || "", i, j];
    }
    function toString() {
        return editor.textContent || "";
    }
    function preventDefault(event) {
        event.preventDefault();
    }
    return {
        updateOptions(options) {
            options = Object.assign(Object.assign({}, options), options);
        },
        updateCode(code) {
            editor.textContent = code;
            highlight(editor);
        },
        onUpdate(cb) {
            callback = cb;
        },
        toString,
        destroy() {
            for (let [type, fn] of listeners) {
                editor.removeEventListener(type, fn);
            }
        },
    };
}