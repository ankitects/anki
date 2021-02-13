function withLineNumbers(highlight, options = {}) {
    const opts = Object.assign({ class: "codejar-linenumbers", wrapClass: "codejar-wrap", width: "35px", backgroundColor: "rgba(128, 128, 128, 0.15)", color: "" }, options);
    let lineNumbers;
    let gutter;
    return function (editor) {
        highlight(editor);
        if (!lineNumbers) {
            [lineNumbers, gutter] = init(editor, opts);
            editor.addEventListener("scroll", () => lineNumbers.style.top = `-${editor.scrollTop}px`);
        }
        initColor(editor, gutter, opts)
        const code = editor.textContent || "";
        const linesCount = code.replace(/\n+$/, "\n").split("\n").length + 1;
        let text = "";
        for (let i = 1; i < linesCount; i++) {
            text += `${i}\n`;
        }
        lineNumbers.innerText = text;
    };
}

function initColor(editor, gutter, opts) {
    const css = getComputedStyle(editor);
    gutter.style.color = opts.css || css.color;
}

function init(editor, opts) {
    const css = getComputedStyle(editor);
    const wrap = document.createElement("div");
    wrap.className = opts.wrapClass;
    wrap.style.position = "relative";
    const gutter = document.createElement("div");
    gutter.className = opts.class;
    wrap.appendChild(gutter);
    // Add own styles
    gutter.style.position = "absolute";
    gutter.style.top = "1px";
    gutter.style.left = "0px";
    gutter.style.bottom = "0px";
    gutter.style.width = opts.width;
    gutter.style.overflow = "hidden";
    gutter.style.backgroundColor = opts.backgroundColor;
    //gutter.style.setProperty("mix-blend-mode", "difference");
    // Copy editor styles
    gutter.style.fontFamily = css.fontFamily;
    gutter.style.fontSize = css.fontSize;
    gutter.style.lineHeight = css.lineHeight;
    gutter.style.paddingTop = css.paddingTop;
    gutter.style.paddingLeft = css.paddingLeft;
    gutter.style.borderTopLeftRadius = css.borderTopLeftRadius;
    gutter.style.borderBottomLeftRadius = css.borderBottomLeftRadius;
    // Add line numbers
    const lineNumbers = document.createElement("div");
    lineNumbers.style.position = "relative";
    lineNumbers.style.top = "0px";
    gutter.appendChild(lineNumbers);
    // Tweak editor styles
    editor.style.paddingLeft = `calc(${opts.width} + ${gutter.style.paddingLeft})`;
    editor.style.whiteSpace = "pre";
    // Swap editor with a wrap
    editor.parentNode.insertBefore(wrap, editor);
    wrap.appendChild(editor);
    return [lineNumbers, gutter];
}
