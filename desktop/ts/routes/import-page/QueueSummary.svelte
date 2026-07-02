<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Icon from "$lib/components/Icon.svelte";
    import IconConstrain from "$lib/components/IconConstrain.svelte";

    import { showInBrowser } from "./lib";
    import type { SummarizedLogQueues } from "./types";

    export let summary: SummarizedLogQueues;

    $: notes = summary.queues.map((queue) => queue.notes).flat();

    function onShow(event: MouseEvent) {
        showInBrowser(notes);
        event.preventDefault();
    }
</script>

{#if notes.length}
    <li>
        <IconConstrain>
            <Icon icon={summary.icon} />
        </IconConstrain>
        {summary.summaryTemplate({ count: notes.length })}
        {#if summary.canBrowse}
            <button class="desktop-only" on:click={onShow}>{tr.importingShow()}</button>
        {/if}
    </li>
{/if}

<style lang="scss">
    li {
        list-style-type: none;
    }
</style>
