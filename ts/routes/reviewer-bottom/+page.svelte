<script lang="ts">
    import { onMount } from "svelte";
    import { writable } from "svelte/store";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import ReviewerBottom from "./ReviewerBottom.svelte";
    import type {AnswerButtonInfo} from "./types"
    import "./index.scss"

    const answerButtons = writable<AnswerButtonInfo[]>([])
    const remaining = writable<[number, number, number]>([0, 0, 0])
    const remainingIndex = writable<number>(-1)

    onMount(() => {
        /*
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
        }*/

        let intervalId: number | undefined;


        function _showQuestion(_txt: string, _maxTime_: number): void {
            _showAnswer([]);
            globalThis.time = 0;
            // maxTime = maxTime_;
            // updateTime();

            if (intervalId !== undefined) {
                clearInterval(intervalId);
            }

            /*
            intervalId = setInterval(function() {
                if (!timerStopped) {
                    globalThis.time += 1;
                    //updateTime();
                }
            }, 1000);*/
        }

        function _showAnswer(info: AnswerButtonInfo[], _stopTimer = false): void {
            console.log(info)
            answerButtons.set(info);
            // timerStopped = stopTimer;
        }

        function _updateRemaining(counts: [number, number, number], idx: number) {
            remaining.set(counts)
            remainingIndex.set(idx)
        }

        globalThis._showQuestion = _showQuestion;
        globalThis._showAnswer = _showAnswer;
        globalThis._updateRemaining = _updateRemaining;

        /*
        function selectedAnswerButton(): string | undefined {
            const node = document.activeElement as HTMLElement;
            if (!node) {
                return;
            }
            return node.dataset.ease;
        }
        */
        bridgeCommand("bottomReady");
    });
</script>


<ReviewerBottom {answerButtons} {remaining} {remainingIndex}></ReviewerBottom>