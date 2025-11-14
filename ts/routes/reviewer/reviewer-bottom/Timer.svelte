<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ReviewerState } from "../reviewer";
    import { onMount } from "svelte";

    export let state: ReviewerState;

    let text = "";

    function step() {
        text = formatTime(Date.now() - state.beginAnsweringMs);
    }

    onMount(() => {
        const interval = setInterval(step, 1000);
        return () => {
            clearInterval(interval);
        };
    });
    step();

    function formatTime(time: number) {
        const seconds = time / 1000;
        return `${Math.floor(seconds / 60)
            .toFixed(0)
            .padStart(2, "0")}:${(seconds % 60).toFixed(0).padStart(2, "0")}`;
    }
</script>

<span>
    <div>
        {text}
    </div>
</span>

<style>
    div {
        width: 88px;
        text-align: center;
    }
</style>
