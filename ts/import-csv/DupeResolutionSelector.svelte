<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import { ImportExport } from "../lib/proto";

    export let dupeResolution: ImportExport.ImportCsvRequest.DupeResolution;

    const dupeResolutions = [
        {
            value: ImportExport.ImportCsvRequest.DupeResolution.ADD,
            label: tr.importingDuplicate(),
        },
        {
            value: ImportExport.ImportCsvRequest.DupeResolution.IGNORE,
            label: tr.importingPreserve(),
        },
        {
            value: ImportExport.ImportCsvRequest.DupeResolution.UPDATE,
            label: tr.importingUpdate(),
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
