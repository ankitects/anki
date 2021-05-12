/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

declare var CodeJar: any;
declare var withLineNumbers: any;
declare var Prism: any;
declare var hljs: any;
declare var markdownit: any;
declare var console: Console;

var ankiPlatform = "desktop";
var typeans;
var codeans;
var log;
var codeansJar;
var _updatingQA = false;
var currlang;

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

    var qa = $("#qa");
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
    _displayProgressBar(raise, '#38c172', '#8fddb0')
}

function _setProgressError() {
    _displayProgressBar('100', '#e3342f')
}

function _setProgressCancelled() {
    _displayProgressBar('100', '#fff403')
}

function _displayProgressBar(raise, barColor, barColorAlt = null) {
    const color = $('body').hasClass('nightMode') ? '#444' : '#dadada';
    if (raise == 100) {
        barColorAlt = null;
    }
    (<any>$('#progressbar')).jQMeter({
        goal: '100',
        raised: raise,
        height: '5px',
        barColor: barColor,
        altBarColor: barColorAlt,
        bgColor: color,
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
    let options = {
        tab: " ".repeat(4), // default is '\t'
        indentOn: /[(\[]$/, // default is /{$/
        height: '66vh'
    };

    codeansJar = CodeJar(codeans, withLineNumbers(highlight), options);
    currlang = codeans.className.split(' ').find(it => it.indexOf('language-') >= 0).replace('language-', '')
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
    setTimeout(function() {
        codeansJar.highlight()
    }, 50)
}

function _showAnswer(a, bodyclass, isCodingQuestion, currLang) {
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
    $(codeans).find(".editor").each(function() {
        $(this).removeClass(function (index, className) {
            return (className.match(/\blanguage-\S+/g) || []).join(" ");
        }).addClass("language-" + lang);
    })
    codeansJar.updateCode(src);
    currlang = lang
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
    const md = markdownit({
        highlight: function (str, lang) {
            if (lang && hljs.getLanguage(lang)) {
                try {
                    return '<pre class="hljs"><code id="' + lang + '">' +
                            hljs.highlight(lang, str, true).value +
                            '</code></pre>';
                } catch (ignore) { }
            }
            return '<pre class="hljs"><code id="' + lang + '">' + md.utils.escapeHtml(str) + '</code></pre>';
        }
    });
    let src = _extractSolutionMarkdownSrc($qa);
    $qa.addClass('markdown-body').html(md.render(src));
    _scrollTo(currlang)
}

function _scrollTo(lang) {
    const el = document.querySelector('.hljs #' + lang);
    if (el) {
        el.scrollIntoView();
    }
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

function _extractSolutionMarkdownSrc($qa) {
    $qa.find('style').remove();
    $qa.find('hr#answer').remove();
    const solution = $qa.html();
    let text = _htmlDecode(solution);
    text = text.replace(/`/gi, '\`');
    return text.replace('[[code:Solution]]', '');
}

function _htmlDecode(input) {
  const e = document.createElement('textarea');
  e.innerHTML = input;
  let result = e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
  return result.replace(/<br>/gi, '\n')
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

