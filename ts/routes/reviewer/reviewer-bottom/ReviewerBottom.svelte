<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import "./index.scss";

    import AnswerButton from "./AnswerButton.svelte";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@generated/ftl";
    import type { ReviewerState } from "../reviewer";
    import Remaining from "./Remaining.svelte";
    import More from "./More.svelte";

    export let state: ReviewerState;

    const answerButtons = state.answerButtons;
    const answerShown = state.answerShown;

    $: button_count = $answerShown ? $answerButtons.length : 1;
</script>

<div id="outer" class="fancy">
    <div id="tableinner" style="--answer-button-count: {button_count}">
        <span class="disappearing"></span>
        <div class="disappearing edit">
            <button
                title={tr.actionsShortcutKey({ val: "E" })}
                on:click={() => bridgeCommand("edit")}
            >
                {tr.studyingEdit()}
            </button>
        </div>
        {#if $answerShown}
            {#each $answerButtons as answerButton}
                <AnswerButton {state} info={answerButton}></AnswerButton>
            {/each}
        {:else}
            <Remaining {state}></Remaining>
            <button on:click={() => state.showAnswer()}>
                {tr.studyingShowAnswer()}
            </button>
        {/if}
        <span class="disappearing"></span>
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

    .more,
    .edit {
        width: 100%;
    }

    .more {
        text-align: right;
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
