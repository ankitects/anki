/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

var ankiPlatform = "desktop";
var typeans;
var _updatingQA = false;

var qFade = 50;
var aFade = 0;

var onUpdateHook;
var onShownHook;

function _runHook(arr: () => Promise<any>[]): Promise<any[]> {
    var promises = [];

    for (var i = 0; i < arr.length; i++) {
        promises.push(arr[i]());
    }

    return Promise.all(promises);
}

async function _updateQA(html, fadeTime, onupdate, onshown) {
    // if a request to update q/a comes in before the previous content
    // has been loaded, wait a while and try again
    if (_updatingQA) {
        setTimeout(function () {
            _updateQA(html, fadeTime, onupdate, onshown);
        }, 50);
        return;
    }

    _updatingQA = true;

    onUpdateHook = [onupdate];
    onShownHook = [onshown];

    var qa = $("#qa");

    // fade out current text
    await qa.fadeTo(fadeTime, 0).promise();

    // update text
    try {
        qa.html(html);
    } catch (err) {
        qa.html(
            (
                `Invalid HTML on card: ${String(err).substring(0, 2000)}\n` +
                String(err.stack).substring(0, 2000)
            ).replace(/\n/g, "<br />")
        );
    };
    await _runHook(onUpdateHook);

    // @ts-ignore wait for mathjax to ready
    await MathJax.startup.promise.then(() => {
        // @ts-ignore clear MathJax buffer
        MathJax.typesetClear();

        // @ts-ignore typeset
        return MathJax.typesetPromise(qa.slice(0, 1));
    });

    // and reveal when processing is done
    await qa.fadeTo(fadeTime, 1).promise();
    await _runHook(onShownHook);

    _updatingQA = false;
}

function _showQuestion(q, bodyclass) {
    _updateQA(
        q,
        qFade,
        function () {
            // return to top of window
            window.scrollTo(0, 0);

            document.body.className = bodyclass;
        },
        function () {
            // focus typing area if visible
            typeans = document.getElementById("typeans");
            if (typeans) {
                typeans.focus();
            }
        }
    );
}

function _showAnswer(a, bodyclass) {
    _updateQA(
        a,
        aFade,
        function () {
            if (bodyclass) {
                //  when previewing
                document.body.className = bodyclass;
            }

            // scroll to answer?
            var e = $("#answer");
            if (e[0]) {
                e[0].scrollIntoView();
            }
        },
        function () {}
    );
}

const _flagColours = {
    1: "#ff6666",
    2: "#ff9900",
    3: "#77ff77",
    4: "#77aaff",
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
    if ((window.event as KeyboardEvent).keyCode === 13) {
        pycmd("ans");
    }
}

function _emulateMobile(enabled: boolean) {
    const list = document.documentElement.classList;
    if (enabled) {
        list.add("mobile");
    } else {
        list.remove("mobile");
    }
}
