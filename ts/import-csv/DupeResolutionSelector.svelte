<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import * as tr from "../lib/ftl";
    import { ImportExport } from "../lib/proto";

    const DupeResolution = ImportExport.ImportCsvRequest.DupeResolution;

    export let dupeResolution: ImportExport.ImportCsvRequest.DupeResolution =
        DupeResolution.IGNORE;

    const dupeResolutions = allResolutions();

    let index = dupeResolutions.findIndex((entry) => entry.value === dupeResolution);
    $: dupeResolution = dupeResolutions[index].value;

    interface IResolution {
        value: ImportExport.ImportCsvRequest.DupeResolution;
        label: string;
    }

    function allResolutions(): IResolution[] {
        return [
            {
                value: DupeResolution.ADD,
                label: tr.importingImportEvenIfExistingNoteHas(),
            },
            {
                value: DupeResolution.IGNORE,
                label: tr.importingIgnoreLinesWhereFirstFieldMatches(),
            },
            {
                value: DupeResolution.UPDATE,
                label: tr.importingUpdateExistingNotesWhenFirstField(),
            },
        ];
    }
</script>

<Row --cols={2}>
    <Col --col-size={1}>
        {tr.importingDuplicateResolution()}
    </Col>
    <Col --col-size={1}>
        <!-- svelte-ignore a11y-no-onchange -->
        <select class="form-select" bind:value={index}>
            {#each dupeResolutions as { label }, idx}
                <option value={idx}>{label}</option>
            {/each}
        </select>
    </Col>
</Row>
