<script lang="ts">
    import { page } from "$app/stores";
    import CardInfo from "../CardInfo.svelte";
    import type { PageData } from "./$types";

    export let data: PageData;

    const showRevlog = $page.url.searchParams.get("revlog") !== "0";
</script>

<CardInfo stats={data.info} {showRevlog} />
