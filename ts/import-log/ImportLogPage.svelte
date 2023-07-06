<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        ImportResponse,
        ImportResponse_Log,
    } from "@tslib/anki/import_export_pb";
    import * as tr from "@tslib/ftl";

    import Container from "../components/Container.svelte";
    import DetailsTable from "./DetailsTable.svelte";
    import QueueSummary from "./QueueSummary.svelte";

    import { getSummaries } from "./lib";

    export let response: ImportResponse;
    const log = response?.log ?? ({} as ImportResponse_Log);

    $: summaries = getSummaries(log);
    $: importedCount = summaries.reduce(
        (total, summary) =>
            total +
            (summary.canBrowse
                ? summary.queues.reduce((sum, queue) => sum + queue.notes.length, 0)
                : 0),
        0,
    );
</script>

<Container class="import-log-page">
    <b class="note-count">
        {tr.importingNumNotesFoundInFile({ count: log.foundNotes })}
        {tr.importingNotesPartOfCollection({
            count: importedCount,
            percent: (importedCount / log.foundNotes) * 100,
        })}
    </b>
    <ul class="summary-list">
        {#each summaries as summary}
            <QueueSummary {summary} />
        {/each}
    </ul>
    <DetailsTable {summaries} />
</Container>

<style lang="scss">
    .note-count {
        margin-bottom: 4px;
    }
    .summary-list {
        padding: 0px;
    }
</style>
