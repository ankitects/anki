<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { CsvMetadata_Delimiter as Delimiter } from "@tslib/anki/import_export_pb";
    import * as tr from "@tslib/ftl";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";

    export let delimiter: Delimiter;
    export let disabled: boolean;

    const delimiters = [
        { value: Delimiter.TAB, label: tr.importingTab() },
        { value: Delimiter.PIPE, label: tr.importingPipe() },
        { value: Delimiter.SEMICOLON, label: tr.importingSemicolon() },
        { value: Delimiter.COLON, label: tr.importingColon() },
        { value: Delimiter.COMMA, label: tr.importingComma() },
        { value: Delimiter.SPACE, label: tr.studyingSpace() },
    ];

    $: label = delimiters.find((d) => d.value === delimiter)?.label;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingFieldSeparator()}
    </Col>
    <Col --col-size={1}>
        <Select bind:value={delimiter} {disabled} {label}>
            {#each delimiters as { value, label }}
                <SelectOption {value}>{label}</SelectOption>
            {/each}
        </Select>
    </Col>
</Row>
