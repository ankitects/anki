/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

declare var MathJax: any;

type Callback = () => void | Promise<void>;

var ankiPlatform = "desktop";
var typeans;
var _updatingQueue: Promise<void> = Promise.resolve();

var qFade = 50;
var aFade = 0;

var onUpdateHook: Array<Callback>;
var onShownHook: Array<Callback>;

function _runHook(arr: Array<Callback>): Promise<void[]> {
    var promises = [];

    for (var i = 0; i < arr.length; i++) {
        promises.push(arr[i]());
    }

    return Promise.all(promises);
}

function _queueAction(action: Callback): void {
    _updatingQueue = _updatingQueue.then(action);
}

async function _updateQA(
    html: string,
    fadeTime: number,
    onupdate: Callback,
    onshown: Callback
): Promise<void> {
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
    }
    await _runHook(onUpdateHook);

    // wait for mathjax to ready
    await MathJax.startup.promise.then(() => {
        // clear MathJax buffers from previous typesets
        MathJax.typesetClear();

        return MathJax.typesetPromise(qa.slice(0, 1));
    });

    // and reveal when processing is done
    await qa.fadeTo(fadeTime, 1).promise();
    await _runHook(onShownHook);
}

function _showQuestion(q: string, bodyclass: string): void {
    _queueAction(() =>
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
        )
    );
}

function _showAnswer(a: string, bodyclass: string): void {
    _queueAction(() =>
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
        )
    );
}

const _flagColours = {
    1: "#ff6666",
    2: "#ff9900",
    3: "#77ff77",
    4: "#77aaff",
};

function _drawFlag(flag: 0 | 1 | 2 | 3 | 4): void {
    var elem = $("#_flag");
    if (flag === 0) {
        elem.hide();
        return;
    }
    elem.show();
    elem.css("color", _flagColours[flag]);
}

function _drawMark(mark: boolean): void {
    var elem = $("#_mark");
    if (!mark) {
        elem.hide();
    } else {
        elem.show();
    }
}

function _typeAnsPress(): void {
    if ((window.event as KeyboardEvent).keyCode === 13) {
        pycmd("ans");
    }
}

function _emulateMobile(enabled: boolean): void {
    const list = document.documentElement.classList;
    if (enabled) {
        list.add("mobile");
    } else {
        list.remove("mobile");
    }
}
