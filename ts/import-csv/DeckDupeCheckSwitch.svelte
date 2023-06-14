<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { CsvMetadata_MatchScope } from "@tslib/anki/import_export_pb";
    import * as tr from "@tslib/ftl";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import Select from "../components/Select.svelte";
    import SelectOption from "../components/SelectOption.svelte";

    export let matchScope: CsvMetadata_MatchScope;

    const matchScopes = [
        {
            value: CsvMetadata_MatchScope.NOTETYPE,
            label: tr.notetypesNotetype(),
        },
        {
            value: CsvMetadata_MatchScope.NOTETYPE_AND_DECK,
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
