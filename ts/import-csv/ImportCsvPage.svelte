<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@tslib/anki/import_export_pb";
    import { importDone } from "@tslib/backend";

    import BackendProgressIndicator from "../components/BackendProgressIndicator.svelte";
    import Container from "../components/Container.svelte";
    import StickyHeader from "../components/StickyHeader.svelte";
    import ImportLogPage from "../import-log/ImportLogPage.svelte";
    import FieldMapper from "./FieldMapper.svelte";
    import FileOptions from "./FileOptions.svelte";
    import ImportOptions from "./ImportOptions.svelte";
    import type { ImportCsvState } from "./lib";

    export let state: ImportCsvState;
    export let path: string;

    let importResponse: ImportResponse | undefined = undefined;
    let importing = false;

    async function onImport(): Promise<ImportResponse> {
        const result = state.importCsv();
        await importDone({});
        importing = false;
        return result;
    }
</script>

<div class="outer">
    {#if importing}
        <BackendProgressIndicator task={onImport} bind:result={importResponse} />
    {:else if importResponse}
        <ImportLogPage response={importResponse} params={{ path }} />
    {:else}
        <StickyHeader {path} onImport={() => (importing = true)} />
        <Container
            breakpoint="sm"
            --gutter-inline="0.25rem"
            --gutter-block="0.75rem"
            class="container-columns"
        >
            <FileOptions {state} />
            <ImportOptions {state} />
            <FieldMapper {state} />
        </Container>
    {/if}
</div>

<style lang="scss">
    :global(.row) {
        // rows have negative margins by default
        --bs-gutter-x: 0;
        margin-bottom: 0.5rem;
    }
</style>
