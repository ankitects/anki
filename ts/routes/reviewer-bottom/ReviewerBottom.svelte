<script lang="ts">
    import type { Writable } from "svelte/store";
    import AnswerButton from "./AnswerButton.svelte";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@generated/ftl";
    import RemainingNumber from "./RemainingNumber.svelte";

    export let answerButtons: Writable<AnswerButtonInfo[]>
    export let remaining: Writable<number[]>
    export let remainingIndex: Writable<number>

    $: console.log($remaining)
</script>

<div id="outer fancy">
    <div id="tableinner">
        <div>
            <button title={tr.actionsShortcutKey({val: "E"})} on:click={()=>bridgeCommand("edit")}>{tr.studyingEdit()}</button>
        </div>
        <div class="review-buttons">
            <span>
                <RemainingNumber cls="new-count" underlined={$remainingIndex === 0}>{$remaining[0]}</RemainingNumber> +
                <RemainingNumber cls="learn-count" underlined={$remainingIndex === 1}>{$remaining[1]}</RemainingNumber> +
                <RemainingNumber cls="review-count" underlined={$remainingIndex === 2}>{$remaining[2]}</RemainingNumber>
            </span>
            <div>
                {#if $answerButtons.length}
                    {#each $answerButtons as answerButton}
                        <AnswerButton info={answerButton}></AnswerButton>
                    {/each}
                {:else}
                    <button on:click={()=>bridgeCommand("ans")}>{tr.studyingShowAnswer()}</button>
                {/if}
            </div>
        </div>
        <div>
            <button on:click={()=>bridgeCommand("more")} title={tr.actionsShortcutKey({val: "M"})}>{tr.studyingMore()}&#8615</button>
        </div>
    </div>
</div>

<style lang="scss">
    #tableinner {
        width: 100%;
        display: grid;
        grid-template-columns: auto 1fr auto;
        justify-items: center;
    }

    .review-buttons {
        display: flex;
        flex-direction: column;
        align-items: center
    }
</style>
