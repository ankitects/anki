<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import "./index.scss";

    import { onMount } from "svelte";
    import { ReviewerState, updateNightMode } from "./reviewer";
    import ReviewerBottom from "./reviewer-bottom/ReviewerBottom.svelte";
    import Reviewer from "./Reviewer.svelte";
    import { _blockDefaultDragDropBehavior } from "../../reviewer";

    const state = new ReviewerState();
    onMount(() => {
        updateNightMode();
        globalThis.anki ??= {};
        globalThis.anki.changeReceived = () => state.showQuestion(null);
        _blockDefaultDragDropBehavior();
    });
    $: cardData = state.cardData;
    $: flag = $cardData?.queue?.cards[0].card?.flags;
    $: marked = $cardData?.marked;
</script>

<div>
    <Reviewer {state}></Reviewer>
    <ReviewerBottom {state}></ReviewerBottom>
</div>

{#if flag}
    <div id="_flag" style:color={`var(--flag-${flag})`}>⚑</div>
{/if}

{#if marked}
    <div id="_mark">★</div>
{/if}

<style>
    div {
        height: 100vh;
        display: flex;
        flex-direction: column;
    }
</style>
