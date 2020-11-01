/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

var time: number; // set in python code

let maxTime = 0;
$(function () {
    $("#ansbut").focus();
    updateTime();
    setInterval(function () {
        time += 1;
        updateTime();
    }, 1000);
});

let updateTime = function () {
    let timeNode = $("#time");
    if (!maxTime) {
        timeNode.text("");
        return;
    }
    time = Math.min(maxTime, time);
    const m = Math.floor(time / 60);
    const s = time % 60;
    let sStr = s.toString();
    if (s < 10) {
        sStr = "0" + s;
    }
    if (maxTime === time) {
        timeNode.html("<font color=red>" + m + ":" + sStr + "</font>");
    } else {
        timeNode.text(m + ":" + sStr);
    }
};

function showQuestion(txt, maxTime_) {
    // much faster than jquery's .html()
    $("#middle")[0].innerHTML = txt;
    time = 0;
    maxTime = maxTime_;
}

function showAnswer(txt) {
    $("#middle")[0].innerHTML = txt;
}

function selectedAnswerButton() {
    let node = document.activeElement as HTMLElement;
    if (!node) {
        return;
    }
    return node.dataset.ease;
}
