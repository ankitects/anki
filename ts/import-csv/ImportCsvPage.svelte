<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import type { Decks, Generic, Notetypes } from "@tslib/proto";
    import { ImportExport, importExport } from "@tslib/proto";

    import Page from "../components/Page.svelte";
    import TitledContainer from "../components/TitledContainer.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import DelimiterSelector from "./DelimiterSelector.svelte";
    import DupeResolutionSelector from "./DupeResolutionSelector.svelte";
    import FieldMapper from "./FieldMapper.svelte";
    import HtmlSwitch from "./HtmlSwitch.svelte";
    import ImportFooter from "./ImportFooter.svelte";
    import { getColumnOptions, getCsvMetadata } from "./lib";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import Preview from "./Preview.svelte";
    import Tags from "./Tags.svelte";

    export let path: string;
    export let notetypeNameIds: Notetypes.NotetypeNameId[];
    export let deckNameIds: Decks.DeckNameId[];
    export let dupeResolution: ImportExport.CsvMetadata.DupeResolution;

    export let delimiter: ImportExport.CsvMetadata.Delimiter;
    export let forceDelimiter: boolean;
    export let forceIsHtml: boolean;
    export let isHtml: boolean;
    export let globalTags: string[];
    export let updatedTags: string[];
    export let columnLabels: string[];
    export let tagsColumn: number;
    export let guidColumn: number;
    export let preview: Generic.StringList[];
    // Protobuf oneofs. Exactly one of these pairs is expected to be set.
    export let notetypeColumn: number | null;
    export let globalNotetype: ImportExport.CsvMetadata.MappedNotetype | null;
    export let deckId: number | null;
    export let deckColumn: number | null;

    let lastNotetypeId = globalNotetype?.id;
    let lastDelimeter = delimiter;

    $: columnOptions = getColumnOptions(
        columnLabels,
        preview[0].vals,
        notetypeColumn,
        deckColumn,
        guidColumn,
    );
    $: getCsvMetadata(path, delimiter, undefined, isHtml).then((meta) => {
        columnLabels = meta.columnLabels;
        preview = meta.preview;
    });
    $: if (globalNotetype?.id !== lastNotetypeId || delimiter !== lastDelimeter) {
        lastNotetypeId = globalNotetype?.id;
        lastDelimeter = delimiter;
        getCsvMetadata(path, delimiter, globalNotetype?.id).then((meta) => {
            globalNotetype = meta.globalNotetype ?? null;
            tagsColumn = meta.tagsColumn;
        });
    }

    async function onImport(): Promise<void> {
        await importExport.importCsv(
            ImportExport.ImportCsvRequest.create({
                path,
                metadata: ImportExport.CsvMetadata.create({
                    dupeResolution,
                    delimiter,
                    forceDelimiter,
                    isHtml,
                    forceIsHtml,
                    globalTags,
                    updatedTags,
                    columnLabels,
                    tagsColumn,
                    guidColumn,
                    notetypeColumn,
                    globalNotetype,
                    deckColumn,
                    deckId,
                }),
            }),
        );
    }
</script>

<Page class="import-csv-page">
    <div class="layout">
        <div class="h-100" style:grid-area="file">
            <TitledContainer title={tr.importingFile()}>
                <DelimiterSelector bind:delimiter disabled={forceDelimiter} />
                <HtmlSwitch bind:isHtml disabled={forceIsHtml} />
                <Preview {columnOptions} {preview} />
            </TitledContainer>
        </div>
        <div class="h-100" style:grid-area="options">
            <TitledContainer title={tr.importingImportOptions()}>
                {#if globalNotetype}
                    <NotetypeSelector
                        {notetypeNameIds}
                        bind:notetypeId={globalNotetype.id}
                    />
                {/if}
                {#if deckId}
                    <DeckSelector {deckNameIds} bind:deckId />
                {/if}
                <DupeResolutionSelector bind:dupeResolution />
                <Tags bind:globalTags bind:updatedTags />
            </TitledContainer>
        </div>
        <div class="h-100" style:grid-area="mapping">
            <TitledContainer title={tr.importingFieldMapping()}>
                <FieldMapper {columnOptions} bind:globalNotetype bind:tagsColumn />
            </TitledContainer>
        </div>
    </div>

    <ImportFooter slot="footer" {onImport} />
</Page>

<style lang="scss">
    @use "sass/breakpoints" as bp;
    .layout {
        width: 100%;
        height: 100%;
        display: grid;
        grid-gap: 0.5rem;

        grid-template:
            "file"
            "options"
            "mapping";

        @include bp.with-breakpoint("md") {
            grid-template:
                "file file" 1fr
                "options mapping" 1fr / 1fr 1fr;
            grid-gap: 1rem;
        }
    }

    :global(.row) {
        // rows have negative margins by default
        --bs-gutter-x: 0;
    }
</style>
