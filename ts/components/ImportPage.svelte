<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { ImportResponse } from "@tslib/anki/import_export_pb";
    import { importDone } from "@tslib/backend";

    import ImportLogPage from "../import-log/ImportLogPage.svelte";
    import BackendProgressIndicator from "./BackendProgressIndicator.svelte";
    import Container from "./Container.svelte";
    import StickyHeader from "./StickyHeader.svelte";

    export let path: string;
    export let doImport: () => Promise<ImportResponse>;

    let importResponse: ImportResponse | undefined = undefined;
    let importing = false;

    async function onImport(): Promise<ImportResponse> {
        const result = doImport();
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
            <slot />
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
