<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import { onMount } from "svelte";
    import { ReviewerState, updateNightMode } from "./reviewer";
    import ReviewerBottom from "./reviewer-bottom/ReviewerBottom.svelte";
    import Reviewer from "./Reviewer.svelte";

    const state = new ReviewerState();
    onMount(() => {
        updateNightMode();
        globalThis.anki ??= {};
        globalThis.anki.changeReceived = () => state.showQuestion(null);
    });
</script>

<div>
    <Reviewer {state}></Reviewer>
    <ReviewerBottom {state}></ReviewerBottom>
</div>

<style>
    div {
        height: 100vh;
        display: flex;
        flex-direction: column;
    }
</style>
