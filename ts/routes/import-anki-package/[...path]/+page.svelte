<script lang="ts">
    import ImportAnkiPackagePage from "../ImportAnkiPackagePage.svelte";
    import type { PageData } from "./$types";
    export let data: PageData;
</script>

<ImportAnkiPackagePage path={data.path} options={data.options} />
