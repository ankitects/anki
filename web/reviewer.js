var ankiPlatform = "desktop";
var typeans;
function _updateQA(q, answerMode, klass) {
    $("#qa").html(q);
    typeans = document.getElementById("typeans");
    if (typeans) {
        typeans.focus();
    }
    if (answerMode) {
        var e = $("#answer");
        if (e[0]) {
            e[0].scrollIntoView();
        }
    } else {
        window.scrollTo(0, 0);
    }
    if (klass) {
        document.body.className = klass;
    }
    // don't allow drags of images, which cause them to be deleted
    $("img").attr("draggable", false);
    MathJax.Hub.Queue(["Typeset", MathJax.Hub]);
}

function _toggleStar(show) {
    if (show) {
        $(".marked").show();
    } else {
        $(".marked").hide();
    }
}

function _typeAnsPress() {
    if (window.event.keyCode === 13) {
        pycmd("ans");
    }
}
