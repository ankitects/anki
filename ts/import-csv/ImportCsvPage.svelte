<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ImportPage from "../components/ImportPage.svelte";
    import FieldMapper from "./FieldMapper.svelte";
    import FileOptions from "./FileOptions.svelte";
    import ImportOptions from "./ImportOptions.svelte";
    import type { ImportCsvState } from "./lib";

    export let state: ImportCsvState;
    export let path: string;
</script>

<ImportPage {path} doImport={state.importCsv}>
    <FileOptions {state} />
    <ImportOptions {state} />
    <FieldMapper {state} />
</ImportPage>
