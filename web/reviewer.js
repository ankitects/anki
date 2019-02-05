/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

var ankiPlatform = "desktop";
var typeans;
var _updatingQA = false;

var qFade = 100;
var aFade = 0;

var onUpdateHook;
var onShownHook;

function _runHook(arr) {
    for (var i=0; i<arr.length; i++) {
        arr[i]();
    }
}

function _updateQA(html, fadeTime, onupdate, onshown) {
    // if a request to update q/a comes in before the previous content
    // has been loaded, wait a while and try again
    if (_updatingQA) {
        setTimeout(function () { _updateQA(html, fadeTime, onupdate, onshown) }, 50);
        return;
    }

    _updatingQA = true;

    onUpdateHook = [onupdate];
    onShownHook = [onshown];

    // fade out current text
    var qa = $("#qa");
    qa.fadeTo(fadeTime, 0, function() {
        // update text
        try {
            qa.html(html);
        } catch(err) {
            qa.text("Invalid HTML on card: "+err);
        }
        _runHook(onUpdateHook);

        // don't allow drags of images, which cause them to be deleted
        $("img").attr("draggable", false);

        // render mathjax
        MathJax.Hub.Queue(["Typeset", MathJax.Hub]);

        // and reveal when processing is done
        MathJax.Hub.Queue(function () {
            qa.fadeTo(fadeTime, 1, function () {
                _runHook(onShownHook);
                _updatingQA = false;
            });
        });
    });
}

function _showQuestion(q, bodyclass) {
    _updateQA(q, qFade, function() {
        // return to top of window
        window.scrollTo(0, 0);

        document.body.className = bodyclass;
    }, function() {
        // focus typing area if visible
        typeans = document.getElementById("typeans");
        if (typeans) {
            typeans.focus();
        }
    });
}

function _showAnswer(a, bodyclass) {
    _updateQA(a, aFade, function() {
        if (bodyclass) {
            //  when previewing
            document.body.className = bodyclass;
        }

        // scroll to answer?
        var e = $("#answer");
        if (e[0]) {
            e[0].scrollIntoView();
        }
    }, function() {
    });
}

_flagColours = {
    1: "#ff6666",
    2: "#ff9900",
    3: "#77ff77",
    4: "#77aaff"
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
