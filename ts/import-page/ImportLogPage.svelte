<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@tslib/anki/import_export_pb";
    import * as tr from "@tslib/ftl";

    import Container from "../components/Container.svelte";
    import CloseButton from "./CloseButton.svelte";
    import DetailsTable from "./DetailsTable.svelte";
    import { getSummaries } from "./lib";
    import QueueSummary from "./QueueSummary.svelte";

    export let response: ImportResponse;
    const result = response;
    $: summaries = result ? getSummaries(result.log!) : [];
    $: foundNotes = result?.log?.foundNotes ?? 0;
    let closeButton: HTMLElement;
</script>

<Container class="import-log-page">
    {#if result}
        <p class="note-count">
            {tr.importingNotesFoundInFile2({
                notes: foundNotes,
            })}
        </p>
        <ul class="summary-list">
            {#each summaries as summary}
                <QueueSummary {summary} />
            {/each}
        </ul>
        {#if closeButton}
            <DetailsTable {summaries} bind:bottomOffset={closeButton.clientHeight} />
        {/if}
        <CloseButton bind:container={closeButton} />
    {/if}
</Container>

<style lang="scss">
    :global(.import-log-page) {
        font-size: 16px;
        margin: 8px auto;
    }
    .note-count {
        margin-bottom: 4px;
    }
    .summary-list {
        padding-inline-start: 8px;
    }
</style>
