<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Spacer from "../components/Spacer.svelte";
    import * as tr from "../lib/ftl";
    import { Decks, ImportExport, importExport, Notetypes } from "../lib/proto";
    import DeckSelector from "./DeckSelector.svelte";
    import DelimiterSelector from "./DelimiterSelector.svelte";
    import DupeResolutionSelector from "./DupeResolutionSelector.svelte";
    import FieldMapper from "./FieldMapper.svelte";
    import Header from "./Header.svelte";
    import HtmlSwitch from "./HtmlSwitch.svelte";
    import { getColumnOptions, getCsvMetadata } from "./lib";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import StickyFooter from "./StickyFooter.svelte";

    export let path: string;
    export let notetypeNameIds: Notetypes.NotetypeNameId[];
    export let deckNameIds: Decks.DeckNameId[];

    export let delimiter: ImportExport.CsvMetadata.Delimiter;
    export let forceDelimiter: boolean;
    export let forceIsHtml: boolean;
    export let isHtml: boolean;
    // TODO
    export const tags: string = "";
    export let columnLabels: string[];
    export let tagsColumn: number;
    // Protobuf oneofs. Exactly one of these pairs is expected to be set.
    export let notetypeColumn: number | null;
    export let globalNotetype: ImportExport.CsvMetadata.MappedNotetype | null;
    export let deckId: number | null;
    export let deckColumn: number | null;

    let dupeResolution: ImportExport.ImportCsvRequest.DupeResolution;
    let lastNotetypeId = globalNotetype?.id;

    $: columnOptions = getColumnOptions(columnLabels, notetypeColumn, deckColumn);
    $: getCsvMetadata(path, delimiter).then((meta) => {
        columnLabels = meta.columnLabels;
    });
    $: if (globalNotetype?.id !== lastNotetypeId) {
        lastNotetypeId = globalNotetype?.id;
        getCsvMetadata(path, delimiter, globalNotetype?.id).then((meta) => {
            globalNotetype = meta.globalNotetype ?? null;
        });
    }

    async function onImport(): Promise<void> {
        await importExport.importCsv(
            ImportExport.ImportCsvRequest.create({
                path,
                dupeResolution,
                metadata: ImportExport.CsvMetadata.create({
                    delimiter,
                    forceDelimiter,
                    isHtml,
                    forceIsHtml,
                    tags,
                    columnLabels,
                    tagsColumn,
                    notetypeColumn,
                    globalNotetype,
                    deckColumn,
                    deckId,
                }),
            }),
        );
    }
</script>

<Container --gutter-inline="0.75rem" --gutter-block="0.25rem">
    <Row --cols={2}>
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
                <DelimiterSelector bind:delimiter disabled={forceDelimiter} />
                <HtmlSwitch bind:isHtml disabled={forceIsHtml} />
            </Container>
        </Col>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <Header heading={tr.importingFieldMapping()} />
                <Spacer --height="1.5rem" />
                <FieldMapper {columnOptions} bind:globalNotetype bind:tagsColumn />
            </Container>
        </Col>
    </Row>
    <StickyFooter {path} {onImport} />
</Container>
