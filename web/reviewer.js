var ankiPlatform = "desktop";
var typeans;

var qFade = 100;
var aFade = 0;

function _updateQA(html, fadeTime, onupdate, onshown) {
    // fade out current text
    var qa = $("#qa");
    qa.fadeTo(fadeTime, 0, function() {
        // update text
        try {
            qa.html(html);
        } catch(err) {
            qa.text("Invalid HTML on card: "+err);
        }
        _removeStylingFromMathjaxCloze();
        onupdate(qa);

        // don't allow drags of images, which cause them to be deleted
        $("img").attr("draggable", false);

        // render mathjax
        MathJax.Hub.Queue(["Typeset", MathJax.Hub]);

        // and reveal when processing is done
        MathJax.Hub.Queue(function () {
            qa.fadeTo(fadeTime, 1, function () {
                onshown(qa);
            });
        });
    });
}

function _showQuestion(q, bodyclass) {
    _updateQA(q, qFade, function(obj) {
        // return to top of window
        window.scrollTo(0, 0);

        document.body.className = bodyclass;
    }, function(obj) {
        // focus typing area if visible
        typeans = document.getElementById("typeans");
        if (typeans) {
            typeans.focus();
        }
    });
}

function _showAnswer(a, bodyclass) {
    _updateQA(a, aFade, function(obj) {
        if (bodyclass) {
            //  when previewing
            document.body.className = bodyclass;
        }

        // scroll to answer?
        var e = $("#answer");
        if (e[0]) {
            e[0].scrollIntoView();
        }
    }, function(obj) {
    });
}

_flagColours = {
    1: "red",
    2: "purple",
    3: "green",
    4: "blue"
};

function _drawFlag(flag) {
    var elem = $("#_flag");
    if (flag === 0) {
        elem.hide();
        return;
    }
    elem.show();
    elem.css("color", _flagColours[flag]);
}

function _drawMark(mark) {
    var elem = $("#_mark");
    if (!mark) {
        elem.hide();
    } else {
        elem.show();
    }
}

function _typeAnsPress() {
    if (window.event.keyCode === 13) {
        pycmd("ans");
    }
}

function _removeStylingFromMathjaxCloze() {
    $(".cloze").each(function (i) {
        if (_clozeIsInsideMathjax(this)) {
            this.outerHTML = this.innerHTML;
        }
    });
}

function _clozeIsInsideMathjax(node) {
    if (!node.previousSibling || node.previousSibling.nodeType !== 3) {
        return;
    }
    // look for mathjax opening in previous text
    return /\\\(|\$\$/.test(node.previousSibling.textContent);
}