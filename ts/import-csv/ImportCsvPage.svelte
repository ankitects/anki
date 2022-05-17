<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Container from "../components/Container.svelte";
    import Row from "../components/Row.svelte";
    import Switch from "../deck-options/Switch.svelte";
    import * as tr from "../lib/ftl";
    import { Decks, ImportExport, importExport, Notetypes } from "../lib/proto";
    import DeckSelector from "./DeckSelector.svelte";
    import DelimiterSelector from "./DelimiterSelector.svelte";
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
            }),
        );
    }
</script>

<div style="--gutter-inline: 0.25rem;">
    <Row class="gx-0" --cols={2}>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <Header heading={tr.importingImportOptions()} />
                <NotetypeSelector {notetypeNameIds} bind:notetypeId />
                <DeckSelector {deckNameIds} bind:deckId />
                <DelimiterSelector bind:delimiter />
                <HtmlSwitch bind:isHtml />
            </Container>
        </Col>
        <Col --col-size={1} breakpoint="md">
            <Container>
                <Header heading={tr.importingFieldMapping()} />
                <FieldMapper {columnNames} {notetypeId} bind:fieldColumnIndices />
                <MetaMapper
                    {columnNames}
                    bind:tagsColumn
                    bind:notetypeColumn
                    bind:deckColumn
                />
            </Container>
        </Col>
    </Row>
    <Row><ImportButton {onImport} /></Row>
</div>
