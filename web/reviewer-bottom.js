/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

var time; // set in python code

var maxTime = 0;
$(function () {
    $("#ansbut").focus();
    updateTime();
    setInterval(function () {
        time += 1;
        updateTime()
    }, 1000);
});

var updateTime = function () {
    var timeNode = $("#time");
    if (!maxTime) {
        timeNode.text("");
        return;
    }
    time = Math.min(maxTime, time);
    var m = Math.floor(time / 60);
    var s = time % 60;
    if (s < 10) {
        s = "0" + s;
    }
    if (maxTime === time) {
        timeNode.html("<font color=red>" + m + ":" + s + "</font>");
    } else {
        timeNode.text(m + ":" + s);
    }
};

function showQuestion(txt, maxTime_) {
    // much faster than jquery's .html()
    $("#middle")[0].innerHTML = txt;
    $("#ansbut").focus();
    time = 0;
    maxTime = maxTime_;
}

function showAnswer(txt) {
    $("#middle")[0].innerHTML = txt;
    $("#defease").focus();
}

function selectedAnswerButton() {
    var node = document.activeElement;
    if (!node) {
        return;
    }
    return node.dataset.ease;
}
