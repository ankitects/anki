<script lang="ts">
    import { writable } from "svelte/store";
    import ReviewerBottom from "./ReviewerBottom.svelte";
    import "./index.scss"

    let timerStopped = false;

    let maxTime = 0;

    function updateTime(): void {
        const timeNode = document.getElementById("time");
        if (maxTime === 0) {
            timeNode.textContent = "";
            return;
        }
        globalThis.time = Math.min(maxTime, globalThis.time);
        const m = Math.floor(globalThis.time / 60);
        const s = globalThis.time % 60;
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

    function _showQuestion(txt: string, maxTime_: number): void {
        _showAnswer([]);
        globalThis.time = 0;
        maxTime = maxTime_;
        // updateTime();

        if (intervalId !== undefined) {
            clearInterval(intervalId);
        }

        intervalId = setInterval(function() {
            if (!timerStopped) {
                globalThis.time += 1;
                //updateTime();
            }
        }, 1000);
    }

    function _showAnswer(info: AnswerButtonInfo[], stopTimer = false): void {
        console.log(info)
        answerButtons.set(info);
        timerStopped = stopTimer;
    }

    globalThis._showQuestion = _showQuestion;
    globalThis._showAnswer = _showAnswer;

    function selectedAnswerButton(): string | undefined {
        const node = document.activeElement as HTMLElement;
        if (!node) {
            return;
        }
        return node.dataset.ease;
    }

</script>

<ReviewerBottom {answerButtons}></ReviewerBottom>