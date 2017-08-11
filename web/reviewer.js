var ankiPlatform = "desktop";
var typeans;

var qFade = 100;
var aFade = 0;

function _updateQA(html, fadeTime, onupdate, onshown) {
    // fade out current text
    var qa = $("#qa");
    qa.fadeTo(fadeTime, 0, function() {
        // update text
        qa.html(html);
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