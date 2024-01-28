<script lang="ts">
    import { page } from "$app/stores";
    import ChangeNotetypePage from "../ChangeNotetypePage.svelte";
    import type { PageData } from "./$types";

    export let data: PageData;
</script>

<ChangeNotetypePage state={data.state} />
