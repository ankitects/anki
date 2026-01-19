<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import "./index.scss";

    import { onMount } from "svelte";
    import { ReviewerState } from "./reviewer";
    import ReviewerBottom from "./reviewer-bottom/ReviewerBottom.svelte";
    import Reviewer from "./Reviewer.svelte";
    import { _blockDefaultDragDropBehavior } from "../../reviewer";
    import type { PageData } from "./$types";
    import { checkNightMode } from "@tslib/nightmode";

    export let data: PageData;
    const state = new ReviewerState();
    const nightMode = checkNightMode();

    onMount(() => {
        globalThis.anki ??= {};
        globalThis.anki.changeReceived = () => state.showQuestion(null);
        _blockDefaultDragDropBehavior();
        state.undoStatus = data.initialUndoStatus;
    });
</script>

<div>
    <Reviewer {state} {nightMode}></Reviewer>
    <ReviewerBottom {state}></ReviewerBottom>
</div>

<style>
    div {
        height: 100vh;
        display: flex;
        flex-direction: column;
    }
</style>
