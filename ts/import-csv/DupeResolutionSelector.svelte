<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { ImportExport } from "@tslib/proto";
    
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";

    export let dupeResolution: ImportExport.CsvMetadata.DupeResolution;

    const dupeResolutions = [
        {
            value: ImportExport.CsvMetadata.DupeResolution.UPDATE,
            label: tr.importingUpdate(),
        },
        {
            value: ImportExport.CsvMetadata.DupeResolution.ADD,
            label: tr.importingDuplicate(),
        },
        {
            value: ImportExport.CsvMetadata.DupeResolution.IGNORE,
            label: tr.importingPreserve(),
        },
    ];
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingExistingNotes()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={dupeResolution}>
            {#each dupeResolutions as { label, value }}
                <option {value}>{label}</option>
            {/each}
        </select>
    </Col>
</Row>
