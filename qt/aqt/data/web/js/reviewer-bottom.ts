/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

var time: number; // set in python code

let maxTime = 0;
document.addEventListener("DOMContentLoaded", () => {
    updateTime();
    setInterval(function () {
        time += 1;
        updateTime();
    }, 1000);
});

function updateTime(): void {
    const timeNode = document.getElementById("time");
    if (maxTime === 0) {
        timeNode.textContent = "";
        return;
    }
    time = Math.min(maxTime, time);
    const m = Math.floor(time / 60);
    const s = time % 60;
    const sStr = String(s).padStart(2, "0");
    const timeString = `${m}:${sStr}`;

    if (maxTime === time) {
        timeNode.innerHTML = `<font color=red>${timeString}</font>`;
    } else {
        timeNode.textContent = timeString;
    }
}

function showQuestion(txt: string, maxTime_: number): void {
    showAnswer(txt);
    time = 0;
    maxTime = maxTime_;
}

function showAnswer(txt: string): void {
    document.getElementById("middle").innerHTML = txt;
}

function selectedAnswerButton() {
    let node = document.activeElement as HTMLElement;
    if (!node) {
        return;
    }
    return node.dataset.ease;
}
