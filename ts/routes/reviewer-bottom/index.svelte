<script lang="ts">
    import type { Writable } from "svelte/store";
    import AnswerButton from "./AnswerButton.svelte";
    import { bridgeCommand } from "@tslib/bridgecommand";
    import * as tr from "@generated/ftl";

    export let answerButtons: Writable<AnswerButtonInfo[]>
    $: console.log($answerButtons)
</script>

<div id="outer fancy">
    <div id="tableinner">
        <div>
            <button title={tr.actionsShortcutKey({val: "E"})} on:click={()=>bridgeCommand("edit")}>{tr.studyingEdit()}</button>
        </div>
        <div>
            {#if $answerButtons.length}
                {#each $answerButtons as answerButton}
                    <AnswerButton info={answerButton}></AnswerButton>
                {/each}
            {:else}
                <button on:click={()=>bridgeCommand("ans")}>{tr.studyingShowAnswer()}</button>
            {/if}
        </div>
        <div>
            <button title={tr.actionsShortcutKey({val: "M"})}>{tr.studyingMore()}&#8615</button>
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
</style>
