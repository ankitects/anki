<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Writable } from "svelte/store";
    import AnswerButton from "./AnswerButton.svelte";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@generated/ftl";
    import RemainingNumber from "./RemainingNumber.svelte";
    import type { AnswerButtonInfo } from "./types";

    export let answerButtons: Writable<AnswerButtonInfo[]>;
    export let remaining: Writable<number[]>;
    export let remainingIndex: Writable<number>;

    $: console.log($remaining);
    $: answerShown = $answerButtons.length;
</script>

<div id="outer" class="fancy">
    <div id="tableinner" style="--answer-button-count: {$answerButtons.length || 1}">
        <span class="disappearing"></span>
        <div class="disappearing edit">
            <button
                title={tr.actionsShortcutKey({ val: "E" })}
                on:click={() => bridgeCommand("edit")}
            >
                {tr.studyingEdit()}
            </button>
        </div>
        {#if answerShown}
            {#each $answerButtons as answerButton}
                <AnswerButton info={answerButton}></AnswerButton>
            {/each}
        {:else}
            <span class="remaining-count">
                <RemainingNumber cls="new-count" underlined={$remainingIndex === 0}>
                    {$remaining[0]}
                </RemainingNumber> +
                <RemainingNumber cls="learn-count" underlined={$remainingIndex === 1}>
                    {$remaining[1]}
                </RemainingNumber> +
                <RemainingNumber cls="review-count" underlined={$remainingIndex === 2}>
                    {$remaining[2]}
                </RemainingNumber>
            </span>
            <button on:click={() => bridgeCommand("ans")}>
                {tr.studyingShowAnswer()}
            </button>
        {/if}
        <span class="disappearing"></span>
        <div class="disappearing more">
            <button
                on:click={() => bridgeCommand("more")}
                title={tr.actionsShortcutKey({ val: "M" })}
            >
                {tr.studyingMore()}&#8615
            </button>
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

    .remaining-count {
        text-align: center;
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
