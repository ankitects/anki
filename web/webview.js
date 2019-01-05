// prevent backspace key from going back a page
document.addEventListener("keydown", function (evt) {
    if (evt.keyCode !== 8) {
        return;
    }

    var nn = evt.target.nodeName;
    if (nn === "INPUT" || nn === "TEXTAREA") {
        return;
    } else if (nn === "DIV" && evt.target.contentEditable) {
        return;
    }

    evt.preventDefault();
});
