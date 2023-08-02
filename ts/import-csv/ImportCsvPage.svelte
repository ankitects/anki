<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { DeckNameId } from "@tslib/anki/decks_pb";
    import type { StringList } from "@tslib/anki/generic_pb";
    import type {
        CsvMetadata_Delimiter,
        CsvMetadata_DupeResolution,
        CsvMetadata_MappedNotetype,
        CsvMetadata_MatchScope,
        ImportResponse,
    } from "@tslib/anki/import_export_pb";
    import type { NotetypeNameId } from "@tslib/anki/notetypes_pb";
    import { getCsvMetadata, importCsv, importDone } from "@tslib/backend";
    import * as tr from "@tslib/ftl";

    import BackendProgressIndicator from "../components/BackendProgressIndicator.svelte";
    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Spacer from "../components/Spacer.svelte";
    import ImportLogPage from "../import-log/ImportLogPage.svelte";
    import DeckDupeCheckSwitch from "./DeckDupeCheckSwitch.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import DelimiterSelector from "./DelimiterSelector.svelte";
    import DupeResolutionSelector from "./DupeResolutionSelector.svelte";
    import FieldMapper from "./FieldMapper.svelte";
    import Header from "./Header.svelte";
    import HtmlSwitch from "./HtmlSwitch.svelte";
    import {
        buildDeckOneof,
        buildNotetypeOneof,
        getColumnOptions,
        tryGetDeckId,
        tryGetGlobalNotetype,
    } from "./lib";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import Preview from "./Preview.svelte";
    import StickyHeader from "./StickyHeader.svelte";
    import Tags from "./Tags.svelte";

    export let path: string;
    export let notetypeNameIds: NotetypeNameId[];
    export let deckNameIds: DeckNameId[];
    export let dupeResolution: CsvMetadata_DupeResolution;
    export let matchScope: CsvMetadata_MatchScope;
    export let delimiter: CsvMetadata_Delimiter;
    export let forceDelimiter: boolean;
    export let forceIsHtml: boolean;
    export let isHtml: boolean;
    export let globalTags: string[];
    export let updatedTags: string[];
    export let columnLabels: string[];
    export let tagsColumn: number;
    export let guidColumn: number;
    export let preview: StringList[];
    // Protobuf oneofs. Exactly one of these pairs is expected to be set.
    export let notetypeColumn: number | null;
    export let globalNotetype: CsvMetadata_MappedNotetype | null;
    export let deckId: bigint | null;
    export let deckColumn: number | null;

    let importResponse: ImportResponse | undefined = undefined;
    let lastNotetypeId = globalNotetype?.id;
    let lastDelimeter = delimiter;
    let importing = false;

    $: columnOptions = getColumnOptions(
        columnLabels,
        preview[0].vals,
        notetypeColumn,
        deckColumn,
        guidColumn,
    );
    $: getCsvMetadata({
        path,
        delimiter,
        notetypeId: undefined,
        deckId: undefined,
        isHtml,
    }).then((meta) => {
        columnLabels = meta.columnLabels;
        preview = meta.preview;
    });
    $: if (globalNotetype?.id !== lastNotetypeId || delimiter !== lastDelimeter) {
        lastNotetypeId = globalNotetype?.id;
        lastDelimeter = delimiter;
        getCsvMetadata({
            path,
            delimiter,
            notetypeId: globalNotetype?.id,
            deckId: deckId ?? undefined,
        }).then((meta) => {
            globalNotetype = tryGetGlobalNotetype(meta);
            deckId = tryGetDeckId(meta);
            tagsColumn = meta.tagsColumn;
        });
    }

    async function onImport(): Promise<ImportResponse> {
        const result = await importCsv({
            path,
            metadata: {
                dupeResolution,
                matchScope,
                delimiter,
                forceDelimiter,
                isHtml,
                forceIsHtml,
                globalTags,
                updatedTags,
                columnLabels,
                tagsColumn,
                guidColumn,
                deck: buildDeckOneof(deckColumn, deckId),
                notetype: buildNotetypeOneof(globalNotetype, notetypeColumn),
                preview: [],
            },
        });
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

        <Container class="csv-page">
            <Row --cols={2}>
                <Col --col-size={1} breakpoint="md">
                    <Container>
                        <Header heading={tr.importingFile()} />
                        <Spacer --height="1.5rem" />
                        <DelimiterSelector bind:delimiter disabled={forceDelimiter} />
                        <HtmlSwitch bind:isHtml disabled={forceIsHtml} />
                        <Preview {columnOptions} {preview} />
                    </Container>
                </Col>
                <Col --col-size={1} breakpoint="md">
                    <Container>
                        <Header heading={tr.importingImportOptions()} />
                        <Spacer --height="1.5rem" />
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
                        <DeckDupeCheckSwitch bind:matchScope />
                        <Tags bind:globalTags bind:updatedTags />
                    </Container>
                </Col>
                <Col --col-size={1} breakpoint="md">
                    <Container>
                        <Header heading={tr.importingFieldMapping()} />
                        <Spacer --height="1.5rem" />
                        <FieldMapper
                            {columnOptions}
                            bind:globalNotetype
                            bind:tagsColumn
                        />
                    </Container>
                </Col>
            </Row>
        </Container>
    {/if}
</div>

<style lang="scss">
    .outer {
        margin: 0 auto;
    }
    :global(.csv-page) {
        --gutter-inline: 0.25rem;

        :global(.row) {
            // rows have negative margins by default
            --bs-gutter-x: 0;
            margin-bottom: 0.5rem;
        }
    }
</style>
