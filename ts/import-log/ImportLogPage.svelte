<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@tslib/anki/import_export_pb";
    import {
        importAnkiPackage,
        importDone,
        importJsonFile,
        importJsonString,
    } from "@tslib/backend";
    import * as tr from "@tslib/ftl";

    import BackendProgressIndicator from "../components/BackendProgressIndicator.svelte";
    import Container from "../components/Container.svelte";
    import CloseButton from "./CloseButton.svelte";
    import DetailsTable from "./DetailsTable.svelte";
    import { getSummaries } from "./lib";
    import QueueSummary from "./QueueSummary.svelte";
    import type { LogParams } from "./types";

    export let params: LogParams;
    export let response: ImportResponse | undefined = undefined;
    let result = response;
    $: summaries = result ? getSummaries(result.log!) : [];
    $: foundNotes = result?.log?.foundNotes ?? 0;
    let closeButton: HTMLElement;

    async function onImport(): Promise<ImportResponse | undefined> {
        let result: ImportResponse | undefined;
        switch (params.type) {
            case "apkg":
                result = await importAnkiPackage({
                    packagePath: params.path,
                });
                break;
            case "json_file":
                result = await importJsonFile({ val: params.path });
                break;
            case "json_string":
                result = await importJsonString({ val: params.json });
                break;
        }
        await importDone({});
        return result;
    }
</script>

<Container class="import-log-page">
    <BackendProgressIndicator task={onImport} bind:result />
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
