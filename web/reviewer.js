var ankiPlatform = "desktop";
var typeans;
var currentAns = "_answer1";
var nextAns = "_answer2";
var fadeTime = 200;

function _switchAnswerTarget() {
    if (currentAns === "_answer1") {
        currentAns = "_answer2";
        nextAns = "_answer1";
    } else {
        currentAns = "_answer1";
        nextAns = "_answer2";
    }
}

function _makeVisible(jquery) {
    return jquery.css("visibility", "visible").css("opacity", "1");
}

function _makeHidden(jquery) {
    // must alter visibility as well so hidden elements are not clickable
    return jquery.css("visibility", "hidden").css("opacity", "0");
}

function _showQuestion(q, a, bodyclass) {
    document.body.className = bodyclass;

    // update question text
    _makeHidden($("#_question")).html(q);

    // preload answer
    _makeHidden($("#"+nextAns)).html(a);

    // fade out previous answer
    $("#"+currentAns).fadeTo(fadeTime, 0, function () {
        // hide fully
        _makeHidden($("#"+currentAns));
        // and reveal question when processing is done
        MathJax.Hub.Queue(function () {
            $("#_question").css("visibility", "visible").fadeTo(fadeTime, 1);
        });
    });

    // focus typing area if visible
    typeans = document.getElementById("typeans");
    if (typeans) {
        typeans.focus();
    }

    // return to top of window
    window.scrollTo(0, 0);

    // don't allow drags of images, which cause them to be deleted
    $("img").attr("draggable", false);

    // render mathjax
    MathJax.Hub.Queue(["Typeset", MathJax.Hub]);
}

function _showAnswer(nextQuestion){
    // hide question; show answer
    _makeHidden($("#_question"));
    _makeVisible($("#"+nextAns));

    // scroll to answer?
    var e = $("#answer");
    if (e[0]) {
        e[0].scrollIntoView();
    }

    _switchAnswerTarget();
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
