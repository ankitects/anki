<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DeckNameId } from "@generated/anki/decks_pb";
    import { getExportFilePath } from "@generated/backend";
    import * as tr from "@generated/ftl";
    import { bridgeCommand } from "@tslib/bridgecommand";

    import BackendProgressIndicator from "$lib/components/BackendProgressIndicator.svelte";
    import Container from "$lib/components/Container.svelte";
    import ErrorPage from "$lib/components/ErrorPage.svelte";
    import Row from "$lib/components/Row.svelte";

    import ContentOptions from "./ContentOptions.svelte";
    import FileOptions from "./FileOptions.svelte";
    import { createExportLimit } from "./lib";
    import StickyHeader from "./StickyHeader.svelte";
    import type { ExportOptions, LimitValue } from "./types";
    import { type Exporter } from "./types";

    export let deckId: bigint | null = null;
    export let noteIds: bigint[] = [];
    export let deckNames: DeckNameId[] = [];

    let exporter: Exporter;
    let limit: LimitValue;
    let limitLabel: string;
    let exportOptions: ExportOptions = {
        includeScheduling: false,
        includeDeckConfigs: false,
        includeMedia: true,
        includeTags: true,
        includeHtml: true,
        legacySupport: false,
        includeDeck: true,
        includeNotetype: true,
        includeGuid: false,
    };
    let result = undefined;
    let error: Error | undefined = undefined;
    let outPath: string = "";

    async function getOutPath() {
        outPath = (
            await getExportFilePath({
                exporter: exporter.label,
                extension: exporter.extension,
                filename: filename(),
            })
        ).val;
    }

    function filename(): string {
        const stem = currentLimitLabel() ?? collectionLabel();
        return `${stem}.${exporter.extension}`;
    }

    function currentLimitLabel(): string | null {
        if (!exporter.showDeckList) {
            return null;
        }
        return limitLabel.replace(/[\\/?<>:*|"^]/g, "_");
    }

    function collectionLabel(): string {
        return `${tr.exportingCollection()}-${new Date()
            .toISOString()
            .replace(/[-T:.]/g, "")}`;
    }

    async function onExport() {
        await exporter.doExport(outPath, createExportLimit(limit), exportOptions);
        bridgeCommand("close");
    }
</script>

{#if error}
    <ErrorPage {error} />
{:else if outPath}
    <BackendProgressIndicator task={onExport} bind:result bind:error />
{:else}
    <StickyHeader onAccept={getOutPath} />
    <Container
        breakpoint="sm"
        --gutter-inline="0.25rem"
        --gutter-block="0.75rem"
        class="container-columns"
    >
        <Row class="d-block">
            <FileOptions
                bind:exporter
                bind:exportOptions
                withLimit={deckId !== null || noteIds.length !== 0}
            />
        </Row>
        {#if exporter}
            <Row class="d-block">
                <ContentOptions
                    {deckId}
                    {deckNames}
                    {noteIds}
                    {exporter}
                    bind:limit
                    bind:limitLabel
                    bind:exportOptions
                />
            </Row>
        {/if}
    </Container>
{/if}

<style lang="scss">
    :global(.row) {
        // rows have negative margins by default
        --bs-gutter-x: 0;
        margin-bottom: 0.5rem;
    }
</style>
