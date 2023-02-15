<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { ImportExport } from "@tslib/proto";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";

    export let matchScope: ImportExport.CsvMetadata.MatchScope;

    const matchScopes = [
        {
            value: ImportExport.CsvMetadata.MatchScope.NOTETYPE,
            label: tr.notetypesNotetype(),
        },
        {
            value: ImportExport.CsvMetadata.MatchScope.NOTETYPE_AND_DECK,
            label: tr.importingNotetypeAndDeck(),
        },
    ];

    $: label = matchScopes.find((r) => r.value === matchScope)?.label;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingMatchScope()}
    </Col>
    <Col --col-size={1}>
        <Select bind:value={matchScope} {label}>
            {#each matchScopes as { label, value }}
                <SelectOption {value}>{label}</SelectOption>
            {/each}
        </Select>
    </Col>
</Row>
