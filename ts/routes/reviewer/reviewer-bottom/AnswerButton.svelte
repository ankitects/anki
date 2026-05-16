<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ReviewerState } from "../reviewer";
    import * as tr from "@generated/ftl";

    export let due: string | undefined;
    export let rating: number;
    export let state: ReviewerState;

    const labels = [
        tr.studyingAgain(),
        tr.studyingHard(),
        tr.studyingGood(),
        tr.studyingEasy(),
    ];
    $: label = labels[rating];
</script>

<span>
    {#if due}
        {due}
    {:else}
        &nbsp;
    {/if}
</span>
<button on:click={() => state.easeButtonPressed(rating)}>
    {label}
</button>

<style>
    span {
        text-align: center;
    }
</style>
