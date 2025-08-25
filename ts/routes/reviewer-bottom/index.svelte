<script lang="ts">
    import type { Writable } from "svelte/store";
    import AnswerButton from "./AnswerButton.svelte";
    import { bridgeCommand } from "@tslib/bridgecommand";

    export let answerButtons: Writable<AnswerButtonInfo[]>
    $: console.log($answerButtons)
</script>

<div id="outer fancy">
    <div id="tableinner">
        <div>
            <button>Edit</button>
        </div>
        <div>
            {#if $answerButtons.length}
                {#each $answerButtons as answerButton}
                    <AnswerButton info={answerButton}></AnswerButton>
                {/each}
            {:else}
                <button on:click={()=>bridgeCommand("ans")}>Review Buttons</button>
            {/if}
        </div>
        <div>
            <button>More</button>
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
