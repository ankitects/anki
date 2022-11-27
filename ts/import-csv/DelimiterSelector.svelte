<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { ImportExport } from "@tslib/proto";
    
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let delimiter: ImportExport.CsvMetadata.Delimiter;
    export let disabled: boolean;

    const Delimiter = ImportExport.CsvMetadata.Delimiter;
    const delimiters = [
        { value: Delimiter.TAB, label: tr.importingTab() },
        { value: Delimiter.PIPE, label: tr.importingPipe() },
        { value: Delimiter.SEMICOLON, label: tr.importingSemicolon() },
        { value: Delimiter.COLON, label: tr.importingColon() },
        { value: Delimiter.COMMA, label: tr.importingComma() },
        { value: Delimiter.SPACE, label: tr.studyingSpace() },
    ];
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingFieldSeparator()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={delimiter} {disabled}>
            {#each delimiters as { value, label }}
                <option {value}>{label}</option>
            {/each}
        </select>
    </Col>
</Row>
