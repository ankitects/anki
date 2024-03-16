<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "$lib/components/Row.svelte";

    import ImportPage from "../import-page/ImportPage.svelte";
    import FieldMapper from "./FieldMapper.svelte";
    import FileOptions from "./FileOptions.svelte";
    import ImportOptions from "./ImportOptions.svelte";
    import type { ImportCsvState } from "./lib";

    export let state: ImportCsvState;
</script>

<ImportPage path={state.path} importer={state}>
    <Row><FileOptions {state} /></Row>
    <Row><ImportOptions {state} /></Row>
    <Row><FieldMapper {state} /></Row>
</ImportPage>
