<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { NextCardDataResponse_AnswerButton } from "@generated/anki/scheduler_pb";
    import * as tr from "@generated/ftl";
    import type { ReviewerState } from "../reviewer";

    export let info: NextCardDataResponse_AnswerButton;
    export let state: ReviewerState;

    const labels = [
        tr.studyingAgain(),
        tr.studyingHard(),
        tr.studyingGood(),
        tr.studyingEasy(),
    ];
    $: label = labels[info.rating];
</script>

<span>
    {#if info.due}
        {info.due}
    {:else}
        &nbsp;
    {/if}
</span>
<button on:click={() => state.easeButtonPressed(info.rating)}>
    {label}
</button>

<style>
    span {
        text-align: center;
    }
</style>
