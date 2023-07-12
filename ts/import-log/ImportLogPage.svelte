<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type {
        ImportResponse,
        ImportResponse_Log,
    } from "@tslib/anki/import_export_pb";
    import {
        importAnkiPackage,
        importJsonFile,
        importJsonString,
    } from "@tslib/backend";
    import * as tr from "@tslib/ftl";

    import BackendProgressIndicator from "../components/BackendProgressIndicator.svelte";
    import Container from "../components/Container.svelte";
    import DetailsTable from "./DetailsTable.svelte";
    import { getSummaries } from "./lib";
    import QueueSummary from "./QueueSummary.svelte";
    import type { LogParams } from "./types";

    export let params: LogParams;
    export let response: ImportResponse | undefined = undefined;
    let result = response;
    $: log = result?.log ?? ({} as ImportResponse_Log);
    $: if (!log.foundNotes) log.foundNotes = 0;
    $: summaries = log.foundNotes ? getSummaries(log) : [];
    $: importedCount = summaries.reduce(
        (total, summary) =>
            total +
            (summary.canBrowse
                ? summary.queues.reduce((sum, queue) => sum + queue.notes?.length, 0)
                : 0),
        0,
    );
</script>

<Container class="import-log-page">
    <BackendProgressIndicator
        task={async () => {
            switch (params.type) {
                case "apkg":
                    return importAnkiPackage({ packagePath: params.path });
                case "json_file":
                    return importJsonFile({ val: params.path });
                case "json_string":
                    return importJsonString({ val: params.json });
            }
        }}
        bind:result
    />
    {#if result}
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
    {/if}
</Container>

<style lang="scss">
    :global(.import-log-page) {
        font-size: 20px;
        margin: 8px auto;
    }
    .note-count {
        margin-bottom: 4px;
    }
    .summary-list {
        padding: 0px;
    }
</style>
