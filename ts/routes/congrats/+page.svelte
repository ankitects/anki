<script lang="ts">
    import CongratsPage from "./CongratsPage.svelte";
    import type { PageData } from "./$types";

    export let data: PageData;
</script>

<CongratsPage info={data.info} />
