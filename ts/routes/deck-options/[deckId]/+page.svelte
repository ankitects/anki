<script lang="ts">
    import DeckOptionsPage from "../DeckOptionsPage.svelte";
    import type { PageData } from "./$types";
    export let data: PageData;
</script>

<DeckOptionsPage state={data.state} />
