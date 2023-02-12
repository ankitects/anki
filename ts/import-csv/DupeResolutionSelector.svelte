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

    export let dupeResolution: ImportExport.CsvMetadata.DupeResolution;

    const dupeResolutions = [
        {
            value: ImportExport.CsvMetadata.DupeResolution.UPDATE,
            label: tr.importingUpdate(),
        },
        {
            value: ImportExport.CsvMetadata.DupeResolution.DUPLICATE,
            label: tr.importingDuplicate(),
        },
        {
            value: ImportExport.CsvMetadata.DupeResolution.PRESERVE,
            label: tr.importingPreserve(),
        },
    ];

    $: label = dupeResolutions.find((r) => r.value === dupeResolution)?.label;
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingExistingNotes()}
    </Col>
    <Col --col-size={1}>
        <Select bind:value={dupeResolution} {label}>
            {#each dupeResolutions as { label, value }}
                <SelectOption {value}>{label}</SelectOption>
            {/each}
        </Select>
    </Col>
</Row>
