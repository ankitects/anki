<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import "./index.scss";

    import AnswerButton from "./AnswerButton.svelte";
    import * as tr from "@generated/ftl";
    import type { ReviewerState } from "../reviewer";
    import Remaining from "./Remaining.svelte";
    import More from "./More.svelte";
    import Timer from "./Timer.svelte";

    export let state: ReviewerState;

    const answerButtons = state.answerButtons;
    const answerShown = state.answerShown;

    $: buttonCount = $answerShown ? $answerButtons.length : 1;
    $: cardData = state.cardData;
    $: remainingShown =
        ($cardData?.queue?.learningCount ?? 0) +
            ($cardData?.queue?.reviewCount ?? 0) +
            ($cardData?.queue?.newCount ?? 0) >
        0;
</script>

<div id="outer" class="fancy">
    <div id="tableinner" style="--answer-button-count: {buttonCount}">
        <span class="disappearing"></span>
        <div class="disappearing edit">
            <button
                title={tr.actionsShortcutKey({ val: "E" })}
                on:click={() => state.displayEditMenu()}
            >
                {tr.studyingEdit()}
            </button>
        </div>
        {#if $answerShown}
            {#each $answerButtons as answerButton}
                <AnswerButton {state} info={answerButton}></AnswerButton>
            {/each}
        {:else}
            {#if remainingShown}
                <Remaining {state}></Remaining>
            {:else}
                <span>&nbsp;</span>
            {/if}
            <button on:click={() => state.showAnswer()}>
                {tr.studyingShowAnswer()}
            </button>
        {/if}
        <div class="disappearing more">
            <Timer {state}></Timer>
        </div>
        <div class="disappearing more">
            <More {state}></More>
        </div>
    </div>
</div>

<style lang="scss">
    #tableinner {
        width: 100%;
        display: grid;
        grid-template-columns: 1fr repeat(var(--answer-button-count, 1), auto) 1fr;
        grid-template-rows: auto auto;
        justify-content: space-between;
        justify-items: center;
        align-items: center;
        grid-auto-flow: column;
    }

    #outer {
        padding: 8px;
    }

    .more,
    .edit {
        width: 100%;
    }

    .more {
        // text-align: right;
        direction: rtl;
    }

    @media (max-width: 583px) {
        .disappearing {
            display: none;
        }

        #tableinner {
            grid-template-columns: repeat(var(--answer-button-count, 1), auto);
            justify-content: center;
        }
    }
</style>
