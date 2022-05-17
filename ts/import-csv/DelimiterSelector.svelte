<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import { ImportExport } from "../lib/proto";

    export let delimiter: ImportExport.CsvMetadata.Delimiter;

    const delimiters = allDelimiters();

    let index = delimiters.findIndex((entry) => entry.value === delimiter);
    $: delimiter = delimiters[index].value;

    interface IDelimiter {
        value: ImportExport.CsvMetadata.Delimiter;
        label: string;
    }

    function allDelimiters(): IDelimiter[] {
        const Delimiter = ImportExport.CsvMetadata.Delimiter;
        return [
            { value: Delimiter.TAB, label: tr.importingTab() },
            { value: Delimiter.PIPE, label: tr.importingPipe() },
            { value: Delimiter.SEMICOLON, label: tr.importingSemicolon() },
            { value: Delimiter.COLON, label: tr.importingColon() },
            { value: Delimiter.COMMA, label: tr.importingComma() },
            { value: Delimiter.SPACE, label: tr.studyingSpace() },
        ];
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingFieldDelimiter()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={index}>
            {#each delimiters as { label }, idx}
                <option value={idx}>{label}</option>
            {/each}
        </select>
    </Col>
</Row>
