/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

declare var CodeJar: any;
declare var withLineNumbers: any;
declare var Prism: any;
declare var hljs: any;
declare var console: Console;

var ankiPlatform = "desktop";
var typeans;
var codeans;
var log;
var codeansJar;
var _updatingQA = false;

var qFade = 100;
var aFade = 0;

var onUpdateHook;
var onShownHook;

function _runHook(arr) {
    for (var i = 0; i < arr.length; i++) {
        arr[i]();
    }
}

function _updateQA(html, fadeTime, onupdate, onshown) {
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

    // fade out current text
    var qa = $("#qa");
    qa.fadeTo(fadeTime, 0, function () {
        // update text
        try {
            qa.html(html);
            _initalizeCodeEditor();
        } catch (err) {
            qa.html(
                (
                    `Invalid HTML on card: ${String(err).substring(0, 2000)}\n` +
                    String(err.stack).substring(0, 2000)
                ).replace(/\n/g, "<br />")
            );
        }
        _runHook(onUpdateHook);

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
    _updateQA(
        q,
        qFade,
        function () {
            // return to top of window
            window.scrollTo(0, 0);
            _initializeProgress()
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

function highlight(editor: HTMLElement) {
    // highlight.js does not trims old tags,
    // let's do it by this hack.
    editor.textContent = editor.textContent;
    hljs.highlightBlock(editor);
}

function _initializeProgress() {
    _setProgress('0');
}

function _setProgress(raise) {
    _displayProgressBar(raise, '#38c172')
}

function _setProgressError() {
    _displayProgressBar('100', '#e3342f')
}

function _displayProgressBar(raise, bgColor) {
    (<any>$('#progressbar')).jQMeter({
        goal: '100',
        raised: raise,
        height: '5px',
        barColor: bgColor,
        bgColor:'#dadada',
        animationSpeed: 0,
        displayTotal: false
    });
}

function _activateRunButton() {
    var $runBtn = $('#start-testing');
    var $stopBtn = $('#stop-testing');
    $stopBtn.addClass('disabled').attr('disabled', 'disabled')
    $runBtn.removeClass('disabled').removeAttr('disabled')
}

function _activateStopButton() {
    var $runBtn = $('#start-testing');
    var $stopBtn = $('#stop-testing');
    $runBtn.addClass('disabled').attr('disabled', 'disabled')
    $stopBtn.removeClass('disabled').removeAttr('disabled')
}

function _initalizeCodeEditor() {
    codeans = document.getElementById("codeans");
    if (!codeans) {
        return;
    }
    log = document.getElementById("log");
    let options = {
        tab: " ".repeat(4), // default is '\t'
        indentOn: /[(\[]$/, // default is /{$/
    };
    codeansJar = CodeJar(codeans, withLineNumbers(highlight), options);
}

function _switchSkin(name) {
    $("head link[rel=stylesheet]").each(function () {
        const $this = $(this);
        let $toEnable = null;
        if ($this.attr("href").indexOf("highlight/") > 0) {
            if ($this.attr("href").endsWith(name + ".css")) {
                $toEnable = $this;
            }
            $this.attr("disabled", "disabled");
        }
        if ($toEnable != null) {
            $toEnable.removeAttr("disabled");
        }
    });
}

function _showAnswer(a, bodyclass, isCodingQuestion) {
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
            if (isCodingQuestion) {
                _initializeCodeAnswers();
            }
        },
        function () { }
    );
}

function _reloadCode(src, lang) {
    var $codeans = $(codeans);
    $codeans.removeClass(function (index, className) {
        return (className.match(/\blanguage-\S+/g) || []).join(" ");
    });
    $codeans.addClass("language-" + lang);
    codeansJar.updateCode(src);
}

function _cleanConsoleLog() {
    $("#log").empty();
}

function _showConsoleLog(html) {
    const $log = $('#log')
    $log.append(html).scrollTop($log.prop("scrollHeight"));
}

function _initializeCodeAnswers() {
    const $qa = $('#qa')
    const input = $qa.html()

    let html = ''
    let match
    const regex = /```(\w+)(<br>|\\n)*([^`]+)```/g;
    while (match = regex.exec(input)) {
        const lang = match[1]
        const src = match[3].replace(/<br>/g, '\n')
        const height = src.split('\n').length
        html += `<h4>${lang.replace(lang[0], lang[0].toUpperCase())}</h4><div class="editor language-${lang}" style="height:${height*20}px;">${src}</div><br><br>`
    }
    $qa.html(html)
    $qa.find('.editor').each(function() {
        let options = {
            tab: ' '.repeat(4),
            indentOn: /[(\[]$/,
        };
        CodeJar(this, withLineNumbers(highlight), options);
        $(this).attr('contenteditable', 'false')
    });
    $(window).scrollTop(0);
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

