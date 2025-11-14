<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ReviewerState } from "../reviewer";
    import { onDestroy } from "svelte";

    export let state: ReviewerState;

    let text = "";
    let cls = "";

    function step() {
        let time = Date.now() - state.beginAnsweringMs;
        const maxTime = state._cardData?.maxTimeMs ?? 0;
        if (time >= maxTime) {
            time = maxTime;
            cls = "overtime";
        } else {
            cls = "";
        }
        text = formatTime(time);
    }

    let interval: ReturnType<typeof setInterval> | undefined = undefined;
    function startTimer() {
        clearInterval(interval);
        interval = setInterval(step, 1000);
        text = formatTime(0);
        cls = "";
        console.log("startTimer");
    }

    state.cardData.subscribe(startTimer);

    onDestroy(() => {
        clearInterval(interval);
    });
    step();

    function formatTime(time: number) {
        const seconds = time / 1000;
        return `${Math.floor(seconds / 60)
            .toFixed(0)
            .padStart(2, "0")}:${(seconds % 60).toFixed(0).padStart(2, "0")}`;
    }
</script>

<div class={cls}>
    {text}
</div>

<style>
    div {
        width: 88px;
        text-align: center;
    }

    .overtime {
        color: red;
    }
</style>
