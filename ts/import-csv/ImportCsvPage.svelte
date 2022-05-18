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
    import ImportButton from "./ImportButton.svelte";
    import { getCsvMetadata } from "./lib";
    import MetaMapper from "./MetaMapper.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";

    export let path: string;
    export let notetypeNameIds: Notetypes.NotetypeNameId[];
    export let deckNameIds: Decks.DeckNameId[];
    export let delimiter: ImportExport.CsvMetadata.Delimiter;
    // TODO
    export const tags: string = "";
    export let columnNames: string[];
    export let notetypeId: number;
    export let deckId: number;
    export let isHtml: boolean;

    let dupeResolution: ImportExport.ImportCsvRequest.DupeResolution;
    let fieldColumnIndices: number[];
    let tagsColumn: number;
    let deckColumn: number;
    let notetypeColumn: number;

    $: {
        getCsvMetadata(path, delimiter).then((meta) => {
            columnNames = meta.columns;
        });
    }

    async function onImport(): Promise<void> {
        await importExport.importCsv(
            ImportExport.ImportCsvRequest.create({
                path,
                deckId,
                notetypeId,
                delimiter,
                isHtml,
                columns: ImportExport.ImportCsvRequest.Columns.create({
                    fields: fieldColumnIndices,
                    tags: tagsColumn,
                    deck: deckColumn,
                    notetype: notetypeColumn,
                }),
                columnNames,
                dupeResolution,
            }),
        );
    }
</script>

<Container --gutter-inline="0.75rem" --gutter-block="0.25rem">
    <Row --cols={5}
        ><Col --col-size={4}>{path}</Col><Col><ImportButton {onImport} /></Col></Row
    >
    <Row --cols={2}>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <Header heading={tr.importingImportOptions()} />
                <Spacer --height="1.5rem" />
                <NotetypeSelector {notetypeNameIds} bind:notetypeId />
                <DeckSelector {deckNameIds} bind:deckId />
                <DupeResolutionSelector bind:dupeResolution />
                <DelimiterSelector bind:delimiter />
                <HtmlSwitch bind:isHtml />
            </Container>
        </Col>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <Header heading={tr.importingFieldMapping()} />
                <Spacer --height="1.5rem" />
                <FieldMapper {columnNames} {notetypeId} bind:fieldColumnIndices />
                <Spacer --height="1.5rem" />
                <MetaMapper
                    {columnNames}
                    bind:tagsColumn
                    bind:notetypeColumn
                    bind:deckColumn
                />
            </Container>
        </Col>
    </Row>
</Container>
