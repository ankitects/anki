<script lang="ts">
    import ImportCsvPage from "../ImportCsvPage.svelte";
    import type { PageData } from "./$types";
    export let data: PageData;
</script>

<ImportCsvPage state={data.state} />
