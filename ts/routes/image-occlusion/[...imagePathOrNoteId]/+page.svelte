<script lang="ts">
    import ImageOcclusionPage from "../ImageOcclusionPage.svelte";
    import type { PageData } from "./$types";
    export let data: PageData;
</script>

<ImageOcclusionPage mode={data.mode} />
