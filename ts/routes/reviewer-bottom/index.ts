/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

/* eslint
@typescript-eslint/no-unused-vars: "off",
*/

import { mount } from "svelte";
import "./index.scss";
import ReviewerBottom from "./index.svelte";
import { writable } from "svelte/store";

let time: number; // set in python code
let timerStopped = false;

let maxTime = 0;

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

let intervalId: number | undefined;
let answerButtons = writable<AnswerButtonInfo[]>([])

export function showQuestion(txt: string, maxTime_: number): void {
    showAnswer([]);
    time = 0;
    maxTime = maxTime_;
    updateTime();

    if (intervalId !== undefined) {
        clearInterval(intervalId);
    }

    intervalId = setInterval(function() {
        if (!timerStopped) {
            time += 1;
            updateTime();
        }
    }, 1000);
}

export function showAnswer(info: AnswerButtonInfo[], stopTimer = false): void {
    answerButtons.set(info);
    timerStopped = stopTimer;
}

function selectedAnswerButton(): string {
    const node = document.activeElement as HTMLElement;
    if (!node) {
        return;
    }
    return node.dataset.ease;
}

mount(
    ReviewerBottom,
    { target: document.body, props: {answerButtons} },
);
