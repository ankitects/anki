<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface Importer {
        doImport: () => Promise<ImportResponse>;
    }
</script>

<script lang="ts">
    import "./import-page-base.scss";
    
    import type { ImportResponse } from "@generated/anki/import_export_pb";
    import { importDone } from "@generated/backend";

    import BackendProgressIndicator from "$lib/components/BackendProgressIndicator.svelte";
    import Container from "$lib/components/Container.svelte";
    import ErrorPage from "$lib/components/ErrorPage.svelte";
    import StickyHeader from "./StickyHeader.svelte";

    import ImportLogPage from "./ImportLogPage.svelte";

    export let path: string;
    export let importer: Importer;
    export const noOptions: boolean = false;

    let importResponse: ImportResponse | undefined = undefined;
    let error: Error | undefined = undefined;
    let importing = noOptions;

    async function onImport(): Promise<ImportResponse> {
        const result = await importer.doImport();
        await importDone({});
        importing = false;
        return result;
    }
</script>

{#if error}
    <ErrorPage {error} />
{:else if importResponse}
    <ImportLogPage response={importResponse} />
{:else if importing}
    <BackendProgressIndicator task={onImport} bind:result={importResponse} bind:error />
{:else}
    <div class="pre-import-page">
        <StickyHeader {path} onImport={() => (importing = true)} />
        <Container
            breakpoint="sm"
            --gutter-inline="0.25rem"
            --gutter-block="0.5rem"
            class="container-columns"
        >
            <slot />
        </Container>
    </div>
{/if}

<style lang="scss">
    :global(.row) {
        // rows have negative margins by default
        --bs-gutter-x: 0;
        margin-bottom: 0.5rem;
    }

    .pre-import-page {
        margin: 0 auto;
    }
</style>
